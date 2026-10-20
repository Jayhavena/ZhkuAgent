// 只投影执行输入，参考答案/评分标准留在评测侧；过程输出仅含ID和摘要。
import {readFileSync,mkdirSync,writeFileSync} from 'node:fs'
import {resolve,join} from 'node:path'
import {createHash} from 'node:crypto'
import {pathToFileURL} from 'node:url'
const sha=bytes=>createHash('sha256').update(bytes).digest('hex')
const text=x=>typeof x==='string'&&x.trim().length>0&&x.length<=2048&&x.isWellFormed()
export function projectInputs(bytes,pin,pilotId){
  if(!/^[a-f0-9]{64}$/.test(pin)||sha(bytes)!==pin)throw new Error('DATASET_PIN_MISMATCH')
  if(!/^[a-z0-9-]{8,64}$/.test(pilotId))throw new Error('INVALID_PILOT_ID')
  const data=JSON.parse(bytes)
  if(data.recordType!=='T08_S02_DATASET'||data.split!=='holdout'||data.cases?.length!==8)throw new Error('EXPECTED_EIGHT_HOLDOUT_CASES')
  const ids=new Set()
  const inputs=data.cases.map(c=>{
    if(!/^[A-Za-z0-9_-]{1,80}$/.test(c.caseId)||ids.has(c.caseId)||c.group!=='G3'||c.synthetic!==false||!text(c.input?.question)||!Array.isArray(c.input?.supplements)||c.input.supplements.length>1||!c.input.supplements.every(text))throw new Error('INVALID_PILOT_CASE')
    ids.add(c.caseId)
    return {id:c.caseId,question:c.input.question,supplements:[...c.input.supplements]}
  })
  return Array.from({length:4},(_,i)=>({pilotId,batch:i+1,cases:inputs.slice(i*2,i*2+2)}))
}
export function writeInputs(dataset,pin,pilotId,out){
  const batches=projectInputs(readFileSync(dataset),pin,pilotId)
  // 原子创建全新目录；不覆盖旧请求输入或修改冻结数据集。
  mkdirSync(out)
  const result=[]
  for(const batch of batches){
    const name=`batch-${String(batch.batch).padStart(2,'0')}-inputs.json`,bytes=Buffer.from(JSON.stringify(batch,null,2)+'\n')
    writeFileSync(join(out,name),bytes,{flag:'wx'})
    result.push({file:name,sha256:sha(bytes),batch:batch.batch,caseIds:batch.cases.map(c=>c.id)})
  }
  return {recordType:'T08_S02_INPUT_PROJECTION',datasetSha256:pin,pilotId,referenceIncluded:false,batches:result,providerCalls:0,databaseOperations:0}
}
if(process.argv[1]&&import.meta.url===pathToFileURL(resolve(process.argv[1])).href){
  try{
    const [dataset,pin,pilot,out]=process.argv.slice(2)
    if(!dataset||!pin||!pilot||!out)throw new Error('Usage: node project-pilot-inputs.mjs DATASET SHA256 PILOT_ID NEW_OUTPUT_DIRECTORY')
    console.log(JSON.stringify(writeInputs(dataset,pin,pilot,out),null,2))
  }catch(e){console.error(e.message);process.exitCode=1}
}
