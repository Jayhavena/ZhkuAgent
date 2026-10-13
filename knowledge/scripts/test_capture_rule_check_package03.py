"""报告外发边界的合成合同；不读取凭证、不发网络请求、不读取16题参考。"""
import json
from pathlib import Path
import tempfile
import unittest
import capture_rule_check_package03 as capture

class CaptureContract(unittest.TestCase):
    def setUp(self):
        scratch=Path(__file__).resolve().parents[1]/'target';scratch.mkdir(exist_ok=True)
        self.temp=tempfile.TemporaryDirectory(prefix='package03-contract-',dir=scratch);self.root=Path(self.temp.name)
        base=self.root/capture.BASE;base.mkdir(parents=True)
        (self.root/'synthetic-system.txt').write_bytes(b'frozen')
        self.freeze={'status':'FROZEN_BEFORE_REPORT_QUESTION_PREPARATION','files':[{'path':'synthetic-system.txt','sha256':capture.sha(b'frozen')}]}
        (base/'system-freeze.json').write_text(json.dumps(self.freeze))
        body={'model':'qwen-plus','max_tokens':128,'temperature':0,'stream':False,'enable_thinking':False,
            'response_format':{'type':'json_object'},'messages':[{'role':'system','content':'synthetic'},
            {'role':'user','content':json.dumps({'question':'synthetic','options':[{'ruleId':'synthetic','questionScope':'synthetic'}]})}]}
        self.pack={'reportDenominator':16,'maxCalls':3,'retryCount':0,'maxOutputTokens':128,
            'systemFreezeSha256':capture.sha((base/'system-freeze.json').read_bytes()),
            'requests':[{'caseId':f'R{i:02}','kind':'SYNTHETIC_CONTRACT','body':body} for i in range(1,4)],
            'localBlockedCases':[{'caseId':f'R{i:02}'} for i in range(4,17)],
            'questionProjection':[{'caseId':f'R{i:02}','question':'synthetic'} for i in range(1,17)]}
        self.original_sha=capture.PACKAGE_SHA;self.write_pack()
    def tearDown(self):
        capture.PACKAGE_SHA=self.original_sha;self.temp.cleanup()
    def write_pack(self):
        raw=json.dumps(self.pack).encode();(self.root/capture.PACKAGE).write_bytes(raw);capture.PACKAGE_SHA=capture.sha(raw)
        self.authorization={'authority':'DIRECT_USER_MESSAGE','decision':'CONFIRMED','endpoint':capture.ENDPOINT,'model':capture.MODEL,
            'maxCalls':len(self.pack['requests']),'packageSha256':capture.PACKAGE_SHA,'packagePath':capture.PACKAGE,
            'reportQueriesAllowed':True,'referenceAnswersAllowed':False,'databaseCredentialsAllowed':False}
        self.write_auth()
    def write_auth(self):
        (self.root/capture.BASE/'external-transfer-authorization.json').write_text(json.dumps(self.authorization))
    def gate(self,*args):return {'validationPassed':True,'freezeGatePassed':True}
    def test_old_development_authorization_cannot_cover_report(self):
        self.authorization['reportQueriesAllowed']=False;self.write_auth()
        with self.assertRaises(ValueError):capture.capture(self.root,asset_audit=self.gate)
        self.assertFalse((self.root/capture.BASE/'provider-capture').exists())
    def test_missing_authorization_does_not_claim(self):
        (self.root/capture.BASE/'external-transfer-authorization.json').unlink()
        with self.assertRaises(FileNotFoundError):capture.capture(self.root,asset_audit=self.gate)
        self.assertFalse((self.root/capture.BASE/'provider-capture').exists())
    def test_failure_stops_and_budget_cannot_be_reused(self):
        result=capture.capture(self.root,runner=lambda *a:{'status':'ERROR','usageKnown':False,'actualHttpAttempt':True},asset_audit=self.gate)
        self.assertEqual(result['recordsProcessed'],1);self.assertEqual(result['reportDenominator'],16)
        with self.assertRaises(ValueError):capture.capture(self.root,asset_audit=self.gate)
    def test_automatic_nulls_keep_sixteen_case_denominator(self):
        result=capture.capture(self.root,runner=lambda *a:{'status':'RESPONSE_RECEIVED','usageKnown':True,'totalTokens':7,'actualHttpAttempt':True},asset_audit=self.gate)
        self.assertTrue(result['completedAllRequests']);self.assertEqual(result['knownTokens'],21)
        self.assertEqual(result['localBlockedCount'],13);self.assertEqual(result['reportDenominator'],16)
        self.assertFalse(result['humanSemanticGradesProduced'])
    def test_runtime_drift_after_question_preparation_blocks_network(self):
        (self.root/'synthetic-system.txt').write_bytes(b'tuned-after-report')
        with self.assertRaises(ValueError):capture.capture(self.root,asset_audit=self.gate)
        self.assertFalse((self.root/capture.BASE/'provider-capture').exists())
    def test_duplicate_case_and_answer_field_are_not_allowed(self):
        self.pack['localBlockedCases'][0]['caseId']='R01';self.write_pack()
        with self.assertRaises(ValueError):capture.admitted(self.root)
        self.pack['localBlockedCases'][0]['caseId']='R04'
        payload=json.loads(self.pack['requests'][0]['body']['messages'][1]['content']);payload['referenceAnswer']='synthetic-gold'
        self.pack['requests'][0]['body']['messages'][1]['content']=json.dumps(payload);self.write_pack()
        with self.assertRaises(ValueError):capture.admitted(self.root)

if __name__=='__main__':unittest.main()
