"""把实际应用输出投影成人工评分表；没有人工判断时不产生通过分数。"""
from pathlib import Path
import argparse
import json
from capture_rule_check_selection import require, read, save_new

def build(root,results_path):
    base=root/'docs/evidence/rule-check-quality/2026-10-11'
    actual=read(results_path);rows=actual['cases'];require(len(rows)==43)
    old={x['caseId']:x for x in read(root/'docs/product/rule-check-2025-human-reference-report.json')['cases']}
    new={x['caseId']:x for x in read(base/'confirmation-cases.json')['cases']}
    expected={**old,**new};expected.update({f'T{i:02}':new['N08'] for i in range(1,4)})
    require({r['caseId'] for r in rows}==set(expected))
    out=base/'human-review';out.mkdir(exist_ok=False)
    records=[];lines=['# 质量收口：实际输出与人工核对\n','评分全部待核对；本表不把机械测试或AI建议视为人工确认。旧16题是已曝光回归，新24题是未调优但AI编制的确认集。重复题只验证该案例重复可靠性。\n']
    for row in rows:
        case=row['caseId'];reference=expected[case];result=row['result'];decision=result['decision']
        record={'caseId':case,'question':row['question'],'referenceRequirement':reference,'actualDecision':decision,
                'actualOriginalLocations':row['actualOriginalLocations'],'humanGrade':'PENDING','failureCodes':[],
                'providerRecordStatus':row.get('providerRecordStatus','UNKNOWN'),'qualityEligible':row.get('qualityEligible',False),
                'criticalError':'UNJUDGED','reviewer':None,'humanConfirmed':False}
        records.append(record)
        lines.extend([f'## {case}\n',row['question']+'\n','人工参考要求：\n```json\n'+json.dumps(reference,ensure_ascii=False,indent=2)+'\n```\n',
            '实际输出：\n```json\n'+json.dumps(decision,ensure_ascii=False,indent=2)+'\n```\n',
            '实际原文定位：\n```json\n'+json.dumps(row['actualOriginalLocations'],ensure_ascii=False,indent=2)+'\n```\n',
            '人工评分：PENDING；关键错误：UNJUDGED；审核者与理由：待填写。\n'])
    save_new(out/'human-grades.json',{'status':'PENDING_HUMAN_REVIEW','cases':records,'qualityPassed':None})
    with (out/'human-output-review.md').open('x',encoding='utf-8') as file:file.write('\n'.join(lines))
    return {'rows':43,'humanJudgments':0,'qualityPassed':None}

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--results',type=Path,required=True);args=parser.parse_args()
    print(json.dumps(build(Path(__file__).resolve().parents[2],args.results)))
