import math
import json
import unittest
import tempfile
import time
from pathlib import Path
from capture_rule_check_embeddings import validate_response, make_inputs, validate_batch_output, run_bounded, BATCH

def slow_response(argument,notify):
    notify();time.sleep(3)
    return {"status":"SUCCESS"}

def fast_response(argument,notify):
    notify();return {"status":"SUCCESS"}

def rejected_response(argument,notify):
    raise RuntimeError("synthetic-secret-must-not-be-returned")


def response(vector=None):
    return {"model":"text-embedding-v4", "id":"synthetic-unit-request", "data":[{"index":0,"embedding":vector if vector is not None else [0.1]*1024}],
            "usage":{"prompt_tokens":12,"total_tokens":12}}


class EmbeddingCaptureTest(unittest.TestCase):
    def test_valid_response_retains_usage_and_request_id(self):
        actual = validate_response(response())
        self.assertEqual(actual["promptTokens"], 12)
        self.assertEqual(actual["requestId"], "synthetic-unit-request")
        self.assertEqual(len(actual["vector"]), 1024)

    def test_empty_wrong_dimensions_and_nonfinite_are_rejected(self):
        for vector in [[], [0.1]*3, [0.0]*1024, [math.nan]*1024, [math.inf]*1024, [True]*1024]:
            with self.subTest(kind=str(vector[:1])):
                with self.assertRaises(ValueError): validate_response(response(vector))

    def test_model_and_usage_must_be_observed(self):
        for field, value in [("model","other"),("data",[]),("usage",{}),("usage",{"prompt_tokens":-1,"total_tokens":0}),
                             ("usage",{"prompt_tokens":12,"total_tokens":10})]:
            actual=response();actual[field]=value
            with self.subTest(field=field, value=value):
                with self.assertRaises(ValueError): validate_response(actual)

    def test_inputs_are_ten_approved_raws_and_twenty_original_queries(self):
        root=Path(__file__).resolve().parents[2]
        actual=make_inputs(root)
        self.assertEqual(len(actual),30)
        self.assertEqual({i["article"] for i in actual if i["kind"]=="DOCUMENT"},{2,4,5,6,8,9,10,11,12,18})
        self.assertEqual([i["id"] for i in actual if i["kind"]=="QUERY"],[f"Q{i:02}" for i in range(1,21)])

    def test_new_output_cannot_repeat_the_authorized_batch(self):
        temporary_base=Path(__file__).resolve().parents[2]/"knowledge/target"
        with tempfile.TemporaryDirectory(dir=temporary_base) as temporary:
            root=Path(temporary);canonical=root/BATCH;canonical.mkdir(parents=True)
            self.assertTrue(root.resolve().is_relative_to(temporary_base.resolve()))
            for output in [canonical,root/"docs/evidence/another-capture"]:
                with self.subTest(path=output.name):
                    with self.assertRaises(ValueError): validate_batch_output(root,output)

    def test_full_response_has_absolute_deadline(self):
        start=time.monotonic();result=run_bounded(slow_response,None,1)
        self.assertEqual(result["status"],"ERROR")
        self.assertEqual(result["errorType"],"AbsoluteDeadlineExceeded")
        self.assertTrue(result["actualHttpAttempt"])
        self.assertLess(time.monotonic()-start,2.5)

    def test_worker_success_retains_attempt_and_failure_is_sanitized(self):
        self.assertTrue(run_bounded(fast_response,None,5)["actualHttpAttempt"])
        failure=run_bounded(rejected_response,None,5)
        self.assertEqual(failure["errorType"],"RuntimeError")
        self.assertFalse(failure["actualHttpAttempt"])
        self.assertNotIn("synthetic-secret",str(failure))

    def test_only_explicit_approval_can_claim_canonical_empty_output(self):
        temporary_base=Path(__file__).resolve().parents[2]/"knowledge/target"
        with tempfile.TemporaryDirectory(dir=temporary_base) as temporary:
            root=Path(temporary);canonical=root/BATCH;canonical.parent.mkdir(parents=True)
            self.assertTrue(root.resolve().is_relative_to(temporary_base.resolve()))
            approval={"authority":"DIRECT_USER_MESSAGE","decision":"CONFIRMED","endpoint":"https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings",
                      "maxCalls":30,"reportQueriesAllowed":False,"databaseCredentialsAllowed":False}
            path=canonical.parent/"external-transfer-authorization.json";path.write_text(json.dumps(approval))
            validate_batch_output(root,canonical)
            approval["maxCalls"]=60;path.write_text(json.dumps(approval))
            with self.assertRaises(ValueError): validate_batch_output(root,canonical)


if __name__=="__main__": unittest.main()
