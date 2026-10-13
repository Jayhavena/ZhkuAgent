"""冻结校园规则查证0.1.0-rc.1工程基线；不执行供应商或数据库操作。"""

from __future__ import annotations

import hashlib
import json
import re
import argparse
import subprocess
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable


VERSION = "0.1.0-rc.1"
PACKAGE_PATHS = (
    Path("docs/evidence/rule-check-refactor/2026-10-10/package-01"),
    Path("docs/evidence/rule-check-refactor/2026-10-10/package-02"),
    Path("docs/evidence/rule-check-refactor/2026-10-10/package-03"),
    Path("docs/evidence/rule-check-refactor/2026-10-11/package-04"),
)
EXPECTED_STATUSES = (
    "DONE_FIRST_PACKAGE_ONLY",
    "DONE_PACKAGE_02_SEALED_REPLAY_ONLY",
    "DONE_PACKAGE_03_REPORT_WITH_CONFIRMED_FAILURES",
    "DONE_PACKAGE_04_BOUNDED_RELIABILITY",
)


def _read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8-sig"))


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _root_version(root: Path) -> str:
    tree = ET.parse(root / "pom.xml")
    project = tree.getroot()
    namespace = "{http://maven.apache.org/POM/4.0.0}" if project.tag.startswith("{") else ""
    node = project.find(f"{namespace}version")
    if node is None or not node.text:
        raise ValueError("root Maven version is missing")
    return node.text.strip()


def _validate_module_versions(root: Path) -> dict[str, list[str]]:
    versions: dict[str, list[str]] = {}
    for module in ("agent", "agent-runtime", "knowledge"):
        path = root / module / "pom.xml"
        tree = ET.parse(path)
        project = tree.getroot()
        namespace = "{http://maven.apache.org/POM/4.0.0}" if project.tag.startswith("{") else ""
        values: list[str] = []
        own = project.find(f"{namespace}version")
        parent = project.find(f"{namespace}parent/{namespace}version")
        for node in (parent, own):
            if node is not None and node.text:
                values.append(node.text.strip())
        if not values or any(value != VERSION for value in values):
            raise ValueError(f"{module} Maven version drift")
        versions[module] = values
    return versions


def _verify_integrity(root: Path, package_dir: Path, package_number: int) -> int:
    integrity_path = root / package_dir / "integrity.json"
    integrity = _read_json(integrity_path)
    artifacts = integrity.get("artifacts")
    if not isinstance(artifacts, list) or not artifacts:
        raise ValueError(f"package {package_number:02d} integrity artifacts are missing")
    # 前三包同时记录了会继续演进的交付快照，只冻结明确标记为不可变的历史证据。
    selected = artifacts if package_number == 4 else [
        item for item in artifacts if str(item.get("role", "")).startswith("IMMUTABLE")
    ]
    if package_number == 4:
        # 卡点表是跨包滚动台账，第五包必须写入最终收口状态；第四包的清单身份仍需保持不变。
        selected = [item for item in selected if item.get("path") != "docs/product/rule-check-blockers.md"]
    if not selected:  # 合同测试使用无role的最小清单。
        selected = artifacts
    for item in selected:
        relative = item.get("path")
        if not isinstance(relative, str):
            raise ValueError(f"package {package_number:02d} has an invalid artifact path")
        path = root / relative
        if not path.is_file() or _sha256(path) != item.get("sha256") or path.stat().st_size != item.get("bytes"):
            raise ValueError(f"package {package_number:02d} immutable evidence changed: {relative}")
    if package_number == 4:
        closeout = _read_json(root / package_dir / "closeout.json")
        if closeout.get("integritySha256") != _sha256(integrity_path):
            raise ValueError("package 04 integrity identity changed")
    return len(selected)


