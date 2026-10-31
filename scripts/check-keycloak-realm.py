"""Checks the running Compose Keycloak against the ADR-027 token contract (issue #86).

Standard library only. Run from the repository root after `docker compose up`:

    py -I scripts/check-keycloak-realm.py

It plays the browser and the BFF against the public URL: an authorization-code login with PKCE for
every demo user, the code exchange with the generated client secret, a signature check against the
realm JWKS, refresh-token rotation, the refused password grant, and a self-registration. It checks
that the user-lookup service account can read users and nothing more (ADR-031). It then deletes the
user it registered. Secrets are read from secrets/keycloak/ and are never printed.
"""
import argparse
import base64
import hashlib
import html
import http.cookiejar
import json
import os
import pathlib
import re
import secrets
import sys
import urllib.error
import urllib.parse
import urllib.request

REALM = "clinicflow"
CLIENT_ID = "clinicflow-bff"
LOOKUP_CLIENT_ID = "clinicflow-user-lookup"
REDIRECT_URI = "http://localhost:3000/api/auth/callback"
# Synthetic and documented in docs/development/local-development.md.
DEMO_PASSWORD = os.environ.get("CLINICFLOW_DEMO_PASSWORD", "ClinicFlowDemo!2026")
DEMO_USERS = {
    "admin@demo.clinicflow.local": ("00000000-0000-4000-8000-000000000001", "ADMIN"),
    "sofia.almeida@demo.clinicflow.local": ("00000000-0000-4000-8000-000000000011", "PSYCHOLOGIST"),
    "miguel.santos@demo.clinicflow.local": ("00000000-0000-4000-8000-000000000012", "PSYCHOLOGIST"),
    "ana.martins@demo.clinicflow.local": ("00000000-0000-4000-8000-000000000021", "PATIENT"),
    "joao.ferreira@demo.clinicflow.local": ("00000000-0000-4000-8000-000000000022", "PATIENT"),
}
REMOVED_CLAIMS = ("userId", "role", "realm_access", "resource_access")
SHA256_DIGEST_INFO = bytes.fromhex("3031300d060960864801650304020105000420")
SECRETS_DIR = pathlib.Path(__file__).resolve().parent.parent / "secrets" / "keycloak"

failures = []


def check(condition, message):
    print(("  ok    " if condition else "  FAIL  ") + message)
    if not condition:
        failures.append(message)


class LocalhostIsSecure(http.cookiejar.DefaultCookiePolicy):
    """Keycloak marks its cookies Secure even over HTTP. Browsers send them to http://localhost, a
    secure context; Python's default policy does not."""

    def return_ok_secure(self, cookie, request):
        return urllib.parse.urlparse(request.get_full_url()).hostname == "localhost" \
            or super().return_ok_secure(cookie, request)


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def read_env(name):
    values = {}
    for line in (SECRETS_DIR / name).read_text().splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            values[key] = value
    return values


def b64url(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def b64url_decode(value):
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))


def request(opener, url, data=None, headers=None, method=None):
    body = urllib.parse.urlencode(data).encode() if isinstance(data, dict) else data
    req = urllib.request.Request(url, data=body, headers=headers or {}, method=method)
    try:
        with opener.open(req) as response:
            return response.status, dict(response.headers), response.read().decode()
    except urllib.error.HTTPError as error:
        return error.code, dict(error.headers), error.read().decode()


def form_action(page, form_id):
    tag = re.search(r'<form[^>]*id="%s"[^>]*>' % re.escape(form_id), page)
    if not tag:
        sys.exit(f"form {form_id} not found")
    return html.unescape(re.search(r'action="([^"]+)"', tag.group(0)).group(1))


def feedback(page):
    match = re.search(r'kc-feedback-text[^>]*>\s*([^<]*\S)\s*<', page)
    return html.unescape(match.group(1)) if match else ""


