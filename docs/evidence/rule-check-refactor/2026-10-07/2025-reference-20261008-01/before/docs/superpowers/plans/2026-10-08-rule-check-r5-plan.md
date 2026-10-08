# R5：可核验原文与单主题查证实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 用户已授权连续实施；默认顺序执行，卡点留账，推进不依赖卡点的任务；不创建新聊天、不推送/部署。

**Goal:** 让首个校园规则查证切片只展示经审核模板或可核对原文，逐步完成条件/例外、必要澄清与真实用户验证。

**Architecture:** 保留现有检索、引用审计和controlled身份边界。先建立可独立验证的原文跨度核验组件，后续应用链复用既有混合检索，不让新组件变成第三套通用RAG。旧四字段协议和v1学分接口保持兼容，新v2查证回复按既定五种业务状态实施。

**Tech Stack:** 当前Java21/JUnit/AssertJ、Spring/JDBC与Vue；Task1仅JDK确定性核验，无数据库、模型或新依赖。

**Spec:** [范围约定](../../product/rule-check-scope.md)、[主路线图R5](2026-10-07-rule-check-refactor-roadmap.md)、[Eval协议](../../product/rule-check-eval-contract.md)。细化已有“已审模板/校验原文跨度”设计，不重新确定产品方向。受限工作列于[卡点表](../../product/rule-check-blockers.md)。

日期：2026-10-08。P0工程收口已完成20类151项及独立审查补修；Task1和Task2A已验证，尚未装配学生入口。G0当前资料和人工参考未通过；允许Task1合成工程组件开发，不允许据此进入学生试点。Task2–4数据依赖单独记录，不能用编造政策补齐。

## Global Constraints

- 面向学生降低查阅、理解和判断成本；不推断个人教务资格，不宣称办理已受理。
- 事实只能来自已审模板或经核验摘录；模型提供的自由改写、数字、标识和位置全部不可信。
- 摘录文本相同只证明取自该位置，不证明回答完整、相关、当前适用或没有遗漏条件/例外。
- G0未通过时，不给合成资料学生发布资格，不执行真实质量留出/用户对照，不扩张主题或实施P2。
- 不改变公共runtime、旧学分循环、旧Flyway迁移或既有v1协议。
- 每批有独立before字节/增量/命令/实际XML/哈希/失败及退出证据；新增测试显式登记，真实模型不加入默认白名单。

## Review Focus

1. 原文4、模型写40、位置合法：核验不能只检查ID或字符范围，必须检查提议文本与实际substring一致。
2. 相同文字出现在多处或重复citation ID：按给定位置和服务端身份核对，重复标识拒绝而不是取第一条。
3. 查询后撤回、版本/hash改变、reader返回另一个source/chunk：输出前重读并拒绝；数据库实际竞态由Task2补证，不用内存回调宣称真库控制。
4. UTF-16位置跨越代理对、负数/越界/空白：必须受控拒绝，不能切出半个字符或泄漏未核验的提议文字。
5. 证据重复原文但不相关、遗漏例外、模型裁判分高：Task1不证明语义完整，Task2/3的审核模板与真实人工评分必须补齐。

## 文件与接口

