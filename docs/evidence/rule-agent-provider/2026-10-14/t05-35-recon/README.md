# T05 接手侦察与方案研究（零真实调用，未外发）

本时段目标：遵守 AGENTS.md 与 docs/git-commit-workflow.md，从冻结提交建立独立工作区，基于既有失败证据完成
"观察事实—可支持归因—尚不能确认"证据表与候选方案比较，选定有界方案；对探针包、启动器、主类、配置、
环境继承与最终构建做零调用一致性核对。**本时段真实调用 0、数据库操作 0、外发费用 0；未申请任何新额度。**

## 一、工作区与身份（基线）

| 项 | 值 |
| --- | --- |
| 独立 worktree（最终落点） | `D:/alldesk/Projects/ZhkuAgent-t05` |
| 分支 | `codex/t05-online-evidence`（`codex/` 前缀，自冻结提交新建） |
| 基线 SHA | `58e1ef751e8a659178afaac04830ae3df86ad579`（= 冻结提交 = 主分支 `zhkuagent-modules` HEAD） |
| 实现身份 | 源码与 `99da32c` 一致（其后 `f91ae15`/`58e1ef7` 仅文档归档） |
| 原 T05 会话状态 | 已停止：主工作区被修改的跟踪文件仅 4 个 `docs/product/*.md`（非 ruleagent 职责）；各 codex 工作区均无 ruleagent 改动；未共用其他会话目录或暂存区，未复制未提交改动 |
| 落点说明 | 修复在 `C:/Users/nangua/.codex/worktrees/t05-online-evidence/` 临时落点完成并首验；因审核目录要求兄弟布局（见第五节缺陷 3）且 `git worktree move` 不支持跨盘，迁移至 `D:/alldesk/Projects/ZhkuAgent-t05` 后重新构建复验，再保存检查点 |

## 二、证据表：观察事实 → 可支持归因 → 尚不能确认

| # | 观察事实（证据来源） | 可支持归因 | 尚不能确认 |
| --- | --- | --- | --- |
| 1 | t05-34 首个 DECISION 请求 4557B，dispatch 14ms，20031ms 后 `MODEL_TIMEOUT`；无 HTTP 状态、无响应头、无响应体、无 requestId、无 usage；SEARCH/READ/FINISH/核验/发布均未执行（`t05-34-p01-closeout/actual-outcome-sanitized.json`） | 宿主侧行为符合约定：消耗 1 次尝试、无自动重试、剩余 4 次关闭、资源退出干净（`resourcesClosed=true`、Java 残留 0、临时容器 0） | 供应商是否收到、是否处理、是否计费；慢在连接层、供应商排队还是模型生成；**实际费用未知，不能记为 0** |
| 2 | 同端点、同模型（qwen3.8-flash）、同结构化输出协议历史上有 5 次成功决策（t05-28 两次、t05-31 三次；t05-31 的 READ 曾一次返回 16 条已核验事实）（`T05-CLOSEOUT.md` 第四/十节） | 端点可达性、鉴权、模型存在性、`json_schema` 严格模式、`oneOf` 四分支在真实环境可用（这些环节不因 t05-34 重新变为未知） | t05-34 首请求的新形状（单分支 `oneOf` + `executionState`）是否触发供应商慢路径——这是与全部已成功批次唯一的请求级差异之一，但无直接证据 |
| 3 | t05-28 独立核验请求 7541B 在 10020ms（当时单次上限）无响应头超时（`t05-33-local/README.md` 校正第 1 条） | 历史上共两次"无响应头"超时，分别撞满当时单次上限（10s / 20s）；两次的载荷大小与协议都不同（7541B `json_object` / 4557B `json_schema`） | 是否存在与载荷或协议相关的系统性慢路径；是否只是"差一点"（放宽上限即可过）——无任何响应时序证据，两种解释均无法排除；不能据单样本估计成功率 |
| 4 | 请求 4557B 远小于 65536B 上限；dispatch 标记 14ms | 请求字节数与派发速度不是超时原因（本地可复核：载荷构造有界检查） | TCP/TLS 完成时刻未测量（`headersMillis=null`），不能排除连接层挂起；但连接失败通常快速报错而非悬挂满 20s，且本机到北京端点既往成功 |
| 5 | 本地 7 类 150 项测试通过（t05-33 r2-final），fake 供应商覆盖超时/坏 JSON/401/429/5xx/断连/未知动作/预算用尽/取消与迟到（`RuleAgentProviderTest` 等 8 个测试类） | 宿主侧协议、状态约束、预算、独立核验、发布门禁、故障分类与资源退出已本地验证 | **不证明任何在线行为**；本地通过不等于真实链路通过 |
| 6 | t05-31 第三次 READ 先被证据容量守卫拒绝，随后决策次数耗尽 `BUDGET_EXCEEDED`（`T05-CLOSEOUT.md` 第十一节校正） | 在"能取得响应"的前提下，模型步数选择与调用次数预算的耦合问题真实存在（每 run 4 决策 + 1 核验，最小可用流程恰好 4 步，零容忍） | t05-34 未到达该点，未取得新证据；5 次席位是否消除零容忍未经在线观察 |

