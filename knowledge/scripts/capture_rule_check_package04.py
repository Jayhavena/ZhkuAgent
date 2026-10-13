"""第四包五次真实独立选题采集；固定分母、无重试，不把重复稳定性冒充质量。"""
from pathlib import Path
import argparse
import json
import time
from datetime import datetime, timezone

from audit_rule_check_eval_assets import audit
from capture_rule_check_embeddings import run_bounded
from capture_rule_check_selection import ENDPOINT, MODEL, read, request_selection, require, save_new, sha, strict_json


BASE = "docs/evidence/rule-check-refactor/2026-10-11/package-04"
PACKAGE = BASE + "/request-package.json"
PACKAGE03_BASE = "docs/evidence/rule-check-refactor/2026-10-10/package-03"
PACKAGE_SHA = "5c67cb3209bc96fd5abb4fe727dfcdb6be27f17975883ebffdf4cd57e8483204"
TRIAL_IDS = [f"P04-R01-T{i}" for i in range(1, 6)]


def verify_frozen_sources(root):
    freeze = read(root / PACKAGE03_BASE / "system-freeze.json")
    require(freeze.get("status") == "FROZEN_BEFORE_REPORT_QUESTION_PREPARATION")
    require(isinstance(freeze.get("files"), list) and freeze["files"])
    for row in freeze["files"]:
        target = (root / row["path"]).resolve()
        require(target.is_relative_to(root.resolve()))
        require(sha(target.read_bytes()) == row["sha256"])
    return freeze


def reserved_questions(root):
    result = set()
    report = read(root / PACKAGE03_BASE / "request-package.json")
    for row in report.get("questionProjection", []):
        if isinstance(row.get("question"), str):
            result.add(row["question"])
    development = read(root / "docs/product/rule-check-2025-human-reference-report.json")
    for row in development.get("records", []):
        if isinstance(row.get("question"), str):
            result.add(row["question"])
    return result


def admitted(root):
    out = root / BASE / "provider-capture"
    require(not out.exists())
    package_path = root / PACKAGE
    raw = package_path.read_bytes()
    require(sha(raw) == PACKAGE_SHA)
    pack = strict_json(raw)
    require(
        pack.get("authority") == "PREPARED_NOT_AUTHORIZED_FOR_TRANSFER"
        and pack.get("purpose") == "PACKAGE_04_INDEPENDENT_REPEAT_NOT_QUALITY_EVAL"
        and pack.get("endpoint") == ENDPOINT
        and pack.get("model") == MODEL
        and pack.get("maxCalls") == 5
        and pack.get("retryCount") == 0
        and pack.get("perRequestDeadlineSeconds") == 5
        and pack.get("overallDeadlineSeconds") == 35
        and pack.get("expectedRuleId") == "S01/GENERAL"
    )
    rows = pack.get("requests")
    require(isinstance(rows, list) and len(rows) == 5)
    require([row.get("trialId") for row in rows] == TRIAL_IDS)
    blocked_questions = reserved_questions(root)
    canonical_payload = None
    for row in rows:
        require(row.get("kind") == "INDEPENDENT_PROVIDER_TRIAL")
        body = row.get("body")
        require(
            isinstance(body, dict)
            and body.get("model") == MODEL
            and body.get("max_tokens") == 128
            and body.get("temperature") == 0
            and body.get("stream") is False
            and body.get("enable_thinking") is False
            and body.get("response_format") == {"type": "json_object"}
        )
        messages = body.get("messages")
        require(
            isinstance(messages, list)
            and len(messages) == 2
            and messages[0].get("role") == "system"
            and messages[1].get("role") == "user"
        )
        payload = strict_json(messages[1].get("content", ""))
        require(set(payload) == {"question", "options"})
        require(isinstance(payload["question"], str) and payload["question"] not in blocked_questions)
        require(isinstance(payload["options"], list) and 1 <= len(payload["options"]) <= 8)
        require(all(set(option) == {"ruleId", "questionScope"} for option in payload["options"]))
        ids = [option["ruleId"] for option in payload["options"]]
        require(len(ids) == len(set(ids)) and pack["expectedRuleId"] in ids)
        current = json.dumps(payload, ensure_ascii=False, sort_keys=True)
        if canonical_payload is None:
            canonical_payload = current
        require(current == canonical_payload)

    authorization = read(root / BASE / "external-transfer-authorization.json")
    require(
        authorization.get("authority") == "DIRECT_USER_MESSAGE"
        and authorization.get("decision") == "CONFIRMED"
        and authorization.get("endpoint") == ENDPOINT
        and authorization.get("model") == MODEL
        and authorization.get("maxCalls") == 5
        and authorization.get("packagePath") == PACKAGE
        and authorization.get("packageSha256") == PACKAGE_SHA
        and authorization.get("independentTrialsAllowed") is True
        and authorization.get("reportQueriesAllowed") is False
        and authorization.get("referenceAnswersAllowed") is False
        and authorization.get("databaseCredentialsAllowed") is False
    )
    verify_frozen_sources(root)
    return pack


