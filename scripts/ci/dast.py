"""Authenticated, bounded ZAP Automation Framework scans in a disposable HTTPS network.
Only sanitized findings/coverage leave the container. Credentials and plans remain ephemeral.
"""
import base64
from datetime import datetime, timedelta, timezone
import hashlib
import html
import http.cookiejar
import http.cookies
import json
import os
from pathlib import Path
import re
import secrets
import ssl
import subprocess
import sys
import time
import traceback
import urllib.error
import urllib.parse
import urllib.request

API = "https://api.clinicflow.localhost:8443"
UI = "https://clinicflow.localhost:8443"
ZAP = "http://127.0.0.1:8090"
ROLES = {
    "psychologist": "sofia.almeida@demo.clinicflow.local",
    "patient": "ana.martins@demo.clinicflow.local",
    "administrator": "admin@demo.clinicflow.local",
}
REALM_ROLES = {"psychologist": "PSYCHOLOGIST", "patient": "PATIENT", "administrator": "ADMIN"}
# Keycloak on the private network (ADR-027). Tokens fetched here still carry the public issuer.
KEYCLOAK = "http://keycloak:8080"
REALM = KEYCLOAK + "/realms/clinicflow"
BFF_CLIENT = "clinicflow-bff"
# Registered on the BFF client. The code is read from the redirect, which is never followed.
REDIRECT_URI = "http://localhost:3000/api/auth/callback"
# Every API route is a resource server that trusts only Keycloak (ADR-027).
RESOURCE_SERVER_PATHS = re.escape(API) + r"/api/v1/.*"
REPORTS = Path("/zap/wrk/reports")
DIRECT = None
PROXY = None
RULES = set()


def require(ok, message):
    if not ok:
        raise RuntimeError(message)


def request(opener, url, method="GET", body=None, headers=None, expected=200):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers={"Content-Type": "application/json", **(headers or {})})
    # Diagnostics name the endpoint and status only: no query strings, bodies, cookies or tokens.
    target = urllib.parse.urlsplit(url)
    try:
        with opener.open(req, timeout=30) as response:
            require(response.status == expected, method + " " + target.netloc + target.path + " returned HTTP " + str(response.status) + ", expected " + str(expected))
            return response.read(), response.headers
    except urllib.error.HTTPError as error:
        raise RuntimeError(method + " " + target.netloc + target.path + " returned HTTP " + str(error.code) + ", expected " + str(expected)) from None


def form_request(opener, url, fields=None, headers=None, expected=200):
    # Keycloak takes form bodies. Diagnostics name the endpoint and status only, as in request().
    data = urllib.parse.urlencode(fields).encode() if fields is not None else None
    target = urllib.parse.urlsplit(url)
    try:
        with opener.open(urllib.request.Request(url, data=data, headers=headers or {}), timeout=30) as response:
            status, body, response_headers = response.status, response.read(), response.headers
    except urllib.error.HTTPError as error:
        status, body, response_headers = error.code, b"", error.headers
    require(status == expected, ("POST " if data else "GET ") + target.netloc + target.path + " returned HTTP " + str(status) + ", expected " + str(expected))
    return body, response_headers


class PrivateNetworkCookies(http.cookiejar.DefaultCookiePolicy):
    # Keycloak marks its cookies Secure even over HTTP; this login stays on the disposable private network.
    def return_ok_secure(self, cookie, request):
        return urllib.parse.urlsplit(request.get_full_url()).netloc == urllib.parse.urlsplit(KEYCLOAK).netloc or super().return_ok_secure(cookie, request)


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def b64url(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def token_claims(token):
    payload = token.split(".")[1]
    return json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))


def private_url(url):
    # Keycloak writes its public URL (KC_HOSTNAME) into redirects and forms; this container reaches the same
    # server on the private network.
    public = urllib.parse.urlsplit(url)
    private = urllib.parse.urlsplit(KEYCLOAK)
    return urllib.parse.urlunsplit((private.scheme, private.netloc, public.path, public.query, ""))


