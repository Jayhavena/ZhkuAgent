# T05-62 动作合同06新包：完整外发披露（未授权）

本申请仅对应新包`1e44705ae88a7fea23852862bc365da1c764357ef3664ed5139b96ace9cf2fd0`、jar`01f2e79e6e6d950d02ecdc80397f445d2acc3076416dc4c0941706c9bde6fe5b`、生产源码56d33efd。当前授权调用额度0，历史剩余额度不恢复。本轮只准备包及零调用预检，没有真实供应商调用、数据库或Docker操作。

## 接收方、用途与数据流

申请接收方为阿里云百炼/DashScope华北2（北京），唯一HTTPS POST目的地：`https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions`，模型固定qwen3.8-flash，不跟随重定向。应用显式设置Content-Type及Authorization: Bearer本机DASHSCOPE_API_KEY；HTTP栈另生成Host/长度等传输元数据。密钥只用于该目的地鉴权，不进入JSON、日志或答复。

用途仅为一次固定P02工程探针。首问“我这门选修课没过，能不能重修？”。只有同run真实检索、读取、必要追问提议及独立核验成功，产生无答案主张的合格WAITING_USER后，宿主才提交预登记合成用户补充PUBLIC（公共选修）；续答必须同task新run，重新检索、读取、独立核验并完整发布。PUBLIC是公开登记的工程输入，不是参考答案、官方学籍或私人学生记录。首run不合格立即结束，不提前注入PUBLIC来换取通过。

固定的全部提示词、动作/核验合同、潜在原文片段、已审事实/条件及可能ruleContext范围文字，完整列于[外发静态内容](OUTBOUND-STATIC-CONTENT.json)。静态附件另列全部可能证据缺口/条件缺口及10种登记能力映射，避免只列字段而省略其可能内容。逐项动态字段、来源、上限及仅本机字段完整列于[动态字段清单](OUTBOUND-DYNAMIC-FIELDS.json)。这些文件供审核，不会整体作为HTTP请求上传；未来确切请求由实际结果生成，不能预造真实运行字节。

## 外发范围与新增字段

JSON外壳为model、stream=false、enable_thinking=false、max_completion_tokens=1024、system/user两条messages。决策response_format使用strict json_schema，独立核验使用json_object；静态Schema随当前候选编号及合法动作动态约束。本地字段和形状核对不证明供应商支持该Schema或模型会遵循。

决策user内容包含当前完整候选集合代号、c编号/摘要/修订/已读标记、合法动作和时间/证据容量反馈；原问题及子问题、用户已声明条件、已读规则上下文/事实/when、工具观察及原文quote/revision、动作合同和剩余预算。最近最多2条rejectedActions包含attempt/action/reasonCode及可选publicationReason；新增availableConditions，仅ruleId/conditionId/allowedValues。它们来自宿主结构化事件与已读审核模板，不发送模型自由文本理由。candidateSetId是一次搜索的临时编号，不是身份/原文访问凭据。所有候选保留；不默认c1、不截断、不预置答案、不因自报完整强制FINISH。

独立核验user内容是安全SemanticInput：questions、userConditions、已读facts与quote/revision、能力的维度/对象/角色/来源指纹、新增conditions审核目录（ruleId、conditionId、question、allowedValues、factIds），以及协议版本、能力版本和inputFingerprint；另有reviewContract和剩余核验调用数。原始task/version仅进入摘要。新增条件目录进入指纹，要求完整可行分支，不把未读事实用于排除分支。核验不接收提议者的ASK/理由/相关性或完整性自评。相同模型另一次调用只提供上下文及职责分离，不能宣称跨模型/供应商独立性。

原文和解释范围仅2025.9.2本科历史已审材料；现行适用性、个人资格及真实教务记录未核。原文不改写，权限/用途边界保持。整本PDF、完整源码、数据库、buildIdentity/runtimeAssets及整份申请包不外发。

特别披露：决策ruleContexts.questionScope包含现有本地C05选题目录派生的范围文字，静态清单列出全部17种可能文字及来源。该草案文件本身不整体发送；这些派生文字的本批外发也属于本次待批准范围。批准本次服务传输不将C05草案升级为正式学校规则审核，不赋予公开发布/跨用途共享资格。独立核验输入不发送该questionScope草案文字，仅发送当前审核条件目录。

不外发owner/session、原文访问token、原始task/run UUID、source/chunk UUID、原始checksum、完整Fact定义、数据库URL/密码、学生私人教务记录或通过门槛/参考答案。capability.sourceFingerprint是事实定义摘要，inputFingerprint是任务绑定安全输入摘要，都不是原始标识或会话凭据。


本包新增/变更范围：决策user增加publicationFactIds，facts每项增加publicationEligible；来源是已读事实在能力目录中的登记及原始完整定义指纹匹配。已读事实仍完整保留，未获资格不裁剪。决策Schema的ASK.supportingFactIds与FINISH.factIds共用该名单作为动态枚举，名单为空时maxItems=0。最近两次拒绝反馈可增加publicationReason（枚举值见静态附件），不发送模型自由文本理由。核验合同明确necessaryConditionIds必须使用审核目录原键（如courseType），带规则前缀或未知键以REVIEW_CONDITION_UNKNOWN拒绝，不自动纠正或绕过。

静态17种scope已从当前C05/审核语义和实际拼接规则重新生成并与附件逐项比对；10种能力映射的当前实现与上一包映射段逐字核对不变，不能以能力版本号未变替代源码检查。

## 受控本机记录与可追溯重建

