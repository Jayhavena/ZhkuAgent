import test from 'node:test'
import assert from 'node:assert/strict'
import { errorMessage, ControlledError } from '../src/api/controlled.js'

// 旧任务页面已退役；这里仅保留仍由 v1 客户端使用的错误语义契约。
test('same submission key with different content explains conflict and deliberate new task',()=>{
  const message=errorMessage(new ControlledError('SUBMISSION_CONTENT_CONFLICT',409))
  assert.match(message,/内容|课程或问题/);assert.match(message,/不一致/);assert.match(message,/原任务/);assert.match(message,/新建/)
  assert.doesNotMatch(message,/未受理|重新发送/)
  assert.match(errorMessage(new ControlledError('NETWORK_UNAVAILABLE',0,{submissionUnknown:true})),/暂未确认/)
})
