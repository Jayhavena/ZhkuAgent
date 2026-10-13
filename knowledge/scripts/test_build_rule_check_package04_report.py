"""第四包报告聚合器合同；用合成记录验证分母、统计与未知项。"""
import unittest
import tempfile
from pathlib import Path

import build_rule_check_package04_report as report


class ReportContract(unittest.TestCase):
    @staticmethod
    def fixtures():
        raw_records = [
            {
                "trialId": f"T{i}",
                "status": "RESPONSE_RECEIVED",
                "protocolDiagnostic": "SELECTED",
                "ruleId": "S01/GENERAL",
                "elapsedMs": elapsed,
                "usageKnown": True,
                "promptTokens": 8,
                "completionTokens": 2,
                "totalTokens": 10,
                "requestId": f"RID{i}",
                "actualHttpAttempt": True,
            }
            for i, elapsed in enumerate([3500, 3328, 3719, 3297, 3484], start=1)
        ]
        records = [
            {
                "trialId": row["trialId"],
                "path": f"{row['trialId']}.json",
                "sha256": f"hash-{row['trialId']}",
                "status": row["status"],
                "protocolDiagnostic": row["protocolDiagnostic"],
                "ruleId": row["ruleId"],
                "elapsedMs": row["elapsedMs"],
                "usageKnown": row["usageKnown"],
                "totalTokens": row["totalTokens"],
                "observedHttpAttemptSignal": row["actualHttpAttempt"],
            }
            for row in raw_records
        ]
        provider = {
            "trialDenominator": 5,
            "recordsProcessed": 5,
            "records": records,
            "retryCount": 0,
            "sourceUnchanged": True,
            "observedHttpAttemptSignals": 5,
            "independentProviderTrials": True,
            "qualityClaimAllowed": False,
        }
        replay = {
            "trialDenominator": 5,
            "providerCallsInReplay": 0,
            "resourcesVerified": True,
            "rows": [
                {
                    "result": {
                        "decision": {
                            "status": "ANSWERED",
                            "confirmedFacts": [{"id": "undergraduate-plan-obligation"}],
                        }
                    },
                    "trialId": f"T{i}",
                    "selectorOutcome": {"code": "SELECTED", "ruleId": "S01/GENERAL"},
                    "selectorAttempts": 1,
                    "actualOriginalLocations": [{
                        "reference": {
                            "sourceId": "source", "chunkId": "chunk", "revision": "2025.9.2",
                            "checksum": "ABC", "startInclusive": 0, "endExclusive": 2, "text": "条款",
                        },
                        "located": {
                            "code": "FOUND",
                            "original": {
                                "sourceId": "source", "chunkId": "chunk", "revision": "2025.9.2",
                                "checksum": "ABC", "contextText": "条款", "contextStartInclusive": 0,
                                "quoteStartInclusive": 0, "quoteEndExclusive": 2,
                            },
                        },
                    }],
                    "persistedAuditCount": 5,
                    "applicationElapsedMs": elapsed,
                    "selectorWorkersClosed": True,
                }
                for i, elapsed in enumerate([648.5, 500.5, 508.8, 440.4, 327.1], start=1)
            ],
        }
        acceptance = {
            "passed": True,
            "testCount": 1,
            "sourceDrift": 0,
            "resourcesVerified": True,
            "containerExited": True,
            "newJavaRemaining": [],
        }
        package03 = {"newProviderCalls": 11, "knownTokens": 2616}
        return provider, raw_records, replay, acceptance, package03

    def test_nearest_rank_p50_and_p95_are_deterministic(self):
        values = [3500, 3328, 3719, 3297, 3484]
        self.assertEqual(report.nearest_rank(values, 0.50), 3484)
        self.assertEqual(report.nearest_rank(values, 0.95), 3719)

    def test_five_successes_are_required_for_pass_power_five(self):
        provider, raw_records, replay, acceptance, package03 = self.fixtures()
        result = report.summarize(provider, raw_records, replay, acceptance, package03)
        self.assertTrue(result["providerSelection"]["passPower5"])
        self.assertTrue(result["applicationReplay"]["passPower5"])
        provider["records"][3]["ruleId"] = None
        raw_records[3]["ruleId"] = None
        result = report.summarize(provider, raw_records, replay, acceptance, package03)
        self.assertFalse(result["providerSelection"]["passPower5"])

    def test_denominator_cannot_shrink_after_failure(self):
        provider, raw_records, replay, acceptance, package03 = self.fixtures()
        provider["records"].pop()
        with self.assertRaises(ValueError):
            report.summarize(provider, raw_records, replay, acceptance, package03)

    def test_usage_is_actual_and_money_remains_unknown(self):
        provider, raw_records, replay, acceptance, package03 = self.fixtures()
        result = report.summarize(provider, raw_records, replay, acceptance, package03)
        self.assertEqual(result["usage"]["package04KnownTokens"], 50)
        self.assertEqual(result["usage"]["knownPackage03And04Tokens"], 2666)
        self.assertEqual(result["usage"]["knownPackage03And04Calls"], 16)
        self.assertEqual(result["cost"], {"status": "UNKNOWN_NOT_CALCULATED", "amount": None, "currency": None})
        provider["records"][0]["usageKnown"] = False
        provider["records"][0]["totalTokens"] = None
        raw_records[0]["usageKnown"] = False
        raw_records[0]["totalTokens"] = None
        result = report.summarize(provider, raw_records, replay, acceptance, package03)
        self.assertFalse(result["usage"]["allPackage04UsageKnown"])
        self.assertIsNone(result["usage"]["package04KnownTokens"])

    def test_capture_usage_breakdown_reads_top_level_fields(self):
        rows = [
            {"usageKnown": True, "promptTokens": 195, "completionTokens": 10, "totalTokens": 205}
            for _ in range(5)
        ]
        self.assertEqual(report.extract_usage_breakdown(rows), (975, 50))
        rows[0]["usageKnown"] = False
        self.assertEqual(report.extract_usage_breakdown(rows), (None, None))

    def test_resource_fields_are_mandatory(self):
        provider, raw_records, replay, acceptance, package03 = self.fixtures()
        del acceptance["containerExited"]
        with self.assertRaises(ValueError):
            report.summarize(provider, raw_records, replay, acceptance, package03)
        provider, raw_records, replay, acceptance, package03 = self.fixtures()
        replay["rows"][2]["selectorWorkersClosed"] = False
        result = report.summarize(provider, raw_records, replay, acceptance, package03)
        self.assertFalse(result["applicationReplay"]["passPower5"])
        self.assertFalse(result["resources"]["verified"])

    def test_selection_latency_is_not_labelled_end_to_end_generation(self):
        provider, raw_records, replay, acceptance, package03 = self.fixtures()
        result = report.summarize(provider, raw_records, replay, acceptance, package03)
        self.assertEqual(result["providerSelection"]["latencyMs"]["p50"], 3484)
        self.assertEqual(result["providerSelection"]["latencyMs"]["p95"], 3719)
        self.assertEqual(result["providerSelection"]["latencyScope"], "RULE_SELECTION_ONLY_SEQUENTIAL")
        self.assertFalse(result["claims"]["endToEndGenerationPerformanceProven"])
        self.assertFalse(result["claims"]["reportQualityImproved"])
        self.assertFalse(result["claims"]["G2Passed"])

    def test_raw_capture_identity_and_summary_must_be_consistent(self):
        provider, raw_records, replay, acceptance, package03 = self.fixtures()
        raw_records[1]["requestId"] = raw_records[0]["requestId"]
        with self.assertRaises(ValueError):
            report.summarize(provider, raw_records, replay, acceptance, package03)

        provider, raw_records, replay, acceptance, package03 = self.fixtures()
        provider["records"][0]["status"] = "ERROR"
        with self.assertRaises(ValueError):
            report.summarize(provider, raw_records, replay, acceptance, package03)

        provider, raw_records, replay, acceptance, package03 = self.fixtures()
        provider["sourceUnchanged"] = False
        with self.assertRaises(ValueError):
            report.summarize(provider, raw_records, replay, acceptance, package03)

        provider, raw_records, replay, acceptance, package03 = self.fixtures()
        raw_records[2]["actualHttpAttempt"] = False
        provider["records"][2]["observedHttpAttemptSignal"] = False
        provider["observedHttpAttemptSignals"] = 4
        provider["independentProviderTrials"] = False
        result = report.summarize(provider, raw_records, replay, acceptance, package03)
        self.assertFalse(result["providerSelection"]["passPower5"])
        self.assertFalse(result["providerSelection"]["independentProviderTrials"])

    def test_replay_trials_and_original_locations_must_match_capture(self):
        provider, raw_records, replay, acceptance, package03 = self.fixtures()
        replay["rows"][1]["trialId"] = replay["rows"][0]["trialId"]
        with self.assertRaises(ValueError):
            report.summarize(provider, raw_records, replay, acceptance, package03)

    def test_blocker_summary_is_replaced_from_current_metrics(self):
        provider, raw_records, replay, acceptance, package03 = self.fixtures()
        metrics = report.summarize(provider, raw_records, replay, acceptance, package03)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "blockers.md"
            path.write_text("# 历史\n\n保留。\n", encoding="utf-8")
            report._update_blockers(path, metrics)
            metrics["usage"]["package04KnownTokens"] = 777
            report._update_blockers(path, metrics)
            text = path.read_text(encoding="utf-8")
            self.assertEqual(text.count("# 第四包收口"), 1)
            self.assertIn("777 token", text)

        provider, raw_records, replay, acceptance, package03 = self.fixtures()
        replay["rows"][0]["actualOriginalLocations"] = [{}]
        with self.assertRaises(ValueError):
            report.summarize(provider, raw_records, replay, acceptance, package03)

        provider, raw_records, replay, acceptance, package03 = self.fixtures()
        replay["rows"][0]["actualOriginalLocations"][0]["located"]["original"]["checksum"] = "WRONG"
        with self.assertRaises(ValueError):
            report.summarize(provider, raw_records, replay, acceptance, package03)


if __name__ == "__main__":
    unittest.main()
