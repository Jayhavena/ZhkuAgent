"""Offline authorization, quota, trace and evidence audit. No external calls."""
import collections, datetime as dt, hashlib, json
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=next(p for p in HERE.parents if (p/"knowledge/pom.xml").exists())
def read(p): return json.loads(p.read_text(encoding="utf-8-sig"))
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def time(s): return dt.datetime.fromisoformat(s.replace("Z","+00:00"))
def emit(p,v):
    with p.open("x",encoding="utf-8") as f: json.dump(v,f,ensure_ascii=False,indent=2);f.write("\n")
package=HERE.parent/"request-package.json";pack=read(package);plan=read(HERE/"batch-plan.json");approval=read(HERE/"execution-approval.json")
reserved=read(HERE/"execution-reserved.json");close=read(HERE/"batch-closeout.json")
assert sha(package)==plan["packageSha256"]==approval["packageSha256"]==reserved["packageSha256"]=="998169056713f4c98f159301838db96b78c01c6b176e0faded48e4dad62159b4"
assert sha(HERE/"execution-approval.json")==plan["approvalSha256"] and sha(HERE/"execution-reserved.json")==plan["reservationSha256"]
assert time(approval["approvedAt"])<=time(reserved["startedAt"])<time(close["closedAt"])<time(approval["validUntil"])
assert (time(approval["validUntil"])-time(approval["approvedAt"])).total_seconds()<=86400
assert sha(ROOT/"knowledge/target/knowledge-0.1.0-rc.1-controlled.jar")==pack["jarSha256"]
assert approval["maximumTasks"]==20 and approval["maximumRuns"]==21 and approval["maximumCalls"]==105
assert approval["automaticRetries"]==0 and approval["repetitions"]==1 and approval["clarificationReply"]=="PUBLIC"
assert close["executionCompleted"] and close["remainingAuthorizedCalls"]==0
calls=[];rows=[];identity=set();issue_counts=collections.Counter();publication_cases=collections.defaultdict(set);validation_cases=collections.defaultdict(set)
for case in plan["plan"]["cases"]:
    folder=HERE/case["caseId"];caseclose=read(folder/"case-closeout.json");assert caseclose["resourcesClosed"]
    events=[json.loads(line) for line in (folder/"provider-traces.jsonl").read_text(encoding="utf-8-sig").splitlines() if line.strip()]
    begin=[e for e in events if e["phase"]=="BEGIN"];finished=[e for e in events if e["phase"]=="FINISHED"]
    assert len(begin)==len(finished)==caseclose["consumedCalls"]<=case["maximumCalls"]
    assert len({(e["taskId"],e["runId"],e["attempt"]) for e in begin})==len(begin)
    calls+=finished
    for turn in case["turns"]:
        p=folder/turn["kind"];c=read(p/"capture.json");i=c["execution"]["identity"];trace=c["execution"]["trace"]
        assert i["caseId"]==case["caseId"] and i["configurationSha256"]==sha(package) and i["datasetSha256"]==plan["plan"]["datasetSha256"]
        identity.add((i["taskId"],i["runId"]))
        counted=[e for e in finished if e["runId"]==i["runId"]];assert 1<=len(counted)<=5
        assert sorted(e["attempt"] for e in counted)==list(range(1,len(counted)+1))
        for e in counted:
            assert e["taskId"]==i["taskId"] and e["status"]==200 and e["responseReceived"]
            assert e["diagnostics"]["responseModel"]=="qwen3.8-flash" and e["outputTokens"]<=1024 and e["diagnostics"]["elapsedMillis"]<=20000
            if e["validation"]["code"]!="NONE":validation_cases[e["validation"]["stage"]+":"+e["validation"]["code"]].add(case["caseId"])
        started=read(p/"turn-started.json");ended=read(p/"turn-finished.json");duration=(time(ended["finishedAt"])-time(started["startedAt"])).total_seconds()
        assert duration<=110
        issues=[x["reasonCode"] for item in c["publishedResult"]["items"] for x in item.get("issues",[])]
        issue_counts.update(issues)
        reasons=sorted({a["publicationReason"] for a in trace["actions"] if a["publicationReason"]})
        for reason in reasons: publication_cases[reason].add(case["caseId"])
        evidence=read(p/"citation-checks.json");assert all(e["quoteMatchesSource"] for e in evidence["checks"])
        annotation=read(p/"annotation-reviewed.json");assert annotation["captureSha256"]==sha(p/"capture.json")
        assert annotation["answerSha256"]==hashlib.sha256(c["execution"]["answer"].encode()).hexdigest()
        # Public artifacts must not retain capability tokens. Record paths/hashes, not their values.
        assert '"originalToken"' not in (p/"capture.json").read_text(encoding="utf-8") and '"continuationToken"' not in (p/"capture.json").read_text(encoding="utf-8")
        rows.append({"caseId":case["caseId"],"kind":turn["kind"],"taskId":i["taskId"],"runId":i["runId"],"calls":len(counted),"durationSeconds":duration,
            "taskStatus":trace["taskStatus"],"terminalIssues":issues,"publicationReasons":reasons,
            "actions":[{"type":a["type"],"reasonCode":a["reasonCode"],"publicationReason":a["publicationReason"],"observation":a["observation"]} for a in trace["actions"]],
            "capturePath":str((p/"capture.json").relative_to(HERE)).replace("\\","/"),"captureSha256":sha(p/"capture.json"),
            "providerTracePath":str((folder/"provider-traces.jsonl").relative_to(HERE)).replace("\\","/"),"providerTraceSha256":sha(folder/"provider-traces.jsonl")})