def authorize(public, credentials=None, registration=None):
    """Browser leg: authorization request with PKCE, then the login or registration form."""
    opener = urllib.request.build_opener(
        urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar(LocalhostIsSecure())), NoRedirect())
    verifier = b64url(secrets.token_bytes(32))
    state, nonce = b64url(secrets.token_bytes(16)), b64url(secrets.token_bytes(16))
    query = urllib.parse.urlencode({
        "response_type": "code", "client_id": CLIENT_ID, "redirect_uri": REDIRECT_URI,
        "scope": "openid", "state": state, "nonce": nonce,
        "code_challenge": b64url(hashlib.sha256(verifier.encode()).digest()),
        "code_challenge_method": "S256",
    })
    status, _, page = request(opener, f"{public}/realms/{REALM}/protocol/openid-connect/auth?{query}")
    if status != 200:
        sys.exit(f"authorization endpoint returned {status}")
    if registration is not None:
        link = re.search(r'href="([^"]*login-actions/registration[^"]*)"', page)
        _, _, page = request(opener, urllib.parse.urljoin(public, html.unescape(link.group(1))))
        status, headers, page = request(opener, form_action(page, "kc-register-form"), registration)
    else:
        status, headers, page = request(opener, form_action(page, "kc-form-login"), credentials)
    if status != 302:
        return None, feedback(page)
    location = urllib.parse.urlparse(headers["Location"])
    params = urllib.parse.parse_qs(location.query)
    if params.get("state") != [state]:
        sys.exit("state mismatch")
    return {"code": params["code"][0], "verifier": verifier, "nonce": nonce,
            "iss": params.get("iss", [None])[0]}, None


def verify_rs256(token, jwks):
    header_b64, payload_b64, signature_b64 = token.split(".")
    header = json.loads(b64url_decode(header_b64))
    key = next((k for k in jwks["keys"] if k["kid"] == header.get("kid")), None)
    if header.get("alg") != "RS256" or key is None:
        return False
    modulus = int.from_bytes(b64url_decode(key["n"]), "big")
    exponent = int.from_bytes(b64url_decode(key["e"]), "big")
    size = (modulus.bit_length() + 7) // 8
    encoded = pow(int.from_bytes(b64url_decode(signature_b64), "big"), exponent, modulus).to_bytes(size, "big")
    digest = hashlib.sha256(f"{header_b64}.{payload_b64}".encode()).digest()
    padding = b"\xff" * (size - 3 - len(SHA256_DIGEST_INFO) - len(digest))
    return encoded == b"\x00\x01" + padding + b"\x00" + SHA256_DIGEST_INFO + digest


def claims(token):
    return json.loads(b64url_decode(token.split(".")[1]))


def audiences(value):
    return value if isinstance(value, list) else [value]


def check_tokens(context, label, grant, expected_sub, expected_role):
    opener, token_endpoint, basic, issuer, jwks = context
    status, _, body = request(opener, token_endpoint, {
        "grant_type": "authorization_code", "code": grant["code"], "redirect_uri": REDIRECT_URI,
        "code_verifier": grant["verifier"]}, {"Authorization": basic})
    check(status == 200, f"{label}: code exchange with PKCE and the client secret")
    if status != 200:
        return None
    tokens = json.loads(body)
    access, id_token = claims(tokens["access_token"]), claims(tokens["id_token"])
    check(verify_rs256(tokens["access_token"], jwks), f"{label}: RS256 signature verifies against the JWKS")
    check(grant["iss"] == issuer, f"{label}: authorization response carries iss={issuer}")
    check(access.get("iss") == issuer, f"{label}: iss is the public issuer")
    if expected_sub:
        check(access.get("sub") == expected_sub, f"{label}: sub is the fixed id {expected_sub}")
    else:
        check(re.fullmatch(r"[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}",
                           access.get("sub", "")) is not None, f"{label}: sub is a v4 UUID")
    check("clinicflow-api" in audiences(access.get("aud")), f"{label}: aud contains clinicflow-api")
    check(access.get("roles") == [expected_role], f"{label}: roles is [\"{expected_role}\"], got {access.get('roles')}")
    check(access.get("email") == access.get("preferred_username"), f"{label}: email is the login email")
    check(access.get("azp") == CLIENT_ID, f"{label}: azp is {CLIENT_ID}")
    check(access.get("exp", 0) - access.get("iat", 0) == 300, f"{label}: access token lives 5 minutes")
    check(not [c for c in REMOVED_CLAIMS if c in access], f"{label}: no userId, role, realm_access or resource_access")
    check(audiences(id_token.get("aud")) == [CLIENT_ID] and "roles" not in id_token
          and id_token.get("nonce") == grant["nonce"], f"{label}: ID token is for the BFF, with the nonce and no roles")
    return tokens