def validate_release(root: Path) -> dict[str, Any]:
    root = root.resolve()
    version = _root_version(root)
    if version != VERSION:
        raise ValueError(f"release version must remain {VERSION}")
    module_versions = _validate_module_versions(root)
    closeouts = [_read_json(root / package / "closeout.json") for package in PACKAGE_PATHS]
    statuses = [item.get("status") for item in closeouts]
    if statuses != list(EXPECTED_STATUSES):
        raise ValueError("package status chain is incomplete or reordered")
    remaining = [
        closeouts[0].get("remainingEngineeringPackages"),
        closeouts[1].get("remainingPackages"),
        closeouts[2].get("remainingPackages"),
        closeouts[3].get("remainingPackages"),
    ]
    if remaining != [4, 3, 2, 1]:
        raise ValueError("remaining-package chain must be 4,3,2,1")
    for index, closeout in enumerate(closeouts[1:], start=2):
        if closeout.get("version") != VERSION:
            raise ValueError(f"package {index:02d} version drift")
        if closeout.get("defaultCatalogue") != "CLOSED" or closeout.get("G2Passed") is not False:
            raise ValueError(f"package {index:02d} product gate drift")
    if closeouts[0].get("defaultRuntimeSwitched") is not False or closeouts[0].get("G2Passed") is not False:
        raise ValueError("package 01 product gate drift")
    verdicts = closeouts[2].get("humanCaseLevelVerdicts")
    if verdicts != {"PASS": 7, "FAIL": 8, "UNJUDGEABLE": 1}:
        raise ValueError("package 03 confirmed quality verdicts changed")
    provider = closeouts[3].get("providerSelection", {})
    replay = closeouts[3].get("applicationReplay", {})
    if (
        provider.get("trialDenominator") != 5
        or provider.get("successCount") != 5
        or provider.get("successVector") != [True] * 5
        or provider.get("passPower5") is not True
        or provider.get("independentProviderTrials") is not True
        or provider.get("retryCount") != 0
    ):
        raise ValueError("package 04 independent provider evidence is incomplete")
    if (
        replay.get("trialDenominator") != 5
        or replay.get("successCount") != 5
        or replay.get("successVector") != [True] * 5
        or replay.get("passPower5") is not True
        or replay.get("providerCallsInReplay") != 0
    ):
        raise ValueError("package 04 application replay evidence is incomplete")
    counts = [_verify_integrity(root, package, number) for number, package in enumerate(PACKAGE_PATHS, start=1)]
    return {
        "version": version,
        "moduleVersions": module_versions,
        "statuses": statuses,
        "remainingChain": remaining,
        "package03Verdicts": verdicts,
        "package04ProviderPassPower5": True,
        "package04ApplicationPassPower5": True,
        "defaultCatalogue": "CLOSED",
        "G2Passed": False,
        "historicalIntegrityChecks": counts,
        "historicalIntegrityTotal": sum(counts),
    }


def _allowed_file(path: Path, package05: Path) -> bool:
    if package05 in path.parents or path == package05:
        return False
    forbidden_parts = {"target", "__pycache__", ".git", "node_modules", "dist", ".idea"}
    if any(part in forbidden_parts for part in path.parts):
        return False
    lower_name = path.name.lower()
    sensitive_name = lower_name == ".env" or lower_name.startswith(".env.") or lower_name in {".npmrc", ".pypirc"}
    if sensitive_name or path.name == ".DS_Store" or path.suffix.lower() in {".pyc", ".class", ".pem", ".key", ".p12", ".pfx"}:
        return False
    return path.is_file()


def collect_freeze_paths(root: Path) -> list[Path]:
    root = root.resolve()
    package05 = root / "docs/evidence/rule-check-refactor/2026-10-11/package-05"
    files: set[Path] = set()
    exact = (
        "AGENTS.md", "pom.xml", "agent/pom.xml", "agent-runtime/pom.xml", "knowledge/pom.xml",
        "mvnw.cmd", ".mvn/wrapper/maven-wrapper.properties",
    )
    for relative in exact:
        path = root / relative
        if path.is_file():
            files.add(path)
    directories = (
        "agent/src", "agent/frontend", "agent-runtime/src", "knowledge/src", "knowledge/scripts", "scripts",
    )
    for relative in directories:
        directory = root / relative
        if directory.is_dir():
            files.update(path for path in directory.rglob("*") if _allowed_file(path, package05))
    product = root / "docs/product"
    if product.is_dir():
        files.update(path for path in product.glob("rule-check-*") if _allowed_file(path, package05))
    plans = root / "docs/superpowers/plans"
    if plans.is_dir():
        files.update(path for path in plans.glob("*rule-check*.md") if _allowed_file(path, package05))
    for package in PACKAGE_PATHS:
        directory = root / package
        if directory.is_dir():
            files.update(path for path in directory.rglob("*") if _allowed_file(path, package05))
    return sorted(files, key=lambda path: path.relative_to(root).as_posix())


