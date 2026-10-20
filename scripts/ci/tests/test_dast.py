import importlib.util
import json
import re
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

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
            return {"messages": [{"requestHeader": "GET / HTTP/1.1\r\nHost: api.clinicflow.localhost:8443\r\n", "responseHeader": "HTTP/1.1 401 Unauthorized"}]}
        with patch.object(dast, "zap_api", side_effect=api), self.assertRaises(RuntimeError):
            dast.verify_traffic(set(), [dast.API])

    def test_verified_active_api_and_bff_traffic(self):
        def api(section, operation, **params):
            if operation == "view/scans":
                return {"scans": [{"id": "1", "progress": "100", "state": "FINISHED"}]}
            if operation == "view/messagesIds":
                return {"messagesIds": ["1", "2", "3"]}
            return {"messages": [
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
            return {"messages": [
                {"requestHeader": "GET / HTTP/1.1\r\nHost: api.clinicflow.localhost:8443\r\n", "responseHeader": "HTTP/1.1 200 OK"},
                {"requestHeader": "GET / HTTP/1.1\r\nHost: clinicflow.localhost:8443\r\n", "responseHeader": "HTTP/1.1 200 OK"}]}
        with patch.object(dast, "zap_api", side_effect=api):
            self.assertEqual(dast.verify_traffic(set(), [dast.API])["progress"], 64)

    def test_running_scan_fails(self):
        def api(section, operation, **params):
            return {"scans": [{"id": "1", "progress": "40", "state": "RUNNING"}]}
        with patch.object(dast, "zap_api", side_effect=api), self.assertRaises(RuntimeError):
            dast.verify_traffic(set(), [dast.API])


if __name__ == "__main__":
    unittest.main()
