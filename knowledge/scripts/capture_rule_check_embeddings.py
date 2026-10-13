"""C04有界供应商证据采集；与非供应商数据库验收分开。"""
import argparse
import hashlib
import json
import math
import multiprocessing
import os
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib.request import Request, build_opener, HTTPRedirectHandler
from urllib.error import HTTPError
from audit_rule_check_eval_assets import audit

MODEL="text-embedding-v4"
DIMENSIONS=1024
ENDPOINT="https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings"
REFERENCE="docs/product/rule-check-2025-human-reference-development.json"
REFERENCE_SHA="c01505d9114274d8b379ca98b0f47739c689f199e699f8a460e53bf9b367efc9"
BUNDLE="docs/product/rule-check-2025-verbatim-import-draft.json"
BATCH="docs/evidence/rule-check-refactor/2026-10-09/c04-vector-01/provider-capture"

def validate_batch_output(root,out):
    # 本批授权是合计30次，不能通过换输出目录重置计数；目录占用后连失败也不自动重跑。
    canonical=(root/BATCH).resolve()
    require(out.resolve()==canonical and not canonical.exists())
    approval=read(canonical.parent/"external-transfer-authorization.json")
    require(approval.get("authority")=="DIRECT_USER_MESSAGE" and approval.get("decision")=="CONFIRMED"
            and approval.get("endpoint")==ENDPOINT and approval.get("maxCalls")==30
            and approval.get("reportQueriesAllowed") is False and approval.get("databaseCredentialsAllowed") is False)


def worker(operation,argument,connection):
    try:
        result=operation(argument,lambda:connection.send({"event":"HTTP_ATTEMPT"}))
    except Exception as failure:
        # 子进程也只传类型/状态，禁止把HTTP正文和可能含密钥的exception message带回。
        result={"status":"ERROR","usageKnown":False,"errorType":type(failure).__name__}
        if isinstance(failure,HTTPError): result["httpStatus"]=failure.code
    connection.send({"event":"RESULT","row":result});connection.close()

def run_bounded(operation,argument,seconds):
    # socket timeout不覆盖持续慢速响应；独立进程绝对截止会同时中断请求与完整读取。
    deadline=time.monotonic()+seconds;context=multiprocessing.get_context("spawn")
    parent,child=context.Pipe(duplex=False);process=context.Process(target=worker,args=(operation,argument,child),daemon=True)
    attempted=False
    try:
        process.start();child.close()
        while time.monotonic()<deadline:
            if parent.poll(max(0,deadline-time.monotonic())):
                try: event=parent.recv()
                except EOFError: return {"status":"ERROR","usageKnown":False,"errorType":"WorkerUnavailable","actualHttpAttempt":attempted}
                if event["event"]=="HTTP_ATTEMPT": attempted=True
                else: return {**event["row"],"actualHttpAttempt":attempted}
        return {"status":"ERROR","usageKnown":False,"errorType":"AbsoluteDeadlineExceeded","actualHttpAttempt":attempted}
    finally:
        if process.is_alive(): process.terminate()
        if process.pid is not None: process.join(timeout=1)
        parent.close();child.close()


def sha(data): return hashlib.sha256(data).hexdigest()
def read(path): return json.loads(path.read_bytes())
def require(okay):
    if not okay: raise ValueError("Embedding evidence contract violated")


def validate_response(response):
    require(isinstance(response,dict) and response.get("model")==MODEL)
    data=response.get("data")
    require(isinstance(data,list) and len(data)==1 and data[0].get("index")==0)
    vector=data[0].get("embedding")
    require(isinstance(vector,list) and len(vector)==DIMENSIONS)
    require(all(type(v) in (int,float) and math.isfinite(v) and abs(v)<1e30 for v in vector))
    require(sum(v*v for v in vector)>0)
    usage=response.get("usage",{}); prompt=usage.get("prompt_tokens"); total=usage.get("total_tokens")
    require(type(prompt) is int and type(total) is int and 0<=prompt<=total)
    request_id=response.get("id")
    require(request_id is None or isinstance(request_id,str))
    return {"vector":vector,"promptTokens":prompt,"totalTokens":total,"requestId":request_id}


def make_inputs(root):
    reference_bytes=(root/REFERENCE).read_bytes(); require(sha(reference_bytes)==REFERENCE_SHA)
    bundle=read(root/BUNDLE); approval=read(root/"docs/product/rule-check-2025-b03-publication-review.json")
    require(approval.get("humanQualificationConfirmed") and approval.get("sharingRightsConfirmed"))
    expected={e["path"]:e["sha256"] for e in approval["inputs"]}
    require(expected.get(BUNDLE)==sha((root/BUNDLE).read_bytes()))
    inputs=[]
    for chunk in bundle["chunks"]:
        text=chunk["content"]; require(sha(text.encode())==chunk["contentChecksum"].lower())
        inputs.append({"id":f'A{chunk["article"]:02}',"kind":"DOCUMENT","article":chunk["article"],"text":text,"inputSha256":sha(text.encode())})
    require({i["article"] for i in inputs}=={2,4,5,6,8,9,10,11,12,18})
    for row in json.loads(reference_bytes)["records"]:
        require(row["split"]=="DEVELOPMENT" and row["annotationStatus"]=="HUMAN_CONFIRMED")
        text=row["question"]; inputs.append({"id":row["reviewId"],"kind":"QUERY","text":text,"inputSha256":sha(text.encode())})
    require(len(inputs)==30 and [i["id"] for i in inputs if i["kind"]=="QUERY"]==[f"Q{i:02}" for i in range(1,21)])
    return inputs