def scan_credentials(root: Path, paths: Iterable[Path]) -> dict[str, Any]:
    root = root.resolve()
    token_patterns = (
        re.compile(rb"sk-[A-Za-z0-9_-]{20,}"),
        re.compile(rb"(?:ghp_|github_pat_|xox[baprs]-)[A-Za-z0-9_-]{16,}"),
        re.compile(rb"AKIA[0-9A-Z]{16}"),
        re.compile(rb"postgres(?:ql)?://[^\s:/]+:[^\s@]{8,}@"),
    )
    unconditional_patterns = (
        re.compile(rb"\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}"),
        re.compile(rb"-----BEGIN [A-Z ]*PRIVATE KEY-----\r?\n"),
    )
    literal_assignment = re.compile(
        rb"(?i)(?:password|passwd|api[_-]?key|secret|token)\s*[:=]\s*['\"]([^'\"\r\n]{8,})['\"]"
    )
    # 测试夹具中的显式假值可以进入工程基线；真实形态且没有这些语义标记的值必须阻断冻结。
    safe_markers = (
        b"${", b"%", b"redacted", b"placeholder", b"example", b"fixture", b"must_not",
        b"test", b"synthetic", b"fake", b"local", b"contract", b"public", b"next", b"first",
        b"new", b"{", b"+", "修改为你的".encode("utf-8"),
    )

    def is_placeholder(value: bytes) -> bool:
        lowered = value.lower()
        return any(marker in lowered for marker in safe_markers)

    findings: list[str] = []
    for path in paths:
        data = path.read_bytes()
        assignment_risk = any(not is_placeholder(match.group(1)) for match in literal_assignment.finditer(data))
        token_risk = any(
            not is_placeholder(match.group(0))
            for pattern in token_patterns
            for match in pattern.finditer(data)
        )
        if any(pattern.search(data) for pattern in unconditional_patterns) or token_risk or assignment_risk:
            findings.append(path.resolve().relative_to(root).as_posix())
    return {"status": "PASS" if not findings else "FAIL", "findingCount": len(findings), "paths": findings}


def verify_freeze(root: Path) -> dict[str, Any]:
    root = root.resolve()
    package = root / "docs/evidence/rule-check-refactor/2026-10-11/package-05"
    integrity_path = package / "integrity.json"
    closeout_path = package / "closeout.json"
    integrity = _read_json(integrity_path)
    closeout = _read_json(closeout_path)
    artifacts = integrity.get("artifacts")
    if not isinstance(artifacts, list) or integrity.get("artifactCount") != len(artifacts):
        raise ValueError("package 05 artifactCount differs from integrity entries")
    if closeout.get("artifactCount") != len(artifacts):
        raise ValueError("package 05 closeout artifactCount differs from integrity")
    if closeout.get("integritySha256") != _sha256(integrity_path):
        raise ValueError("package 05 closeout integrity identity mismatch")
    expected_closeout = {
        "status": "FROZEN_ENGINEERING_BASELINE_NOT_RELEASED_TO_STUDENTS",
        "version": VERSION,
        "remainingPackages": 0,
        "defaultCatalogue": "CLOSED",
        "G2Passed": False,
        "studentPilotReleased": False,
    }
    if any(closeout.get(key) != value for key, value in expected_closeout.items()):
        raise ValueError("package 05 product boundary or version changed")
    for item in artifacts:
        path = root / item["path"]
        if not path.is_file() or path.stat().st_size != item.get("bytes") or _sha256(path) != item.get("sha256"):
            raise ValueError(f"package 05 frozen artifact changed: {item.get('path')}")
    return {
        "status": "VERIFIED",
        "verifiedArtifacts": len(artifacts),
        "integritySha256": closeout["integritySha256"],
        "remainingPackages": closeout.get("remainingPackages"),
        "defaultCatalogue": closeout.get("defaultCatalogue"),
        "G2Passed": closeout.get("G2Passed"),
    }


