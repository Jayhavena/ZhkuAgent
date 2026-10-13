"""聚合第四包证据，生成可复算的性能、用量与收口记录。"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable


EXPECTED_TRIALS = 5
EXPECTED_RULE_ID = "S01/GENERAL"
EXPECTED_FACT_ID = "undergraduate-plan-obligation"
PACKAGE04_DIR = Path("docs/evidence/rule-check-refactor/2026-10-11/package-04")
PACKAGE03_DIR = Path("docs/evidence/rule-check-refactor/2026-10-10/package-03")


def nearest_rank(values: Iterable[float], percentile: float) -> float:
    """按 nearest-rank 计算百分位，避免小样本下统计口径漂移。"""
    ordered = sorted(values)
    if not ordered or percentile <= 0 or percentile > 1:
        raise ValueError("values must be non-empty and percentile must be in (0, 1]")
    return ordered[math.ceil(percentile * len(ordered)) - 1]


def _required(mapping: dict[str, Any], keys: Iterable[str], label: str) -> None:
    missing = [key for key in keys if key not in mapping]
    if missing:
        raise ValueError(f"{label} missing mandatory fields: {', '.join(missing)}")


def _latency(values: list[float]) -> dict[str, float]:
    return {
        "p50": nearest_rank(values, 0.50),
        "p95": nearest_rank(values, 0.95),
        "max": max(values),
    }


def _valid_original_location(location: dict[str, Any]) -> bool:
    """定位必须能把引用跨度还原到同一份审核原文，空占位不算证据。"""
    if not isinstance(location, dict):
        return False
    reference = location.get("reference")
    located = location.get("located")
    if not isinstance(reference, dict) or not isinstance(located, dict) or located.get("code") != "FOUND":
        return False
    original = located.get("original")
    if not isinstance(original, dict):
        return False
    identity_fields = ("sourceId", "chunkId", "revision")
    if any(not reference.get(field) or reference.get(field) != original.get(field) for field in identity_fields):
        return False
    # 原文定位对象的既有schema不重复checksum；若提供则必须一致，权威引用侧始终必须有值。
    if not reference.get("checksum") or (
        original.get("checksum") is not None and original.get("checksum") != reference.get("checksum")
    ):
        return False
    start = reference.get("startInclusive")
    end = reference.get("endExclusive")
    context_start = original.get("contextStartInclusive")
    quote_start = original.get("quoteStartInclusive")
    quote_end = original.get("quoteEndExclusive")
    context = original.get("contextText")
    text = reference.get("text")
    if not all(isinstance(value, int) for value in (start, end, context_start, quote_start, quote_end)):
        return False
    if not isinstance(context, str) or not isinstance(text, str) or start < 0 or end <= start:
        return False
    if quote_start < 0 or quote_end <= quote_start or quote_end > len(context):
        return False
    span_aligned = context_start + quote_start == start and context_start + quote_end == end
    # 运行器历史证据里的Java跨度按原始Unicode文本计；若终端转存发生乱码，字符串长度会改变，
    # 但整段引用与整段上下文仍须逐字一致，且记录的逻辑跨度必须一致。
    text_aligned = context[quote_start:quote_end] == text or (
        quote_start == 0 and context_start == start and quote_end == end and context == text
    )
    return span_aligned and text_aligned


def summarize(
    provider: dict[str, Any],
    provider_rows: list[dict[str, Any]],
    replay: dict[str, Any],
    acceptance: dict[str, Any],
    package03: dict[str, Any],
) -> dict[str, Any]:
    """从原始记录重算结论，不信任上游汇总出的通过布尔值。"""
    _required(
        acceptance,
        ("passed", "testCount", "sourceDrift", "resourcesVerified", "containerExited", "newJavaRemaining"),
        "acceptance",
    )
    _required(
        provider,
        ("trialDenominator", "recordsProcessed", "records", "retryCount", "sourceUnchanged"),
        "provider",
    )
    _required(replay, ("trialDenominator", "providerCallsInReplay", "resourcesVerified", "rows"), "replay")

    provider_records = provider["records"]
    replay_rows = replay["rows"]
    if provider["trialDenominator"] != EXPECTED_TRIALS or replay["trialDenominator"] != EXPECTED_TRIALS:
        raise ValueError("package 04 denominator must remain five")
    if provider["recordsProcessed"] != EXPECTED_TRIALS or len(provider_records) != EXPECTED_TRIALS or len(provider_rows) != EXPECTED_TRIALS:
        raise ValueError("provider records cannot shrink after a failure")
    if len(replay_rows) != EXPECTED_TRIALS:
        raise ValueError("application replay records cannot shrink after a failure")

    if provider["sourceUnchanged"] is not True:
        raise ValueError("frozen package-03 sources changed during provider capture")
    paths = [record.get("path") for record in provider_records]
    if None in paths or len(set(paths)) != EXPECTED_TRIALS:
        raise ValueError("provider capture paths must be five unique non-null values")
    trial_ids = [record.get("trialId") for record in provider_rows]
    if None in trial_ids or len(set(trial_ids)) != EXPECTED_TRIALS:
        raise ValueError("provider trial ids must be five unique non-null values")

    comparable = (
        "trialId", "status", "protocolDiagnostic", "ruleId", "elapsedMs", "usageKnown", "totalTokens"
    )
    for summary_row, raw_row in zip(provider_records, provider_rows, strict=True):
        if any(summary_row.get(field) != raw_row.get(field) for field in comparable):
            raise ValueError(f"provider summary differs from raw capture: {raw_row.get('trialId')}")
        if summary_row.get("observedHttpAttemptSignal") != raw_row.get("actualHttpAttempt"):
            raise ValueError(f"provider attempt signal differs from raw capture: {raw_row.get('trialId')}")

    request_ids = [record.get("requestId") for record in provider_rows]
    unique_request_ids = all(isinstance(value, str) and value for value in request_ids) and len(set(request_ids)) == EXPECTED_TRIALS
    if not unique_request_ids:
        raise ValueError("provider request ids must be five unique non-empty values")
    actual_attempts = [record.get("actualHttpAttempt") is True for record in provider_rows]
    independent_provider_trials = unique_request_ids and all(actual_attempts)
    if provider.get("observedHttpAttemptSignals") != sum(actual_attempts):
        raise ValueError("provider attempt total differs from raw captures")
    if "independentProviderTrials" in provider and provider["independentProviderTrials"] != independent_provider_trials:
        raise ValueError("provider independence summary differs from raw captures")

    provider_success = [
        record.get("status") == "RESPONSE_RECEIVED"
        and record.get("protocolDiagnostic") == "SELECTED"
        and record.get("ruleId") == EXPECTED_RULE_ID
        and record.get("actualHttpAttempt") is True
        for record in provider_rows
    ]
    provider_latency = [float(record["elapsedMs"]) for record in provider_rows]

    replay_trial_ids = [row.get("trialId") for row in replay_rows]
    if replay_trial_ids != trial_ids or len(set(replay_trial_ids)) != EXPECTED_TRIALS:
        raise ValueError("application replay trials must map one-to-one to provider captures")

    replay_success: list[bool] = []
    replay_latency: list[float] = []
    workers_closed: list[bool] = []
    for row in replay_rows:
        decision = row.get("result", {}).get("decision", {})
        fact_ids = {fact.get("id") for fact in decision.get("confirmedFacts", [])}
        selector = row.get("selectorOutcome", {})
        closed = row.get("selectorWorkersClosed") is True
        workers_closed.append(closed)
        locations = row.get("actualOriginalLocations", [])
        if not locations or not all(_valid_original_location(location) for location in locations):
            raise ValueError(f"application replay lacks a verified original location: {row.get('trialId')}")
        replay_success.append(
            decision.get("status") == "ANSWERED"
            and EXPECTED_FACT_ID in fact_ids
            and selector.get("code") == "SELECTED"
            and selector.get("ruleId") == EXPECTED_RULE_ID
            and row.get("selectorAttempts") == 1
            and len(locations) >= 1
            and row.get("persistedAuditCount", 0) > 0
            and closed
        )
        replay_latency.append(float(row["applicationElapsedMs"]))

    resources_verified = (
        acceptance["passed"] is True
        and acceptance["testCount"] == 1
        and acceptance["sourceDrift"] == 0
        and acceptance["resourcesVerified"] is True
        and acceptance["containerExited"] is True
        and acceptance["newJavaRemaining"] == []
        and replay["providerCallsInReplay"] == 0
        and replay["resourcesVerified"] is True
        and all(workers_closed)
    )

    all_usage_known = all(
        record.get("usageKnown") is True
        and isinstance(record.get("totalTokens"), int)
        and record["totalTokens"] >= 0
        for record in provider_rows
    )
    package04_tokens = sum(record["totalTokens"] for record in provider_rows) if all_usage_known else None
    package03_tokens = package03.get("knownTokens")
    package03_calls = package03.get("newProviderCalls")
    combined_tokens = (
        package03_tokens + package04_tokens
        if isinstance(package03_tokens, int) and isinstance(package04_tokens, int)
        else None
    )
    combined_calls = package03_calls + EXPECTED_TRIALS if isinstance(package03_calls, int) else None

    return {
        "providerSelection": {
            "trialDenominator": EXPECTED_TRIALS,
            "successCount": sum(provider_success),
            "successVector": provider_success,
            "passPower5": all(provider_success),
            "retryCount": provider["retryCount"],
            "sourceUnchanged": True,
            "independentProviderTrials": independent_provider_trials,
            "latencyScope": "RULE_SELECTION_ONLY_SEQUENTIAL",
            "latencyMs": _latency(provider_latency),
        },
        "applicationReplay": {
            "trialDenominator": EXPECTED_TRIALS,
            "successCount": sum(replay_success),
            "successVector": replay_success,
            "passPower5": all(replay_success) and resources_verified,
            "providerCallsInReplay": replay["providerCallsInReplay"],
            "latencyScope": "OFFLINE_APPLICATION_REPLAY_WITH_REAL_DATABASE",
            "latencyMs": _latency(replay_latency),
        },
        "usage": {
            "package04Calls": EXPECTED_TRIALS,
            "allPackage04UsageKnown": all_usage_known,
            "package04KnownTokens": package04_tokens,
            "knownPackage03And04Calls": combined_calls,
            "knownPackage03And04Tokens": combined_tokens,
        },
        # 没有冻结的官方单价与实际账单，金额必须保持未知，不能用推算值替代账单事实。
        "cost": {"status": "UNKNOWN_NOT_CALCULATED", "amount": None, "currency": None},
        "resources": {
            "verified": resources_verified,
            "databaseContainerExited": acceptance["containerExited"] is True,
            "selectorWorkersClosed": all(workers_closed),
            "sourceDrift": acceptance["sourceDrift"],
        },
        "claims": {
            "endToEndGenerationPerformanceProven": False,
            "concurrentLoadPerformanceProven": False,
            "reportQualityImproved": False,
            "studentBenefitProven": False,
            "G2Passed": False,
            "defaultCatalogueOpened": False,
        },
    }


def _read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8-sig"))


def _write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _verify_provider_files(root: Path, provider: dict[str, Any]) -> list[dict[str, Any]]:
    capture_dir = root / PACKAGE04_DIR / "provider-capture"
    rows: list[dict[str, Any]] = []
    seen_paths: set[str] = set()
    for record in provider["records"]:
        relative = record.get("path")
        if not isinstance(relative, str) or Path(relative).name != relative or relative in seen_paths:
            raise ValueError("provider capture path must be a unique file name")
        seen_paths.add(relative)
        path = capture_dir / relative
        if not path.is_file() or _sha256(path) != record["sha256"]:
            raise ValueError(f"provider capture hash mismatch: {record['path']}")
        rows.append(_read_json(path))
    return rows


def _verify_package03_immutable(root: Path) -> int:
    integrity = _read_json(root / PACKAGE03_DIR / "integrity.json")
    immutable = [item for item in integrity["artifacts"] if item.get("role") == "IMMUTABLE_PACKAGE_03_EVIDENCE"]
    for item in immutable:
        path = root / item["path"]
        if not path.is_file() or _sha256(path) != item["sha256"]:
            raise ValueError(f"package 03 immutable evidence changed: {item['path']}")
    return len(immutable)


def extract_usage_breakdown(rows: list[dict[str, Any]]) -> tuple[int | None, int | None]:
    """提取采集器冻结的顶层用量；任一记录未知时不拼出看似精确的分项。"""
    prompt = 0
    completion = 0
    for row in rows:
        if (
            row.get("usageKnown") is not True
            or not isinstance(row.get("promptTokens"), int)
            or not isinstance(row.get("completionTokens"), int)
        ):
            return None, None
        prompt += row["promptTokens"]
        completion += row["completionTokens"]
    return prompt, completion


def _read_usage_breakdown(root: Path, provider: dict[str, Any]) -> tuple[int | None, int | None]:
    rows = [
        _read_json(root / PACKAGE04_DIR / "provider-capture" / item["path"])
        for item in provider["records"]
    ]
    return extract_usage_breakdown(rows)


def _update_blockers(path: Path, metrics: dict[str, Any]) -> None:
    heading = "# 第四包收口（2026-10-11 DONE，固定路径重复通过）"
    current = path.read_text(encoding="utf-8-sig")
    provider = metrics["providerSelection"]
    replay = metrics["applicationReplay"]
    tokens = metrics["usage"]["package04KnownTokens"]
    prefix = f"""{heading}

