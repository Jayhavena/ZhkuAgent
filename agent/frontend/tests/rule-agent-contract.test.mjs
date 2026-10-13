import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
const data=JSON.parse(await readFile(new URL('../../../knowledge/src/test/resources/ruleagent/contract-v3-fixtures.json',import.meta.url),'utf8'))
const contract=()=>import('../src/api/rule-agent-contract.js')
for(const row of data.snapshots)test(`shared snapshot: ${row.name}`,async()=>{
  const {validateRuleAgentSnapshot}=await contract()
  assert.equal(validateRuleAgentSnapshot(row.value),row.value)
})
for(const row of data.invalid.filter(x=>x.method==='readSnapshot'))test(`reject snapshot: ${row.name}`,async()=>{
  const {validateRuleAgentSnapshot}=await contract()
  assert.throws(()=>validateRuleAgentSnapshot(row.value),{name:'RuleAgentContractError'})
})
test('old reply cannot be silently upgraded to the new task schema',async()=>{
  const {validateRuleAgentSnapshot}=await contract()
  assert.throws(()=>validateRuleAgentSnapshot({status:'INSUFFICIENT_EVIDENCE',unknownItems:['回放未命中']}),{name:'RuleAgentContractError'})
})