| 任务 | 计划路径 | 责任 |
| --- | --- | --- |
| Task1 | `knowledge/src/main/java/com/zhku/knowledge/rulecheck/RuleCheckEvidenceSnapshot.java` | 服务端本轮证据与最新读取结果：citationId/sourceId/chunkId/revision/checksum/text/eligible |
| Task1 | 同包`RuleCheckEvidenceSpan.java` | 模型提议：citationId/startInclusive/endExclusive/quote；不承载客户端自证的资料资格 |
| Task1 | 同包`RuleCheckVerifiedExcerpt.java` | 核验通过后由原文生成的摘录及服务端身份/版本/hash/位置 |
| Task1 | 同包`RuleCheckEvidenceVerifier.java`；同测试包`RuleCheckEvidenceVerifierTest.java` | 本轮引用、位置、文字、SHA-256与最新资格/身份/版本复核；结果含明确拒绝原因 |
| Task1 | `knowledge/scripts/verify-rule-check-refactor.ps1` | 仅登记该纯单元类，保留供应商/数据库目标限制 |
| Task2 | 同包`RuleCheckRequest/Reply/ApplicationService/Controller`及必要的当前证据读取适配器、测试；现有controlled安全配置/引用审计 | 五状态、审核模板、共享检索与输出前真库重新取证；执行前补确切文件白名单 |
| Task3 | `agent/frontend/src/views/RuleCheckView.vue`、`src/api/rule-check.js`、页面测试与必要路由/首页 | 原文入口、必要追问、独立确认项及未知项、旧学分实验导航 |
| Task4 | 独立评估与用户对照证据 | 真实16题留出、失败归因、配对变化及收益；不添加假用户或模型评分成绩 |

Task1接口：`verify(proposal, List<RuleCheckEvidenceSnapshot> approved)`，构造器注入`CurrentEvidenceReader.read(sourceId, chunkId)`，返回`VerificationResult`。reader是服务端依赖，不能由学生请求提供；Task1只有内存替身，尚不接数据库。通过时文本由snapshot的substring产生，不照搬模型文字。

位置统一Java/JavaScript的UTF-16 code unit，起点包含、终点不包含，不能割裂代理对。checksum指片段文本UTF-8 SHA-256值，按十六进制值比较；审核版本独立匹配，不因文字相同省略版本检查。reader异常、无结果、当前不合格或身份/文字/版本/hash变更均拒绝。不要把该回调接口当现有权限装配已完成。

## Task1：原文跨度核验组件（可独立推进）

- [x] **Step1：准备基线与任务证据。** 确认拟新增路径不存在、P0最终源码测试一致；记G0与本批未接学生端。创建本计划独立账本，不复写P0历史。
- [x] **Step2：编写合同反例。** 正常摘录、4→40、伪造/重复ID、负数/越界/空/空白范围、错误SHA、版本/身份/正文改变、撤回、reader缺失/异常、UTF-16边界。非法提议在调用reader前拒绝，计数必须0；正常核验reader一次。
- [x] **Step3：观察RED。** Java新API最小壳只默认拒绝，不连接或生成事实；测试应暴露正常路径与详细拒绝合同未实现。该RED是新组件合同缺失，不冒充旧生产链已复现或修复。
- [x] **Step4：最小实现。** 本轮标识唯一→核验原文与SHA/跨度/quote→读取当前服务端快照→资格/身份/版本/hash/text一致→仅从原文生成输出；异常受控拒绝，不加通用语义裁判或关键词证明器。
- [x] **Step5：定向与相关回归。** 显式Unit入口、无真实模型/DB；保存XML、源码哈希和资源退出。将同一4→40作为新组件反例拒绝，同时保留旧RAG仍放行的R3已知风险，不混淆两条路径。
- [x] **Step6：更新证据与职责边界。** 标`COMPONENT_VERIFIED_NOT_INTEGRATED`；RC-04-SEMANTICS仍OPEN。仅原文组件不能勾掉整个主路线图R5或关闭G2。

## Task2：审核模板与应用链（受B-01/B-02资料条件限制）

### Task2A：数据库当前证据读取准备（可独立推进，不开放学生入口）

2026-10-08依赖复核：真实审核模板依赖B-01/B-02，但现有schema的输出前读取适配可用专用临时库合成夹具验证，不必等待人工参考。

