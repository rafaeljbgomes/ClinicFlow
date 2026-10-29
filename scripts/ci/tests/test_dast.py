import importlib.util
import json
import re
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import urllib.parse

spec = importlib.util.spec_from_file_location("dast", Path(__file__).resolve().parents[1] / "dast.py")
dast = importlib.util.module_from_spec(spec)
spec.loader.exec_module(dast)


class AutomationContractTest(unittest.TestCase):
    def run_plan(self, progress):
        captured = []
        def api(section, operation, **params):
            if operation == "action/runPlan":
                captured.append(json.loads(Path(params["filePath"]).read_text()))
                return {"planId": "1"}
            return progress
        with tempfile.TemporaryDirectory(prefix="clinicflow-dast-test-") as directory:
            real_path = Path
            def plan_path(value):
                return real_path(directory) if value == "/tmp" else real_path(value)
            with patch.object(dast, "zap_api", side_effect=api), patch.object(dast, "authenticate") as auth, patch.object(dast, "Path", side_effect=plan_path), patch.object(dast, "traffic_snapshot", return_value=set()), patch.object(dast, "verify_traffic", return_value={"api": {}, "ui": {}}):
                dast.run_plan("psychologist", [dast.API + "/api/v1/patients"], False, [dast.UI + "/api/patients"])
                auth.assert_called_once_with("psychologist")
        return captured[0]

    def test_automation_contract_and_scope(self):
        plan = self.run_plan({"finished": "2026-10-15T12:00:00Z", "error": [], "warn": []})
        requestor = next(job for job in plan["jobs"] if job["type"] == "requestor")
        self.assertIn("requests", requestor)
        self.assertNotIn("parameters", requestor)
        self.assertEqual(requestor["requests"][0]["responseCode"], 200)
        active = next(job for job in plan["jobs"] if job["type"] == "activeScan")
        self.assertEqual(active["parameters"]["maxScanDurationInMins"], 2)
        self.assertEqual(active["parameters"]["threadPerHost"], 2)
        self.assertTrue(active["parameters"]["addQueryParam"])
        self.assertEqual(len(plan["env"]["contexts"][0]["includePaths"]), 2)
        scope = plan["env"]["contexts"][0]["includePaths"][0]
        self.assertIsNotNone(re.fullmatch(scope, dast.API + "/api/v1/patients?query=test"))
        self.assertIsNone(re.fullmatch(scope, dast.API + "/api/v1/patients-other"))

    def test_plan_errors_or_warnings_fail_closed(self):
        for progress in [{"finished": "", "error": ["bad configuration"], "warn": []}, {"finished": "", "error": [], "warn": ["unrecognized option"]}, {"finished": "done"}]:
            with self.subTest(progress=progress), self.assertRaises(RuntimeError):
                self.run_plan(progress)

    def test_actual_active_messages_are_required(self):
        with patch.object(dast, "zap_api", return_value={"scans": []}), self.assertRaises(RuntimeError):
            dast.verify_traffic(set(), [dast.API])

    def test_active_authentication_loss_fails(self):
        def api(section, operation, **params):
            if operation == "view/scans":
                return {"scans": [{"id": "1", "progress": "100", "state": "FINISHED"}]}
            if operation == "view/messagesIds":
                return {"messagesIds": ["1", "2", "3"]}
            return {"messagesById": [{"requestHeader": "GET / HTTP/1.1\r\nHost: api.clinicflow.localhost:8443\r\n", "responseHeader": "HTTP/1.1 401 Unauthorized"}]}
        with patch.object(dast, "zap_api", side_effect=api), self.assertRaises(RuntimeError):
            dast.verify_traffic(set(), [dast.API])

    def test_verified_active_api_and_bff_traffic(self):
        def api(section, operation, **params):
            if operation == "view/scans":
                return {"scans": [{"id": "1", "progress": "100", "state": "FINISHED"}]}
            if operation == "view/messagesIds":
                return {"messagesIds": ["1", "2", "3"]}
            return {"messagesById": [
                {"requestHeader": "GET / HTTP/1.1\r\nHost: api.clinicflow.localhost:8443\r\n", "responseHeader": "HTTP/1.1 200 OK"},
                {"requestHeader": "GET / HTTP/1.1\r\nHost: clinicflow.localhost:8443\r\n", "responseHeader": "HTTP/1.1 200 OK"},
                {"requestHeader": "POST /api/v1/auth/login HTTP/1.1\r\nHost: api.clinicflow.localhost:8443\r\n", "responseHeader": "HTTP/1.1 401 Unauthorized"}]}
        with patch.object(dast, "zap_api", side_effect=api):
            self.assertEqual(dast.verify_traffic(set(), [dast.API])["requests"], 3)

    def test_budget_stopped_scan_reports_partial_progress(self):
        def api(section, operation, **params):
            if operation == "view/scans":
                return {"scans": [{"id": "1", "progress": "64", "state": "FINISHED"}]}
            if operation == "view/messagesIds":
                return {"messagesIds": ["1", "2", "3"]}
            return {"messagesById": [
                {"requestHeader": "GET / HTTP/1.1\r\nHost: api.clinicflow.localhost:8443\r\n", "responseHeader": "HTTP/1.1 200 OK"},
                {"requestHeader": "GET / HTTP/1.1\r\nHost: clinicflow.localhost:8443\r\n", "responseHeader": "HTTP/1.1 200 OK"}]}
        with patch.object(dast, "zap_api", side_effect=api):
            self.assertEqual(dast.verify_traffic(set(), [dast.API])["progress"], 64)

    def test_failure_detail_names_missing_key_without_values(self):
        try:
            {"accessToken": "secret-value"}["planId"]
        except KeyError as error:
            detail = dast.failure_detail(error)
        self.assertIn("missing key 'planId'", detail)
        self.assertNotIn("secret-value", detail)

    def test_failure_detail_hides_unexpected_errors(self):
        self.assertEqual(dast.failure_detail(OSError("connection to 10.0.0.1 reset")), "")

    def test_running_scan_fails(self):
        def api(section, operation, **params):
            return {"scans": [{"id": "1", "progress": "40", "state": "RUNNING"}]}
        with patch.object(dast, "zap_api", side_effect=api), self.assertRaises(RuntimeError):
            dast.verify_traffic(set(), [dast.API])

    def test_login_form_posts_to_keycloak_on_the_private_network(self):
        page = '<form id="kc-form-login" action="http://localhost:32004/realms/clinicflow/login-actions/authenticate?session_code=a&amp;execution=b" method="post">'
        self.assertEqual(dast.login_form_action(page), "http://keycloak:8080/realms/clinicflow/login-actions/authenticate?session_code=a&execution=b")

    def test_missing_login_form_fails_closed(self):
        with self.assertRaises(RuntimeError):
            dast.login_form_action("<p>We are sorry...</p>")

    def test_resource_servers_and_auth_service_get_separate_tokens(self):
        cases = {"/api/v1/patients": (True, False), "/api/v1/clinical/clinical-cases?page=1": (True, False),
                 "/api/v1/auth/users/me": (False, True), "/api/v1/auth/login": (False, False), "/api/v1/auth/register?x=1": (False, False)}
        for path, (resource_server, auth_service) in cases.items():
            with self.subTest(path=path):
                self.assertEqual(re.fullmatch(dast.RESOURCE_SERVER_PATHS, dast.API + path) is not None, resource_server)
                self.assertEqual(re.fullmatch(dast.AUTH_SERVICE_PATHS, dast.API + path) is not None, auth_service)


