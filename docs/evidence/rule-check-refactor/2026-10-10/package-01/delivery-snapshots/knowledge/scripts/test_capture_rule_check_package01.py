"""网络边界替身合同，不读取密钥、不调用供应商。"""
import json
from pathlib import Path
import tempfile
import unittest
import capture_rule_check_package01 as capture

class CaptureContract(unittest.TestCase):
    def setUp(self):
        # 沙箱的系统临时目录权限与工作区不同；夹具仅在项目target内创建和删除。
        scratch=Path(__file__).resolve().parents[1]/'target'
        scratch.mkdir(exist_ok=True)
        self.temp=tempfile.TemporaryDirectory(prefix='package01-contract-',dir=scratch);self.root=Path(self.temp.name)
        base=self.root/capture.BASE;base.mkdir(parents=True)
        ids=[f'Q{i:02}' for i in range(1,21) if i!=2]+['O04']
        body={'model':'qwen-plus','max_tokens':128,'temperature':0,'stream':False,'enable_thinking':False,
            'response_format':{'type':'json_object'},'messages':[{'role':'system','content':'synthetic'},
            {'role':'user','content':json.dumps({'question':'synthetic','options':[{'ruleId':'synthetic','questionScope':'synthetic'}]})}]}
        self.pack={'requests':[{'caseId':i,'kind':'SYNTHETIC_CONTRACT','body':body} for i in ids],
            'localBlockedCases':[{'caseId':i} for i in ['Q02','O01','O02','O03']]}
        self.original_sha=capture.PACKAGE_SHA
        self.write_pack()
    def tearDown(self):
        capture.PACKAGE_SHA=self.original_sha;self.temp.cleanup()
    def write_pack(self):
        raw=json.dumps(self.pack).encode();(self.root/capture.PACKAGE).write_bytes(raw)
        capture.PACKAGE_SHA=capture.sha(raw)
        approval={'authority':'DIRECT_USER_MESSAGE','decision':'CONFIRMED','endpoint':capture.ENDPOINT,
            'model':capture.MODEL,'maxCalls':20,'packageSha256':capture.PACKAGE_SHA,'packagePath':capture.PACKAGE,
            'reportQueriesAllowed':False,'databaseCredentialsAllowed':False}
        (self.root/capture.BASE/'external-transfer-authorization.json').write_text(json.dumps(approval))
    def gate(self,*args):return {'validationPassed':True,'freezeGatePassed':True}
    def test_missing_authorization_never_claims_or_sends(self):
        (self.root/capture.BASE/'external-transfer-authorization.json').unlink()
        with self.assertRaises(FileNotFoundError):capture.capture(self.root,asset_audit=self.gate)
        self.assertFalse((self.root/capture.BASE/'provider-capture').exists())
    def test_first_failure_stops_and_claim_prevents_rerun(self):
        result=capture.capture(self.root,runner=lambda *a:{'status':'ERROR','usageKnown':False,'actualHttpAttempt':True},asset_audit=self.gate)
        self.assertEqual(result['recordsProcessed'],1);self.assertFalse(result['completedAllRequests'])
        with self.assertRaises(ValueError):capture.capture(self.root,asset_audit=self.gate)
    def test_twenty_success_records_retain_exact_budget_and_usage(self):
        result=capture.capture(self.root,runner=lambda *a:{'status':'RESPONSE_RECEIVED','usageKnown':True,'totalTokens':7,'actualHttpAttempt':True},asset_audit=self.gate)
        self.assertEqual(result['recordsProcessed'],20);self.assertEqual(result['knownTokens'],140)
        self.assertTrue(result['completedAllRequests']);self.assertTrue(result['sourceUnchanged'])
    def test_changed_bytes_or_local_case_cannot_reuse_approval(self):
        (self.root/capture.PACKAGE).write_bytes(b'{}')
        with self.assertRaises(ValueError):capture.admitted(self.root)
        self.pack['localBlockedCases'][0]['caseId']='Q01';self.write_pack()
        with self.assertRaises(ValueError):capture.admitted(self.root)

if __name__=='__main__':unittest.main()
