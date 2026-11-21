"""Final local integrity/coverage verification; does not run the Agent or provider."""
import hashlib,json,re
from pathlib import Path
H=Path(__file__).resolve().parent;R=next(p for p in H.parents if (p/"knowledge/pom.xml").exists())
def read(p):return json.loads(p.read_text(encoding="utf-8-sig"))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
a=read(H/"report-reviewed.json");b=read(H/"report-replay.json");q=a["qualityReport"];pack=read(H.parent/"request-package.json")
assert a["sources"]==b["sources"] and a["executionIdentity"]==b["executionIdentity"]
for key in ("datasetSha256","denominator","passed","failed","unjudgeable","errors","knownOutcomes","fullyEvaluable","casePassRate","knownOutcomeRatio","fullyEvaluableRatio","dimensions"):
    assert q[key]==b["qualityReport"][key],key
assert set(q["cases"])=={f"RFE-{i:03}" for i in range(1,21)}
for cid,c in q["cases"].items():
    d=b["qualityReport"]["cases"][cid];assert c["outcome"]==d["outcome"] and c["fullyEvaluable"]==d["fullyEvaluable"]
    for dim in c["dimensions"]:assert c["dimensions"][dim]["verdict"]==d["dimensions"][dim]["verdict"]
assert a["failureAnalysis"]==b["failureAnalysis"]
assert q["passed"]==1 and q["failed"]==19 and q["knownOutcomes"]==20 and q["fullyEvaluable"]==3
for d in q["dimensions"].values():assert d["passed"]+d["failed"]+d["unjudgeable"]+d["errors"]+d["notApplicable"]==20
assert read(H/"audit-verification.json")["status"]=="PASS"
assert read(H.parent/"execution-closeout.json")["remainingAuthorizedCalls"]==0
assert read(H/"container-cleanup.json")["cleanupSucceeded"]
for path,value in pack["sourceIdentity"].items():assert sha(R/path)==value,path
assert sha(R/"knowledge/target/knowledge-0.1.0-rc.1-controlled.jar")==pack["jarSha256"]
priorities=read(H/"repair-priorities.json")["priorities"]
assert {c for p in priorities for c in p["affectedCases"]}=={c for c,x in q["cases"].items() if x["outcome"]=="FAIL"}
assert sum(len(p["affectedCases"]) for p in priorities)==19
for p in priorities:
    for e in p["evidence"]:assert sha(H/e["capturePath"])==e["captureSha256"] and sha(H/e["providerTracePath"])==e["providerTraceSha256"]
for path in (H/"quality-report.md",H/"report-reviewed.md",H.parent/"README.md"):
    for target in re.findall(r'\[[^\]]*\]\(([^)]+)\)',path.read_text(encoding="utf-8")):
        if "://" not in target and not target.startswith("#"):assert (path.parent/target.split("#")[0]).exists(),(path,target)
comparison=read(H/"baseline-comparison-results.json")
assert comparison["m3CommonTen"]=={"passed":1,"denominator":10,"fullyEvaluable":2,"calls":43}
assert a["executionIdentity"]["authorizationEvidence"]=="PACKAGE_BOUND_LOCAL_COPIES"
files={str(p.relative_to(H)).replace("\\","/"):sha(p) for p in sorted(H.rglob("*")) if p.is_file() and p.name!="delivery-verification.json"}
result={"status":"PASS","milestone":"M3_FULL_TWENTY_DEVELOPMENT_BASELINE_DELIVERED","answerQuality":"FAIL",
    "packageSha256":sha(H.parent/"request-package.json"),"repository":str(R),"branch":"feat/zhkuagent-v3-frontend","head":"338c1ea184ac6240cdaaaaa5045d1c24e72dcc0a",
    "tasks":20,"runs":21,"calls":79,"passed":1,"failed":19,"fullyEvaluable":3,"remainingAuthorizedCalls":0,
    "replayMetricsCaseDimensionVerdictsAndSourcesMatch":True,"byteIdenticalReasonDisplayNotRequired":True,
    "approvedJarAndAllProductionSourcesMatch":True,"priorityGroupsCoverAllNineteenFailures":True,"localReportLinksExist":True,
    "semanticReview":"Codex independent offline review; not user human sign-off","files":files}
with (H/"delivery-verification.json").open("x",encoding="utf-8") as f:json.dump(result,f,ensure_ascii=False,indent=2);f.write("\n")
print("PASS final delivery: all20 retained, all21 reviewed, evidence/authorization bound, replay verdicts stable, priority groups cover19 failures")
