# T08 交接说明（供其他 Agent / 集成方消费）

> 生成时间 2026-10-16。汇总 T08 验收准备已做与未做。**全部为本地提交，未 push、未 PR、未标签、未部署、未合并。**
> 本线全程：真实供应商调用 0、数据库操作 0、Docker 操作 0；只用明确标记的合成记录测试工具。

## 0. 一句话状态

**T08 准备工具／方案局部通过，正式 T08 未执行。** 验收矩阵、评分规则、证据格式、启动/停止协议、检索方案证据、开发案例、反例模板与校验工具（22/22 合成测试）已就绪；正式验收被 S1–S7 启动条件阻塞，其中 S1 的口径已按 2026-10-16 T05 权威收口重校（见 §3）。

## 1. 分支与提交（限定集成范围）

| 项 | 值 |
| --- | --- |
| 分支 | `codex/t08-acceptance-prep` |
| worktree | `C:/Users/nangua/.codex/worktrees/t08-acceptance-prep/ZhkuAgent` |
| 基线 | `88e57d68799d3a5996bb84346090a84040a88387`（T06/T07 reviewed-tools 同版本模拟记录） |
| 本轮起点 | `9904184f915ea01f8de86b8fdc0b4cbdf6d525a7`（第二轮） |
| 累计提交 | 3 个：`939241b`（第一轮）→ `9904184f`（第二轮）→ 第三轮（本交接，见 git log） |

**改动只在这些路径**：

```
docs/preparation/t08/                                    （7 份准备材料 + README）
tools/t08-acceptance/validate-evidence.mjs               （校验工具）
tools/t08-acceptance/test/validate-evidence.test.mjs     （22 项合成测试）
tools/t08-acceptance/test/fixtures/*.json                （19 个夹具，全部 synthetic:true）
tools/t08-acceptance/README.md
docs/evidence/t08-preparation/2026-10-14/                （第一、二轮证据回执）
docs/evidence/t08-preparation/2026-10-16/                （第三轮：交接说明、复核日志、夹具扫描）
```

**未触碰**：生产源码、共享合同/样例、模型提示、审核材料、既有测试、其他 Agent 文件（全程只读）。
合同未修改；所有合同增量只是"现有定义→具体问题→建议增量→影响模块→对应测试"形式的建议，**待用户协调**。

## 2. 已交付内容（三层，按可验证程度排列）

### 2.1 准备材料（`docs/preparation/t08/`，纯文档）

| 文件 | 内容 | 消费方注意 |
| --- | --- | --- |
| acceptance-matrix.md | 四组分离：G1 旧回放回归 / G2 已曝光开发问题 / G3 新报告集 / G4 异常注入；A–H 八类问题覆盖（直接/改写、澄清/跨轮、资料缺失、部分答案、对象时间范围、否定/阈值、故障注入、权限/幂等/取消/迟到响应）；5 条混用禁令 | 四组统计永不混合；G3 建议 24 题（6 类各 4）只是**建议量**，候选冻结后才独立形成 |
| scoring-rules.md | 三种"完成"分离（run 完成 / 任务成功 / 产品目标）；五类结果（SUCCESS/TASK_FAILURE/REFUSAL/UNKNOWN/INDETERMINATE）；八维度 D1–D8；6 条关键错误（0 容忍） | 关键错误含"把可答题拒答记为成功＝评分舞弊"；未知记 UNKNOWN 不得记 0 |
| evidence-record-format.md | T08_SAMPLE/T08_BATCH JSON 规范；**未知值保留 null 严禁转 0**；合同增量建议表（7 项待协调） | 增量未实现前，工具按建议口径校验，正式记录以用户裁决为准 |
| acceptance-protocol.md | 启动条件 S1–S8、停止条件 T1–T6、运行前/中/后清单、依赖清单 | §1 状态基线已于 2026-10-16 重校 |
| retrieval-scheme.md | **当前链路实际检索路径**的源码级证据：中文二元组词法检索（`JdbcLexicalCandidateRetriever.forChineseBigramEvaluation`）＋三层资格过滤＋读取事实绑定；仓库存在向量/混合能力（`JdbcVectorCandidateRetriever`/`HybridRetrievalService` RRF）但**当前链路未接线**（新路径向量实现为空 `List.of()`，受控目录 `embedding=NULL`，profile `LEXICAL_ONLY_NO_EMBEDDINGS`） | "仓库有能力"≠"链路在使用"；引用时必须用这份区分，不得声称当前链路有向量检索/重排 |
| development-case.md | S10（第18条重修/缓考）案例：条款结构、courseType 条件链（USER_DECLARED）、专业选修缓考缺证、3 条禁止结论、3 分支模板；T05 P01 实际输出（失败样本）；**"20条"口径＝20 条已批准事实**（非条款数/非题目数；10 片段＋10 规则＋20 事实＋16 目录模板＋第4条叠加 2 事实） | 数量≠可答题数；本案例只证明复杂度与证据边界，不是成功案例 |
| scoring-counterexamples.md | CE1–CE5 反例（引用身份对但事实映射错 / 条件遗漏 / 问题未覆盖 / 范围越界 / 误拒绝）；确定性字段校验与人工/模型语义判断分工；漏拦截登记（E-SEM-006）与误拒绝登记（E-SEM-007）规则 | 无保证性措辞；语义判定必须留痕 |

