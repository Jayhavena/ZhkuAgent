"""第四包真实独立重复的合成准入合同；不读取密钥、不发网络请求。"""
import json
from pathlib import Path
import tempfile
import unittest

import capture_rule_check_package04 as capture


class CaptureContract(unittest.TestCase):
    def setUp(self):
        scratch = Path(__file__).resolve().parents[1] / "target"
        scratch.mkdir(exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(prefix="package04-contract-", dir=scratch)
        self.root = Path(self.temp.name)
        base = self.root / capture.BASE
        base.mkdir(parents=True)
        package03 = self.root / capture.PACKAGE03_BASE
        package03.mkdir(parents=True)
        (self.root / "frozen.txt").write_bytes(b"frozen")
        self.freeze = {
            "status": "FROZEN_BEFORE_REPORT_QUESTION_PREPARATION",
            "files": [{"path": "frozen.txt", "sha256": capture.sha(b"frozen")}],
        }
        (package03 / "system-freeze.json").write_text(json.dumps(self.freeze), encoding="utf-8")
        (package03 / "request-package.json").write_text(
            json.dumps({"questionProjection": [{"caseId": "R01", "question": "报告题"}]}), encoding="utf-8"
        )
        product = self.root / "docs/product"
        product.mkdir(parents=True)
        (product / "rule-check-2025-human-reference-report.json").write_text(
            json.dumps({"records": [{"reviewId": "Q01", "question": "开发题"}]}), encoding="utf-8"
        )
        body = {
            "model": capture.MODEL,
            "max_tokens": 128,
            "temperature": 0,
            "stream": False,
            "enable_thinking": False,
            "response_format": {"type": "json_object"},
            "messages": [
                {"role": "system", "content": "synthetic-system"},
                {
                    "role": "user",
                    "content": json.dumps(
                        {
                            "question": "新的重复探针",
                            "options": [
                                {"ruleId": "S01/GENERAL", "questionScope": "synthetic"},
                                {"ruleId": "A03/GENERAL", "questionScope": "synthetic distractor"},
                            ],
                        },
                        ensure_ascii=False,
                    ),
                },
            ],
        }
        self.pack = {
            "authority": "PREPARED_NOT_AUTHORIZED_FOR_TRANSFER",
            "purpose": "PACKAGE_04_INDEPENDENT_REPEAT_NOT_QUALITY_EVAL",
            "endpoint": capture.ENDPOINT,
            "model": capture.MODEL,
            "maxCalls": 5,
            "retryCount": 0,
            "perRequestDeadlineSeconds": 5,
            "overallDeadlineSeconds": 35,
            "expectedRuleId": "S01/GENERAL",
            "probeId": "P04-R01",
            "requests": [
                {"trialId": f"P04-R01-T{i}", "kind": "INDEPENDENT_PROVIDER_TRIAL", "body": body}
                for i in range(1, 6)
            ],
        }
        self.original_sha = capture.PACKAGE_SHA
        self.write_pack()

    def tearDown(self):
        capture.PACKAGE_SHA = self.original_sha
        self.temp.cleanup()

    def write_pack(self):
        raw = json.dumps(self.pack, ensure_ascii=False).encode("utf-8")
        (self.root / capture.PACKAGE).write_bytes(raw)
        capture.PACKAGE_SHA = capture.sha(raw)
        self.authorization = {
            "authority": "DIRECT_USER_MESSAGE",
            "decision": "CONFIRMED",
            "endpoint": capture.ENDPOINT,
            "model": capture.MODEL,
            "maxCalls": 5,
            "packagePath": capture.PACKAGE,
            "packageSha256": capture.PACKAGE_SHA,
            "independentTrialsAllowed": True,
            "reportQueriesAllowed": False,
            "referenceAnswersAllowed": False,
            "databaseCredentialsAllowed": False,
        }
        self.write_authorization()

    def write_authorization(self):
        (self.root / capture.BASE / "external-transfer-authorization.json").write_text(
            json.dumps(self.authorization), encoding="utf-8"
        )

    @staticmethod
    def gate(*_):
        return {"validationPassed": True, "freezeGatePassed": True}

    @staticmethod
    def received(rule="S01/GENERAL", tokens=9):
        return {
            "status": "RESPONSE_RECEIVED",
            "protocolDiagnostic": "SELECTED",
            "ruleId": rule,
            "usageKnown": True,
            "totalTokens": tokens,
            "actualHttpAttempt": True,
        }

    def test_missing_or_old_authorization_stops_before_claim(self):
        (self.root / capture.BASE / "external-transfer-authorization.json").unlink()
        with self.assertRaises(FileNotFoundError):
            capture.capture(self.root, runner=lambda *_: self.received(), asset_audit=self.gate)
        self.assertFalse((self.root / capture.BASE / "provider-capture").exists())
        self.write_authorization()
        self.authorization["independentTrialsAllowed"] = False
        self.write_authorization()
        with self.assertRaises(ValueError):
            capture.capture(self.root, runner=lambda *_: self.received(), asset_audit=self.gate)
        self.assertFalse((self.root / capture.BASE / "provider-capture").exists())

    def test_wrong_packet_hash_and_frozen_source_drift_stop_before_network(self):
        capture.PACKAGE_SHA = "0" * 64
        with self.assertRaises(ValueError):
            capture.capture(self.root, runner=lambda *_: self.received(), asset_audit=self.gate)
        self.assertFalse((self.root / capture.BASE / "provider-capture").exists())
        self.write_pack()
        (self.root / "frozen.txt").write_bytes(b"changed")
        with self.assertRaises(ValueError):
            capture.capture(self.root, runner=lambda *_: self.received(), asset_audit=self.gate)
        self.assertFalse((self.root / capture.BASE / "provider-capture").exists())

    def test_failure_remains_in_five_trial_vector_without_retry(self):
        calls = []

        def runner(_, row, __):
            calls.append(row["trialId"])
            if len(calls) == 2:
                return {"status": "ERROR", "errorType": "SyntheticFailure", "usageKnown": False, "actualHttpAttempt": True}
            return self.received()

        result = capture.capture(self.root, runner=runner, asset_audit=self.gate)
        self.assertEqual(calls, [f"P04-R01-T{i}" for i in range(1, 6)])
        self.assertEqual(result["trialDenominator"], 5)
        self.assertEqual(result["statusVector"], ["RESPONSE_RECEIVED", "ERROR", "RESPONSE_RECEIVED", "RESPONSE_RECEIVED", "RESPONSE_RECEIVED"])
        self.assertEqual(result["retryCount"], 0)
        self.assertFalse(result["passPower5"])

    def test_success_aggregates_known_usage_and_pass_power_five(self):
        result = capture.capture(self.root, runner=lambda *_: self.received(tokens=11), asset_audit=self.gate)
        self.assertEqual(result["recordsProcessed"], 5)
        self.assertEqual(result["knownTokens"], 55)
        self.assertTrue(result["allUsageKnown"])
        self.assertTrue(result["passPower5"])
        self.assertEqual(result["selectedRuleVector"], ["S01/GENERAL"] * 5)

    def test_batch_deadline_preserves_unattempted_trials_in_denominator(self):
        moments = iter([0.0, 0.0, 1.0, 36.0])
        result = capture.capture(
            self.root,
            runner=lambda *_: self.received(),
            asset_audit=self.gate,
            monotonic=lambda: next(moments),
        )
        self.assertEqual(result["trialDenominator"], 5)
        self.assertEqual(result["recordsProcessed"], 5)
        self.assertEqual(result["statusVector"][0], "RESPONSE_RECEIVED")
        self.assertEqual(result["statusVector"][1:], ["SKIPPED_BATCH_DEADLINE"] * 4)
        self.assertFalse(result["passPower5"])

    def test_report_or_development_question_cannot_be_reused(self):
        payload = json.loads(self.pack["requests"][0]["body"]["messages"][1]["content"])
        payload["question"] = "报告题"
        for row in self.pack["requests"]:
            row["body"]["messages"][1]["content"] = json.dumps(payload, ensure_ascii=False)
        self.write_pack()
        with self.assertRaises(ValueError):
            capture.admitted(self.root)


if __name__ == "__main__":
    unittest.main()