同一条明显问题在5次独立供应商选类中{provider['successCount']}/5选择S01/GENERAL，随后用冻结响应离线回放真实应用与专用临时库，{replay['successCount']}/5均形成ANSWERED、必要事实、审计记录和可定位原文；无重试，供应商选类耗时p50 {provider['latencyMs']['p50']:.4f}ms、p95 {provider['latencyMs']['p95']:.4f}ms，应用回放耗时p50 {replay['latencyMs']['p50']:.4f}ms、p95 {replay['latencyMs']['p95']:.4f}ms，本包实际用量{tokens} token。该结论只覆盖单一明显固定路径的顺序选类和离线应用回放，不代表16题质量改善、完整生成链路性能、并发能力或学生收益；金额因缺少冻结单价与实际账单保持未知。默认CLOSED、G2未通过，只剩第五包版本冻结，不新增开发包。见[第四包报告](rule-check-package-04-results.md)。以下保留历史。

"""
    if heading in current:
        start = current.index(heading)
        next_heading = current.find("\n# ", start + len(heading))
        remainder = current[next_heading + 1 :] if next_heading >= 0 else ""
        current = current[:start] + remainder
    path.write_text(prefix + current, encoding="utf-8")


def _report_markdown(metrics: dict[str, Any], immutable_count: int, prompt: int | None, completion: int | None) -> str:
    provider = metrics["providerSelection"]
    replay = metrics["applicationReplay"]
    usage = metrics["usage"]
    return f"""# 校园规则查证第四包：独立重复、性能与用量报告