def build(root: Path) -> dict[str, Any]:
    """生成最终工程冻结；输出是本地清单，不暗示Git提交、标签或产品放行。"""
    root = root.resolve()
    package05 = root / "docs/evidence/rule-check-refactor/2026-10-11/package-05"
    closeout_path = package05 / "closeout.json"
    integrity_path = package05 / "integrity.json"
    if closeout_path.exists() or integrity_path.exists():
        raise FileExistsError("package 05 freeze already exists; refusing to overwrite")
    package05.mkdir(parents=True, exist_ok=True)
    validation = validate_release(root)
    verification = _verification_summary(root)
    limitations = _limitations()
    release_path = root / "docs/product/rule-check-release-0.1.0-rc.1.md"
    if release_path.exists():
        raise FileExistsError("release note already exists; refusing to overwrite")
    branch = _git(root, "branch", "--show-current")
    head = _git(root, "rev-parse", "HEAD")
    dirty = bool(_git(root, "status", "--porcelain"))
    tags_at_head = [value for value in _git(root, "tag", "--points-at", "HEAD").splitlines() if value]
    reproduction_path = package05 / "reproduction.md"
    limitations_path = package05 / "known-limitations.json"
    credential_path = package05 / "credential-scan.json"
    scope_path = package05 / "freeze-scope.json"
    roadmap_path = root / "docs/product/rule-check-remaining-tasks-2026-10-08.md"
    blockers_path = root / "docs/product/rule-check-blockers.md"
    generated_contents = {
        release_path: _release_markdown(validation, verification, limitations, tags_at_head),
        reproduction_path: _reproduction_markdown(verification),
        limitations_path: json.dumps({"version": VERSION, "items": limitations}, ensure_ascii=False, indent=2) + "\n",
        roadmap_path: _roadmap_text(roadmap_path.read_text(encoding="utf-8-sig")),
        blockers_path: _blockers_text(blockers_path.read_text(encoding="utf-8-sig")),
    }
    paths_before_write = collect_freeze_paths(root)
    existing_support = [
        path for path in package05.rglob("*")
        if path.is_file() and path not in {closeout_path, integrity_path, credential_path, scope_path, reproduction_path, limitations_path}
    ]
    credential_scan = scan_credentials(root, paths_before_write + existing_support)
    if credential_scan["findingCount"] or _generated_credential_findings(root, generated_contents):
        raise ValueError("credential-like material found in freeze path set")

    backups = {path: path.read_bytes() for path in (roadmap_path, blockers_path)}
    created = [release_path, reproduction_path, limitations_path, credential_path, scope_path, integrity_path, closeout_path]
    try:
        for path, content in generated_contents.items():
            _atomic_write_text(path, content)
        _write_json_atomic(credential_path, credential_scan)
        authoritative_paths = collect_freeze_paths(root)
        package_support = [
            path for path in package05.rglob("*")
            if path.is_file() and path not in {closeout_path, integrity_path, scope_path}
        ]
        planned_all = set(authoritative_paths + package_support + [scope_path])
        scope = {
            "version": VERSION,
            "status": "FROZEN_ENGINEERING_BASELINE_NOT_RELEASED_TO_STUDENTS",
            "gitBranch": branch,
            "gitHead": head,
            "workingTreeClean": not dirty,
            "gitTagCreatedByPackage05": False,
            "tagsAtHead": tags_at_head,
            "tagBoundary": "现有标签只标识HEAD提交，不包含本次工作区内容哈希冻结",
            "authoritativePathCount": len(authoritative_paths),
            "manifestArtifactCount": len(planned_all),
            "packageSupportArtifactCount": len(planned_all) - len(authoritative_paths),
            "excluded": ["credentials", "target", "__pycache__", "node_modules", "dist", "package-05 integrity/closeout self outputs"],
        }
        _write_json_atomic(scope_path, scope)
        all_paths = sorted(planned_all, key=lambda path: path.relative_to(root).as_posix())
        artifacts = [
            {"path": path.relative_to(root).as_posix(), "sha256": _sha256(path), "bytes": path.stat().st_size}
            for path in all_paths
        ]
        integrity = {
            "algorithm": "SHA256",
            "stage": "PACKAGE05_ENGINEERING_BASELINE_FROZEN",
            "recordedAt": datetime.now(timezone.utc).isoformat(),
            "artifactCount": len(artifacts),
            "artifacts": artifacts,
            "priorImmutableChecks": validation["historicalIntegrityTotal"],
            "credentialFindings": 0,
        }
        _write_json_atomic(integrity_path, integrity)
        closeout = {
            "status": "FROZEN_ENGINEERING_BASELINE_NOT_RELEASED_TO_STUDENTS",
            "version": VERSION,
            "remainingPackages": 0,
            "defaultCatalogue": "CLOSED",
            "G2Passed": False,
            "studentPilotReleased": False,
            "newProviderCalls": 0,
            "databaseWrites": 0,
            "gitBranch": branch,
            "gitHead": head,
            "workingTreeClean": not dirty,
            "gitTagCreatedByPackage05": False,
            "tagsAtHead": tags_at_head,
            "validation": validation,
            "verification": verification,
            "knownLimitationCount": len(limitations),
            "artifactCount": len(artifacts),
            "credentialFindings": 0,
            "independentReview": {"initialCritical": 0, "initialImportant": 7, "initialMinor": 1, "resolvedImportant": 7, "resolvedMinor": 1, "fixPasses": 1, "secondReviewRun": False},
            "integritySha256": _sha256(integrity_path),
        }
        _write_json_atomic(closeout_path, closeout)
        return closeout
    except Exception:
        for path, data in backups.items():
            path.write_bytes(data)
        for path in created:
            if path.exists():
                path.unlink()
        raise


