# M2 单任务真实验证准备（未授权执行）

基线main505a0dea，M1.1与M1.2已通过限定SIMULATED验收。本阶段不开展20题或整体质量Probe，只验证一个任务的两轮在线执行；旧P02和20题授权均已关闭，不恢复、不重放旧包。

## 待冻结的批次范围

- 合成问题：我这门选修课没过，能不能重修？
- 首轮必须由模型选择检索、读取工具并经独立核验发布必要追问；不得宿主硬塞追问，不得直接给结论。
- 只有首轮WAITING_USER且必要性门禁通过，才提交预登记回复PUBLIC（公共选修课）。同task续答一次，不创建第二个独立题目；重复续答只验证幂等，不增加run。
- 续答需发布至少一条适用公共选修的已审核结论，证据与Claim的fact/source/chunk/revision/checksum/span及语义绑定可追溯；不以非空文本代替核验。
- 任何首轮/续答错误、缺证据、模型越界、预算/超时或审计保存失败均停止本批；不自动重试、无第三轮、不按结果追加调用。

## 拟议额度，尚未获授权

固定当前代码qwen3.8-flash、北京兼容端点；同任务最多2个run，每run4次决策+1次独立核验，整批最多10次供应商尝试（含失败），单次20秒/1024输出token，run110秒。建议本批独立费用上限1元，最终须绑定实际包SHA并由用户明确批准，本文不是许可，也不是供应商账户级扣费硬限额。请求字节上限不是精确token数量，不能据此把目录价估算冒充实际账单或绝对人民币上限。

2026-11-14核对官方模型页北京原价：输入0.8元/百万token、输出2.7元/百万token（不含优惠）；来源 https://help.aliyun.com/zh/model-studio/qwen3-8-flash 。实际费用另从已登录百炼费用控制台按模型/地域/Key ID及开始结束时间窗核验；记录原价、抵扣、应付和结算状态，账单延迟时保持UNVERIFIED。推理密钥不能读取账单。不得把授权1元或估算写成实际消费。

## 执行前必须关闭的缺口

1. 当前PersistentStudentTaskConfiguration的ONLINE正向装配此前未测：新增隔离Java子进程测试，用明确的合成环境覆盖真实System.getenv路径，验证唯一在线工厂/注册表、无模拟工厂、ONLINE runtime和整宿主10次额度；工具工厂一旦打开即抛错。没有构造任务或发出供应商HTTP请求，不读取本机真实密钥。不是ONLINE业务执行验收。
2. 已有RuleAgentOnlineProbeHost是独立工程宿主，能保存provider-traces、exchange、outcome、task-results并连接现有Collector/Grader；持久学生ONLINE Options目前只有用量logger，无exchange落盘。必须明确选用哪个入口并绑定源码/产物；独立宿主通过不能写成持久HTTP在线入口通过。
3. 旧Probe支持新目录和新构建绑定，但没有持久一次性消耗票据；新批次需防止重复执行恢复额度，不能只靠每进程10次上限。
4. 逐项审查冻结包的最大外发资料、提示词、审核事实/工程草案及动态观察边界；参考答案、真实学生资料、owner/session/token、密钥、整本PDF不得外发。存在工程草案时明确披露，不能统称已审核资料。
5. 先冻结Grader两轮期望与完整证据判据；旧suite是历史回放，不能直接冒充新批结果。新runId只能执行后填绑定，不回写期望迎合输出。Collector降级为terminal fallback、缺outcome/observation、无claim关联必须在报告中披露。

## 交付判据

一份同task两run的Trace/Evidence（决策、实际工具观察、独立核验、发布状态及Claim引用）；一份现有Grader生成的Evaluation Report，保留FAIL/UNJUDGEABLE；供应商请求次数、responseModel、用量及实际账单状态；固定代码SHA/构建摘要/新包摘要；失败停止与资源回收证据。单个成功任务只证明该任务链可执行，不推导整体成功率或学生上线GO。

当前状态：准备与离线验证中，0次本批真实调用，费用授权未取得，尚无本批Evaluation Report或实际账单。

## 离线检查记录

新增子进程ONLINE正向装配测试随PersistentStudentTaskConfigurationTest共5项通过；可移植Java可执行路径修订后，连同PersistentRuleCheckLocalHostTest、RuleAgentControlledLaunchTest、AgentEvalChainRegressionTest共27项通过（0失败/错误/跳过）。生产源码未改，不把27项解释成真实ONLINE业务通过。

第一次扩大选类加入RuleAgentOnlineFactoryTest，合计73项出现8个环境性错误：诊断evidenceDir缺失与本机手册默认路径不可用。失败日志保留knowledge/target/m2-preflight.log；装配/守卫/Grader独立复跑日志为m2-preflight-green.log。后续显式指定已核对PDF路径和已创建输出目录重新跑Factory类，结果另以最终检查点记录为准。离线Factory测试即使使用loopback HTTP也不等于真实供应商调用。

指定PDF和输出目录后Factory类46项中43通过、3错误，均为包准备路径跨盘`IllegalArgumentException: 'other' has different root`（C盘工作区与D盘已核对PDF）。日志m2-factory-green.log名称不代表通过；未覆盖或修改资料、未运行供应商。该项阻断本机新包冻结；后续须修正资产身份路径表达或在同盘正确布局独立验证，禁止绕过资料指纹。当前检查点只保存已通过的新增装配测试及准备方案，不把M2或完整Factory回归标成完成。

## 准备链修复检查点

独立审查completed，请求OpenCode/deepseek-v4.1-flash，executedModel=null；主Agent独立核对后：外置手册使用稳定逻辑身份`@external/student-handbook.pdf`，内部资产仍用相对路径，原PDF SHA校验未改；新批次启动器规范化包路径并要求位于证据根下、证据和launcher日志跟随新包父目录、显式HandbookPdf传给真实Java子进程。不拷贝PDF、不开放草案外发、不构建/执行真实Probe。

新增逻辑键测试先因缺方法编译失败，修复后`Reviewed2025RuntimeAssetsTest`与`RuleAgentOnlineFactoryTest`共58项通过，0失败/错误/跳过（含原先3项跨盘包准备测试）；PowerShell语法解析通过。日志knowledge/target/m2-assets-red.log、m2-assets-green.log。后续一次性票据、Grader失败报告、两run绑定、外发披露和新包零调用Check仍待完成；本次没有真实调用授权和费用结论。
