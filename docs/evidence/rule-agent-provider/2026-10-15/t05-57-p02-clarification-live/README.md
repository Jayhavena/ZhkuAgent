# T05-57：合同02一次 P02 真实批次失败归档

本次完整 P02 未通过。唯一一次启动、同 task 一个 run、4 次真实调用；全部 HTTP 200，检索与读取成功，ASK 在独立核验返回后被发布门禁以 CAPABILITY_UNAVAILABLE 拒绝。没有合格 WAITING_USER，没有注入 PUBLIC，没有第二 run、FINISH 或答案发布。工程成果已冻结，真实在线验收未通过。

## 本批身份与授权关闭

用户明确“批准”绑定 t05-55 完整外发披露及新包 `0dd3f0a00f4e83d1fccfa7283ae0e168b8e0c307ac6d6ee0ebad9aa1c430acef`，阿里云北京 qwen3.8-flash，唯一一次 P02、最多 2 run/10 调用/12 元。执行 HEAD `0b405470b2771bf867619562535818dd7576001a`，源码 `796c95894611fcdcd041b79da68b5f7fe0b572ac`，实际及冻结 jar `82c25684f5993b96ddea4fa72b6bca85e25e055ab82bedac663593081f0b1541`。主仓库尚未合入。

授权在本目录 authorization.json 记为 CLOSED_FAILED：消耗 4，未用 6 全部关闭，剩余 0；没有复用旧授权、自动重试、修复、补额度或下一批。t05-55 的准备时 NOT_AUTHORIZED 文件保留为历史快照，不覆写。

## 已证实的首次失败

1. 调用 1：SEARCH，获得完整 10 候选；调用 2：模型自主选择 c1，宿主映射真实句柄读取第十八条，获得 6 个已审事实。没有额外 READ、容量拒绝或无进展循环。
2. 调用 3：ASK 的 conditionId 已为合法原键 courseType，支持事实却为两个 S10/COMBINED 重修事实。决策请求确实提供了这两个已读事实及 COMBINED 的 availableConditions。RuleResultPublisher.java:31-44 的核验前检查允许进入独立核验。
3. 调用 4：核验返回协议02、正确 inputFingerprint、两类课程的 IDENTIFIED/CORE 关系；绑定的是两个 S10/RETAKE_ONLY 事实。Provider 的 JSON/关联分支均为 NONE。完整 REVIEW_INPUT_SNAPSHOT 保存了原始 Fact 身份；三个已登记能力的定义 SHA 已准确重算并匹配，不是 checksum 展示大小写导致指纹失配。
4. RulePublicationGate.java:35-46 先检查核验绑定的已登记事实，再进入 assessAsk。对提议选择的第一个 COMBINED 事实，88-91 行调用 capability，155 行 catalogue.find 缺项而拒绝。RuleFactCapabilityCatalogue.java:72-75 仅登记 RETAKE_ONLY 重修事实，没有 COMBINED。故此次首次触发是“决策/前置检查允许选择的已读事实，与发布能力目录不一致”；门禁按未登记能力拒绝是受控行为。不能把 ASK 事件上的 INVALID_MODEL_RESPONSE 当成核验前先失败：它是 Runner.java:118-143 在同次核验拒绝后记录的传播结果。

这揭示实现合同衔接问题与本次模型选项的组合，不是网络超时、缺少原文或本批预算耗尽。没有据此倒推 t05-48、t05-52 的原因，也没有替换事实 ID 后自动重跑。

此外，核验的 necessaryConditionIds 两次写成 `S10/RETAKE_ONLY:courseType`，合同要求原键 `courseType`。后续 Gate.java:95-98 要按原键匹配；这是已见但未执行到的第二项合同不遵循，不能称为此次首次触发，也不能声称仅修复能力缺项即可通过。为何选择这些 ID、第二 run 是否可完成及稳定成功率仍未知。本批无后续决策，不能证明新增拒绝反馈有效。

## 时间、费用与退出

4 次 FINISHED 耗时分别 1636、1166、2531、11975ms，合计 17308ms；启动至退出 29.1253477 秒。均收到响应头、requestId 和 usage，没有 20 秒超时或 110 秒 run 耗尽。connectedMillis 为 null，不由此推断实际 HTTP 连接阶段。

共 6468 输入、631 输出 token；按本次重新核对的北京公开原价估算 0.0068781 元，不含优惠，实际供应商账单未核、保持未知。12 条 trace 是 4 次调用的 BEGIN/RECEIVED/FINISHED，12 份交换记录包含本机快照，不增加调用。逐次账本保留真实派发、字节 SHA、响应 ID、usage、wire 动作、宿主动作和拒绝码。

Docker 引擎原先未启动，按既有许可启动 Desktop；镜像已在本机，未拉取。只创建专用随机容器、回环端口10050、zhkuagent_rulecheck_e2e，未连接原演示库。已有启动器删除临时容器；独立确认无探针容器或 Java 残留，原演示数据库及演示 web 的完整 State 与批次前一致。其他运行容器只有健康检查滚动日志变化，稳定状态未变；Desktop 启动可能按其自身重启策略启动其他原有容器，未手动操作它们，Docker 引擎保持运行。

删除前临时表行数没有独立测到，记未知。失败路径宿主未保存任务仓的原始发布快照，仅保留 Runner outcome；不声称独立核对过失败发布快照。原始 evidence 保持不改写。预检本地 Docker JSONL/命令解析及归档读取编码错误均发生在控制脚本层，不作为产品失败归因、不触发模型补跑。

## 复核入口及恢复边界

- [逐次账本](attempt-ledger.json)、[拒绝分层](diagnosis.json)、[结果](result-summary.json)、[授权关闭](authorization.json)。
- [证据核对](verification.json)、[独立资源核对](independent-resource-check.json)、[资源差异审定](resource-verification.json)、[资产预检](identity-preflight.json)、[费用预检](pricing-preflight.json)。
- 原始轨迹与完整本机快照：`../t05-55-clarification-request/live-c284f31002e5419f8c4b9061dcf5a599/`；启动器日志在 t05-55 根目录新增独立文件。原申请包、披露、旧包与旧 jar 均保留。

本轮只执行并归档，未改源码、提示词、事实、门禁或预算，未重跑本地194项。一次固定工程探针、旧 P01 成功和本地确定性验收均不等于总体可靠性、未预置问题质量或学生收益。归档提交是失败证据检查点；恢复可撤回本归档提交，但不能删除历史失败、退款或恢复已关闭额度。下一项若获安排，先零外发复现上述合同衔接；本批不自动进入实施。