### 2.2 校验工具（`tools/t08-acceptance/`）

- `validate-evidence.mjs`：单样本/批次/文档三种模式；发现代码分 ERROR（E-ID/E-VER/E-MODE/E-TRJ/E-DUP/E-OMIT/E-UNK/E-SCR/E-SEM/E-BUD/E-SYN/E-GRP/E-BAT/E-FILE/E-DOC）、WARN（W-*）、INFO（I-*）。
- 检查：身份缺失、版本混用、模拟冒充在线（回放答案/无供应商响应/尝试计数缺失）、轨迹缺失、重复计数、失败样本被静默剔除、未知转 0、关键错误记成功、合成记录未标记、跨组混批、语义反例五项、拦截/误拒绝登记。
- CLI：`node tools/t08-acceptance/validate-evidence.mjs <file> [--batch --expect-group G3 --expect-source-sha <sha> ...]`；ERROR 清零方可归档。
- **测试：22/22 通过**（第三轮 2026-10-16 复核，日志 `synthetic-test-run-round3.log`）；19 个夹具全量扫描 `validator-fixture-scan-round3.txt`：3 个合法夹具 0 ERROR/0 WARN，16 个问题夹具各自只触发目标代码。
- 夹具全部 `"synthetic": true`，工具从未对真实证据记录运行过——**这是工具成熟度的已知边界**。

### 2.3 证据回执（`docs/evidence/t08-preparation/`）

- `2026-10-14/`：第一轮交付表、身份记录、两轮测试日志与夹具扫描。
- `2026-10-16/`：第三轮身份记录、复核日志、夹具扫描、本交接说明。

## 3. 关键状态更新（2026-10-16 只读核对，改变 S1 口径）

| 线 | 当前权威状态 | 依据 |
| --- | --- | --- |
| T05 | **工程验收通过并收口**（`T05_ENGINEERING_ACCEPTED`，`1c270c4`，2026-10-16）：按 2026-10-13 既定退出标准九项核对；t05-69 同 task 两 run 整链 8 次 HTTP 200（含 ASK 澄清续答）；候选源码 `bc5cdf4`、jar `73500a44`。**但**：当前构建未重跑固定 P01（t05-44 历史通过单独归档，不得当作当前构建复验）；**T06 当时尚未开始**；真实外发仍须新包完整披露＋明确授权 | `codex/t05-online-evidence` @ `1c270c4`（只读） |
| T06 | 工程局部通过；受控启动入口/装配兼容/专用临时库资料安装/真实身份仓储已验证；**SIMULATED 模式真实身份/资料 HTTP 整链未做**（其指定的唯一下一项） | `codex/t06-controlled-launch` @ `9eb9015`（只读） |
| T07 | 学生入口证据索引（`de96fa4`）；浏览器部分只覆盖路由与入口（S-B 身份），非整链 | `codex/t07-student-entry`（只读） |
| 主仓库 | `zhkuagent-modules` @ `ec1e362`；T05/T06/T08 分支均未合入 | 只读核对 |