def check_user_lookup(public, opener, token_endpoint, issuer):
    """ADR-031: client credentials only, a token no resource server accepts, and view-users only."""
    secret = read_env("clinicflow-user-lookup.env")["CLINICFLOW_USER_LOOKUP_CLIENT_SECRET"]
    basic = "Basic " + base64.b64encode(f"{LOOKUP_CLIENT_ID}:{secret}".encode()).decode()
    status, _, body = request(opener, token_endpoint, {"grant_type": "client_credentials"}, {"Authorization": basic})
    check(status == 200, "the lookup client gets a client-credentials token")
    if status != 200:
        return
    access = claims(json.loads(body)["access_token"])
    check(access.get("iss") == issuer and access.get("azp") == LOOKUP_CLIENT_ID, "lookup token: public issuer, azp is the lookup client")
    check("clinicflow-api" not in audiences(access.get("aud", [])) and "roles" not in access,
          "lookup token: no clinicflow-api audience and no roles claim")
    bearer = {"Authorization": "Bearer " + json.loads(body)["access_token"]}
    base = f"{public}/admin/realms/{REALM}"
    user_id, role = DEMO_USERS["sofia.almeida@demo.clinicflow.local"]
    status, _, body = request(opener, f"{base}/users/{user_id}", headers=bearer)
    check(status == 200 and json.loads(body).get("id") == user_id, "lookup client reads a user by id")
    status, _, body = request(opener, f"{base}/users/{user_id}/role-mappings/realm/composite", headers=bearer)
    check(status == 200 and role in [r["name"] for r in json.loads(body)], "lookup client reads the user's effective realm roles")
    for label, url, method, data in [
        ("change a user", f"{base}/users/{user_id}", "PUT", json.dumps({"enabled": True}).encode()),
        ("read clients", f"{base}/clients", "GET", None),
        ("read the realm's events", f"{base}/events", "GET", None),
        ("read the master realm", f"{public}/admin/realms/master/users", "GET", None),
    ]:
        status, _, _ = request(opener, url, data, {**bearer, "Content-Type": "application/json"}, method=method)
        check(status == 403, f"lookup client may not {label} ({status})")
    status, _, body = request(opener, token_endpoint, {
        "grant_type": "password", "username": "sofia.almeida@demo.clinicflow.local", "password": DEMO_PASSWORD},
        {"Authorization": basic})
    check(status in (400, 401) and json.loads(body).get("error") == "unauthorized_client",
          "the lookup client is refused the password grant")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--public", default="http://localhost:8180", help="KC_HOSTNAME of the stack")
    args = parser.parse_args()
    public, issuer = args.public, f"{args.public}/realms/{REALM}"
    client_secret = read_env("clinicflow-bff.env")["CLINICFLOW_BFF_CLIENT_SECRET"]
    admin = read_env("admin.env")
    opener = urllib.request.build_opener()
    basic = "Basic " + base64.b64encode(f"{CLIENT_ID}:{client_secret}".encode()).decode()

    print("Discovery and keys")
    _, _, body = request(opener, f"{issuer}/.well-known/openid-configuration")
    discovery = json.loads(body)
    check(discovery["issuer"] == issuer, f"discovery issuer is {issuer}")
    _, _, body = request(opener, discovery["jwks_uri"])
    jwks = json.loads(body)
    context = (opener, discovery["token_endpoint"], basic, issuer, jwks)

    for email, (user_id, role) in DEMO_USERS.items():
        print(f"Demo user {email}")
        grant, error = authorize(public, credentials={"username": email, "password": DEMO_PASSWORD})
        check(grant is not None, f"{email}: login with the documented demo password ({error or 'redirected'})")
        if not grant:
            continue
        tokens = check_tokens(context, email, grant, user_id, role)
        if tokens and role == "PSYCHOLOGIST":
            status, _, body = request(opener, discovery["token_endpoint"], {
                "grant_type": "refresh_token", "refresh_token": tokens["refresh_token"]}, {"Authorization": basic})
            check(status == 200 and claims(json.loads(body)["access_token"]).get("roles") == [role],
                  f"{email}: refresh keeps sub and roles")
            status, _, body = request(opener, discovery["token_endpoint"], {
                "grant_type": "refresh_token", "refresh_token": tokens["refresh_token"]}, {"Authorization": basic})
            check(status == 400 and json.loads(body).get("error") == "invalid_grant",
                  f"{email}: a reused refresh token is refused")

    print("Password grant")
    status, _, body = request(opener, discovery["token_endpoint"], {
        "grant_type": "password", "username": "sofia.almeida@demo.clinicflow.local",
        "password": DEMO_PASSWORD, "scope": "openid"}, {"Authorization": basic})
    check(status in (400, 401) and json.loads(body).get("error") == "unauthorized_client",
          f"the BFF client is refused the password grant ({status} {json.loads(body).get('error')})")

    print("User lookup service account")
    check_user_lookup(public, opener, discovery["token_endpoint"], issuer)

    print("Self-registration")
    email = f"self-registered-{secrets.token_hex(4)}@example.test"
    password = secrets.token_urlsafe(18)
    # Extra fields a caller might try; the user profile ignores attributes it does not declare.
    grant, error = authorize(public, registration={
        "email": email, "firstName": "Self", "lastName": "Registered", "password": password,
        "password-confirm": password, "roles": "ADMIN", "role": "PSYCHOLOGIST", "realmRoles": "ADMIN"})
    check(grant is not None, f"registration succeeds ({error or 'redirected'})")
    if grant:
        check_tokens(context, email, grant, None, "PATIENT")
    short = secrets.token_urlsafe(6)
    _, error = authorize(public, registration={
        "email": f"short-{secrets.token_hex(4)}@example.test", "firstName": "Short", "lastName": "Password",
        "password": short, "password-confirm": short})
    check(error is not None, f"a password under 12 characters is refused ({error})")
    _, error = authorize(public, registration={
        "email": "sofia.almeida@demo.clinicflow.local", "firstName": "Sofia", "lastName": "Again",
        "password": password, "password-confirm": password})
    print(f"  info  registering an existing email answers: {error!r} (issue #79)")

    print("Realm configuration")
    status, _, body = request(opener, f"{public}/realms/master/protocol/openid-connect/token", {
        "grant_type": "password", "client_id": "admin-cli",
        "username": admin["KC_BOOTSTRAP_ADMIN_USERNAME"], "password": admin["KC_BOOTSTRAP_ADMIN_PASSWORD"]})
    check(status == 200, "bootstrap administrator signs in to the master realm")
    if status != 200:
        return report()
    auth = {"Authorization": "Bearer " + json.loads(body)["access_token"]}
    base = f"{public}/admin/realms/{REALM}"

    def get(path):
        return json.loads(request(opener, base + path, headers=auth)[2])

    default_role = get("/roles/default-roles-clinicflow/composites/realm")
    check(sorted(r["name"] for r in default_role) == ["PATIENT", "offline_access", "uma_authorization"],
          "the default-role composite holds PATIENT and Keycloak's own roles only")
    for email, (user_id, role) in DEMO_USERS.items():
        direct = sorted(r["name"] for r in get(f"/users/{user_id}/role-mappings/realm"))
        check(direct == [role], f"{email} has exactly {role} and no default role")
    realm = get("")
    check(realm["bruteForceProtected"] and realm["failureFactor"] == 5 and realm["maxFailureWaitSeconds"] == 900,
          "brute-force detection: 5 failures, incremental wait up to 15 minutes")
    check(realm["passwordPolicy"].startswith("length(12) and maxLength(128)"), "password policy: 12 to 128 characters")
    check(realm["ssoSessionIdleTimeout"] == 1800 and realm["revokeRefreshToken"] and realm["refreshTokenMaxReuse"] == 0,
          "30-minute SSO idle timeout and refresh-token rotation")
    client = get(f"/clients?clientId={CLIENT_ID}")[0]
    check(not client["directAccessGrantsEnabled"] and not client["implicitFlowEnabled"]
          and not client["fullScopeAllowed"], "BFF client: standard flow only, no full scope")
    lookup = get(f"/clients?clientId={LOOKUP_CLIENT_ID}")[0]
    check(lookup["serviceAccountsEnabled"] and not lookup["standardFlowEnabled"] and not lookup["directAccessGrantsEnabled"]
          and not lookup["implicitFlowEnabled"] and not lookup["fullScopeAllowed"],
          "lookup client: client credentials only, no full scope")
    account = get(f"/clients/{lookup['id']}/service-account-user")
    management = get("/clients?clientId=realm-management")[0]
    granted = sorted(r["name"] for r in get(f"/users/{account['id']}/role-mappings/clients/{management['id']}"))
    check(granted == ["view-users"], f"lookup service account holds view-users only ({granted})")
    for user in get(f"/users?email={urllib.parse.quote('@example.test')}&max=100"):
        request(opener, f"{base}/users/{user['id']}", headers=auth, method="DELETE")
    check(not get(f"/users?email={urllib.parse.quote('@example.test')}&max=100"), "registered test users removed")
    return report()


def report():
    print("PASSED" if not failures else f"FAILED: {len(failures)} check(s)")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
