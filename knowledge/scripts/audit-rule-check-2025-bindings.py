"""只读核对2025原件和数据库导出；不把摘要、版本别名或历史许可变成v2放行。"""
import argparse
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path
from uuid import UUID


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def utf16_length(text):
    # Java substring按UTF-16计数；Python字符数遇到补充平面字符会产生错位。
    return len(text.encode("utf-16-le")) // 2


def verify_span(text, start, end, quote):
    if not (0 <= start < end <= utf16_length(text)):
        return False
    try:
        actual = text.encode("utf-16-le")[start * 2:end * 2].decode("utf-16-le")
    except UnicodeDecodeError:
        return False
    return actual == quote


def check_draft(draft, pages, pdf_hash):
    if draft["sourceFileSha256"] != pdf_hash:
        raise ValueError("原件指纹不一致")
    for clause in draft["clauses"]:
        text = clause["rawExtractedText"]
        if clause["sourceFileSha256"] != pdf_hash:
            raise ValueError("条款原件指纹不一致")
        if clause["excerptUtf8Sha256"] != sha256(text.encode("utf-8")):
            raise ValueError("条款摘录指纹不一致")
        if text not in pages[clause["pdfPhysicalPage"]]:
            raise ValueError(f"原件指定页没有精确摘录：{clause['locatorId']}")


