# M2 单任务最小批次：待授权审查包

状态：PREPARED_NOT_AUTHORIZED。没有真实模型调用、数据库操作、本批实际账单或真实执行Evaluation Report。

## 固定身份

- 正式工程基线main：505a0dea86c5b88f030c6692d8a9c0df9f52898a。
- 本次待运行候选源码：57105f5499cb667569beefa5bb88ff45876965bd，基于上述main增加准备链修复/一次性额度/失败报告及测试与绑定器，未带入旧20题质量修复分支；该候选尚未合入main。
- 新包SHA256：`8d2d086efba7027ccb6ecdb731fff83409f03d0f5db66a58e9d03e619c06e895`。
- 包、本次重建controlled.jar、模板、绑定器和披露的摘要见manifest.json。源码身份指上述候选；后续文档提交不冒充新代码身份。
- 完整请求包位于本机docs/evidence/rule-agent-provider/2026-11-14/m2-minimal/request-package.json，仅本地保存、不纳入本报告提交或自动公开。其与固定jar/模板/绑定器/完整披露已复制到用户级私有目录`.zhkuagent/m2/prepared/<包SHA>/`，不会依赖易变target目录保留唯一产物。

## 待授权的唯一执行范围

一个合成问题“我这门选修课没过，能不能重修？”，首轮模型检索/读取后经独立门禁形成必要追问；仅首轮合格才在同task续答PUBLIC。第二run最多发布已核验公共选修结论，最多2个run、10次供应商尝试（含独立核验和失败）、每run4决策+1核验、无自动重试/第三轮。失败即停并保留证据，不能为取得成功追加调用。

端点固定DashScope北京兼容接口，模型qwen3.8-flash。建议新批次费用上限1元，尚未取得授权。授权还须绑定本包SHA及全部最大外发资料；旧12元/旧包许可不沿用。授权额度不等于实际账单、也不是供应商账户硬限额；实际账单从既定百炼费用控制台按Key ID（不记录密钥）、模型/地域/时间窗核验，未结算保持UNVERIFIED。

## 最大外发范围（不是实际请求记录）

已审资料最大集：2025.9.2的10条条款，共616字符；20条语义事实及条件；另有2条经人工确认的Article4 overlay事实。导入文件虽以draft命名，正文资格与语义仍由已有B03/B02审核快照和字节指纹约束，不把文件名作为资格依据。实际请求仅包含当轮检索候选/已读部分，执行后应逐exchange核对。

工程内容单列：SYSTEM/REVIEW_SYSTEM提示词、动作合同/Schema、发布核验合同/策略、预算、协议、语义能力和条件结构；它们不是学校规则原文。静态完整内容在本机同批次OUTBOUND-STATIC-CONTENT.json。动态字段完整披露在OUTBOUND-DYNAMIC-FIELDS.json，含候选c编号/集合代号/摘要/版本/已读标记、问题/子问题、PUBLIC用户条件、规则上下文、事实/when、失败枚举、工具观察（状态/级别/引文/版本）、剩余额度、独立核验输入及指纹。

仅本地：源码/构建及审核资产摘要、完整PDF、C05选题草案、任务/主体/会话和原文令牌。禁止外发密钥、owner/session、continuation/original token、参考答案、真实学生数据、整本PDF。审核源/条款的UUID仅留本机；核验输入对任务绑定只发送摘要。包本身是最大范围和构建绑定，不整体作为模型消息发送。

## 验证结果及未完成边界

1. 22项绑定器合成单测、89项Java准备/Factory/装配/票据/Grader回归通过，无失败/跳过。主Agent复核并修复非UUID taskId和错误证据关系容器两个漏检，测试先失败再通过。
2. 实际新包Prepare与CheckPackage均完成；Check回执passed=true、providerCalls=0、databaseOperations=0、maximumProviderCalls=10。未创建或消耗真实票据。
3. 另做合成“证据目录为空”穿透检查：绑定器退出3，现有Grader真实CLI生成3项NOT_EVALUABLE、0 PASS，regressionPassed=false、退出1。这是失败管线冒烟，不是本批真实模型Evaluation Report。
4. 执行前冻结两case模板；执行后只绑定runId/evidenceDir，独立结构守卫要求恰2run、同task、不同runId、轮次状态和Claim关系。Grader仍不覆盖完整checksum/span/factId语义，原始snapshot/独立核验输入/发布记录另行核对；绑定PASS不能替代Grader或发布闸门。
5. 执行入口为独立RuleAgentOnlineProbeHost，具有完整trace/exchange/outcome/终态保存，不冒充持久学生HTTP在线实测。后者目前只做工厂/配置正向装配测试，完整持久ONLINE业务仍未验证。

尚待独立复核本次修订及冻结包，再请求用户明确批准新外发范围/10次调用/费用上限。没有授权前不得运行Probe。当前仅本地提交，未push、未部署、未改变main或既有质量成绩。