REQUEST/RESPONSE/PROPOSED_ACTION按(taskId,runId,attempt)关联，另新增仅本机REVIEW_INPUT_SNAPSHOT，保存taskId/version、安全核验输入与完整Fact（含原始大小写checksum、source/chunk、跨度、原文、独立性和when）。完整Fact用于准确重建能力和输入身份，绝不加入供应商请求。展示Evidence会把checksum小写化，不能据展示层代替原Fact身份。

记录原字节SHA、受控副本及redacted标志，不记录HTTP头。凭证按解码后的字段名/值和有界Unicode展开脱敏；无法可靠解析的JSON正文省略并标记，不能宣称可逐字重放。快照/请求最大65536字节，证据工具保留最多8片段/32768字节，同任务可复用；超容量拒绝，不裁剪分支以通过。记录器失败以AUDIT_UNAVAILABLE终止；收到的可解析usage先保存。快照不增加外部调用额度。

## 拟申请批次、调用和时间账

唯一一次P02，同task最多2 run、整批最多10次真实调用、人民币12元。每run最多4次决策+1次独立核验，20秒/调用、110秒/run，Java进程235秒；工具最多6步/run，请求最多65536字节、输出请求上限1024token。通用领域budget的3run/12次不是本批许可，探针只编排两run并共享10次。

每run最满80秒决策+20秒核验+5秒发布=105秒，仅余5秒供检索/读取、序列化、完整快照I/O及调度；不以“多一次机会”保证完成。两run最满220秒，Java上限余15秒还要承担迁移/导入、任务编排和清理，仍可能不足。Docker就绪等待和入口预检在Java外，执行轮主动执行仍最多20分钟。动作会按剩余时间受限，不能为了通过减少独立核验、篡改答案或延长已批准预算。

无P01重跑、第三run、应用层自动重试、失败后自动修复、补额度或开启下一批。首run未形成合格必要追问就停；第二run未完整核验并发布就失败，不能跨批次拼接已到达的阶段。

## 费用预留与全部尝试记录

本轮于2026-10-15T13:49:28.594283+00:00重新核对[阿里云官方定价](https://help.aliyun.com/zh/model-studio/qwen3-8-flash)：北京公开原价输入0.8元/百万token、输出2.7元/百万token；非思考输入上限991808、公开输出上限131072。保守按公开最大输出而非请求1024预留：10×(991808×0.8+131072×2.7)/百万=11.473408元≤12元。详见[定价与预算](pricing-and-budget.json)。不计缓存、优惠或免费额度；这是风险预留，不是预计账单或供应商账户硬钱包限额。执行前必须重新核价，无法确认预留不超过12元则不启动。

逐次按task/run/attempt去重，BEGIN/RECEIVED/FINISHED及本机快照不重复计次/计费。记录每次是否派发、是否收到响应头及requestId、状态、耗时、字节SHA、usage、提议动作、Provider校验分支和Gate发布拒绝码。派发后超时或缺usage仍占用调用额度，费用UNKNOWN，不记零、不补调用；usage费用估算与实际账单分开，实际账单核对前保持未知。

## 成功、失败与资源退出

首run必须WAITING_USER/INCOMPLETE且run已终止，具有真实响应、LIVE_AGENT标记、原文依据和指向courseType的澄清关系，只给PUBLIC/PROFESSIONAL选项且无答案主张；SEARCH、READ、ASK及独立核验均成功，Runner产物须与任务仓发布快照一致。

续答必须同task、不同且版本递增的新run，RESOLVED/SUCCEEDED、coverage COMPLETE，重新SEARCH/READ/FINISH及独立核验成功，宿主按已读审核事实发布正确的公共选修不合格处理解释，有原文与依据关系、无残留追问/缺口。对应本机期望factId仅用于验收，绝不作为参考答案/成功标签发送给模型。重复提交必须幂等，不产生额外run/调用。

任一身份/权限/输入/快照/Schema/关联/条件/时间/容量/独立核验/发布/资源退出不合格均失败并停止。保存所有实际尝试及首次失败位置；原始任务仓快照未保存或清理行数未测到时如实记未知。关闭全部未用额度，清理自有随机容器/进程，独立核对原演示容器状态未变，不按进程名误杀其他任务。

自动CheckPackage覆盖包定义、jar、335项生产源码/启动文件及15项运行资产；根/模块POM、共享模块源码和wrapper等额外构建输入由本机receipt另外核对，不能声称这些全部都由现有自动门禁覆盖。批准后执行前必须连同receipt再做fresh环境、镜像、凭证、jar/源码/资产及费用预检；需数据库时仅随机容器、空闲回环端口的专用zhkuagent_rulecheck_e2e，拒绝原演示54329。凭证每次操作前重新读取，不打印值。本准备轮不启动Docker或连接数据库。

通过也只证明该固定P02工程探针链路；上一轮205项本地回归及审查后3项复验、有限工程探针及跨批次到达阶段不能替代冻结配置可靠性、未预置问题质量或学生收益。当前工程成果已冻结，真实在线验收未通过。

## 本轮核对的具体限制

已冻结jar直接生成新包，本轮无Maven重建。CheckPackage核对通过，旧SHA及重复Prepare被拒绝且新包未被覆盖。普通用户持久PATH的Windows PowerShell5.1→CMD→PowerShell7入口零调用CheckPackage通过。205项回归和审查后3项是t05-61已有结果，本轮不声称重新运行测试。

费用上限由固定目的地、非思考模式、禁用可选联网/多模态能力、最多10次派发及公开最大输入/输出价的保守预留共同约束；不是账户钱包的自动扣款硬限额，不保证供应商定价永远不变。执行前价格或上限无法确认则不启动。本轮未检查Docker引擎、镜像或数据库就绪，不代表在线环境已就绪。