def audit(draft, snapshot, pages, pdf_hash):
    check_draft(draft, pages, pdf_hash)
    if snapshot["transactionReadOnly"] != "on":
        raise ValueError("快照不是只读事务导出")
    sources = {s["id"]: s for s in snapshot["sources"]}
    if len(sources) != len(snapshot["sources"]):
        raise ValueError("重复来源身份")
    for source_id in sources:
        UUID(source_id)
    if len({c["id"] for c in snapshot["chunks"]}) != len(snapshot["chunks"]):
        raise ValueError("重复片段身份")
    chunks = []
    for c in snapshot["chunks"]:
        UUID(c["id"])
        if c["source_id"] not in sources:
            raise ValueError("片段来源身份悬空")
        actual = sha256(c["content"].encode("utf-8"))
        if actual != c["content_checksum"].strip().lower():
            raise ValueError("数据库片段正文指纹不一致")
        chunks.append({"sourceId": c["source_id"], "chunkId": c["id"],
                       "chunkIndex": c["chunk_index"], "sourceVersion": sources[c["source_id"]]["source_version"],
                       "scope": sources[c["source_id"]]["knowledge_scope"],
                       "checksum": actual, "utf16Length": utf16_length(c["content"]),
                       "recordedPage": c["page_number"], "locator": c["source_locator"],
                       "matchingEntryCount": sum(e["chunk_id"] == c["id"] for e in snapshot["entries"])})
    clauses = []
    for clause in draft["clauses"]:
        text = clause["rawExtractedText"]
        exact = []
        for c in snapshot["chunks"]:
            start = c["content"].find(text)
            if start >= 0:
                start16 = utf16_length(c["content"][:start])
                end16 = start16 + utf16_length(text)
                assert verify_span(c["content"], start16, end16, text)
                exact.append({"sourceId": c["source_id"], "chunkId": c["id"],
                              "startInclusive": start16, "endExclusive": end16})
        # 精确逐字绑定与语义相似必须分开；不能靠移除条号/空白拼造库内原文。
        clauses.append({"locatorId": clause["locatorId"], "article": clause["article"],
                        "pdfPhysicalPage": clause["pdfPhysicalPage"], "printedPage": clause["printedPage"],
                        "pdfExcerptVerified": True, "exactFullExcerptBindings": exact,
                        "existingBindingStatus": "EXACT_TEXT_FOUND_PENDING_INDEX_AND_REVIEW" if exact else "NO_EXACT_FULL_EXCERPT",
                        "proposedLiteralChunk": {"sourceId": None, "chunkId": None,
                            "referenceRevision": draft["referenceRevision"], "knowledgeScope": "RULE_CHECK_PUBLIC_ELECTIVE_2025",
                            "profileId": None, "chunkingVersion": "handbook-2025-clause-verbatim-v1",
                            "publicationStatus": "DRAFT", "visibilityScope": "INTERNAL_ONLY",
                            "sharingRightsConfirmed": False, "content": text,
                            "contentChecksum": sha256(text.encode("utf-8")),
                            "span": {"unit": "UTF16_CODE_UNIT", "startInclusive": 0,
                                     "endExclusive": utf16_length(text)},
                            "physicalPage": clause["pdfPhysicalPage"], "printedPage": clause["printedPage"]}})
    return {"schemaVersion": 1, "generatedAt": datetime.now(timezone.utc).isoformat(),
            "referenceRevision": draft["referenceRevision"], "sourceFileSha256": pdf_hash,
            "databaseObservedAt": snapshot["observedAt"], "sourcesCount": len(sources),
            "chunksCount": len(chunks), "entriesCount": len(snapshot["entries"]),
            "profilesCount": len(snapshot["profiles"]), "existingChunks": chunks, "clauses": clauses,
            "runtimeBindingsActivated": False, "studentReleaseApproved": False,
            "status": "SOURCE_AUDITED_RUNTIME_BINDING_OPEN",
            "revisionMapping": {"declaredReferenceRevision": "2025.9.2", "existingSourceVersions": sorted({s["source_version"] for s in sources.values()}),
                "automaticAliasesAllowed": False, "existingSourceToPdfProvenance": "NOT_ESTABLISHED_BY_DATABASE_CHECKSUM"},
            "notes": ["快照为原库只读观察，不能恢复成测试库后沿用原库运行身份。",
                      "新逐条原文是导入草案，不是已入库片段；实际UUID与profile须在专用环境真实导入/索引后回填。",
                      "PDF摘要checksum、数据库正文checksum与文件checksum是不同对象。",
                      "未调用供应商、未评估学生问题；已有摘要可能支持子句，需B02逐事实审核，不能据无全文精确匹配断言事实均错误。"]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pdf", required=True, type=Path)
    parser.add_argument("--snapshot", required=True, type=Path)
    parser.add_argument("--draft", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()
    from pypdf import PdfReader
    import pdfplumber
    draft = json.loads(args.draft.read_text(encoding="utf-8-sig"))
    snapshot = json.loads(args.snapshot.read_text(encoding="utf-8-sig"))
    pdf_hash = sha256(args.pdf.read_bytes())
    reader = PdfReader(args.pdf)
    page_numbers = sorted({c["pdfPhysicalPage"] for c in draft["clauses"]})
    pages = {p: reader.pages[p - 1].extract_text() for p in page_numbers}
    report = audit(draft, snapshot, pages, pdf_hash)
    report["pdf"] = {"path": str(args.pdf.resolve()), "bytes": args.pdf.stat().st_size,
                     "pageCount": len(reader.pages), "pageMappings": []}
    for p in page_numbers:
        # 只记录本批参考声明的页码映射，不能将两页观察推广成整本恒定偏移。
        printed = {c["printedPage"] for c in draft["clauses"] if c["pdfPhysicalPage"] == p}
        if len(printed) != 1:
            raise ValueError("同一物理页的印刷页声明不一致")
        report["pdf"]["pageMappings"].append({"physicalPage": p, "printedPage": printed.pop(),
                                            "mappingStatus": "PENDING_VISUAL_CONFIRMATION"})
    report["inputFingerprints"] = {"draftSha256": sha256(args.draft.read_bytes()),
                                   "snapshotSha256": sha256(args.snapshot.read_bytes())}
    args.out.mkdir(parents=True, exist_ok=True)
    if (args.out / "binding-audit.json").exists():
        raise ValueError("证据批次已存在，请指定新目录，避免覆盖历史结果")
    with pdfplumber.open(args.pdf) as pdf:
        for p in page_numbers:
            pdf.pages[p - 1].to_image(resolution=110).save(args.out / f"page-{p}.png")
    (args.out / "binding-audit.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"原件{len(reader.pages)}页，{len(report['clauses'])}条摘录精确通过；实际{len(report['existingChunks'])}片段hash通过，索引条目{report['entriesCount']}")


if __name__ == "__main__":
    main()