assert len(rows)==21 and len({r["taskId"] for r in rows})==20 and len(identity)==21
assert len(calls)==close["consumedCalls"]==79 and close["unusedCallsClosed"]==26
ask=next(r for r in rows if r["kind"]=="ASK");child=next(r for r in rows if r["kind"]=="RESOLVE_PUBLIC")
assert ask["taskStatus"]=="WAITING_USER" and ask["taskId"]==child["taskId"] and ask["runId"]!=child["runId"]
link=read(HERE/"RFE-013/RESOLVE_PUBLIC/continuation-link.json");assert link["reply"]=="PUBLIC" and link["previousCaptureSha256"]==ask["captureSha256"]
assert read(HERE/"container-cleanup.json")["cleanupSucceeded"]
launcher=read(HERE/"launcher-result.json");assert launcher["exitCode"]==0 and launcher["resourcesClosed"] and not launcher["timedOut"]
host_seconds=(time(close["closedAt"])-time(plan["startedAt"])).total_seconds();assert host_seconds<=2550
emit(HERE/"execution-breakdown.json",{"origin":"OFFLINE_AUDIT_OF_REAL_RUNS","turns":rows,"terminalIssueTurnCounts":dict(issue_counts),"publicationReasonCases":{k:sorted(v) for k,v in publication_cases.items()},"providerValidationCases":{k:sorted(v) for k,v in validation_cases.items()}})
emit(HERE/"audit-verification.json",{"status":"PASS","packageSha256":sha(package),"tasks":20,"runs":21,"calls":len(calls),"http200":len(calls),"unusedCallsClosed":26,"remainingAuthorizedCalls":0,
    "hostSeconds":host_seconds,"maximumRunSeconds":max(r["durationSeconds"] for r in rows),"maximumCallMillis":max(e["diagnostics"]["elapsedMillis"] for e in calls),"maximumOutputTokens":max(e["outputTokens"] for e in calls),
    "inputTokens":sum(e["inputTokens"] for e in calls),"outputTokens":sum(e["outputTokens"] for e in calls),"actualModels":sorted({e["diagnostics"]["responseModel"] for e in calls}),
    "publicEvidenceChecks":sum(len(read(p.parent/"citation-checks.json")["checks"]) for p in HERE.glob("RFE-*/*/capture.json")),
    "noAutomaticRetries":True,"noReplacementOrRepeatTrial":True,"approvalAndReservationHashesMatch":True,"allPerCaseResourcesClosed":True,"ownedContainerCleanupReceiptSucceeded":True,"capabilityTokenFieldsAbsentInCaptures":True})
print("PASS real execution audit: 20 tasks / 21 runs / 79 calls, all HTTP200, within limits; unused26 closed")