def login_form_action(page):
    tag = re.search(r'<form[^>]*id="kc-form-login"[^>]*>', page)
    action = re.search(r'action="([^"]+)"', tag.group(0)) if tag else None
    require(action is not None, "Keycloak login form missing")
    return private_url(html.unescape(action.group(1)))


def keycloak_login(role, authorization_url):
    # Plays the browser on Keycloak's pages and returns the redirect back to the client, unfollowed.
    browser = urllib.request.build_opener(urllib.request.ProxyHandler({}), urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar(PrivateNetworkCookies())), NoRedirect())
    page, _ = form_request(browser, private_url(authorization_url))
    credentials = {"username": ROLES[role], "password": os.environ.get("CLINICFLOW_DEMO_PASSWORD", "")}
    _, headers = form_request(browser, login_form_action(page.decode()), credentials, expected=302)
    return headers.get("Location", "")


def keycloak_token(role):
    # Authorization code with PKCE as the BFF client, the only flow the realm allows (ADR-027).
    verifier, state = b64url(secrets.token_bytes(32)), b64url(secrets.token_bytes(16))
    query = urllib.parse.urlencode({"response_type": "code", "client_id": BFF_CLIENT, "redirect_uri": REDIRECT_URI, "scope": "openid", "state": state,
                                    "code_challenge": b64url(hashlib.sha256(verifier.encode()).digest()), "code_challenge_method": "S256"})
    location = keycloak_login(role, REALM + "/protocol/openid-connect/auth?" + query)
    params = urllib.parse.parse_qs(urllib.parse.urlsplit(location).query)
    require(params.get("state") == [state] and "code" in params, "Keycloak login did not return the authorization code")
    secret = os.environ.get("CLINICFLOW_BFF_CLIENT_SECRET", "")
    require(len(secret) > 20, "BFF client secret is not configured")
    basic = "Basic " + base64.b64encode((BFF_CLIENT + ":" + secret).encode()).decode()
    raw, _ = form_request(DIRECT, REALM + "/protocol/openid-connect/token", {"grant_type": "authorization_code", "code": params["code"][0], "redirect_uri": REDIRECT_URI, "code_verifier": verifier}, {"Authorization": basic})
    token = json.loads(raw)["access_token"]
    require(token_claims(token).get("roles") == [REALM_ROLES[role]], "Keycloak token does not carry the " + role + " role")
    return token


def session_cookies(set_cookie_lines):
    # The BFF session: tokens in HttpOnly cookies, everything Secure and SameSite=Lax; spent cookies are dropped.
    cookies = http.cookies.SimpleCookie()
    for line in set_cookie_lines:
        cookies.load(line)
    require(all(name in cookies and cookies[name].value for name in ("clinicflow_access_token", "clinicflow_refresh_token", "clinicflow_csrf")), "BFF session cookies missing")
    require(cookies["clinicflow_access_token"]["httponly"] and cookies["clinicflow_refresh_token"]["httponly"], "BFF token cookies are not HttpOnly")
    for cookie in cookies.values():
        require(cookie["secure"] and cookie["samesite"].lower() == "lax", "BFF cookie transport protections missing")
    return {name: item.value for name, item in cookies.items() if item.value}


def bff_session(role):
    # The BFF's authorization-code login (ADR-027): its redirect to Keycloak, the login on the private
    # network, and the callback that sets the session cookies.
    browser = urllib.request.build_opener(urllib.request.ProxyHandler({}), urllib.request.HTTPSHandler(context=ssl.create_default_context(cafile="/certs/ca.crt")),
                                          urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()), NoRedirect())
    _, headers = form_request(browser, UI + "/api/auth/login", expected=303)
    callback = keycloak_login(role, headers.get("Location", ""))
    require(callback.startswith(UI + "/api/auth/callback?"), "Keycloak did not return to the BFF callback")
    _, headers = form_request(browser, callback, expected=303)
    require(urllib.parse.urlsplit(headers.get("Location", "")).path.startswith("/dashboard"), "BFF callback did not sign in")
    return session_cookies(headers.get_all("Set-Cookie", []))


