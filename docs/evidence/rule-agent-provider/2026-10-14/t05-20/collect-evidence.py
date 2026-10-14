import hashlib
import json
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(__file__).resolve().parents[5]
base = root / 'docs/evidence/rule-agent-provider/2026-10-14'
def cases(phase):
    values = {}
    for path in (base / phase / 'xml').glob('TEST-*.xml'):
        for case in ET.parse(path).getroot().findall('testcase'):
            key = case.attrib['classname'] + '#' + case.attrib['name']
            values[key] = 'ERROR' if case.find('error') is not None else 'FAIL' if case.find('failure') is not None else 'PASS'
    return values
red = cases('t05-p01-preflight-red-01')
green = cases('t05-p01-preflight-green-01')
assert len(red) == 25 and len(green) == 136
assert all(status == 'PASS' for status in green.values())
rows = [{'case': key, 'before': status, 'after': green[key]} for key, status in red.items()]
assert sum(row['before'] != 'PASS' for row in rows) == 5
package = root / 'docs/evidence/rule-agent-provider/2026-10-14/t05-19/request-package.json'
assert hashlib.sha256(package.read_bytes()).hexdigest() == '7bcb597074997edd9fc2ef22e5e85003df093e9eaf92b2cebc6d5a5d14a3f560'
(base / 't05-20/case-comparison.json').write_text(json.dumps({'same25Cases': rows, 'relatedBackendCases': len(green), 'packetUnchanged': True}, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps({'redCases': len(red), 'fixedCases': 5, 'greenCases': len(green), 'packetUnchanged': True}))
