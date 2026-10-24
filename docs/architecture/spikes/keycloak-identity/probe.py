"""Throwaway probe for the Keycloak identity spike (issue #85, ADR-027).

Standard library only. Each leg plays one party of the real topology:

  login / register  the browser, on the host, against the public URL
  bff               the BFF, in a container, against http://keycloak:8080
  admin             an operator, on the host, against the Admin REST API
"""
import argparse
import base64
import hashlib
import html
import http.cookiejar
import json
import os
import re
import secrets
import sys
import urllib.error
import urllib.parse
import urllib.request

REALM = "clinicflow"
CLIENT_ID = "clinicflow-bff"
REDIRECT_URI = "http://localhost:3000/api/auth/callback"
SHA256_DIGEST_INFO = bytes.fromhex("3031300d060960864801650304020105000420")


class LocalhostIsSecure(http.cookiejar.DefaultCookiePolicy):
    """Keycloak sets Secure cookies even over HTTP. Browsers send them to
    http://localhost (a secure context); Python's default policy does not."""

    def return_ok_secure(self, cookie, request):
        return not cookie.secure or urllib.parse.urlparse(request.get_full_url()).hostname == "localhost" \
            or super().return_ok_secure(cookie, request)


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def b64url_decode(value: str) -> bytes:
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))


def request(opener, url, data=None, headers=None):
    body = urllib.parse.urlencode(data).encode() if isinstance(data, dict) else data
    req = urllib.request.Request(url, data=body, headers=headers or {})
    try:
        with opener.open(req) as response:
            return response.status, dict(response.headers), response.read().decode()
    except urllib.error.HTTPError as error:
        return error.code, dict(error.headers), error.read().decode()


def form_action(page: str, form_id: str) -> str:
    tag = re.search(r'<form[^>]*id="%s"[^>]*>' % re.escape(form_id), page)
    if not tag:
        sys.exit(f"form {form_id} not found")
    return html.unescape(re.search(r'action="([^"]+)"', tag.group(0)).group(1))


def browser_flow(public, out, credentials=None, registration=None):
    """Authorization request with PKCE, then the login or registration form."""
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
    assert status == 200, f"authorization endpoint returned {status}"
    if registration:
        link = re.search(r'href="([^"]*login-actions/registration[^"]*)"', page)
        status, _, page = request(opener, urllib.parse.urljoin(public, html.unescape(link.group(1))))
        status, headers, page = request(opener, form_action(page, "kc-register-form"), registration)
    else:
        status, headers, page = request(opener, form_action(page, "kc-form-login"), credentials)
    if status != 302:
        error = re.search(r'class="[^"]*kc-feedback-text[^"]*"[^>]*>([^<]+)<', page)
        sys.exit(f"expected a redirect to the BFF, got {status}: {error.group(1).strip() if error else ''}")
    location = urllib.parse.urlparse(headers["Location"])
    params = urllib.parse.parse_qs(location.query)
    assert f"{location.scheme}://{location.netloc}{location.path}" == REDIRECT_URI
    assert params["state"] == [state], "state mismatch"
    result = {"code": params["code"][0], "verifier": verifier, "nonce": nonce,
              "iss_param": params.get("iss", [None])[0]}
    with open(out, "w") as handle:
        json.dump(result, handle)
    print(f"browser: code issued, iss parameter {result['iss_param']}")


def verify_rs256(token, jwks):
    header_b64, payload_b64, signature_b64 = token.split(".")
    header = json.loads(b64url_decode(header_b64))
    key = next(k for k in jwks["keys"] if k["kid"] == header["kid"])
    modulus = int.from_bytes(b64url_decode(key["n"]), "big")
    exponent = int.from_bytes(b64url_decode(key["e"]), "big")
    size = (modulus.bit_length() + 7) // 8
    encoded = pow(int.from_bytes(b64url_decode(signature_b64), "big"), exponent, modulus).to_bytes(size, "big")
    digest = hashlib.sha256(f"{header_b64}.{payload_b64}".encode()).digest()
    padding = b"\xff" * (size - 3 - len(SHA256_DIGEST_INFO) - len(digest))
    return header["alg"] == "RS256" and encoded == b"\x00\x01" + padding + b"\x00" + SHA256_DIGEST_INFO + digest


def claims(token):
    return json.loads(b64url_decode(token.split(".")[1]))