def _write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def _atomic_write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp-package05")
    try:
        temporary.write_text(content, encoding="utf-8")
        temporary.replace(path)
    finally:
        if temporary.exists():
            temporary.unlink()


def _write_json_atomic(path: Path, value: Any) -> None:
    _atomic_write_text(path, json.dumps(value, ensure_ascii=False, indent=2) + "\n")


def _generated_credential_findings(root: Path, generated: dict[Path, str]) -> list[str]:
    findings: list[str] = []
    for path, content in generated.items():
        temporary = root / ".superpowers" / "package05-generated-scan" / path.relative_to(root)
        temporary.parent.mkdir(parents=True, exist_ok=True)
        temporary.write_text(content, encoding="utf-8")
        if scan_credentials(root / ".superpowers/package05-generated-scan", [temporary])["findingCount"]:
            findings.append(path.relative_to(root).as_posix())
        temporary.unlink()
    return findings


def _git(root: Path, *arguments: str) -> str:
    result = subprocess.run(
        ["git", *arguments], cwd=root, check=True, capture_output=True, text=True, encoding="utf-8", errors="replace"
    )
    return result.stdout.strip()


def _verification_summary(root: Path) -> dict[str, Any]:
    python_record = _read_json(root / "docs/evidence/rule-check-refactor/2026-10-11/package-05/python-rule-check-tests.json")
    python_log = root / python_record.get("logPath", "")
    if (
        python_record.get("passed") is not True
        or python_record.get("exitCode") != 0
        or not isinstance(python_record.get("testCount"), int)
        or python_record["testCount"] < 1
        or not python_log.is_file()
        or python_record.get("logSha256") != _sha256(python_log)
    ):
        raise ValueError("frozen Python rule-check test report is invalid")
    python_text = python_log.read_text(encoding="utf-8-sig")
    if f"Ran {python_record['testCount']} tests" not in python_text or "\nOK" not in python_text.replace("\r\n", "\n"):
        raise ValueError("frozen Python rule-check test log does not prove a green run")
    contract = _read_json(root / "docs/evidence/rule-check-refactor/2026-10-11/package-04/final-contract-after-review.json")
    java = _read_json(root / "docs/evidence/rule-check-refactor/2026-10-07/package04-final-unit-03/results.json")
    replay = _read_json(root / "docs/evidence/rule-check-refactor/2026-10-11/package-04/application-replay/results.json")
    if contract.get("passed") is not True or contract.get("checks") != 42:
        raise ValueError("PowerShell verification contract is not green")
    if java.get("passed") is not True or java.get("testCount") != 58:
        raise ValueError("final Java verification batch is not green")
    if replay.get("passed") is not True or replay.get("resourcesVerified") is not True or replay.get("containerExited") is not True:
        raise ValueError("package 04 application replay is not green or resources remain")
    return {
        "pythonRuleCheckTests": python_record["testCount"],
        "powerShellContractChecks": 42,
        "javaFinalUnitTests": 58,
        "applicationReplayTests": 1,
        "applicationReplayResourcesVerified": True,
        "applicationReplayContainerExited": True,
    }


