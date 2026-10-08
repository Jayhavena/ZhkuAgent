# R5 Task2J / A05第一段：有界选题协议验收

2026-10-08。BOUNDED_SELECTOR_PROTOCOL_VERIFIED_NOT_PROVIDER_ASSEMBLED。

|解决的问题|实现|验证|
|---|---|---|
|选题可能越界生成事实/工具或自造规则|只有ruleId/null，精确白名单，不复用答案协议|额外answer/toolCalls/错误类型/重复/尾随/越权ID拒绝；无匹配不取第一项|
|目录/问题/输出不受控消耗预算|Unicode、数量、UTF16及UTF8预算，原始响应解析前限1024字节|空/重复/非法目录及超大中文输入不调用；过长中文/错误Unicode输出拒绝|
|失败重试或超时持续创建外发任务|总尝试有界，无重试；单worker/单current/有界等待|失败计入预算、耗尽拒绝；非协作超时100次BUSY且替身只1调用，release后清零|
|关闭等待供应商无限阻塞、异常正文泄漏|关闭取消/interrupt不无限等待；固定Outcome不返回cause|并发/关闭latch与finally退出、异常不含输入/cause|

原RED17项17断言失败，timeout测试常量误与状态重名，因此仅其余16行为可计有效RED；首GREEN69中1失败比较Duration与TIMEOUT状态，状态正确；更名WAIT_BUDGET后69PASS。旧报告保留，不伪造timeout业务失败证据。最终31类374项（285纯单元+77真库含12HTTP+12凭据配置）、29计数替身合同；无卷55465 V22连接0/退出。0失败/异常/跳过；源码562及XMLhash复算，8项before/after，原DRAFT/20参考/36题/此前rulecheckJava/所有profileYAML/18迁移不变。

一次增量审查见review.md/review-resolution.json。本批纯Transport替身证明协议/资源合同，不是供应商效果、HTTP端语义或网络抓包。响应String预算是在返回后检查，不证明物理读流已受限；真实适配须另验网络超时/读流/无隐式重试/实际用量。单实例总预算不宣称全局/分布式限额。白名单合法或prompt约束不证明选题正确/注入语义安全；简单基线比较与真实问题人工参考留C组。正式CLOSED未装配，A05整体/原文A06/最终外发A07/页面浏览器/B/C/G2仍OPEN。
