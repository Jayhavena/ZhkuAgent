import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import capture_rule_check_selection as selection
from capture_rule_check_embeddings import run_bounded
import time

def slow_selection(argument,notify):
    notify();time.sleep(3);return {"status":"RESPONSE_RECEIVED"}

class FakeResponse:
    def __init__(self,raw):self.raw=raw;self.requested=[]
    def __enter__(self):return self
    def __exit__(self,*args):pass
    def read(self,size):self.requested.append(size);return self.raw[:size]

def response(content='{"ruleId":"S01/GENERAL"}',usage=None):
    return {"model":"qwen-plus-observed-version","id":"synthetic-id","choices":[{"index":0,"finish_reason":"stop","message":{"role":"assistant","content":content}}],
        "usage":{"prompt_tokens":12,"completion_tokens":6,"total_tokens":18} if usage is None else usage}

class SelectionCaptureTest(unittest.TestCase):
    def prepared_root(self,root):
        source=Path(__file__).resolve().parents[2]/selection.PACKAGE
        dest=root/selection.PACKAGE;dest.parent.mkdir(parents=True)
        dest.write_bytes(source.read_bytes())
        approval={"authority":"DIRECT_USER_MESSAGE","decision":"CONFIRMED","endpoint":selection.ENDPOINT,"model":selection.MODEL,"maxCalls":24,
            "packagePath":selection.PACKAGE,"packageSha256":selection.PACKAGE_SHA,"reportQueriesAllowed":False,"databaseCredentialsAllowed":False}
        (dest.parent/'external-transfer-authorization.json').write_text(json.dumps(approval),encoding='utf-8')
        return root/selection.BATCH

    def test_response_keeps_observed_model_raw_content_and_usage(self):
        actual=selection.validate_response(response(),{"S01/GENERAL"})
        self.assertEqual(actual.get("status"),"RESPONSE_RECEIVED")
        self.assertEqual(actual.get("observedModel"),"qwen-plus-observed-version")
        self.assertEqual(actual.get("protocolDiagnostic"),"SELECTED")
        self.assertEqual(actual.get("totalTokens"),18)
        self.assertEqual(actual.get("rawModelContent"),'{"ruleId":"S01/GENERAL"}')

    def test_null_unknown_duplicate_extra_and_trailing_content_are_diagnostic_not_semantic_grades(self):
        for content,expected in [(' {"ruleId":null} ','ABSTAINED'),('{"ruleId":"unknown"}','UNKNOWN_RULE'),
            ('{"ruleId":"S01/GENERAL","ruleId":null}','INVALID_RESPONSE'),('{"ruleId":"S01/GENERAL","answer":"policy"}','INVALID_RESPONSE'),
            ('{"ruleId":"S01/GENERAL"} {}','INVALID_RESPONSE'),('not json','INVALID_RESPONSE')]:
            with self.subTest(content=content):
                actual=selection.validate_response(response(content),{"S01/GENERAL"})
                self.assertEqual(actual.get("protocolDiagnostic"),expected)
                self.assertEqual(actual.get("rawModelContent"),content)
                self.assertFalse(actual.get("humanSemanticGradeProduced",True))

    def test_usage_unknown_is_not_silently_zero(self):
        for usage in [{},{"prompt_tokens":True,"completion_tokens":1,"total_tokens":2},{"prompt_tokens":-1,"completion_tokens":2,"total_tokens":1}]:
            with self.subTest(usage=usage):
                actual=selection.validate_response(response(usage=usage),{"S01/GENERAL"})
                self.assertFalse(actual.get("usageKnown",True));self.assertIsNone(actual.get("totalTokens"))

    def test_oversized_nontext_tools_truncated_and_missing_outer_shape_are_rejected(self):
        bad=[response("中"*400),response(),response(),response(),{}]
        bad[1]["choices"][0]["message"]["content"]={"ruleId":"S01/GENERAL"}
        bad[2]["choices"][0]["message"]["tool_calls"]=[{"id":"synthetic-tool"}]
        bad[3]["choices"][0]["finish_reason"]="length"
        for value in bad:
            with self.subTest(kind=str(type(value))):
                with self.assertRaises(ValueError):selection.validate_response(value,{"S01/GENERAL"})

    def test_unapproved_wrong_destination_and_repeat_output_cannot_start_capture(self):
        with tempfile.TemporaryDirectory(dir=Path(__file__).resolve().parents[1]/"target") as temporary:
            root=Path(temporary);out=root/selection.BATCH;out.parent.mkdir(parents=True)
            with self.assertRaises(ValueError):selection.validate_batch_output(root,out)
            approval={"authority":"DIRECT_USER_MESSAGE","decision":"CONFIRMED","endpoint":"https://wrong.example","model":"qwen-plus","maxCalls":24,"packageSha256":selection.PACKAGE_SHA}
            (out.parent/'external-transfer-authorization.json').write_text(json.dumps(approval),encoding='utf-8')
            with self.assertRaises(ValueError):selection.validate_batch_output(root,out)
            out.mkdir()
            with self.assertRaises(ValueError):selection.validate_batch_output(root,out)
            with self.assertRaises(ValueError):selection.validate_batch_output(root,root/'another-batch')

    def test_payload_pin_and_approval_bind_the_exact_single_use_batch(self):
        with tempfile.TemporaryDirectory(dir=Path(__file__).resolve().parents[1]/"target") as temporary:
            root=Path(temporary);out=self.prepared_root(root)
            selection.validate_batch_output(root,out)
            (root/selection.PACKAGE).write_bytes(b'{}')
            with self.assertRaises(ValueError):selection.validate_batch_output(root,out)

    def test_request_uses_exact_endpoint_one_attempt_bounded_read_and_sanitized_success(self):
        item=json.loads((Path(__file__).resolve().parents[2]/selection.PACKAGE).read_bytes())['requests'][0]
        seen=[];notifications=[];reply=FakeResponse(json.dumps(response()).encode())
        class Opener:
            def open(self,request,timeout):seen.append((request,timeout));return reply
        # 只给本地替身固定测试字符串；不会读取本机凭证或访问网络。
        with patch.object(selection,'credential',return_value='synthetic-test-key',create=True),patch.object(selection,'build_opener',return_value=Opener(),create=True):
            actual=selection.request_selection(item,lambda:notifications.append(True))
        self.assertEqual(actual.get('status'),'RESPONSE_RECEIVED');self.assertEqual(len(seen),1);self.assertEqual(notifications,[True])
        self.assertEqual(seen[0][0].full_url,selection.ENDPOINT);self.assertEqual(seen[0][1],5)
        self.assertEqual(json.loads(seen[0][0].data),item['body']);self.assertEqual(reply.requested,[16385])
        self.assertNotIn('synthetic-test-key',str(actual))

    def test_oversize_and_credential_echo_never_become_saved_success(self):
        item=json.loads((Path(__file__).resolve().parents[2]/selection.PACKAGE).read_bytes())['requests'][0]
        outer_escape=json.dumps(response('synthetic-test-key')).replace('synthetic-test-key','\\u0073ynthetic-test-key').encode()
        inner_escape=json.dumps(response('{"ruleId":"'+''.join('\\u%04x'%ord(c) for c in 'synthetic-test-key')+'"}')).encode()
        for raw in [b'x'*16385,json.dumps(response('synthetic-test-key')).encode(),outer_escape,inner_escape]:
            class Opener:
                def open(self,*args,**kwargs):return FakeResponse(raw)
            with self.subTest(size=len(raw)),patch.object(selection,'credential',return_value='synthetic-test-key',create=True),patch.object(selection,'build_opener',return_value=Opener(),create=True):
                with self.assertRaises(ValueError):selection.request_selection(item,lambda:None)

    def test_repeated_read_cannot_replace_the_approved_request_snapshot(self):
        with tempfile.TemporaryDirectory(dir=Path(__file__).resolve().parents[1]/"target") as temporary:
            root=Path(temporary);out=self.prepared_root(root);package=root/selection.PACKAGE
            original_read=Path.read_bytes;approved=package.read_bytes();modified=json.loads(approved)
            modified['requests'][0]['body']['messages'][1]['content']='{"question":"UNAPPROVED_CHANGED_QUERY","options":[]}'
            changed=json.dumps(modified).encode();reads=[];calls=[]
            def changing_read(path):
                if path==package:
                    reads.append(True);return approved if len(reads)==1 else changed
                return original_read(path)
            def runner(operation,item,seconds):
                calls.append(item);return {'status':'RESPONSE_RECEIVED','actualHttpAttempt':True,'usageKnown':True,'totalTokens':18}
            with patch.object(Path,'read_bytes',changing_read),patch.object(selection,'audit',return_value={'validationPassed':True,'freezeGatePassed':True}):
                with self.assertRaises(ValueError):selection.capture(root,out,runner=runner)
            self.assertEqual(calls,[])

    def test_absolute_deadline_covers_the_whole_request_worker(self):
        result=run_bounded(slow_selection,None,1)
        self.assertEqual(result['errorType'],'AbsoluteDeadlineExceeded');self.assertTrue(result['actualHttpAttempt'])

    def test_capture_is_single_use_and_failure_stops_without_retry(self):
        for failure in [False,True]:
            with self.subTest(failure=failure),tempfile.TemporaryDirectory(dir=Path(__file__).resolve().parents[1]/"target") as temporary:
                root=Path(temporary);out=self.prepared_root(root);calls=[]
                def runner(operation,item,seconds):
                    calls.append(item['caseId']);self.assertLessEqual(seconds,5)
                    if failure and len(calls)==2:return {'status':'ERROR','errorType':'SyntheticFailure','actualHttpAttempt':True,'usageKnown':False}
                    return {'status':'RESPONSE_RECEIVED','actualHttpAttempt':True,'usageKnown':True,'totalTokens':18}
                with patch.object(selection,'audit',return_value={'validationPassed':True,'freezeGatePassed':True}):summary=selection.capture(root,out,runner=runner)
                self.assertEqual(summary.get('recordsProcessed'),2 if failure else 24)
                self.assertEqual(len(calls),2 if failure else 24)
                self.assertTrue((out/'batch-claim.json').exists())
                with self.assertRaises(ValueError):selection.capture(root,out,runner=runner)

if __name__=='__main__':unittest.main()
