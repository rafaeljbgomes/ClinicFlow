"""Authenticated, bounded ZAP Automation Framework scans in a disposable HTTPS network.
Only sanitized findings/coverage leave the container. Credentials and plans remain ephemeral.
"""
from datetime import datetime, timedelta, timezone
import http.cookies
import json
import os
from pathlib import Path
import re
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
DEMO_BOOTSTRAP = Path("/opt/clinicflow/DemoDataBootstrap.java")
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


def demo_password():
    # Reuse the exact fixture that creates these disposable accounts; do not duplicate credentials.
    match = re.search(r'DEMO_PASSWORD\s*=\s*"([^"]+)"', DEMO_BOOTSTRAP.read_text())
    require(match is not None, "Synthetic bootstrap credential definition changed")
    return match.group(1)


def authenticate(role):
    body = {"email": ROLES[role], "password": demo_password()}
    raw, _ = request(DIRECT, API + "/api/v1/auth/login", "POST", body)
    token = json.loads(raw)["accessToken"]
    require(isinstance(token, str) and len(token) > 20, "Bearer login did not produce a token")
    _, headers = request(DIRECT, UI + "/api/auth/login", "POST", body)
    cookies = http.cookies.SimpleCookie()
    for line in headers.get_all("Set-Cookie", []):
        cookies.load(line)
    require(all(name in cookies for name in ("clinicflow_access_token", "clinicflow_csrf")), "BFF session cookies missing")
    require(cookies["clinicflow_access_token"]["httponly"], "Access token cookie is not HttpOnly")
    for cookie in cookies.values():
        require(cookie["secure"] and cookie["samesite"].lower() == "lax", "BFF cookie transport protections missing")
    set_rule("api-auth", "Authorization", "Bearer " + token, re.escape(API) + r"/api/v1/(?!auth/(?:login|register)(?:$|[/?])).*")
    set_rule("bff-cookie", "Cookie", "; ".join(name + "=" + item.value for name, item in cookies.items()), re.escape(UI) + r"/.*")
    set_rule("bff-csrf", "x-csrf-token", cookies["clinicflow_csrf"].value, re.escape(UI) + r"/api/.*")
    request(PROXY, API + "/api/v1/auth/users/me")
    request(PROXY, UI + "/api/auth/me")


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
        public_login = re.match(r"[A-Z]+\s+(?:https?://[^/]+)?/(?:api/v1/auth|api/auth)/(?:login|register)(?:[?\s])", header)
        if match and not public_login:
            statuses.append(int(match.group(1)))
    require(statuses and sum(code in (401, 403) for code in statuses) <= len(statuses) / 2, "Active traffic predominantly lost authenticated access")
    require(all(urllib.parse.urlsplit(origin).netloc in origins for origin in (API, UI)), "Active API/BFF traffic missing")
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
                authenticate(role)
                # Seed public JSON login bodies for active authentication-endpoint scanning.
                login_body = {"email": ROLES[role], "password": demo_password()}
                request(PROXY, API + "/api/v1/auth/login", "POST", login_body)
                request(PROXY, UI + "/api/auth/login", "POST", login_body)
                extra = [API + "/api/v1/auth/login", UI + "/api/auth/login"]
                urls = [API + "/api/v1/auth/users/me", UI + "/api/auth/me"]
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
