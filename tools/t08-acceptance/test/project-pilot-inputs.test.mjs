import test from 'node:test'
import assert from 'node:assert/strict'
import {createHash} from 'node:crypto'
import {mkdtempSync,writeFileSync,readFileSync,existsSync} from 'node:fs'
import {tmpdir} from 'node:os'
import {join} from 'node:path'
import {projectInputs,writeInputs} from '../project-pilot-inputs.mjs'
const sha=b=>createHash('sha256').update(b).digest('hex')
function dataset(){return {recordType:'T08_S02_DATASET',split:'holdout',cases:Array.from({length:8},(_,i)=>({caseId:`FIXTURE_${i}`,group:'G3',synthetic:false,input:{question:`合成输入${i}？`,supplements:i===0?['合成补充']:[]},reference:{answerPoints:['PRIVATE_REFERENCE_SENTINEL']},expected:{private:'PRIVATE_EXPECTED_SENTINEL'}}))}}
const encode=x=>Buffer.from(JSON.stringify(x))
test('projects only exact questions and declared supplements into four two-case batches',()=>{
  const b=encode(dataset()),r=projectInputs(b,sha(b),'synthetic-pilot')
  assert.equal(r.length,4);assert.ok(r.every((x,i)=>x.batch===i+1&&x.cases.length===2))
  assert.equal(r[0].cases[0].question,'合成输入0？');assert.deepEqual(r[0].cases[0].supplements,['合成补充'])
  assert.doesNotMatch(JSON.stringify(r),/PRIVATE_REFERENCE|PRIVATE_EXPECTED|reference|expected/)
})
test('dataset byte mutation is rejected before output',()=>{const b=encode(dataset());assert.throws(()=>projectInputs(Buffer.concat([b,Buffer.from(' ')]),sha(b),'synthetic-pilot'),/PIN_MISMATCH/)})
test('mixed group, synthetic content and duplicate IDs cannot enter real pilot input',()=>{
  for(const mutate of [d=>d.cases[0].group='G4',d=>d.cases[0].synthetic=true,d=>d.cases[1].caseId=d.cases[0].caseId]){const d=dataset();mutate(d);const b=encode(d);assert.throws(()=>projectInputs(b,sha(b),'synthetic-pilot'),/INVALID_PILOT_CASE/)}
})
test('more than one supplement, malformed unicode and too long questions are rejected',()=>{
  for(const mutate of [d=>d.cases[0].input.supplements=['a','b'],d=>d.cases[0].input.question='bad\ud800',d=>d.cases[0].input.question='x'.repeat(2049)]){const d=dataset();mutate(d);const b=encode(d);assert.throws(()=>projectInputs(b,sha(b),'synthetic-pilot'),/INVALID_PILOT_CASE/)}
})
test('fresh write preserves input bytes and does not overwrite existing directory',()=>{
  const dir=mkdtempSync(join(tmpdir(),'zhku-project-')),file=join(dir,'dataset.json'),out=join(dir,'inputs'),b=encode(dataset())
  writeFileSync(file,b);const receipt=writeInputs(file,sha(b),'synthetic-pilot',out)
  assert.equal(receipt.referenceIncluded,false)
  assert.equal(sha(readFileSync(join(out,receipt.batches[0].file))),receipt.batches[0].sha256)
  assert.throws(()=>writeInputs(file,sha(b),'synthetic-pilot',out))
})
test('bad dataset cannot create output directory',()=>{const dir=mkdtempSync(join(tmpdir(),'zhku-project-')),file=join(dir,'dataset.json'),out=join(dir,'inputs');writeFileSync(file,'{}');assert.throws(()=>writeInputs(file,'0'.repeat(64),'synthetic-pilot',out));assert.equal(existsSync(out),false)})
