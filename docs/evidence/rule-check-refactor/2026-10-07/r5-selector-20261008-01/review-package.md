# Task2J / A05第一段有界选题协议最终增量审查

只审8项before→当前增量；changes-review.diff/source-before.json/source-review.json/before/*.bin及实际源，不纳入历史dirty。只读，不写文件、不跑测试/DB/模型/网络、不派agent、不发其他线程消息。

规范remaining-tasks A05、decision-contract、计划Task2J最后及ledger三Ruling。模型只返回一个审核目录ID或null，严禁answer/工具字段/条件推断。provider-neutral原始String Transport，不复用事实生成ModelResponse，正式CLOSED未装配/未外发。只传系统约束和结构化question+审核选题ID/questionScope，不额外传正文证据/个人条件/凭据；用户问题可能自行带信息，未宣称PII脱敏。

输入question<=2048 UTF16且合法Unicode；选项1–64，ID<=128/scope<=2048、唯一非空且合法Unicode；SYSTEM+userPayload UTF8总量<=65536，响应<=1024 bytes且合法Unicode。严格重复键/尾随/对象唯一字段ruleId/null/textual，精确不trim白名单，无语义匹配则提示null但prompt无法证明执行，合法ID却错题仍C05。解析异常/供应商cause不回显，不自动重试。

每实例最多128准入尝试（失败/超时也计，关闭前取消可保守消耗预算），每轮<=5秒有界等待；单physical worker，current准入标记拒绝BUSY，executor queue1仅线程handoff，不积压调用者。FutureTask override run/finally只有实际run退出才释放current；cancel(true)不释放非协作任务。close拒绝新请求、取消/interrupt且不无限等待；shutdown移走尚未run任务时清理已取消current。diagnostic attempts/inFlight/liveWorkers不保存请求/响应。

Review focus：严格白名单/JSON/Unicode/字节预算及no retry/cause；并发准入/关闭/超时/非协作future取消真实占用、工作线程与队列是否有界；transport仍须物理网络读取限制/连接超时/隐性重试，此批String后检查不证明网络下载已限长；响应合法不证明选题语义或安全注入抵抗。

测试17项纯替身。原RED17/17assert失败但timeout测试误用Duration同名TIMEOUT，只有其余16行为断言可计有效RED；首GREEN69有1失败，实际状态TIMEOUT正确、预期错误为Duration，修为WAIT_BUDGET后69PASS。旧输出保留，不制造RED证明已实现timeout。超时非协作100次新调用BUSY且provider1、实际release后0；关闭/并发latch有界finally退出。无真实模型/数据库正向语义。

最终31类374=285纯单元+77真库含12HTTP+12凭据配置；29runner计数替身合同；无卷55465/V22/连接0/容器退出，源码562/XMLhash与真实DRAFT/20参考/36题/此前rulecheckJava/所有profileYAML/18迁移保护。A05只有组件第一段，不勾整体完成。真实供应商适配/网络字节及retry/调用费用/简单基线/语义A05后段C组，原文A06/最终下发A07/页面浏览器B/C/G2仍OPEN。

请返回Strengths/Critical/Important/Minor(file:line+触发+最小修复)/Declined to judge/Assessment，一次审查。
