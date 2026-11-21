const KEY='zhku-rule-task-history-v1'
const uuid=x=>typeof x==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(x)
const fields=['taskId','runId','turnId','version','recordedAt']

/** 缓存只保存定位元数据；正文和权限Token必须重新向已鉴权的后端取得。 */
export function createTaskHistory({storage=()=>globalThis.sessionStorage,now=()=>Date.now()}={}){
  function clear(){try{storage()?.removeItem(KEY)}catch{}}
  function write(owner,entries){try{const target=storage();if(!target)return;target.setItem(KEY,JSON.stringify({owner,entries}))}catch{}}
  function list(owner){
    try{
      const raw=storage()?.getItem(KEY);if(!raw)return[]
      if(raw.length>4096)throw new Error('oversized')
      const value=JSON.parse(raw)
      if(!uuid(owner)||value.owner!==owner||Object.keys(value).sort().join(',')!=='entries,owner'||!Array.isArray(value.entries)||value.entries.length>5)throw new Error('invalid owner')
      for(const row of value.entries){
        if(!row||Object.keys(row).length!==fields.length||fields.some(k=>!Object.hasOwn(row,k))||!uuid(row.taskId)||!uuid(row.runId)||!uuid(row.turnId)||!Number.isSafeInteger(row.version)||row.version<1||!Number.isSafeInteger(row.recordedAt)||row.recordedAt<0)throw new Error('invalid metadata')
      }
      if(new Set(value.entries.map(x=>x.taskId)).size!==value.entries.length)throw new Error('duplicates')
      const entries=value.entries.filter(x=>now()>=x.recordedAt&&now()-x.recordedAt<900000)
      if(entries.length!==value.entries.length)write(owner,entries)
      return entries
    }catch{clear();return[]}
  }
  return{list,clear,
    remember(owner,ticket){
      if(!uuid(owner)||!uuid(ticket?.taskId)||!uuid(ticket.runId)||!uuid(ticket.turnId)||!Number.isSafeInteger(ticket.version)||ticket.version<1)return
      const row={taskId:ticket.taskId,runId:ticket.runId,turnId:ticket.turnId,version:ticket.version,recordedAt:now()}
      write(owner,[row,...list(owner).filter(x=>x.taskId!==row.taskId)].slice(0,5))
    },
    forget(owner,task){write(owner,list(owner).filter(x=>x.taskId!==task))}
  }
}
export const ruleTaskHistory=createTaskHistory()
