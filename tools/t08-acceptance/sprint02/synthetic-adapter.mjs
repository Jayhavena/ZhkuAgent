/** 固定合成轨迹是执行器测试夹具，不能用于推断模型语义质量。 */
export function syntheticAdapter(c) {
  if(c.caseId==='synthetic-throw')throw new Error('Synthetic injected adapter failure');
  const one=(index)=>({taskId:`synthetic-task-${c.caseId}`,turnId:`synthetic-turn-${index}`,runId:`synthetic-run-${c.caseId}-${index}`,
    taskStatus:index<c.input.supplements.length?'WAITING_USER':'RESOLVED',runState:'SUCCEEDED',
    actions:[{step:1,actionId:`a-${index}`,type:'SYNTHETIC_READ'}],
    observations:c.caseId==='synthetic-missing-trajectory'?[]:[{step:1,actionId:`a-${index}`,status:'SUCCEEDED',dataLevel:'SYNTHETIC'}],
    result:{coverage:index<c.input.supplements.length?'NONE':'COMPLETE',items:[],issues:[],evidence:[{id:'synthetic-evidence'}],evidenceRelations:[]}});
  return {turns:Array.from({length:1+c.input.supplements.length},(_,i)=>one(i))};
}