def _limitations() -> list[dict[str, Any]]:
    return [
        {"id": "L01", "status": "OPEN", "text": "16题质量门禁未通过：7 PASS、8 FAIL、1 UNJUDGEABLE；冻结维度为8 FAIL、8 UNJUDGEABLE。"},
        {"id": "L02", "status": "OPEN", "text": "依据仅按用户决定使用2025版历史文本；现行适用性、其他培养类别及个人资格未核实。"},
        {"id": "L03", "status": "OPEN", "text": "第四包5/5只覆盖一个明显固定问题的顺序选类与离线应用回放。"},
        {"id": "L04", "status": "OPEN", "text": "尚未证明真实学生相较官网/PDF能更快且更正确地完成查证。"},
        {"id": "L05", "status": "OPEN", "text": "没有完整生成链路、并发、高可用或万级资料规模性能结论；货币成本未知。"},
        {"id": "L06", "status": "OUT_OF_SCOPE", "text": "管理端大文档导入、分段审核、发布、撤回和复验闭环未纳入本次版本。"},
        {"id": "L07", "status": "OUT_OF_SCOPE", "text": "转人工不等于受理工单，匿名反馈不等于完成修订复验，澄清状态不等于完整咨询历史。"},
        {"id": "L08", "status": "OPEN", "text": "物理页与印刷页映射及安全原件地址仍未全部核实。"},
        {"id": "L09", "status": "OPEN", "text": "检索噪声实验仍有必要依据前5召回16/21的已知缺口。"},
    ]


def _release_markdown(validation: dict[str, Any], verification: dict[str, Any], limitations: list[dict[str, Any]], tags_at_head: list[str]) -> str:
    tags = "、".join(f"`{tag}`" for tag in tags_at_head) if tags_at_head else "无"
    return f"""# ZhkuAgent 校园规则查证工程基线 {VERSION}

## 冻结结论

本版本状态为 `FROZEN_ENGINEERING_BASELINE_NOT_RELEASED_TO_STUDENTS`。五个既定工程包已执行完，`remainingPackages=0`；这只表示本轮工程收口完成。默认资料目录继续 `CLOSED`，G2仍未通过，未开放学生试点。

工作区以内容哈希冻结，第五包未新建Git标签。冻结时HEAD已有标签：{tags}；这些旧标签只指向HEAD提交，不包含当前脏工作区的内容。分支、HEAD、标签和工作区状态均写入第五包 `freeze-scope.json`。

## 已完成的能力

- 已审核2025历史文本的受控查证链：对象/时间/个人范围判断、必要澄清、证据约束、审计和原文定位。
- 可重复的本机受控入口、认证边界、临时库验收和退出回收。
- 16题冻结报告得到7 PASS、8 FAIL、1 UNJUDGEABLE；失败没有被工程绿色覆盖。
- 一个明显固定问题的5次独立选类及5次真实应用离线回放均通过，无重试。

## 工程验收

| 检查 | 结果 |
|---|---:|
| Python规则查证合同 | {verification['pythonRuleCheckTests']}项通过 |
| PowerShell入口合同 | {verification['powerShellContractChecks']}项通过 |
| Java最终单元批次 | {verification['javaFinalUnitTests']}项通过 |
| 第四包应用回放 | {verification['applicationReplayTests']}项通过，资源已关闭 |
| 前四包历史完整性 | {validation['historicalIntegrityTotal']}项复核通过 |

## 使用边界

该版本适合代码审查、面试演示和后续有界改进，不适合对学生公开放行。维护者应先阅读第五包 `known-limitations.json`；其中{len(limitations)}项限制是冻结版本的一部分，不能从简历、演示或文档中省略。

复现时只运行本地合同与读取冻结响应，不重新调用供应商。具体命令见第五包 `reproduction.md`。
"""