def summarize(messages):
    # ZAP plan messages name jobs and URLs; strip query strings and bound the length.
    return "; ".join(re.sub(r"\?\S*", "", str(message))[:200] for message in (messages or [])[:3])


def zap_api(section, operation, **params):
    url = ZAP + "/JSON/" + section + "/" + operation + "/?" + urllib.parse.urlencode(params)
    with urllib.request.urlopen(url, timeout=30) as response:
        data = json.load(response)
    require("code" not in data, "ZAP API operation failed: " + section + "/" + operation)
    return data


def set_rule(name, header, replacement, pattern):
    if name in RULES:
        zap_api("replacer", "action/removeRule", description=name)
    zap_api("replacer", "action/addRule", description=name, enabled="true", matchType="REQ_HEADER", matchRegex="false", matchString=header, replacement=replacement, url=pattern)
    RULES.add(name)


def authenticate(role):
    # Returns the role's Keycloak user id. The API endpoints in scope verify the bearer rule when they are
    # requested; /api/auth/me verifies the BFF session.
    token = keycloak_token(role)
    cookies = bff_session(role)
    set_rule("api-auth", "Authorization", "Bearer " + token, RESOURCE_SERVER_PATHS)
    set_rule("bff-cookie", "Cookie", "; ".join(name + "=" + value for name, value in cookies.items()), re.escape(UI) + r"/.*")
    set_rule("bff-csrf", "x-csrf-token", cookies["clinicflow_csrf"], re.escape(UI) + r"/api/.*")
    request(PROXY, UI + "/api/auth/me")
    return token_claims(token)["sub"]


def traffic_snapshot():
    return {str(scan["id"]) for scan in zap_api("ascan", "view/scans")["scans"]}


