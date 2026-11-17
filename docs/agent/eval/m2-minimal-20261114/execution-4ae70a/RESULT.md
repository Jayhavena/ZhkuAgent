# M2 最小真实任务结果：技术链通过，账单待核验

2026-11-14北京时间12:19:20–12:19:45，执行用户确认授权的一个问题、同任务两轮Probe。新包SHA256 `8d2d086efba7027ccb6ecdb731fff83409f03d0f5db66a58e9d03e619c06e895`，授权最多10次尝试/2run/1元、无自动重试。本次实际8次（6决策+2独立核验），均HTTP200，响应模型字段均qwen3.8-flash；未补跑，票据已消耗，剩余2次不延用。

## 实际链路与现有Grader

问题为合成“我这门选修课没过，能不能重修？”。首轮模型实际选择SEARCH_REVIEWED_RULES→READ_VERIFIED_RULE→ASK_NECESSARY_CONDITION，独立PUBLICATION_REVIEW通过后发布必要追问，WAITING_USER/INCOMPLETE，无claim。宿主只在必要追问通过后按预登记用户回复PUBLIC续答，同task第二run实际SEARCH→READ→FINISH_CHECK→PUBLICATION_REVIEW，发布RESOLVED/SUCCEEDED与1条结论。结论为2025.9.2已核验公共选修规则的“不安排重修”，不是对当前学籍/个案或当前有效性的学校正式认定。

- taskId：1df4eca4-5432-4bc1-84cf-74c7e5c2fab0。
- 首轮run：c5061667-def2-44ad-82ca-3c3e71956d40，4次模型尝试。
- 续答run：0bd41ecc-60e5-4a68-9cb1-b62cbe335890，4次模型尝试。
- 冻结模板绑定器：PASS，两个run同task、ID不同、轮次状态与claim关联通过；只替换runId/evidenceDir。
- 现有Grader：M2-CLARIFY PASS、M2-RESOLVE PASS，EXPECTATIONS_MET，regressionPassed=true，CLI退出0。报告为同目录report.json/report.md；这次是新批真实证据，不是旧suite回放。

## 用户六项验收

| 项目 | 本轮证据与结论 |
| --- | --- |
| 何时追问 | 首轮读取两类别规则后才提courseType，必要性独立核验通过；未抢答；PUBLIC后不再追问。此单题通过，不推出整体澄清准确率。 |
| 工具真实执行 | 两轮均有SUCCEEDED检索观察和VERIFIED_FACT读取观察；使用专用临时PostgreSQL，不是模拟工具。 |
| 观察被记录 | 两份完整outcome保留动作与观察；两轮各4个动作事件，工具观察、核验事件与发布终态可关联。 |
| 证据关联Claim | factId=S10/RETAKE_ONLY:public-failed-no-retake，when.courseType=PUBLIC，CLAIM关系指向c0；source/chunk/revision/span/quote与审核绑定相同。 |
| 发布闸门 | 两次独立核验事件均无拒绝原因，终态与outcome相符；主Agent另核claim文本/when、原文48字符范围与SHA256及冻结原文。审核原始摘要大写、展示摘要小写，按摘要字节比较，不改写身份输入。 |
| 失败正确停止 | 本批没有触发真实失败，不能宣称在线失败停止已实测。此前离线覆盖预算/坏响应/一次性票据及失败报告；本批正常关闭、无重试且额度未耗尽也停止。真实故障注入仍是后续缺口。 |

Grader不覆盖完整checksum/span/factId语义；主Agent对原始snapshot、独立核验输入、实际交换另做19项只读核对，全部通过（execution-audit.json）。54次引文出现、24次事实文本出现均在预披露最大范围内；24份交换记录摘要核对通过，无禁止上下文字段。该核对不是独立人工签核，也不证明所有潜在敏感数据均被通用检测覆盖。

## 用量与账单

输入14625 tokens、输出1023 tokens，8次响应均有usage。按已记录北京目录价输入0.8/输出2.7元每百万token计算，名义估算0.0144621元；这是估算，不是实际账单，不证明优惠、免费额度、应付或最终扣费。授权上限1元也不是实际消费。

实际账单、成功任务实际成本均为null，billingStatus=UNVERIFIED。已尝试既定百炼控制台：当前preview profile显示登录态缺失，登录按钮连续两次超时；备用电脑浏览器返回native pipe/browser access host不可用，未能访问用户原有登录会话。没有索取或提交密码，没有读取Cookie/密钥，也没有改用推理密钥查询账单。费用核验仍需恢复已登录费用控制台访问，按北京/qwen3.8-flash/Key ID和12:19:20–12:19:45时间窗核对，账单延迟时保持未核验；禁止用本轮估算或账户总费用冒充精确批次账单。

## 固定源码、原始证据及资源

运行候选源码57105f5499cb667569beefa5bb88ff45876965bd，构建jar SHA256 `4c155c0bc705dfd77e30c92aaf9f87ac91ff451e4e9c162a141f5c610a593db4`。执行checkout948390c4只增加绑定器/测试/文档补正，生产源码与运行候选无差异；基于正式main505a0dea，本地准备保护改动尚未合入main。入口为独立RuleAgentOnlineProbeHost，不是持久学生HTTP ONLINE业务验收。

原始证据目录：docs/evidence/rule-agent-provider/2026-11-14/m2-minimal/live-4ae70aecc64d4695a468b231bd1331ca。32个原始文件（请求/响应/提案/核验输入、trace/outcome/终态、授权/claim等）全量私有归档；归档ZIP和32项逐文件摘要验证通过，路径和摘要在archive.json。原始交换/条文/令牌/私有日志不提交Git；本目录只保存Grader白名单报告、绑定、摘要和核对结果。运行材料仍按私有材料管理，不向委派模型发送新原始交换。

启动器退出0，factoryResourcesClosed=true；专用55528端口释放，本批随机命名临时容器已回收，未写共享54329库。授权票据保留，不自动删除/恢复。

限定判断：一个最小真实任务的正常查证/必要澄清/核验发布链已通过；M2财务验收未完成，真实失败停止未实测。不能据1题推出Agent整体可靠、不能把结果升级为正式学生在线准入，也不改写旧20题4/20的基线。没有追加调用、push、部署或合并操作。
