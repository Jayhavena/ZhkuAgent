"""M2 套件绑定器的离线单元测试。

测试只用临时目录里的合成两轮快照，不读取 target、真实证据、旧 suite 或旧报告，
也不连接数据库/模型/浏览器。
"""

import contextlib
import hashlib
import io
import json
import shutil
import sys
import tempfile
import unittest
from copy import deepcopy
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import bind_m2_suite as binder

REPO_ROOT = Path(__file__).resolve().parents[3]
SHIPPED_TEMPLATE = (
    REPO_ROOT / "docs" / "agent" / "eval" / "m2-minimal-20261114" / "suite-template.json"
)

TASK_ID = "11111111-1111-4111-8111-111111111111"
OTHER_TASK_ID = "44444444-4444-4444-8444-444444444444"
RUN_FIRST = "22222222-2222-4222-8222-222222222222"
RUN_SECOND = "33333333-3333-4333-8333-333333333333"


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


class BindM2SuiteTest(unittest.TestCase):
    def test_task_identity_must_be_uuid(self):
        self.assertIsNone(binder._entry_identity(self.first_run(taskId="not-a-task-uuid")))

    def test_malformed_relation_container_cannot_crash_or_pass(self):
        second = self.second_run()
        second["result"]["evidenceRelations"] = 42
        self.assertEqual(binder._second_run_claims(second), (1, 0))

    def setUp(self):
        self._temp = tempfile.TemporaryDirectory()
        self.addCleanup(self._temp.cleanup)
        self.root = Path(self._temp.name) / "repo"
        self.evidence = self.root / "docs/evidence/m2/live-synthetic"
        self.evidence.mkdir(parents=True)
        self.results = self.evidence / "task-results.json"
        self.template = self.root / "docs/agent/eval/m2-minimal-20261114/suite-template.json"
        self.template.parent.mkdir(parents=True)
        shutil.copyfile(SHIPPED_TEMPLATE, self.template)
        self.out = self.root / "out"
        self.write_results([self.first_run(), self.second_run()])

    # ---- 合成输入 -------------------------------------------------------

    def first_run(self, **overrides):
        entry = {
            "schemaVersion": 3,
            "taskId": TASK_ID,
            "runId": RUN_FIRST,
            "taskStatus": "WAITING_USER",
            "runState": "INCOMPLETE",
            "result": {
                "coverage": "NONE",
                "items": [{"id": "q1", "claims": [], "issues": [{"reasonCode": "MISSING_CONDITION"}]}],
                "evidence": [{"id": "e-clarify"}],
                "evidenceRelations": [
                    {"evidenceId": "e-clarify", "subquestionId": "q1", "role": "CLARIFICATION",
                     "targetId": "courseType"}
                ],
            },
        }
        entry.update(overrides)
        return entry

    def second_run(self, **overrides):
        entry = {
            "schemaVersion": 3,
            "taskId": TASK_ID,
            "runId": RUN_SECOND,
            "taskStatus": "RESOLVED",
            "runState": "SUCCEEDED",
            "result": {
                "coverage": "COMPLETE",
                "items": [{"id": "q1", "claims": [{"id": "c0"}], "issues": [], "clarification": []}],
                "evidence": [{"id": "e-claim"}],
                "evidenceRelations": [
                    {"evidenceId": "e-claim", "subquestionId": "q1", "role": "CLAIM", "targetId": "c0"}
                ],
            },
        }
        entry.update(overrides)
        return entry

    def write_results(self, entries):
        self.results.write_text(json.dumps(entries, ensure_ascii=False, indent=2), encoding="utf-8")

    def load_json(self, path):
        return json.loads(Path(path).read_text(encoding="utf-8"))

    def invoke(self, out=None):
        argv = [
            "--template", str(self.template),
            "--evidence", str(self.evidence),
            "--out", str(out or self.out),
            "--repo-root", str(self.root),
        ]
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            return binder.main(argv)

    def binding(self):
        return self.load_json(self.out / "binding.json")

    def suite(self):
        return self.load_json(self.out / "suite.json")

    def assert_reason(self, binding, code):
        self.assertIn(code, binding["reasons"], f"实际原因: {binding['reasons']}")

    # ---- 成功绑定 -------------------------------------------------------

    def test_success_binds_both_runs_and_keeps_checks(self):
        self.assertEqual(binder.EXIT_PASS, self.invoke())
        suite = self.suite()
        binding = self.binding()

        self.assertEqual("PASS", binding["status"])
        self.assertEqual([], binding["reasons"])
        self.assertTrue(binding["suiteGenerated"])
        self.assertTrue(binding["expectationsUnchanged"])
        self.assertEqual([RUN_FIRST, RUN_SECOND], [case["runId"] for case in binding["cases"]])
        self.assertTrue(all(case["bound"] for case in binding["cases"]))
        self.assertEqual("docs/evidence/m2/live-synthetic", suite["blocks"][0]["evidenceDir"])
        self.assertEqual([RUN_FIRST, RUN_SECOND],
                         [case["runId"] for case in suite["blocks"][0]["cases"]])
        self.assertEqual(2, binding["checks"]["taskResultCount"])
        self.assertTrue(binding["checks"]["taskIdMatches"])
        self.assertTrue(binding["checks"]["runIdsDistinct"])
        self.assertTrue(binding["checks"]["firstRoundStatusMatches"])
        self.assertTrue(binding["checks"]["secondRoundStatusMatches"])
        self.assertEqual(1, binding["checks"]["secondRoundClaimCount"])
        self.assertEqual(1, binding["checks"]["secondRoundLinkedClaimCount"])
        self.assertEqual("suite.json", binding["outputs"]["suite"])

    def test_only_run_ids_and_evidence_dir_change(self):
        self.assertEqual(binder.EXIT_PASS, self.invoke())
        template = self.load_json(self.template)
        suite = self.suite()
        restored = deepcopy(suite)
        restored["blocks"][0]["evidenceDir"] = template["blocks"][0]["evidenceDir"]
        for case in restored["blocks"][0]["cases"]:
            case["runId"] = None
        self.assertEqual(template, restored, "除 runId/evidenceDir 外模板被改动")

    def test_hashes_are_recorded_and_binding_is_self_consistent(self):
        self.assertEqual(binder.EXIT_PASS, self.invoke())
        binding = self.binding()
        self.assertEqual(sha256(self.template.read_bytes()), binding["templateSha256"])
        self.assertEqual(sha256((self.out / "suite.json").read_bytes()), binding["suiteSha256"])
        self.assertEqual(binder.manifest_sha256(binding), binding["bindingSha256"])
        self.assertTrue(binding["taskResultsSha256"])

    def test_binding_is_reproducible_across_output_directories(self):
        self.assertEqual(binder.EXIT_PASS, self.invoke(self.out))
        self.assertEqual(binder.EXIT_PASS, self.invoke(self.root / "out-replay"))
        first = self.binding()
        second = self.load_json(self.root / "out-replay/binding.json")
        self.assertEqual(first, second)
        self.assertEqual((self.out / "suite.json").read_bytes(),
                         (self.root / "out-replay/suite.json").read_bytes())

    # ---- 缺 run / 异 task / 重复 run ------------------------------------

    def test_missing_second_run_is_not_evaluable_and_unbound(self):
        self.write_results([self.first_run()])
        self.assertEqual(binder.EXIT_NOT_EVALUABLE, self.invoke())
        binding = self.binding()
        suite = self.suite()
        self.assertEqual("NOT_EVALUABLE", binding["status"])
        self.assert_reason(binding, "RUN_COUNT_MISMATCH")
        self.assertEqual([RUN_FIRST, None], [case["runId"] for case in binding["cases"]])
        self.assertEqual([RUN_FIRST, None], [case["runId"] for case in suite["blocks"][0]["cases"]])
        self.assertFalse(binding["cases"][1]["bound"])

    def test_second_run_from_another_task_fails_and_stays_unbound(self):
        self.write_results([self.first_run(), self.second_run(taskId=OTHER_TASK_ID)])
        self.assertEqual(binder.EXIT_FAIL, self.invoke())
        binding = self.binding()
        self.assertEqual("FAIL", binding["status"])
        self.assert_reason(binding, "TASK_ID_MISMATCH")
        self.assertFalse(binding["checks"]["taskIdMatches"])
        self.assertEqual([RUN_FIRST, None], [case["runId"] for case in binding["cases"]])

    def test_duplicate_run_id_does_not_fake_a_second_run(self):
        self.write_results([self.first_run(), self.second_run(runId=RUN_FIRST)])
        self.assertEqual(binder.EXIT_FAIL, self.invoke())
        binding = self.binding()
        self.assert_reason(binding, "RUN_ID_DUPLICATE")
        self.assertFalse(binding["checks"]["runIdsDistinct"])
        self.assertEqual([RUN_FIRST, None], [case["runId"] for case in binding["cases"]])

    def test_wrong_round_status_fails_but_keeps_real_run_ids(self):
        self.write_results([self.first_run(runState="SUCCEEDED"), self.second_run()])
        self.assertEqual(binder.EXIT_FAIL, self.invoke())
        binding = self.binding()
        self.assert_reason(binding, "FIRST_RUN_STATE_MISMATCH")
        self.assertFalse(binding["checks"]["firstRoundStatusMatches"])
        self.assertEqual([RUN_FIRST, RUN_SECOND], [case["runId"] for case in binding["cases"]])

    # ---- 损坏输入 -------------------------------------------------------

    def test_malformed_task_results_is_not_evaluable_without_raw_json(self):
        self.results.write_text('{"not": "a list"', encoding="utf-8")
        self.assertEqual(binder.EXIT_NOT_EVALUABLE, self.invoke())
        binding = self.binding()
        self.assert_reason(binding, "TASK_RESULTS_UNREADABLE")
        self.assertEqual([None, None], [case["runId"] for case in binding["cases"]])
        self.assertNotIn('"a list"', (self.out / "binding.json").read_text(encoding="utf-8"))

    def test_task_results_object_is_not_an_array(self):
        self.results.write_text('{"taskId": "x"}', encoding="utf-8")
        self.assertEqual(binder.EXIT_NOT_EVALUABLE, self.invoke())
        binding = self.binding()
        self.assert_reason(binding, "TASK_RESULTS_NOT_ARRAY")
        self.assertEqual([None, None], [case["runId"] for case in binding["cases"]])

    def test_missing_task_results_is_not_evaluable(self):
        self.results.unlink()
        self.assertEqual(binder.EXIT_NOT_EVALUABLE, self.invoke())
        self.assert_reason(self.binding(), "TASK_RESULTS_MISSING")
        self.assertEqual([None, None], [case["runId"] for case in self.binding()["cases"]])

    def test_missing_template_is_not_evaluable_and_writes_failure_binding(self):
        self.template.unlink()
        self.assertEqual(binder.EXIT_NOT_EVALUABLE, self.invoke())
        binding = self.binding()
        self.assert_reason(binding, "TEMPLATE_MISSING")
        self.assertFalse(binding["suiteGenerated"])
        self.assertIsNone(binding["templateSha256"])
        self.assertFalse((self.out / "suite.json").exists())

    def test_malformed_template_is_not_evaluable(self):
        self.template.write_text("{not json", encoding="utf-8")
        self.assertEqual(binder.EXIT_NOT_EVALUABLE, self.invoke())
        self.assert_reason(self.binding(), "TEMPLATE_UNREADABLE")
        self.assertFalse((self.out / "suite.json").exists())

    # ---- claim / 证据引用 -----------------------------------------------

    def test_missing_claim_fails_but_keeps_real_run_ids(self):
        second = self.second_run()
        second["result"]["items"] = [{"id": "q1", "claims": [], "issues": [], "clarification": []}]
        self.write_results([self.first_run(), second])
        self.assertEqual(binder.EXIT_FAIL, self.invoke())
        binding = self.binding()
        self.assert_reason(binding, "SECOND_RUN_CLAIM_MISSING")
        self.assertEqual(0, binding["checks"]["secondRoundClaimCount"])
        self.assertEqual([RUN_FIRST, RUN_SECOND], [case["runId"] for case in binding["cases"]])

    def test_claim_without_evidence_relation_fails(self):
        second = self.second_run()
        second["result"]["evidenceRelations"] = []
        self.write_results([self.first_run(), second])
        self.assertEqual(binder.EXIT_FAIL, self.invoke())
        binding = self.binding()
        self.assertEqual(1, binding["checks"]["secondRoundClaimCount"])
        self.assertEqual(0, binding["checks"]["secondRoundLinkedClaimCount"])
        self.assert_reason(binding, "SECOND_RUN_EVIDENCE_LINK_MISSING")
        self.assertEqual([RUN_FIRST, RUN_SECOND], [case["runId"] for case in binding["cases"]])

    # ---- 模板冻结与不覆盖 -----------------------------------------------

    def test_template_expectation_drift_is_rejected_before_binding(self):
        template = self.load_json(self.template)
        template["blocks"][0]["cases"][1]["expectation"]["expectClaims"] = False
        self.template.write_text(json.dumps(template, ensure_ascii=False, indent=2), encoding="utf-8")
        self.assertEqual(binder.EXIT_FAIL, self.invoke())
        binding = self.binding()
        self.assert_reason(binding, "TEMPLATE_EXPECTATION_DRIFT")
        self.assertFalse(binding["suiteGenerated"])
        self.assertFalse((self.out / "suite.json").exists())

    def test_template_run_id_must_stay_null(self):
        template = self.load_json(self.template)
        template["blocks"][0]["cases"][0]["runId"] = RUN_FIRST
        self.template.write_text(json.dumps(template, ensure_ascii=False, indent=2), encoding="utf-8")
        self.assertEqual(binder.EXIT_FAIL, self.invoke())
        self.assert_reason(self.binding(), "TEMPLATE_RUNID_NOT_NULL")
        self.assertFalse((self.out / "suite.json").exists())

    def test_existing_suite_is_never_overwritten(self):
        self.out.mkdir(parents=True)
        sentinel = self.out / "suite.json"
        sentinel.write_text("keep-suite", encoding="utf-8")
        self.assertEqual(binder.EXIT_OUTPUT_EXISTS, self.invoke())
        self.assertEqual("keep-suite", sentinel.read_text(encoding="utf-8"))
        self.assertFalse((self.out / "binding.json").exists())

    def test_existing_binding_is_never_overwritten(self):
        self.out.mkdir(parents=True)
        sentinel = self.out / "binding.json"
        sentinel.write_text("keep-binding", encoding="utf-8")
        self.assertEqual(binder.EXIT_OUTPUT_EXISTS, self.invoke())
        self.assertEqual("keep-binding", sentinel.read_text(encoding="utf-8"))
        self.assertFalse((self.out / "suite.json").exists())

    # ---- 发布模板契约 ---------------------------------------------------

    def test_shipped_template_is_the_frozen_two_case_contract(self):
        template = self.load_json(SHIPPED_TEMPLATE)
        self.assertEqual([], binder.validate_template(template))
        self.assertEqual("M2", template["suiteId"])
        block = template["blocks"][0]
        self.assertEqual("__REPLACE_WITH_EVIDENCE_DIR__", block["evidenceDir"])
        self.assertEqual(["M2-CLARIFY", "M2-RESOLVE"],
                         [case["caseId"] for case in block["cases"]])
        self.assertEqual([None, None], [case["runId"] for case in block["cases"]])
        first, second = block["cases"]
        self.assertEqual("WAITING_USER", first["expectation"]["taskStatus"])
        self.assertEqual("INCOMPLETE", first["expectation"]["runState"])
        self.assertEqual("NONE", first["expectation"]["coverage"])
        self.assertIn("ASK_NECESSARY_CONDITION", first["expectation"]["requiredActions"])
        self.assertFalse(first["expectation"]["expectClaims"])
        self.assertEqual("RESOLVED", second["expectation"]["taskStatus"])
        self.assertEqual("SUCCEEDED", second["expectation"]["runState"])
        self.assertEqual("COMPLETE", second["expectation"]["coverage"])
        self.assertIn("FINISH_CHECK", second["expectation"]["requiredActions"])
        self.assertTrue(second["expectation"]["expectClaims"])
        self.assertTrue(second["expectation"]["requireEvidenceLinks"])
        self.assertEqual("NOT_EVALUABLE", block["bundleCase"]["expectedVerdict"])
        self.assertEqual(["COLLECTION_INCOMPLETE"], block["bundleCase"]["expectedDetails"])


if __name__ == "__main__":
    unittest.main()