## 三、候选方案比较（不默认加超时/加额度/换模型/换框架）

| 候选 | 针对失败机制 | 影响源码/合同 | 如何验证 | 如何恢复 | 证据支持 | 授权需求 |
| --- | --- | --- | --- | --- | --- | --- |
| A. 提高超时与整 run 预算（如单次 20s→30s、run 110s→130s） | 若超时是"刚好差一点" | `RuleAgentContract.BUDGET_REVISION`+`DEFAULT_BUDGET`+全部硬编码字面量（测试、批次号）+新请求包 | 只能靠真实批次；零调用无法证明 | 回滚常量并重新生成包 | 弱：两次超时均撞满当时上限，存在"差一点"可能，但无响应时序证据；且只放宽单次会被整 run 剩余截断，须同时放宽两者 | 预算变更须用户明确授权，**不默认采用** |
| B. 诊断优先（零外发）：B1 连接层计时；B2 本地重建首请求载荷 | 把"无响应不能归因"这个核心未知变为已知 | `RuleAgentProvider` 的 `Measurement`/`Diagnostics`/`Trace` 增字段 + 测试；不动任何合同 | 本地 fake 供应商（静默/拒连/延迟响应/坏响应）零调用全验证 | 反向提交 | 直接针对未知本身，是任何真实尝试前的必要投资 | 无（纯本地） |
| C. 首决策 schema 形状回退（四分支 `oneOf`，宿主侧保留 `allowedActions` 校验） | 若单分支 `oneOf`+`strict` 触发供应商慢路径/编译缓存未命中 | `RuleAgentProvider.payload` 的 schema 过滤 + 提示词 + 新请求包 | 本地零调用验证 schema 与宿主校验不回退；在线效果需真实批次 | 反向提交 | 中：唯一请求级差异，但无直接证据；代价是模型自由度上升、t05-33 状态约束设计意图减弱 | 改变候选设计意图，须用户知情并写入申请 |
| D. 提示词层收敛指令（一次读取已覆盖需求即 FINISH） | t05-31 的预算耗尽 | 提示词 + 新包 | 在线效果不确定 | 反向提交 | 中（针对旧缺口，不影响首请求超时）；t05-33 已加状态约束提示 | 实现改进，可在申请中一并说明 |
| E. 换模型/换供应商/换框架 | — | — | — | — | 无证据支持 | 须重新授权，不列为候选 |
| F. 接受能力边界声明（修改验收标准） | — | — | — | — | 属产品决策 | 仅用户可决定 |

## 四、选定的有界方案（下一时段执行，本时段不动 ruleagent 源码）

**诊断优先 + 最小修复**，顺序：

1. **B2 先行（零调用）**：用 `LOCAL_TEST_ONLY` 端点本地重建 t05-34 首决策请求的 View 与载荷，核对 4557B 与 schema
   形状（单分支 `oneOf`、`executionState` 投影、`candidateSetId=null`、无空 `enum`、`currentCandidates` 为空）。
   若发现本客户端畸形，先建失败检查再做最小修复。
2. **B1（零调用）**：为 `RuleAgentProvider` 增加连接层计时（区分"未连接"与"已连接无响应头"），并先建失败检查：
   fake 供应商"接受连接后静默"时诊断必须诚实报告已连接/无响应头、原因码 `MODEL_TIMEOUT`、消耗尝试、无重试；
   "拒绝连接"时为 `MODEL_CONNECTION_FAILURE`。
3. **回归**：ruleagent 全量本地测试保持绿（当前基线 150 项），零调用核对 `--CheckPackage`/`--CheckEnvironment` 通过。
4. **备选须用户决定**：C（schema 形状）与 A（预算）不在未授权下实施；D 可并入但单独说明。
5. **外发链**：本地候选完成并零调用核对后，生成新请求包（新批次号），提交外发申请（源码 SHA、模型与目的地、
   问题、动态载荷边界、调用次数与费用上限、超时、停止条件、资料资格、包指纹），**获得新的明确批准后才执行
   一次有界真实批次**；失败即停止本批、关闭剩余额度、不自动重试、不补调用。

## 五、零调用一致性核对结果（本时段实证，含三处已修复缺陷）

- **构建**：最终位置 `D:/alldesk/Projects/ZhkuAgent-t05` 内
  `./mvnw.cmd -o -pl knowledge -am -DskipTests package` → BUILD SUCCESS，
  `knowledge-0.1.0-rc.1-controlled.jar` 产出。
