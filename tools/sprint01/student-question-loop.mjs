// 真实学生构建 + 同批模拟宿主；密码仅从环境读取，不录制含认证数据的 trace/HAR。
import {createRequire} from 'node:module'
import {writeFile} from 'node:fs/promises'
import {join} from 'node:path'
import assert from 'node:assert/strict'
const [modulePath,base,evidence]=process.argv.slice(2)
const {chromium}=createRequire(import.meta.url)(modulePath)
const password=process.env.ZHKU_SIM_DEMO_STUDENT_PASSWORD
if(!password||!/^http:\/\/127\.0\.0\.1:\d+$/.test(base))throw new Error('Bounded browser environment required')
const browser=await chromium.launch({channel:'msedge',headless:true})
const context=await browser.newContext({viewport:{width:1440,height:1000}})
const page=await context.newPage(),errors=[],checks=[]
page.setDefaultTimeout(15000)
page.on('pageerror',e=>errors.push(e.name))
let passed=false,failure=null
try{
  await page.goto(base+'/rules')
  await page.getByText('工程模拟：仅支持下方固定问题，不调用在线模型。',{exact:false}).waitFor()
  await page.screenshot({path:join(evidence,'student-scope.png'),fullPage:true})
  await page.getByLabel('账号',{exact:true}).fill('m104b-demo-student')
  await page.getByLabel('密码',{exact:true}).fill(password)
  await page.getByRole('button',{name:'登录',exact:true}).click()
  await page.getByLabel('你想查证什么？',{exact:true}).waitFor()
  checks.push('scope-before-login-and-real-login')
  for(const course of [{value:'PUBLIC',text:'公共选修课',expected:'不安排重修'},{value:'PROFESSIONAL',text:'专业选修课',expected:'重修'}]){
    await page.getByLabel('你想查证什么？',{exact:true}).fill('我这门选修课没过，能不能重修？')
    await page.getByRole('button',{name:'查证规则',exact:true}).click()
    await page.locator('#rule-condition').waitFor()
    await page.locator('#rule-condition').selectOption(course.value)
    await page.getByRole('button',{name:'确认条件并继续',exact:true}).click()
    await page.getByRole('heading',{name:'有依据可确认',exact:true}).waitFor()
    const claims=await page.locator('.confirmed-summary').innerText()
    assert.ok(claims.includes(course.expected),course.text+' conclusion')
    assert.ok(claims.includes(course.text),course.text+' declared applicability')
    await page.locator('.related-evidence summary').first().click()
    await page.getByRole('button',{name:'核对原文',exact:true}).first().click()
    await page.locator('#original-panel .original-unknown').waitFor()
    await page.getByText('展开本条原文与必要上下文',{exact:true}).click()
    assert.ok((await page.locator('#original-panel mark').innerText()).length>0)
    await page.screenshot({path:join(evidence,'student-'+course.value.toLowerCase()+'.png'),fullPage:true})
    checks.push('answer-original-'+course.value)
    await page.getByRole('button',{name:'新问题',exact:true}).click()
  }
  // 固定模拟任务执行很快，本批只验证等待澄清和跨窗口退出；取消由专用合同回归覆盖。
  await page.getByLabel('你想查证什么？',{exact:true}).fill('我这门选修课没过，能不能重修？')
  await page.getByRole('button',{name:'查证规则',exact:true}).click()
  await page.locator('#rule-condition').waitFor()
  // 当前页面只在 ACTIVE 暴露停止；WAITING_USER 的行为在下方明确观察，不伪造已覆盖。
  checks.push('waiting-user-visible')
  await page.locator('#rule-condition').selectOption('PUBLIC')
  await page.getByRole('button',{name:'确认条件并继续',exact:true}).click()
  await page.getByRole('heading',{name:'有依据可确认',exact:true}).waitFor()
  await page.locator('.related-evidence summary').first().click()
  await page.getByRole('button',{name:'核对原文',exact:true}).first().click()
  await page.locator('#original-panel .original-unknown').waitFor()
  await page.getByText('展开本条原文与必要上下文',{exact:true}).click()
  assert.ok((await page.locator('#original-panel mark').innerText()).length>0)
  assert.equal(await page.locator('.decision').count(),1)
  const other=await context.newPage();await other.goto(base+'/rules')
  await other.getByRole('button',{name:'退出登录',exact:true}).click()
  await page.getByRole('heading',{name:'登录规则查证',exact:true}).waitFor()
  assert.equal(await page.locator('.decision').count(),0)
  assert.equal(await page.locator('#original-panel mark').count(),0)
  checks.push('cross-window-logout-clears-result-and-original')
  assert.deepEqual(errors,[])
  passed=true
}catch(e){failure=e.message.replaceAll(password,'[REDACTED]');await page.screenshot({path:join(evidence,'student-failure.png'),fullPage:true}).catch(()=>{})}
finally{
  await context.close();await browser.close()
  await writeFile(join(evidence,'browser-result.json'),JSON.stringify({passed,failure,checks,pageErrors:errors,browserClosed:true,scope:'FIXED_SIMULATED_SCENARIO_REAL_HTTP'},null,2))
}
if(!passed)throw new Error(failure)