- [x] 保存新增`knowledge/src/main/java/com/zhku/knowledge/rulecheck/JdbcRuleCheckEvidenceReader.java`、`knowledge/src/test/java/com/zhku/knowledge/retrieval/JdbcRuleCheckEvidenceReaderTest.java`、同测试包`JdbcRuleCheckEvidenceReaderTargetTest.java`缺失基线，及runner当前字节。只实现CurrentEvidenceReader，不加controller或模板。
- [x] API最小壳与合同RED→GREEN；以server配置的profile/切分版本/scope及请求source/chunk参数绑定读取，要求ACTIVE与R4同口径的现有元数据资格；不允许BUILDING回退，不混用切分版本。
- [x] 真库验证读取后撤回/可见性/分享资格/scope/profile退役/run/版本/正文/hash漂移；连接中断只使核验拒绝。声明是两次顺序真实数据库读写之间的变更，不伪称并发线性化或已完成流式输出撤回。
- [x] 新DB测试在任何连接/迁移前复用严格临时目标guard，AfterEach拒绝后不连接；增加实际setup/cleanup纯单元反例。凭证每次运行重新检查，数据库写入仅随机无卷临时库。
- [x] 保存真库与相关纯单元XML/源哈希/退出证据，标`DB_ADAPTER_VERIFIED_NOT_INTEGRATED`；完整时间/清单/审核模板/真实质量仍OPEN。新读取不是新检索引擎，不复制候选排序。

- [ ] 锁定一个具有当前有效、可传播且人工已审资料的主题；明示对象、前提、结论、例外、原文位置及审核责任。
- [ ] 采用既定v2前缀，复用混合检索/引用审计，不复制学分loop；可信身份、CSRF、模式绑定及输入限长继承当前controlled合同。
- [ ] 以审核规则对象决定五状态，未知条件只有会影响结论才追问；部分事实必须独立成立，不用摘录原文暗示整体个人资格。
- [ ] 为CurrentEvidenceReader实现真库读取，用真实数据库撤回/版本/hash/权限竞态矩阵验证；不能用Task1的内存替身代替。
- [ ] 必要时加入受控模型“选片段/缺条件”适配，严格JSON合同与调用预算；实际供应商和费用数据独立验收。

卡点不解除时，不填虚构审核规则或把一般RAG自由回答接到新学生入口。可继续准备接口/验收设计和独立反例，但不宣称真实查证闭环完成。

## Task3：查证页面与真实链路（依赖Task2）

- [ ] 新查证首页、原文位置/版本、确认项/未知项与必要澄清；旧v1/学分URL保持独立兼容。
- [ ] 修RC-11冲突/已完成文案，避免“尚未确认=未受理”。
- [ ] 实际Vue→Nginx→Java→专用临时库验证正常、澄清、拒答、定位、撤回与换账号；不以源码字符串或模型替身冒充浏览器/真实模型效果。

## Task4：G2方向验证（依赖B-01/B-02与真实参与者）

- [ ] 冻结16题独立报告集、人工评分rubric及版本；记录开发/报告家族隔离和未知项。
- [ ] 对照官网/PDF，招募5–8位真实目标用户、平衡题组和顺序，报告正确完成与耗时、失败/超时，不虚构参与者。
- [ ] 按预登记预算重复关键随机路径，记录轨迹、成功向量、实际调用/用量/失败成本；没有用量就不算金额。
- [ ] 输出继续/调整/收缩/停止扩张结论；G2未通过不展开R6–R8多主题与P2设施。

## 回退与收口

Task1新组件未装配时，只涉及新增文件和显式验收白名单；回退按当前哈希逐项审核新增清单，不重写旧RAG链或删除历史证据。Task2以后先关闭受影响v2入口再恢复对应增量，旧v1不借回退改变语义。每项工程状态、真实资料/模型/数据库/浏览器/用户状态分别报告。

**本批实际：**Task1 19项定向，Task2A 15项真库+3项目标/故障合同；最终18类172项相关回归通过，合同29项。见[R5组件](../../evidence/rule-check-refactor/2026-10-07/r5-evidence-20261008-01/acceptance.md)和[DB适配](../../evidence/rule-check-refactor/2026-10-07/r5-db-20261008-01/acceptance.md)。完整Task2/3/4仍待外部审核与真实参与者；不将部分勾选解释为整个R5完成。
