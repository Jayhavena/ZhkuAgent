"""评分门禁的反例；合成标签不是产品问答成绩。"""
import unittest
from copy import deepcopy
from audit_rule_check_eval_assets import case_verdict, validate_cases, validate_frozen_content, required_case_dimensions


class EvalAssetsTest(unittest.TestCase):
    def test_unknown_is_not_a_pass(self):
        rows = [{"id": "E04", "judgment": "UNJUDGEABLE", "severity": "NONE"}]
        self.assertEqual("UNJUDGEABLE", case_verdict(rows, {"E04"}))

    def test_missing_required_score_is_evaluation_error(self):
        self.assertEqual("ERROR", case_verdict([], {"E04"}))

    def test_duplicate_score_cannot_hide_failure(self):
        rows = [{"id": "E04", "judgment": x, "severity": "MAJOR" if x == "FAIL" else "NONE"}
                for x in ["FAIL", "PASS"]]
        self.assertEqual("ERROR", case_verdict(rows, {"E04"}))

    def test_confirmed_failure_is_not_averaged_away(self):
        rows = [{"id": "E04", "judgment": "FAIL", "severity": "CRITICAL"},
                {"id": "E05", "judgment": "PASS", "severity": "NONE"}]
        self.assertEqual("FAIL", case_verdict(rows, {"E04", "E05"}))

    def test_error_is_separate_from_product_failure(self):
        rows = [{"id": "E04", "judgment": "ERROR", "severity": "NONE"}]
        self.assertEqual("ERROR", case_verdict(rows, {"E04"}))

    def test_valid_scores_pass(self):
        rows = [{"id": "E04", "judgment": "PASS", "severity": "NONE"}]
        self.assertEqual("PASS", case_verdict(rows, {"E04"}))

    def test_invalid_rating_and_severity_do_not_pass(self):
        for judgment, severity in [("N/A", "NONE"), ("PASS", "CRITICAL"), ("FAIL", "NONE"), ("FAIL", "MINOR")]:
            self.assertEqual("ERROR", case_verdict(
                [{"id": "E04", "judgment": judgment, "severity": severity}], {"E04"}))

    def cases(self):
        return [{"caseId": f"r-{i}", "questionFamily": f"f-{i}",
                 "requiredEvidence": [{"article": 13}], "requiredFacts": ["事实"],
                 "expectedStatus": "ANSWERED", "applicableRubricIds": ["E04"]}
                for i in range(16)]

    def test_cross_split_task_family_is_rejected(self):
        self.assertTrue(validate_cases(self.cases(), {13}, {2}, {"f-0"}))

    def test_uncovered_and_development_articles_are_rejected(self):
        for sources, development in [({14}, {2}), ({13}, {13})]:
            self.assertTrue(validate_cases(self.cases(), sources, development, set()))

    def test_duplicate_ids_and_short_dataset_are_rejected(self):
        cases = self.cases()
        cases[1]["caseId"] = cases[0]["caseId"]
        self.assertTrue(validate_cases(cases, {13}, {2}, set()))
        self.assertTrue(validate_cases(self.cases()[:15], {13}, {2}, set()))

    def test_complete_declared_split_has_no_mechanical_errors(self):
        self.assertEqual([], validate_cases(self.cases(), {13}, {2}, set()))

    def materials(self):
        return {"report": {"locked":False,"cases":[{"question":"原问题","requiredFacts":["原事实"]}],
                           "familyAudit":{"semanticAuditHumanConfirmed":False}},
                "source": {"humanQualificationConfirmed":False,"chunks":[{"content":"原文"}]},
                "rubric": {"locked":False,"dimensions":[{"passWhen":"原评分口径"}]}}

    def test_empty_or_duplicate_freeze_manifest_is_rejected(self):
        current = self.materials()
        for files in [[], [{"path":"a","sha256":"1"},{"path":"a","sha256":"1"}]]:
            self.assertTrue(validate_frozen_content(current,current,files,{"a":"1","b":"2"}))

    def test_approved_flags_do_not_hide_payload_changes(self):
        before = self.materials()
        for kind in ["report","source","rubric"]:
            current = deepcopy(before)
            if kind == "report":
                current[kind]["locked"] = True
                current[kind]["cases"][0]["requiredFacts"] = ["改变结论"]
            elif kind == "source":
                current[kind]["humanQualificationConfirmed"] = True
                current[kind]["chunks"][0]["content"] = "更换原文"
            else:
                current[kind]["locked"] = True
                current[kind]["dimensions"][0]["passWhen"] = "放宽口径"
            self.assertTrue(validate_frozen_content(current,before,[{"path":"a","sha256":"1"}],{"a":"1"}))

    def test_only_declared_approval_metadata_may_change(self):
        before = self.materials()
        current = deepcopy(before)
        current["report"].update(locked=True,humanReferenceApproved=True,humanReview={"decision":"CONFIRMED"})
        current["source"]["humanQualificationConfirmed"] = True
        current["rubric"].update(locked=True,humanRubricApproved=True)
        self.assertEqual([],validate_frozen_content(current,before,[{"path":"a","sha256":"1"}],{"a":"1"}))

    def test_actual_partial_answer_adds_critical_independence_check(self):
        case = {"expectedStatus":"INSUFFICIENT_EVIDENCE","applicableRubricIds":["E08","E09"]}
        required = required_case_dimensions(case, "PARTIALLY_ANSWERED")
        self.assertIn("E07", required)
        rows = [{"id":"E08","judgment":"PASS","severity":"NONE"},
                {"id":"E09","judgment":"PASS","severity":"NONE"}]
        self.assertEqual("ERROR", case_verdict(rows, required))
        rows.append({"id":"E07","judgment":"FAIL","severity":"CRITICAL"})
        self.assertEqual("FAIL", case_verdict(rows, required))

    def test_actual_status_cannot_reduce_reference_checks(self):
        case = {"expectedStatus":"PARTIALLY_ANSWERED","applicableRubricIds":["E07","E08"]}
        self.assertIn("E07", required_case_dimensions(case, "ANSWERED"))


if __name__ == "__main__":
    unittest.main()
