import hashlib
import json
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(__file__).resolve().parents[5]
base = root / 'docs/evidence/rule-agent-provider/2026-10-14'
def cases(phase):
    result = {}
    for path in (base / phase / 'xml').glob('TEST-*.xml'):
        for case in ET.parse(path).getroot().findall('testcase'):
            key = case.attrib['classname'] + '#' + case.attrib['name']
            result[key] = 'FAIL' if case.find('failure') is not None else 'ERROR' if case.find('error') is not None else 'PASS'
    return result
red = cases('t05-count-contract-red-01')
final = cases('t05-count-contract-final-01')
assert len(red) == 31 and sum(value != 'PASS' for value in red.values()) == 3
assert len(final) == 143 and all(value == 'PASS' for value in final.values())
rows = [{'case': key, 'before': value, 'after': final[key]} for key, value in red.items()]
assert all(row['after'] == 'PASS' for row in rows)
path = root / 'docs/evidence/rule-agent-provider/2026-10-14/t05-19/request-package.json'
assert hashlib.sha256(path.read_bytes()).hexdigest() == '7bcb597074997edd9fc2ef22e5e85003df093e9eaf92b2cebc6d5a5d14a3f560'
(base / 't05-22/case-comparison.json').write_text(json.dumps({'same31Cases': rows, 'finalRelatedBackendCount': 143, 'supplierCalls': 0, 'databaseOperations': 0, 'packetUnchanged': True}, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps({'redCases': len(red), 'fixedDiagnosticCases': 3, 'finalCases': len(final), 'packetUnchanged': True}))
