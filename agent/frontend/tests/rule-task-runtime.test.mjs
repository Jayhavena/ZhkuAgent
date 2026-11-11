import test from 'node:test'
import assert from 'node:assert/strict'
import {validateRuntimeInfo} from '../src/api/rule-task.js'

test('runtime mode is explicit and simulated scope cannot be confused with online',()=>{
  assert.equal(validateRuntimeInfo({mode:'SIMULATED',simulatedQuestion:'合成固定问题'}).mode,'SIMULATED')
  assert.equal(validateRuntimeInfo({mode:'ONLINE',simulatedQuestion:null}).mode,'ONLINE')
  for(const x of [{mode:'UNKNOWN',simulatedQuestion:null},{mode:'ONLINE',simulatedQuestion:'合成问题'},{mode:'SIMULATED',simulatedQuestion:''}])
    assert.throws(()=>validateRuntimeInfo(x))
})
