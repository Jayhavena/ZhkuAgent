import hashlib
import json
import shutil
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(__file__).resolve().parents[5]
base = root / 'docs/evidence/rule-agent-provider/2026-10-14'
output = base / 't05-19'

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def cases(phase):
    values = {}
    for path in (base / phase / 'xml').glob('TEST-*.xml'):
        suite = ET.parse(path).getroot()
        for case in suite.findall('testcase'):
            key = case.attrib['classname'] + '#' + case.attrib['name']
            status = 'ERROR' if case.find('error') is not None else 'FAIL' if case.find('failure') is not None else 'SKIP' if case.find('skipped') is not None else 'PASS'
            values[key] = status
    return values

red = cases('t05-read-ref-red-02')
green = cases('t05-read-ref-final-02')
comparison = [{'case': key, 'before': status, 'after': green.get(key, 'MISSING')} for key, status in red.items()]
assert len(comparison) == 121
assert sum(item['before'] == 'FAIL' for item in comparison) == 13
assert all(item['after'] == 'PASS' for item in comparison)
assert len(green) == 134 and all(status == 'PASS' for status in green.values())
(output / 'case-comparison.json').write_text(json.dumps({'same121Cases': comparison, 'additionalFinalCases': [key for key in green if key not in red]}, ensure_ascii=False, indent=2), encoding='utf-8')
packet = base / 't05-read-ref-final-02/p01-request-package.json'
shutil.copyfile(packet, output / 'request-package.json')
data = json.loads(packet.read_text(encoding='utf-8'))
assert data['maximumProviderCalls'] == 4 and data['authorizationStatus'] == 'NOT_AUTHORIZED'
assert len(data['cases']) == 1 and data['cases'][0]['id'] == 'P01'
assert data['protocolRevision'] == 'RULE_AGENT_ACTION_CONTRACT_03'
(output / 'packet-integrity.json').write_text(json.dumps({'sha256': digest(packet), 'source': str(packet.relative_to(root)).replace('\\', '/'), 'providerCallsThisRound': 0, 'databaseOperationsThisRound': 0, 'authorization': 'NOT_AUTHORIZED', 'oldRemaining10': 'CLOSED_NOT_REUSABLE', 'currentLauncher': 'MUST_REJECT_NEW_PACKET'}, indent=2), encoding='utf-8')
print(json.dumps({'sameCasesGreen': 121, 'oldFailuresNowPass': 13, 'finalTests': 134, 'packetSha256': digest(packet)}))