## 结论

- 状态：`DONE_PACKAGE_04_BOUNDED_RELIABILITY`
- 版本：`0.1.0-rc.1`
- 默认资料目录：`CLOSED`
- G2：未通过
- 剩余开发包：1（第五包版本冻结）

第四包已经回答一个有界问题：同一条明显问题能否在5次彼此独立、无重试的真实模型选类中稳定选择同一条已审核规则，并在冻结响应离线回放后通过真实应用与专用临时数据库形成可核验结果。供应商侧与应用侧均为5/5通过，因此本路径的 `pass^5` 为真。

## 可复算结果

| 层级 | 分母与结果 | p50 | p95 | 最大值 | 范围 |
|---|---:|---:|---:|---:|---|
| 供应商选类 | {provider['successCount']}/{provider['trialDenominator']}，pass^5=`{str(provider['passPower5']).lower()}` | {provider['latencyMs']['p50']:.4f} ms | {provider['latencyMs']['p95']:.4f} ms | {provider['latencyMs']['max']:.4f} ms | 顺序执行的规则选择调用 |
| 应用回放 | {replay['successCount']}/{replay['trialDenominator']}，pass^5=`{str(replay['passPower5']).lower()}` | {replay['latencyMs']['p50']:.4f} ms | {replay['latencyMs']['p95']:.4f} ms | {replay['latencyMs']['max']:.4f} ms | 冻结模型响应、真实应用、专用临时库 |

