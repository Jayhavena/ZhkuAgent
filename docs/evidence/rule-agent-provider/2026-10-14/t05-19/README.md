# T05 READ候选引用本地加固检查点

范围：20分钟本地执行窗口，2026-10-14 11:08:26开始。仅模型READ线协议、宿主映射、安全诊断及对应测试；不外发、不运行真实探针、不改时间预算，不进入T06。基线df4fdb5050c336f9d1984a5993090fbac12363d1。提交身份见commit-receipt.json。

## 事实、设计判断与未知

- 已观察事实：旧真实批次检索签发10个合法候选UUID，包含第四条；第二次响应在READ_HANDLE_UUID处被拒绝，未执行READ。错误值未保留。本轮未改旧失败、评分或18次历史调用总数。
- 设计判断：让模型直接生成长UUID存在脆弱边界，本轮改为宿主签发`c1`等编号和当前集合代号。此为协议加固，不是已证实的旧故障根因修复。
- 已验证：同一组本地工厂测试旧协议失败，新协议可读取；未知/重复/超量、UUID/对象/数字/非法字符串、跨任务、旧检索集合及已结束run引用在READ前拒绝。原澄清续答、取消、权限和证据到期测试通过。
- 仍未知：真实模型是否遵循新编号合同、实际P01/P02能否完成、独立语义核验质量、线上端到端和学生收益。本机模拟服务不是供应商验证；本轮没有数据库写入或SQL链路复验。

## 问题—改动—证据

| 问题 | 改动 | 验证 |
| --- | --- | --- |
| READ非法元素只能定位到笼统UUID错误 | Validation增加readSelection，仅位置、JSON类型、字符串长度、受控失败类别、当前集合归属；不含UUID、编号原值、集合代号或自由正文 | invalidWireSelectionCannotReadOrPublish逐项核对；非法响应仍保留25/12已知usage |
| 模型直接生成长UUID | RuleCandidateReferences在每个task/run供应商端口内生成编号，Provider投影完整currentCandidates；模型不得提交真实UUID | localProviderAndActualDomainToolsFollowObservations、legalReadUsesCurrentCandidateAndAdvertisesAuthoritativeLimits |
| 同名c1可能在不同集合重新解释 | 必须回传宿主随机集合代号；新检索重新签发，关闭清表；未知、重复、超量严格拒绝 | stale、candidateReferenceCannotCrossTasks、finishedRunReferenceCannotBeReusedInSameTask |
| 新合同可能偏离内部工具或悄悄删候选 | 仅宿主解析为原Read(List<UUID>)；继续原decode/preflight/broker/原文核验/独立发布约束。保留全部当前检索候选 | allCandidatesRemainVisibleAndThreeUniqueReferencesRead：4候选全部可见，3唯一选择执行READ并合法发布 |
| 澄清续答或取消回归 | 不改Factory、TaskStore、Reviewer、Publisher及权限边界 | 真实TaskStore令牌续答、取消以及原Runner/Gate/EvidenceTools测试 |

模型协议版本RULE_AGENT_ACTION_CONTRACT_03；内部UUID动作协议不变。编号格式、字段由RuleAgentProtocol权威合同投影并供解析复用；数量上下限继续共享DEFAULT_BUDGET。每个run的Provider归指定task/run独占，映射不持久化、不跨run继承。新检索失败同样使旧集合失效。编号集合失效与原工具句柄TTL是两层约束，TTL仍由原工具重新核验，本轮未更改其机制。

## 修复前后验证

- red-01：121项，55个环境错误，原因是沙箱拒绝本机回环连接；不算有效协议RED。
- red-02：同一组121项，13个断言失败、0错误，其余通过；合法编号被旧字段/UUID协议拒绝，新安全诊断不存在。详见case-comparison.json逐项对照。
- green-01：121/121通过；final-01追加原权限/到期与已结束run检查，132/132通过。
- final-02：134/134通过，7个后端测试类；sourceDrift=0，resourcesVerified=true。不是全仓库数据库/供应商套件。
- 前端首次71通过、1文件环境错误（沙箱realpath EPERM），允许本机权限后重跑84/84通过。保留两次记录；没有为修复环境错误修改前端。
- 合法与非法模拟响应经过生产RuleAgentOnlineFactory → RuleAgentProvider → RuleAgentRunner → RuleActionBroker → RuleAgentEvidenceTools → 原发布/核验链路。候选由实际工具动态签发，未硬编码UUID。模拟资料、检索端和核验决定属于测试数据，未声称实际SQL或真实语义成功。

所有拒绝案例均无READ事件、无主张，端口资源退出；合法例执行VERIFIED_FACT读取并完成发布。模拟HTTP仅127.0.0.1，真实供应商调用0，数据库0，无自动重试。

## 新P01请求包与停止标准

新包：[request-package.json](request-package.json)，SHA256见packet-integrity.json。仅P01比较修读安排，同批10条已审核2025.9.2原文/解释、事实能力与来源指纹、动态工具反馈及新编号合同。实际候选UUID映射只留宿主。最多4次（3次决策+1次独立核验），单次10秒/1024输出token、run45秒，原预算不变；新授权尚未取得。

| 调用 | 成功后继续条件 | 失败停止条件 |
| --- | --- | --- |
| 1 SEARCH | 响应合同有效，真实检索签发相关当前候选 | 供应商/JSON/权限/检索/预算失败，停止，不重试 |
| 2 READ | 使用当前集合代号及1—3个唯一编号；映射后原工具核验取得所需审核事实 | 任一非法编号/旧集合/原文或权限核验失败，停止，不发布 |
| 3 FINISH | 提议仅使用实际读取且适用的事实，进入原独立核验 | 非法动作、漏读事实或预算耗尽，停止，不发布 |
| 4 独立核验 | 宿主原约束接受，发布两项第四条修读事实；实际原文可核对，资源退出，usage未知则保持未知 | 相关性/范围/覆盖/证据/协议不满足，保留阶段原因，停止本批 |

单次HTTP200不是成功；最终需真实检索→READ→FINISH→独立核验→受控发布全部成立，才可记录P01协议探针通过，仍不代表自然语言能力总体达标。

**执行门槛**：本轮只生成请求包。当前启动器绑定旧P01+P02包，会拒绝新包；下一步先在原探针范围完成仅P01的零调用启动前核对，再申请此包明确外发许可，不能借旧启动器执行或恢复剩余10次。P01通过后才能准备/申请P02；不得同时执行两者。

## 范围、恢复

未改Factory、TaskStore、Runner、Broker、EvidenceTools、Publisher、Reviewer实现、原数据库或学生UI。候选总范围不变；历史候选在模型投影中不再作为可用集合展示，宿主历史观察保留。没有新题库或固定答案。

本地提交为局部READ加固检查点，T05仍未收口。不push/PR/标签/部署。恢复应基于提交生成反向变更并重新验收，不重置既有工作区、不复活旧外发许可。已有四份产品文档及其他无关未跟踪资料未纳入提交。