def capture(root, runner=None, asset_audit=None, monotonic=None):
    pack = admitted(root)
    check_assets = audit if asset_audit is None else asset_audit
    gate = check_assets(root, root.parent / "SuperAgent-zhkuagent-mvp/data/raw/2025版学生手册2025.9.2.pdf")
    require(gate["validationPassed"] and gate["freezeGatePassed"])

    out = root / BASE / "provider-capture"
    out.mkdir(exist_ok=False)
    save_new(out / "batch-claim.json", {"status": "CLAIMED_BEFORE_NETWORK", "maxCalls": 5, "retryCount": 0, "singleUse": True})
    protected = [
        root / PACKAGE,
        Path(__file__).resolve(),
        Path(__file__).resolve().with_name("capture_rule_check_selection.py"),
        Path(__file__).resolve().with_name("capture_rule_check_embeddings.py"),
    ]
    before = {str(path): sha(path.read_bytes()) for path in protected}
    save_new(
        out / "preflight.json",
        {
            "endpoint": ENDPOINT,
            "model": MODEL,
            "packageSha256": PACKAGE_SHA,
            "sourceHashes": before,
            "freezeGate": gate,
            "trialDenominator": 5,
            "perRequestDeadlineSeconds": 5,
            "overallDeadlineSeconds": 35,
            "retryCount": 0,
            "qualityClaimAllowed": False,
        },
    )

    execute = run_bounded if runner is None else runner
    now = time.monotonic if monotonic is None else monotonic
    started = now()
    records = []
    rows = pack["requests"]
    for index, row in enumerate(rows):
        begin = now()
        remaining = 35 - (begin - started)
        if remaining <= 0:
            for skipped in rows[index:]:
                value = {
                    "trialId": skipped["trialId"],
                    "kind": skipped["kind"],
                    "status": "SKIPPED_BATCH_DEADLINE",
                    "elapsedMs": 0,
                    "actualHttpAttempt": False,
                    "usageKnown": False,
                }
                target = out / (skipped["trialId"] + ".json")
                save_new(target, value)
                records.append(_identity(target, value))
            break
        try:
            require(sha((root / PACKAGE).read_bytes()) == PACKAGE_SHA)
            verify_frozen_sources(root)
            observed = execute(request_selection, row, min(5, remaining))
        except Exception as error:
            observed = {
                "status": "ERROR",
                "errorType": type(error).__name__,
                "actualHttpAttempt": False,
                "usageKnown": False,
            }
        value = {
            "trialId": row["trialId"],
            "kind": row["kind"],
            "elapsedMs": (now() - begin) * 1000,
            **observed,
        }
        target = out / (row["trialId"] + ".json")
        save_new(target, value)
        records.append(_identity(target, value))

    after = {path: sha(Path(path).read_bytes()) for path in before}
    verify_frozen_sources(root)
    expected = pack["expectedRuleId"]
    statuses = [row["status"] for row in records]
    selected = [row.get("ruleId") for row in records]
    success = [
        row["status"] == "RESPONSE_RECEIVED"
        and row.get("protocolDiagnostic") == "SELECTED"
        and row.get("ruleId") == expected
        for row in records
    ]
    summary = {
        "recordedAt": datetime.now(timezone.utc).isoformat(),
        "trialDenominator": 5,
        "recordsProcessed": len(records),
        "records": records,
        "statusVector": statuses,
        "selectedRuleVector": selected,
        "successVector": success,
        "passPower5": len(success) == 5 and all(success),
        "responseRecords": sum(status == "RESPONSE_RECEIVED" for status in statuses),
        "observedHttpAttemptSignals": sum(bool(row.get("observedHttpAttemptSignal")) for row in records),
        "knownTokens": sum(row["totalTokens"] for row in records if row.get("usageKnown")),
        "allUsageKnown": len(records) == 5 and all(row.get("usageKnown") for row in records),
        "retryCount": 0,
        "sourceUnchanged": before == after,
        "independentProviderTrials": True,
        "reportCasesConsumed": 0,
        "humanSemanticGradesProduced": False,
        "qualityClaimAllowed": False,
        "defaultSwitched": False,
    }
    save_new(out / "summary.json", summary)
    require(len(records) == 5 and before == after)
    return summary


def _identity(path, value):
    return {
        "trialId": value["trialId"],
        "path": path.name,
        "sha256": sha(path.read_bytes()),
        "status": value["status"],
        "protocolDiagnostic": value.get("protocolDiagnostic"),
        "ruleId": value.get("ruleId"),
        "observedHttpAttemptSignal": value.get("actualHttpAttempt", False),
        "usageKnown": value.get("usageKnown", False),
        "totalTokens": value.get("totalTokens"),
        "elapsedMs": value.get("elapsedMs"),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--capture", action="store_true")
    args = parser.parse_args()
    require(args.capture)
    root = Path(__file__).resolve().parents[2]
    try:
        summary = capture(root)
        print(
            json.dumps(
                {key: summary[key] for key in ["recordsProcessed", "passPower5", "knownTokens", "sourceUnchanged"]},
                ensure_ascii=False,
            )
        )
        return 0 if summary["passPower5"] else 2
    except Exception as failure:
        print(json.dumps({"status": "STOPPED", "errorType": type(failure).__name__}))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