def verify_traffic(before, urls):
    scans = [scan for scan in zap_api("ascan", "view/scans")["scans"] if str(scan["id"]) not in before]
    require(scans, "Automation produced no active scan")
    message_ids = []
    progress = []
    for scan in scans:
        # A scan stopped by its time budget ends FINISHED below 100%; its progress is reported, not hidden.
        require(str(scan.get("state", "")).upper() == "FINISHED", "Active scan ended in state " + str(scan.get("state")))
        progress.append(int(scan.get("progress", 0)))
        response = zap_api("ascan", "view/messagesIds", scanId=scan["id"])
        lists = [value for value in response.values() if isinstance(value, list)]
        require(len(lists) == 1, "Active message inventory missing")
        message_ids.extend(str(value) for value in lists[0])
    message_ids = list(dict.fromkeys(message_ids))
    require(len(message_ids) > len(urls), "Active scan produced no traffic beyond discovery")
    # Sample actual scanner messages, not proxy-history authentication probes.
    stride = max(1, len(message_ids) // 50)
    sampled = message_ids[::stride][:50]
    # The list's response key differs across ZAP versions, so require exactly one list as for messagesIds.
    response = zap_api("core", "view/messagesById", ids=",".join(sampled))
    lists = [value for value in response.values() if isinstance(value, list)]
    require(len(lists) == 1, "Active message sample missing")
    messages = lists[0]
    statuses = []
    origins = set()
    for message in messages:
        header = message.get("requestHeader", "")
        host = re.search(r"(?im)^Host:\s*([^\r\n]+)", header)
        if host:
            origins.add(host.group(1).strip())
        match = re.match(r"HTTP/[^ ]+ (\d+)", message.get("responseHeader", ""))
        public_login = re.match(r"[A-Z]+\s+(?:https?://[^/]+)?/api/auth/(?:login|register)(?:[?\s])", header)
        if match and not public_login:
            statuses.append(int(match.group(1)))
    require(statuses and sum(code in (401, 403) for code in statuses) <= len(statuses) / 2, "Active traffic predominantly lost authenticated access")
    # The BFF is always in scope; the API only for a role with an API endpoint in scope.
    require(all(urllib.parse.urlsplit(url).netloc in origins for url in urls), "Active API/BFF traffic missing")
    return {"requests": len(message_ids), "sampleSize": len(statuses), "rejected": sum(code in (401, 403) for code in statuses), "progress": min(progress)}


def run_plan(role, urls, full_scan, extra):
    name = "clinicflow-" + role
    includes = [re.escape(url) + r"(?:/.*|\?.*)?" for url in urls + extra]
    plan = {
        "env": {"contexts": [{"name": name, "urls": urls, "includePaths": includes,
                               "excludePaths": [re.escape(UI) + r"/api/auth/logout.*"]}],
                "parameters": {"failOnError": True, "failOnWarning": True}},
        "jobs": [
            {"type": "passiveScan-config", "parameters": {"scanOnlyInScope": True}},
            {"type": "requestor", "requests": [{"url": url, "method": "GET", "responseCode": 200} for url in urls]},
            {"type": "activeScan", "parameters": {"context": name, "maxScanDurationInMins": 12 if full_scan else 2, "maxRuleDurationInMins": 2 if full_scan else 1, "addQueryParam": True, "scanHeadersAllRequests": True, "threadPerHost": 2, "delayInMs": 100},
             "policyDefinition": {"defaultStrength": "MEDIUM" if full_scan else "LOW", "defaultThreshold": "MEDIUM"}},
            {"type": "passiveScan-wait", "parameters": {"maxDuration": 2}},
        ],
    }
    filename = Path("/tmp") / (name + ".json")
    filename.write_text(json.dumps(plan))
    before = traffic_snapshot()
    plan_id = zap_api("automation", "action/runPlan", filePath=str(filename))["planId"]
    deadline = time.monotonic() + (900 if full_scan else 240)
    refresh_at = time.monotonic() + 45
    while time.monotonic() < deadline:
        progress = zap_api("automation", "view/planProgress", planId=plan_id)
        require(isinstance(progress.get("error"), list), "ZAP automation progress schema is incomplete")
        require(not progress["error"], "ZAP automation reported an error: " + summarize(progress["error"]))
        require(not progress.get("warn"), "ZAP automation reported a warning; scan is incomplete: " + summarize(progress.get("warn")))
        if re.match(r"^\d{4}-\d{2}-\d{2}T", str(progress.get("finished", ""))):
            authenticate(role)
            return verify_traffic(before, urls)
        if time.monotonic() >= refresh_at:
            authenticate(role)
            refresh_at = time.monotonic() + 45
        time.sleep(2)
    raise RuntimeError("ZAP automation exceeded its bounded scan duration")


def main():
    global DIRECT, PROXY
    os.environ["NO_PROXY"] = os.environ["no_proxy"] = ""
    REPORTS.mkdir(parents=True, exist_ok=True)
    DIRECT = urllib.request.build_opener(urllib.request.ProxyHandler({}), urllib.request.HTTPSHandler(context=ssl.create_default_context(cafile="/certs/ca.crt")))
    deadline = time.monotonic() + 240
    while True:
        try:
            request(DIRECT, UI + "/login")
            break
        except (OSError, urllib.error.URLError, RuntimeError):
            require(time.monotonic() < deadline, "HTTPS application readiness failed")
            time.sleep(2)
    with open("/tmp/zap.log", "w") as log:
        # -silent skips the add-on update and telemetry calls that made startup time depend on the network.
        zap = subprocess.Popen(["/zap/zap.sh", "-daemon", "-silent", "-host", "127.0.0.1", "-port", "8090", "-config", "api.disablekey=true", "-config", "connection.timeoutInSecs=10"], stdout=log, stderr=subprocess.STDOUT)
        try:
            deadline = time.monotonic() + 300
            while True:
                try:
                    version = zap_api("core", "view/version")["version"]
                    break
                except (OSError, urllib.error.URLError):
                    require(zap.poll() is None, "ZAP exited during startup with code " + str(zap.poll()))
                    require(time.monotonic() < deadline, "ZAP API did not answer within 300 seconds of startup")
                    time.sleep(2)
            root_bytes = urllib.request.urlopen(ZAP + "/OTHER/core/other/rootcert/", timeout=30).read()
            root_cert = root_bytes.decode() if root_bytes.startswith(b"-----BEGIN") else ssl.DER_cert_to_PEM_cert(root_bytes)
            PROXY = urllib.request.build_opener(urllib.request.ProxyHandler({"https": ZAP}), urllib.request.HTTPSHandler(context=ssl.create_default_context(cadata=root_cert)))
            manifest = json.loads(Path("/opt/clinicflow/requests.json").read_text())
            selected = set(os.environ["DAST_COMPONENTS"].split(","))
            full_scan = os.environ.get("DAST_FULL_SCAN") == "true"
            coverage = []
            for role in ROLES:
                user_id = authenticate(role)
                # The BFF takes no passwords; its login is a redirect to Keycloak (ADR-027).
                extra = []
                urls = [UI + "/api/auth/me"]
                if role == "administrator":
                    # The user lookup reads Keycloak's Admin API through a read-only service account (ADR-031).
                    urls += [UI + "/api/users/" + user_id, UI + "/dashboard/system"]
                for item in manifest["requests"]:
                    if item["component"] in selected and role in item["roles"]:
                        raw, _ = request(PROXY, API + item["path"])
                        urls.append(API + item["path"])
                        request(PROXY, UI + item["bff"])
                        urls.append(UI + item["bff"])
                        if role == "psychologist" and item.get("fixture") == "patients":
                            patients = json.loads(raw)
                            require(isinstance(patients, list) and patients, "Synthetic patient discovery is empty")
                            patient_id = patients[0]["id"]
                            urls += [API + "/api/v1/patients/" + patient_id,
                                     API + "/api/v1/clinical/patients/" + patient_id + "/clinical-history"] if "clinical-service" in selected else [API + "/api/v1/patients/" + patient_id]
                        if role == "psychologist" and item.get("fixture") == "cases":
                            # The case list is paged (ADR-029); the first page is enough to pick a fixture.
                            cases = json.loads(raw).get("content")
                            require(isinstance(cases, list) and cases, "Synthetic clinical discovery is empty")
                            case_id = cases[0]["id"]
                            urls += [API + "/api/v1/clinical/clinical-cases/" + case_id,
                                     API + "/api/v1/clinical/clinical-cases/" + case_id + "/session-records"]
                if role == "psychologist":
                    # Exercise JSON mutation bodies so active scanning covers more than collection GETs.
                    raw, _ = request(PROXY, API + "/api/v1/patients")
                    existing = json.loads(raw)
                    require(isinstance(existing, list) and existing, "Synthetic patient fixtures missing")
                    patient_id = existing[0]["id"]
                    stamp = str(time.time_ns())
                    # A fresh synthetic patient keeps case creation clear of the one-active-case rule.
                    body = {"email": "dast." + stamp + "@demo.clinicflow.local", "fullName": "Synthetic DAST Patient", "consentStatus": "GRANTED"}
                    raw, _ = request(PROXY, API + "/api/v1/patients", "POST", body, expected=201)
                    fresh_patient_id = json.loads(raw)["id"]
                    if "patient-service" in selected:
                        body["email"] = "dast.bff." + stamp + "@demo.clinicflow.local"
                        request(PROXY, UI + "/api/patients", "POST", body, expected=201)
                        extra += [API + "/api/v1/patients", UI + "/api/patients"]
                    if "appointment-service" in selected:
                        when = (datetime.now(timezone.utc) + timedelta(days=30)).isoformat()
                        request(PROXY, API + "/api/v1/appointments", "POST", {"patientId": patient_id, "scheduledAt": when, "type": "IN_PERSON"}, expected=201)
                        extra.append(API + "/api/v1/appointments")
                    if "clinical-service" in selected:
                        raw, _ = request(PROXY, API + "/api/v1/clinical/clinical-cases", "POST", {"patientId": fresh_patient_id, "presentingConcern": "Synthetic DAST case"}, expected=201)
                        case_id = json.loads(raw)["id"]
                        sessions = API + "/api/v1/clinical/clinical-cases/" + case_id + "/session-records"
                        raw, _ = request(PROXY, sessions, "POST", {"sessionDate": datetime.now(timezone.utc).isoformat(), "modality": "IN_PERSON", "durationMinutes": 30, "attendanceStatus": "ATTENDED", "summary": "Synthetic DAST note"}, expected=201)
                        extra += [API + "/api/v1/clinical/clinical-cases", sessions]
                        urls += [API + "/api/v1/clinical/clinical-cases/" + case_id, sessions]
                # Record BFF responses under the authenticated session, including SSR page HTML.
                for url in list(urls):
                    request(PROXY, url)
                if role == "psychologist":
                    for route in ["/dashboard", "/dashboard/patients", "/dashboard/clinical"]:
                        request(PROXY, UI + route)
                        urls.append(UI + route)
                if os.environ.get("DAST_SMOKE_ONLY") != "true":
                    metrics = run_plan(role, list(dict.fromkeys(urls)), full_scan, extra)
                coverage.append({"role": role, "verifiedUrls": sorted(set(urls)), "authenticated": True, "traffic": metrics if os.environ.get("DAST_SMOKE_ONLY") != "true" else {}})
            alerts = zap_api("core", "view/alerts", start=0, count=100000)["alerts"]
            # Do not export requests, responses, evidence, query strings, or session values.
            sanitized = []
            for alert in alerts:
                url = urllib.parse.urlsplit(alert["url"])
                require(url.hostname in ("clinicflow.localhost", "api.clinicflow.localhost"), "Out-of-scope ZAP alert")
                sanitized.append({"id": "ZAP-" + str(alert["pluginId"]), "name": alert["name"],
                                  "risk": alert["risk"], "origin": "api" if url.hostname.startswith("api.") else "ui",
                                  "path": url.path, "role": "all"})
            report = {"schemaVersion": 1, "complete": os.environ.get("DAST_SMOKE_ONLY") != "true",
                      "zapVersion": version, "components": sorted(selected), "coverage": coverage, "alerts": sanitized}
            (REPORTS / "zap.json").write_text(json.dumps(report, indent=2))
            print("Authenticated HTTPS DAST finished; sanitized coverage and alerts saved.")
        finally:
            zap.terminate()
            try:
                zap.wait(timeout=20)
            except subprocess.TimeoutExpired:
                zap.kill()


def failure_detail(error):
    # Messages raised by this script never contain credentials or response data. For data-shape errors,
    # name only the failing line and missing key, never values taken from responses.
    if isinstance(error, RuntimeError):
        return str(error)
    if isinstance(error, (KeyError, IndexError, TypeError, ValueError)) and error.__traceback__:
        frame = traceback.extract_tb(error.__traceback__)[-1]
        missing = ": missing key " + repr(error.args[0])[:80] if isinstance(error, KeyError) and error.args else ""
        return "line " + str(frame.lineno) + " in " + frame.name + missing
    return ""


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        REPORTS.mkdir(parents=True, exist_ok=True)
        detail = failure_detail(error)
        (REPORTS / "failure.json").write_text(json.dumps({"complete": False, "errorType": type(error).__name__, "detail": detail}))
        print("DAST failed: " + type(error).__name__ + (": " + detail if detail else "") + ". No passing scan result was produced.", file=sys.stderr)
        sys.exit(1)