def credential():
    # 每次外发重新查当前进程，缺失再读Windows用户/系统级；从不把值放入证据。
    value=os.environ.get("DASHSCOPE_API_KEY")
    if value: return value
    if os.name=="nt":
        import winreg
        for hive,key in [(winreg.HKEY_CURRENT_USER,"Environment"),(winreg.HKEY_LOCAL_MACHINE,r"SYSTEM\CurrentControlSet\Control\Session Manager\Environment")]:
            try:
                with winreg.OpenKey(hive,key) as registry: value=winreg.QueryValueEx(registry,"DASHSCOPE_API_KEY")[0]
                if value: return value
            except OSError: pass
    raise RuntimeError("CredentialUnavailable")


class NoRedirect(HTTPRedirectHandler):
    # Authorization只能发送到已登记端点，不能随着响应重定向外发。
    def redirect_request(self,*args,**kwargs): return None


def request_embedding(item,notify):
    key=credential()
    payload=json.dumps({"model":MODEL,"input":item["text"],"dimensions":DIMENSIONS,"encoding_format":"float"}).encode()
    request=Request(ENDPOINT,data=payload,headers={"Authorization":"Bearer "+key,"Content-Type":"application/json"},method="POST")
    notify()
    with build_opener(NoRedirect()).open(request,timeout=20) as result:
        raw=result.read(2_000_001);require(len(raw)<=2_000_000);observed=json.loads(raw)
    return {**validate_response(observed),"status":"SUCCESS","usageKnown":True}


def save_new(path,value):
    with path.open("x",encoding="utf-8") as stream: json.dump(value,stream,ensure_ascii=False,indent=2,allow_nan=False)


def capture(root,out):
    validate_batch_output(root,out)
    inputs=make_inputs(root)
    gate=audit(root,root.parent/"SuperAgent-zhkuagent-mvp/data/raw/2025版学生手册2025.9.2.pdf")
    require(gate["validationPassed"] and gate["freezeGatePassed"])
    out.mkdir(parents=True,exist_ok=False)
    save_new(out/"batch-claim.json",{"batch":BATCH,"maxRequests":30,"singleUse":True,"status":"CLAIMED_BEFORE_NETWORK"})
    protected=[root/REFERENCE,root/BUNDLE,Path(__file__).resolve()]
    before={str(p.relative_to(root)).replace("\\","/"):sha(p.read_bytes()) for p in protected}
    save_new(out/"preflight.json",{"frozenAssetGate":gate,"sourceHashes":before,"endpoint":ENDPOINT,"model":MODEL,"dimensions":DIMENSIONS,
        "maxRequests":30,"retryCount":0,"perRequestTimeoutSeconds":20,"overallBudgetSeconds":240,"reportQueriesConsumed":0})
    records=[];start=time.monotonic(); overall_started=datetime.now(timezone.utc).isoformat()
    for item in inputs:
        row={**item,"status":"ERROR","actualHttpAttempt":False,"usageKnown":False,"startedAt":datetime.now(timezone.utc).isoformat()}
        attempted=time.monotonic()
        try:
            remaining=240-(attempted-start); require(remaining>0)
            row.update(run_bounded(request_embedding,item,min(20,remaining)))
        except Exception as failure:
            # HTTP错误正文、异常message和request对象可能含凭证，均不写日志。
            row["errorType"]=type(failure).__name__
            if isinstance(failure,HTTPError): row["httpStatus"]=failure.code
        row["elapsedMs"]=(time.monotonic()-attempted)*1000
        name=item["id"]+".json";save_new(out/name,row);records.append({"id":item["id"],"path":name,"sha256":sha((out/name).read_bytes()),"status":row["status"]})
        if row["status"]!="SUCCESS": break
    after={p:sha((root/p).read_bytes()) for p in before}
    rows=[read(out/r["path"]) for r in records]
    manifest={"kind":"ACTUAL_PROVIDER_EMBEDDINGS_FOR_CONTROLLED_RANKING","startedAt":overall_started,"finishedAt":datetime.now(timezone.utc).isoformat(),
        "endpoint":ENDPOINT,"model":MODEL,"dimensions":DIMENSIONS,"complete":len(records)==30 and all(r["status"]=="SUCCESS" for r in records) and before==after,
        "sourceBefore":before,"sourceAfter":after,"files":records,"actualHttpAttempts":sum(r["actualHttpAttempt"] for r in rows),
        "successes":sum(r["status"]=="SUCCESS" for r in rows),"failedUsageUnknown":sum(not r["usageKnown"] for r in rows),
        "observedTotalTokens":sum(r.get("totalTokens",0) for r in rows),"chargedAmount":None,"chargedAmountStatus":"ACCOUNT_BILL_NOT_OBSERVED",
        "retryCount":0,"reportQueriesConsumed":0,"defaultSwitched":False,"preflightSha256":sha((out/"preflight.json").read_bytes())}
    save_new(out/"manifest.json",manifest)
    print(json.dumps({k:manifest[k] for k in ["complete","actualHttpAttempts","successes","failedUsageUnknown","observedTotalTokens"]}))
    return 0 if manifest["complete"] else 1


if __name__=="__main__":
    parser=argparse.ArgumentParser();parser.add_argument("--out",type=Path,required=True);args=parser.parse_args()
    root=Path(__file__).resolve().parents[2]; output=args.out.resolve();require(output.is_relative_to(root/"docs/evidence"))
    raise SystemExit(capture(root,output))