def bff_flow(backchannel, grant_file, out):
    opener = urllib.request.build_opener()
    secret = os.environ["CLINICFLOW_BFF_CLIENT_SECRET"]
    basic = "Basic " + base64.b64encode(f"{CLIENT_ID}:{secret}".encode()).decode()
    report = {}

    _, _, body = request(opener, f"{backchannel}/realms/{REALM}/.well-known/openid-configuration")
    discovery = json.loads(body)
    report["discovery_via_backchannel"] = {k: discovery[k] for k in (
        "issuer", "authorization_endpoint", "token_endpoint", "jwks_uri", "end_session_endpoint")}
    _, _, body = request(opener, f"{backchannel}/realms/{REALM}/protocol/openid-connect/certs")
    jwks = json.loads(body)
    report["jwks_keys"] = [{k: key.get(k) for k in ("kid", "kty", "alg", "use")} for key in jwks["keys"]]

    with open(grant_file) as handle:
        grant = json.load(handle)
    token_endpoint = f"{backchannel}/realms/{REALM}/protocol/openid-connect/token"
    status, _, body = request(opener, token_endpoint, {
        "grant_type": "authorization_code", "code": grant["code"], "redirect_uri": REDIRECT_URI,
        "code_verifier": grant["verifier"]}, {"Authorization": basic})
    assert status == 200, f"code exchange failed: {status} {body}"
    tokens = json.loads(body)
    access, id_token = claims(tokens["access_token"]), claims(tokens["id_token"])
    report["access_token_signature_valid"] = verify_rs256(tokens["access_token"], jwks)
    report["access_token_header"] = json.loads(b64url_decode(tokens["access_token"].split(".")[0]))
    report["access_token"] = access
    report["id_token_nonce_matches"] = id_token.get("nonce") == grant["nonce"]
    report["id_token_aud"] = id_token.get("aud")
    report["id_token_has_roles"] = "roles" in id_token
    report["expires_in"] = tokens["expires_in"]
    report["refresh_expires_in"] = tokens["refresh_expires_in"]

    status, _, body = request(opener, token_endpoint, {
        "grant_type": "refresh_token", "refresh_token": tokens["refresh_token"]}, {"Authorization": basic})
    refreshed = json.loads(body)
    report["refresh_status"] = status
    report["refreshed_iss_sub_roles"] = {k: claims(refreshed["access_token"]).get(k) for k in ("iss", "sub", "roles")}
    status, _, body = request(opener, token_endpoint, {
        "grant_type": "refresh_token", "refresh_token": tokens["refresh_token"]}, {"Authorization": basic})
    report["refresh_token_reuse"] = {"status": status, "error": json.loads(body).get("error")}

    # Any value will do: the client must refuse the grant before checking it.
    status, _, body = request(opener, token_endpoint, {
        "grant_type": "password", "username": access.get("email", ""),
        "password": secrets.token_urlsafe(16), "scope": "openid"}, {"Authorization": basic})
    report["password_grant"] = {"status": status, "error": json.loads(body).get("error")}

    with open(out, "w") as handle:
        json.dump(report, handle, indent=2)
    print(json.dumps(report, indent=2))


def admin_flow(public, out, password):
    opener = urllib.request.build_opener()
    status, _, body = request(opener, f"{public}/realms/master/protocol/openid-connect/token", {
        "grant_type": "password", "client_id": "admin-cli", "username": "spike-admin", "password": password})
    assert status == 200, f"admin token failed: {status}"
    auth = {"Authorization": "Bearer " + json.loads(body)["access_token"]}
    base = f"{public}/admin/realms/{REALM}"
    report = {}

    def get(path):
        return json.loads(request(opener, base + path, headers=auth)[2])

    def post(path, payload):
        return request(opener, base + path, json.dumps(payload).encode(), {**auth, "Content-Type": "application/json"})

    for user in get("/users?max=50"):
        report.setdefault("users", []).append({
            "id": user["id"], "username": user["username"],
            "direct_realm_roles": sorted(r["name"] for r in get(f"/users/{user['id']}/role-mappings/realm")),
        })

    wanted = "00000000-0000-4000-8000-0000000000a1"
    status, headers, _ = post("/users", {"id": wanted, "username": "fixed-id-admin-api@spike.local",
                                         "email": "fixed-id-admin-api@spike.local", "enabled": True})
    created = headers.get("Location", "").rsplit("/", 1)[-1]
    report["admin_api_create_with_id"] = {"status": status, "requested": wanted, "assigned": created,
                                          "kept": created == wanted}

    wanted = "00000000-0000-4000-8000-0000000000a2"
    status, _, body = post("/partialImport", {"ifResourceExists": "SKIP", "users": [{
        "id": wanted, "username": "fixed-id-partial-import@spike.local",
        "email": "fixed-id-partial-import@spike.local", "enabled": True, "realmRoles": ["PSYCHOLOGIST"]}]})
    report["partial_import_with_id"] = {"status": status, "requested": wanted,
                                        "kept": request(opener, f"{base}/users/{wanted}", headers=auth)[0] == 200,
                                        "direct_realm_roles": sorted(r["name"] for r in get(f"/users/{wanted}/role-mappings/realm"))}

    with open(out, "w") as handle:
        json.dump(report, handle, indent=2)
    print(json.dumps(report, indent=2))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("leg", choices=["login", "register", "bff", "admin"])
    parser.add_argument("--public", default="http://localhost:8180")
    parser.add_argument("--backchannel", default="http://keycloak:8080")
    parser.add_argument("--grant", default="out/grant.json")
    parser.add_argument("--out", default="out/report.json")
    parser.add_argument("--email")
    args = parser.parse_args()
    password = os.environ.get("SPIKE_DEMO_PASSWORD", "")
    if args.leg == "login":
        browser_flow(args.public, args.grant, credentials={"username": args.email, "password": password})
    elif args.leg == "register":
        browser_flow(args.public, args.grant, registration={
            "email": args.email, "firstName": "Spike", "lastName": "Registrant",
            "password": password, "password-confirm": password})
    elif args.leg == "bff":
        bff_flow(args.backchannel, args.grant, args.out)
    else:
        admin_flow(args.public, args.out, os.environ["KC_BOOTSTRAP_ADMIN_PASSWORD"])


if __name__ == "__main__":
    main()