- **请求包比对**：`t05-33-local/request-package.json` 指纹 `8a3a4548…731a` 与 t05-34 授权一致；
  `protocolRevision`/`budgetRevision`/`model`/`endpoint`/预算数值/`remainingBudgetMaximum`/`cases`/
  `authorizationStatus` 与当前源码常量全部吻合；`SYSTEM`/`REVIEW_SYSTEM` 与源码文本块逐字一致
  （比对须按 Java 文本块规则去缩进；直接提取源码文本会因 CRLF 与缩进产生假性差异）。
- **启动器/主类/配置/环境继承**：`start-rule-agent-probe.ps1` 指向 t05-33-local 包、主类
  `RuleAgentOnlineProbeHost`、批次 `P01_CLOSEOUT_07`、拒绝原库端口 54329、显式删除回放键；
  `--CheckPackage` 不导入真实凭证、不建库、不构造模型客户端；t05-34 preflight 记录
  java/docker/pwsh7/数据库密码/DashScope 密钥可用、17 个源码无漂移、jar 身份一致。
- **缺陷 1（行尾转换破坏字节指纹，已修复）**：全新 Windows worktree（`core.autocrlf=true`）中
  `--CheckPackage` 失败于 `Reviewed2025ControlledCatalogue` 构造（"B03输入指纹变化"），且包内
  `factCapabilityAuthoritySha256` 不符。根因：`knowledge/data/curation/**` 与 `knowledge/src/**` 下参与
  字节哈希验收的资产被检出时 LF→CRLF 转换，而 `.gitattributes` 只保护 `docs/**`。
  修复：`.gitattributes` 增加 `knowledge/data/** -text` 与 `knowledge/src/** -text`，并重新检出恢复原始字节。
- **缺陷 2（已审核字节被提交规范化，已修复）**：进一步定位发现三个 B03 审核输入知识片段的**记录指纹**
  与 git blob 不一致——其中两个的指纹是 CRLF 字节的哈希、一个是 LF 字节的哈希，而三个 blob 都是 LF
  （提交时被 autocrlf clean 过滤静默规范化）。即"同一提交两种字节"：主工作区磁盘字节能过、任何全新
  检出在任何统一行尾策略下都不可能同时满足三个指纹。修复：把这两个文件的**已审核原始字节（CRLF）**
  提交进 git（`-text` 属性下 blob 即精确字节）；语义内容零变化，仅行尾还原为被审核时的字节。
  `git diff --ignore-cr-at-eol` 为空可证明无语义改动。
- **缺陷 3（外部 PDF 原件依赖兄弟布局，已通过落点决策解决）**：审核目录还要求
  `root.resolveSibling("SuperAgent-zhkuagent-mvp")/data/raw/2025版学生手册2025.9.2.pdf`（外部资产，不入库）。
  该布局只在 `D:/alldesk/Projects/` 成立；`.codex/worktrees/` 位置没有兄弟目录，探针会在**任何模型调用之前**
  失败关闭。决定：T05 worktree 最终落点 `D:/alldesk/Projects/ZhkuAgent-t05`（与主线/lineA/lineB/mvp 同层），
  不再需要 junction 之类的环境补丁。
- **最终零调用验证（最终位置 `D:/alldesk/Projects/ZhkuAgent-t05`，兄弟 PDF 布局自然成立，无 junction）**：

```
{"passed":true,"maximumProviderCalls":5,"protocolRevision":"RULE_AGENT_ACTION_CONTRACT_05","packageSha256":"8a3a4548fc72814d76f732d39692ccc9bd41b87a48502caddce248d7e866731a","databaseOperations":0,"providerCalls":0,"mode":"PACKAGE_CHECK_ONLY"}
```

## 六、边界与停止条件

- 本时段零真实调用；旧授权（包 `8a3a4548`、5 次、5 元上限）已随 t05-34 关闭，**不可沿用**。
- 不修改：审核原文（B03 批准 JSON 及其指纹、人工参考）、旧失败记录、`T05-CLOSEOUT.md` 既有结论；
  不进入 T08；不修改 T06/T07 及验收宿主文件；不以修改 HTTP 或页面掩盖模型/执行问题。
- 对主工作区的瞬态影响：`.gitattributes` 与两个 CRLF blob 合入前，主工作区（旧属性 + autocrlf）会把这两个
  知识片段显示为已修改；合入后（新属性、字节精确比较）自动恢复干净。合入前不要在该两个文件上执行
  `git checkout --` 或 `git add`（旧属性下会把字节再次规范化回 LF）。
- 下一时段停止条件：诊断改动与本地回归完成即停并保存本地检查点；真实外发仅在取得新的明确批准后执行
  一次有界批次；未获批准时交付本地候选，保持"真实在线待验证"。
- 状态不变：**P01 未通过（首请求 20.031s 无响应），P02 未执行，T05 未完成。**