对 T08 的含义（已写入 acceptance-protocol.md §1/§2）：

- **S1 从"未满足"重校为"部分满足"**：T05 工程关口已关闭，但只对应候选源码 `bc5cdf4`——T08 候选必须与该 SHA 一致才可沿用；若候选变更须重新工程验收。历史 P01 通过（t05-44）**不得**引述为当前构建的在线能力证明。
- T05 收口明确把"未预置能力与可靠性评估"划归 **T08**——这正是正式 T08 的职责，不因工程关口关闭而减免。
- S2 仍**未满足**（T06 整链未做、T07 只到路由/入口）。
- 任何真实外发仍须**新授权**（S5），T05 工程通过不自动带额度。

## 4. 未做清单（正式 T08 的剩余工作）

| # | 事项 | 阻塞/前置 | 状态 |
| --- | --- | --- | --- |
| 1 | S2：T06/T07 同版本整链通过（真实 HTTP、真实资料安装、身份/启动器、浏览器全过程） | T06 线推进 SIMULATED 整链（其唯一下一项） | 未满足 |
| 2 | S3：候选冻结（源码 SHA、合同/schemaVersion、目录版本、事实增量、提示词、模型、预算标识写入 manifest 并只读化） | 用户确认候选 SHA（应与 `bc5cdf4` 一致或重新工程验收） | 未开始 |
| 3 | S4：G3 新报告题候选冻结后独立形成（建议 24 题），运行前核对参考答案并封存 | S3 | 未开始（不得提前编写/曝光） |
| 4 | S5：新的调用授权（接口/模型/问题/资料/载荷/次数/包 SHA 逐项确认，费用上限明确） | 用户显式批准 | 无 |
| 5 | S6：评分规则与评分员锁定 | 用户指定评分员 | 待定 |
| 6 | S7：环境就绪（专用临时库、端口/容器/进程边界、无共用目录） | 执行前准备 | 未满足 |
| 7 | 合同增量（7 项：usage/费用/延迟/评分/批次分组/observedFacts/coverageMap）协调落地 | 用户协调，生产合同只读 | 待协调 |
| 8 | 校验工具对**真实**证据记录的首跑验证 | 正式验收第一批样本产生后 | 未验证（现仅合成夹具） |
| 9 | 检索召回/排序质量测量 | 只能在正式 G3 在线批次测 | 未测（当前链路无向量/重排，见 retrieval-scheme.md） |

## 5. 给其他 Agent 的使用规则

**可以**：
- 消费 `docs/preparation/t08/` 全部材料作为 T08 执行方案与评分依据；
- 使用/扩展 `tools/t08-acceptance/` 校验自己线的证据记录（用 `--expect-source-sha` 等参数绑定身份）；
- 引用 retrieval-scheme.md 的"仓库能力 vs 实际使用"区分描述当前检索链路。

**不得**：
- 把本线任何材料当作 T08 通过、在线能力通过或产品目标达成的证据；
- 在 S1–S5 满足前以 T08 名义执行真实运行或外发；
- 引用 t05-44 历史 P01 作为当前构建复验；引用 T05 工程通过带额度；
- 把工具通过写成 Agent 准确率（工具通过 ≠ Agent 准确率 ≠ T08 通过）；
- 未经理用户协调合并本分支或修改共享合同/样例。

**接手配方**（本机）：
```
worktree：C:/Users/nangua/.codex/worktrees/t08-acceptance-prep/ZhkuAgent
分支：codex/t08-acceptance-prep
跑测试：node --test tools/t08-acceptance/test/validate-evidence.test.mjs
校验：  node tools/t08-acceptance/validate-evidence.mjs <记录.json> [--batch --expect-group G3 ...]
```
node 用 managed 22.22.2（`C:\Users\nangua\.workbuddy\binaries\node\versions\22.22.2-6\node.exe`）。

## 6. 结论边界（唯一合法表述）

> **T08 准备工具／方案局部通过，正式 T08 未执行。**

不得据此宣布 T08 通过、在线能力通过、检索质量达标或产品目标达成；不得使用保证性措辞描述未执行评测的能力。