- 真实供应商调用：5；重试：0；回放新增供应商调用：0。
- 第四包已知 token：{usage['package04KnownTokens']}（prompt={prompt}，completion={completion}）；第三、四包合计已知调用 {usage['knownPackage03And04Calls']} 次、token {usage['knownPackage03And04Tokens']}。
- 金额：`UNKNOWN_NOT_CALCULATED`。没有冻结官方单价和实际账单，不推算货币金额。
- 资源：5次选择器执行器均关闭；临时数据库容器退出；源码漂移为0；应用验收通过。
- 完整性：5份供应商响应哈希复核通过；第三包 {immutable_count} 项不可变证据复核通过。
- 独立审查：0 Critical、2 Important、1 Minor；在唯一一次修复批次中全部处理，未再次调用供应商。

## 通过标准

供应商记录只有同时满足 `RESPONSE_RECEIVED`、协议解析为 `SELECTED`、规则为 `S01/GENERAL` 才算成功。应用回放只有同时得到 `ANSWERED`、必要事实 `undergraduate-plan-obligation`、同一规则、一次选择尝试、至少一处原文定位、正数审计记录并关闭选择器执行器才算成功。任何失败都保留5次分母，不能删题缩小分母。

## 结论边界

本包没有重新消费16题报告集，也没有产生新的人工语义评分。第三包仍是7 PASS、8 FAIL、1 UNJUDGEABLE，冻结维度仍是8 FAIL、8 UNJUDGEABLE。第四包的5/5不能覆盖这些失败。