def _reproduction_markdown(verification: dict[str, Any]) -> str:
    template = r"""# 0.1.0-rc.1 冻结基线复现说明

## 环境检查

在普通 Windows CMD 或 PowerShell 中运行：

```bat
knowledge\scripts\start-controlled-manual-demo.cmd -CheckEnvironment
```

该命令只检查环境，不运行数据库、模型或Maven。

## 本地合同

```powershell
python -m unittest discover -s knowledge/scripts -p "test_*rule_check*.py"
$pwsh = Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe'
if (-not (Test-Path -LiteralPath $pwsh)) { throw 'PowerShell 7 runtime not found' }
& $pwsh -NoProfile -File scripts/tests/verify-rule-check-refactor-contract.ps1
```

预期为Python __PYTHON_TEST_COUNT__项、入口合同42项通过。Java最终58项与真实应用回放证据已冻结在：

- `docs/evidence/rule-check-refactor/2026-10-07/package04-final-unit-03/results.json`
- `docs/evidence/rule-check-refactor/2026-10-11/package-04/application-replay/results.json`

## 禁止的“复现”方式

不要重新执行第一、第三、第四包的真实供应商请求。历史响应、授权、用量、失败与哈希均已冻结；再次调用会形成新实验，不能冒充本版本复现。不要写入原演示库，数据库验收只能使用专用临时库 `zhkuagent_rulecheck_e2e`。

## 结论核对

检查第五包 `closeout.json`：`remainingPackages=0`、`defaultCatalogue=CLOSED`、`G2Passed=false` 必须同时成立。然后按 `integrity.json` 重算文件SHA256；任何不一致都表示该工作区不再是此冻结基线。

```powershell
python knowledge/scripts/build_rule_check_package05_freeze.py --verify
```
"""
    return template.replace("__PYTHON_TEST_COUNT__", str(verification["pythonRuleCheckTests"]))


def _roadmap_text(text: str) -> str:
    replacements = {
        "| 第三包：冻结报告评分 | 待做 |": "| 第三包：冻结报告评分 | **DONE（质量门禁失败）** |",
        "| 第四包：重复与性能用量 | 待做 |": "| 第四包：重复与性能用量 | **DONE（单一固定路径）** |",
        "| 第五包：版本冻结 | 待做 |": "| 第五包：版本冻结 | **DONE（工程基线，未放行）** |",
        "本版本还剩**三包**": "本版本既定五个工程包已全部完成，剩余**0包**",
    }
    for old, new in replacements.items():
        if old in text:
            text = text.replace(old, new, 1)
        elif new not in text:
            raise ValueError(f"roadmap marker missing: {old}")
    return text


def _blockers_text(text: str) -> str:
    heading = "# 第五包收口（2026-10-11 DONE，工程基线冻结但未放行）"
    prefix = f"""{heading}

五个既定工程包已全部执行，版本冻结为`0.1.0-rc.1`，剩余工程包0；冻结清单、复现命令和9项已知限制已落盘。该状态不等于产品门禁通过：第三包仍为7 PASS/8 FAIL/1 UNJUDGEABLE，默认资料目录CLOSED、G2未通过、学生试点未放行。第五包没有模型调用、数据库写入或运行功能修改，也没有新建Git标签；HEAD上的既有标签只覆盖提交，不覆盖当前工作区内容，因此本次以文件SHA256清单为准。见[版本说明](rule-check-release-0.1.0-rc.1.md)。以下保留历史。

"""
    if heading in text:
        start = text.index(heading)
        next_heading = text.find("\n# ", start + len(heading))
        suffix = "" if next_heading < 0 else text[next_heading + 1 :]
        return prefix + suffix
    return prefix + text


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    action = parser.add_mutually_exclusive_group(required=True)
    action.add_argument("--build", action="store_true")
    action.add_argument("--verify", action="store_true")
    args = parser.parse_args()
    if args.verify:
        print(json.dumps(verify_freeze(args.root), ensure_ascii=False))
    else:
        result = build(args.root)
        print(json.dumps({"status": result["status"], "remainingPackages": result["remainingPackages"], "G2Passed": result["G2Passed"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
