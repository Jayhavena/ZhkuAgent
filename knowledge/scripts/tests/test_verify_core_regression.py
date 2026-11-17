"""只验证门禁是否能发现漏跑/跳过；XML 全为合成，不读取真实交换。"""
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import verify_core_regression as gate


class CoreRegressionGateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.contract = {"testClasses": ["AlphaTest"], "requiredCases": [
            {"id": "scope", "testClass": "AlphaTest", "testMethod": "rejectWrongScope"}
        ]}

    def report(self, tests="1", skipped="0", failures="0", errors="0", body=None):
        body = body if body is not None else '<testcase name="rejectWrongScope(String)[1]" classname="example.AlphaTest"/>'
        (self.root / "TEST-example.AlphaTest.xml").write_text(
            f'<testsuite name="example.AlphaTest" tests="{tests}" skipped="{skipped}" failures="{failures}" errors="{errors}">{body}</testsuite>', encoding="utf-8")

    def test_parameterized_success_proves_required_method(self):
        self.report()
        result = gate.verify_reports(self.contract, self.root)
        self.assertTrue(result["passed"])
        self.assertEqual(result["tests"], 1)
        self.assertEqual(result["criteria"], 1)

    def test_missing_report_fails(self):
        with self.assertRaisesRegex(ValueError, "REPORT_MISSING_OR_DUPLICATE"):
            gate.verify_reports(self.contract, self.root)

    def test_zero_test_or_skipped_failed_error_reports_fail(self):
        for counts in [("0", "0", "0", "0"), ("1", "1", "0", "0"), ("1", "0", "1", "0"), ("1", "0", "0", "1")]:
            with self.subTest(counts=counts):
                self.report(*counts)
                with self.assertRaisesRegex(ValueError, "REPORT_NOT_GREEN"):
                    gate.verify_reports(self.contract, self.root)

    def test_testcase_failure_cannot_hide_behind_green_suite_counts(self):
        self.report(body='<testcase name="rejectWrongScope"><failure/></testcase>')
        with self.assertRaisesRegex(ValueError, "REPORT_NOT_GREEN"):
            gate.verify_reports(self.contract, self.root)

    def test_missing_required_method_fails(self):
        self.report(body='<testcase name="otherMethod"/>')
        with self.assertRaisesRegex(ValueError, "CRITERION_NOT_EXECUTED"):
            gate.verify_reports(self.contract, self.root)

    def test_malformed_xml_fails_without_dumping_body(self):
        (self.root / "TEST-example.AlphaTest.xml").write_text("private malformed input", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "REPORT_INVALID"):
            gate.verify_reports(self.contract, self.root)

    def test_declared_count_must_match_actual_testcases(self):
        self.report(tests="2")
        with self.assertRaisesRegex(ValueError, "REPORT_COUNT_MISMATCH"):
            gate.verify_reports(self.contract, self.root)

    def test_manifest_cannot_omit_required_class_or_duplicate_selector(self):
        (self.root / "matrix.json").write_text(json.dumps({"cases": self.contract["requiredCases"]}), encoding="utf-8")
        path = self.root / "manifest.json"
        for classes in [["OtherTest"], ["AlphaTest", "AlphaTest"], ["AlphaTest,*"]]:
            with self.subTest(classes=classes):
                path.write_text(json.dumps({"schemaVersion": 1, "testClasses": classes, "matrixPath": "matrix.json"}), encoding="utf-8")
                with self.assertRaises(ValueError):
                    gate.load_contract(self.root, path)

    def test_shipped_selector_contains_every_m3_class_and_pure_extractor(self):
        root = Path(__file__).resolve().parents[3]
        contract = gate.load_contract(root)
        self.assertEqual(len(contract["requiredCases"]), 21)
        self.assertIn("RuleAgentQuestionConditionsTest", contract["testClasses"])
        self.assertIn("RuleAgentKnownConditionActionTest", contract["testClasses"])
        self.assertIn("ProbeBatchClaimTest", contract["testClasses"])
        self.assertEqual(gate.selector(contract).split(","), contract["testClasses"])


if __name__ == "__main__":
    unittest.main()