耗时只代表规则选类的顺序调用，以及冻结响应下的应用回放。它不证明完整回答生成端到端性能、并发负载、万级资料检索或供应商服务等级。单个明显问题的重复成功也不证明学生查证更快、更正确或更愿意使用。

因此默认目录继续保持 `CLOSED`，G2保持未通过。下一步只执行第五包版本冻结：固定版本、证据索引、验收命令与未解决限制，不再扩展本轮功能范围。
"""


def _credential_scan(root: Path, paths: list[Path]) -> dict[str, Any]:
    patterns = [
        re.compile(rb"sk-[A-Za-z0-9_-]{20,}"),
        re.compile(rb"(?i)(?:password|api[_-]?key)\s*[:=]\s*['\"][^'\"\r\n]{8,}['\"]"),
    ]
    findings: list[str] = []
    for relative in paths:
        data = (root / relative).read_bytes()
        if any(pattern.search(data) for pattern in patterns):
            findings.append(relative.as_posix())
    return {"status": "PASS" if not findings else "FAIL", "findingCount": len(findings), "paths": findings}


def generate(root: Path) -> dict[str, Any]:
    root = root.resolve()
    package04 = root / PACKAGE04_DIR
    closeout_path = package04 / "closeout.json"
    integrity_path = package04 / "integrity.json"
    if closeout_path.exists() or integrity_path.exists():
        raise FileExistsError("package 04 closeout already exists; refusing to overwrite frozen evidence")

    provider = _read_json(package04 / "provider-capture" / "summary.json")
    replay = _read_json(package04 / "application-replay" / "package04-reliability-results.json")
    acceptance = _read_json(package04 / "application-replay" / "results.json")
    package03 = _read_json(root / PACKAGE03_DIR / "closeout.json")
    provider_rows = _verify_provider_files(root, provider)
    metrics = summarize(provider, provider_rows, replay, acceptance, package03)
    if not metrics["providerSelection"]["passPower5"] or not metrics["applicationReplay"]["passPower5"]:
        raise ValueError("package 04 cannot close because pass^5 failed")
    if provider.get("retryCount") != 0 or provider.get("observedHttpAttemptSignals") != EXPECTED_TRIALS:
        raise ValueError("provider attempt or retry count violates the authorized budget")
    if provider.get("reportCasesConsumed") != 0 or provider.get("qualityClaimAllowed") is not False:
        raise ValueError("package 04 must not consume report cases or claim quality")

    provider_hashes = len(provider_rows)
    package03_immutable = _verify_package03_immutable(root)
    prompt_tokens, completion_tokens = extract_usage_breakdown(provider_rows)
    if prompt_tokens is not None and completion_tokens is not None:
        if prompt_tokens + completion_tokens != metrics["usage"]["package04KnownTokens"]:
            raise ValueError("provider usage breakdown does not match total tokens")
    metrics["usage"]["package04PromptTokens"] = prompt_tokens
    metrics["usage"]["package04CompletionTokens"] = completion_tokens

    report_path = root / "docs/product/rule-check-package-04-results.md"
    blockers_path = root / "docs/product/rule-check-blockers.md"
    if report_path.exists():
        raise FileExistsError("package 04 product report already exists; refusing to overwrite")
    report_path.write_text(
        _report_markdown(metrics, package03_immutable, prompt_tokens, completion_tokens), encoding="utf-8"
    )
    _update_blockers(blockers_path, metrics)

    package_files = [
        path.relative_to(root)
        for path in package04.rglob("*")
        if path.is_file() and path.name not in {"integrity.json", "closeout.json"}
    ]
    delivery_files = [
        Path("knowledge/scripts/capture_rule_check_package04.py"),
        Path("knowledge/scripts/test_capture_rule_check_package04.py"),
        Path("knowledge/scripts/build_rule_check_package04_report.py"),
        Path("knowledge/scripts/test_build_rule_check_package04_report.py"),
        Path("knowledge/src/test/java/com/zhku/knowledge/retrieval/RuleCheck2025ReliabilityReplayTest.java"),
        Path("knowledge/src/test/java/com/zhku/knowledge/rulecheck/BoundedRuleSelectorTest.java"),
        Path("knowledge/scripts/verify-rule-check-refactor.ps1"),
        Path("scripts/tests/verify-rule-check-refactor-contract.ps1"),
        Path("docs/product/rule-check-package-04-plan.md"),
        Path("docs/superpowers/plans/2026-10-11-rule-check-package-04-plan.md"),
        Path("docs/product/rule-check-package-04-results.md"),
        Path("docs/product/rule-check-blockers.md"),
    ]
    # 失败批次也属于工程证据，不能只冻结最终绿色结果。
    for phase in ("package04-final-unit-01", "package04-final-unit-02", "package04-final-unit-03"):
        phase_dir = root / "docs/evidence/rule-check-refactor/2026-10-07" / phase
        delivery_files.extend(path.relative_to(root) for path in phase_dir.rglob("*") if path.is_file())
    all_paths = sorted(set(package_files + delivery_files), key=lambda item: item.as_posix())
    scan = _credential_scan(root, all_paths)
    if scan["findingCount"]:
        raise ValueError("credential-like material found in package 04 evidence")
    credential_scan_path = package04 / "credential-scan.json"
    _write_json(credential_scan_path, scan)
    all_paths.append(credential_scan_path.relative_to(root))
    all_paths = sorted(set(all_paths), key=lambda item: item.as_posix())

    artifacts = [
        {
            "path": path.as_posix(),
            "sha256": _sha256(root / path),
            "bytes": (root / path).stat().st_size,
        }
        for path in all_paths
    ]
    integrity = {
        "algorithm": "SHA256",
        "stage": "PACKAGE04_BOUNDED_RELIABILITY_CLOSED",
        "recordedAt": datetime.now(timezone.utc).isoformat(),
        "artifacts": artifacts,
        "providerCaptureHashesVerified": provider_hashes,
        "package03ImmutableChecks": package03_immutable,
        "credentialFindings": 0,
    }
    _write_json(integrity_path, integrity)

    closeout = {
        "status": "DONE_PACKAGE_04_BOUNDED_RELIABILITY",
        "remainingPackages": 1,
        "version": "0.1.0-rc.1",
        "defaultCatalogue": "CLOSED",
        "G2Passed": False,
        "newProviderCalls": EXPECTED_TRIALS,
        "retryCount": 0,
        "knownTokens": metrics["usage"]["package04KnownTokens"],
        "providerSelection": metrics["providerSelection"],
        "applicationReplay": metrics["applicationReplay"],
        "usage": metrics["usage"],
        "cost": metrics["cost"],
        "resources": metrics["resources"],
        "claims": metrics["claims"],
        "providerCaptureHashesVerified": provider_hashes,
        "package03ImmutableChecks": package03_immutable,
        "credentialFindings": 0,
        "independentReview": {
            "initialCritical": 0,
            "initialImportant": 2,
            "initialMinor": 1,
            "resolvedCritical": 0,
            "resolvedImportant": 2,
            "resolvedMinor": 1,
            "fixPasses": 1,
            "secondReviewRun": False,
        },
        "integritySha256": _sha256(integrity_path),
        "limitations": [
            "只覆盖单一明显固定问题的五次独立顺序选类与冻结响应应用回放",
            "不证明16题质量改善、完整生成链路性能、并发能力或学生收益",
            "缺少冻结官方单价与实际账单，货币成本保持未知",
        ],
    }
    _write_json(closeout_path, closeout)
    return closeout


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--build", action="store_true", help="生成第四包收口产物")
    args = parser.parse_args()
    if not args.build:
        parser.error("use --build to create the immutable closeout")
    closeout = generate(args.root)
    print(
        json.dumps(
            {
                "status": closeout["status"],
                "providerPassPower5": closeout["providerSelection"]["passPower5"],
                "applicationPassPower5": closeout["applicationReplay"]["passPower5"],
                "remainingPackages": closeout["remainingPackages"],
            },
            ensure_ascii=False,
        )
    )


if __name__ == "__main__":
    main()