class KeycloakLoginTest(unittest.TestCase):
    PAGE = b'<form id="kc-form-login" action="http://localhost:32004/realms/clinicflow/login-actions/authenticate?session_code=a">'

    def login(self, roles=("PSYCHOLOGIST",), state=None, secret="s" * 32):
        token = "e30." + dast.b64url(json.dumps({"roles": list(roles)}).encode()) + ".sig"
        calls = []
        def form_request(opener, url, fields=None, headers=None, expected=200):
            calls.append((url, fields, headers, expected))
            if "/openid-connect/auth" in url:
                return self.PAGE, {}
            if "/login-actions/" in url:
                sent = urllib.parse.parse_qs(urllib.parse.urlsplit(calls[0][0]).query)["state"][0]
                return b"", {"Location": dast.REDIRECT_URI + "?state=" + (state or sent) + "&code=c1"}
            return json.dumps({"access_token": token}).encode(), {}
        environment = {"CLINICFLOW_DEMO_PASSWORD": "demo", "CLINICFLOW_BFF_CLIENT_SECRET": secret}
        with patch.object(dast, "form_request", side_effect=form_request), patch.dict(dast.os.environ, environment):
            return dast.keycloak_token("psychologist"), calls

    def test_exchanges_a_pkce_code_for_the_role_token(self):
        token, calls = self.login()
        authorize = urllib.parse.parse_qs(urllib.parse.urlsplit(calls[0][0]).query)
        login, exchange = calls[1], calls[2]
        self.assertEqual(authorize["code_challenge_method"], ["S256"])
        self.assertEqual(login[0], "http://keycloak:8080/realms/clinicflow/login-actions/authenticate?session_code=a")
        self.assertEqual((login[1]["username"], login[3]), (dast.ROLES["psychologist"], 302))
        self.assertEqual(exchange[0], dast.REALM + "/protocol/openid-connect/token")
        self.assertEqual(exchange[1]["code"], "c1")
        self.assertEqual(dast.b64url(dast.hashlib.sha256(exchange[1]["code_verifier"].encode()).digest()), authorize["code_challenge"][0])
        self.assertTrue(exchange[2]["Authorization"].startswith("Basic "))
        self.assertEqual(dast.token_claims(token)["roles"], ["PSYCHOLOGIST"])

    def test_state_mismatch_fails_closed(self):
        with self.assertRaises(RuntimeError):
            self.login(state="forged")

    def test_token_without_exactly_the_expected_role_fails_closed(self):
        for roles in [(), ("PATIENT",), ("PSYCHOLOGIST", "ADMIN")]:
            with self.subTest(roles=roles), self.assertRaises(RuntimeError):
                self.login(roles=roles)

    def test_missing_client_secret_fails_closed(self):
        with self.assertRaises(RuntimeError):
            self.login(secret="")


if __name__ == "__main__":
    unittest.main()
