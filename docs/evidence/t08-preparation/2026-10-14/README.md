# T08 验收准备：本轮记录（2026-10-14）

**结论：T08 准备工具／方案局部通过，正式 T08 未执行。** 本轮未调用真实供应商、未运行数据库、未编写或曝光新报告题、未改变 T05/T06/T07 状态。

## 身份

| 项 | 值 |
| --- | --- |
| worktree | `C:/Users/nangua/.codex/worktrees/t08-acceptance-prep/ZhkuAgent` |
| 分支 | `codex/t08-acceptance-prep`（自基线新建，未与任何其他 Agent 目录/暂存区共用） |
| 基线 | `88e57d68799d3a5996bb84346090a84040a88387`（T06/T07 reviewed-tools 同版本模拟记录提交） |
| 起点工作区 | 清洁（0 项未提交），未复制任何未提交改动 |
| 只读引用 | T05 冻结提交 `58e1ef751e8a659178afaac04830ae3df86ad579`（`git show` 只读获取收口报告） |
| 供应商调用 / 数据库操作 | 0 / 0 |

## 实际改动（仅限三个拟新增目录）

- `docs/preparation/t08/`：README、acceptance-matrix、scoring-rules、evidence-record-format、acceptance-protocol。
- `tools/t08-acceptance/`：validate-evidence.mjs、test/validate-evidence.test.mjs、test/fixtures/（12 个显式标记 synthetic 的夹具）、README。
- `docs/evidence/t08-preparation/2026-10-14/`：本目录（identity、日志、交付表）。

生产源码、共享合同/样例、模型提示、审核材料、既有测试与其他 Agent 文件均只读，未修改。

## 验证证据

| 验证 | 结果 | 证据 |
| --- | --- | --- |
| 合成测试 `node --test "tools/t08-acceptance/test/*.test.mjs"` | **15/15 通过，0 失败 0 跳过**（Node 22.22.2） | `synthetic-test-run.log` |
| 合法夹具不误报 | valid-batch / valid-online-sample：**ERROR 0、WARN 0**，退出码 0 | `validator-fixture-scan.txt` |
| 反例夹具必报 | 10 个反例共 **19 ERROR + 3 WARN**，覆盖 E-ID/E-VER/E-MODE/E-TRJ/E-DUP/E-OMIT/E-UNK/E-SCR/E-SYN/E-GRP 全部设计码 | `validator-fixture-scan.txt` |
| 证据文档交叉引用 | 计划/合同/收口/README 引用路径均存在于基线或只读提交 | 本轮 Read/git show 记录 |

## 未验证范围

- 正式 T08 未执行：G1–G4 均无真实样本；新报告题未形成。
- 校验工具未在真实证据记录上运行（按约定只用合成夹具）。
- T05 真实在线、T06/T07 同版本整链未通过，本材料不改变其状态。
- 合同增量（usage/cost/latency/scoring/batch 分组）仅为建议，未实施、未修改合同。
- 工具未做跨平台（非 Windows）运行验证。

## 恢复方式

本目录与 `docs/preparation/t08/`、`tools/t08-acceptance/` 均为新增文件；如需回退，删除这三个目录即可，不影响任何既有文件。

## 下一项

等待用户对 T05 缺口决策与新调用授权；S1–S5 启动条件满足前不执行正式 T08。下一可推进项由用户在 acceptance-protocol.md 第 5 节依赖清单中选择。

---

# 第二轮（20:59–21:26）：检索方案证据、业务复杂度案例、核验与评分反例

**结论不变：T08 准备工具／方案局部通过，正式 T08 未执行。** 第二轮同样 0 供应商调用、0 数据库操作；检索核对为只读源码调查。

## 实际改动（仍在三个归属目录内）

- `docs/preparation/t08/retrieval-scheme.md`：当前链路检索路径/单元/资格过滤/候选预算/读取绑定的源码级核对；仓库向量/混合组件“存在但未接入”的区分；取舍说明。
- `docs/preparation/t08/development-case.md`：S10（第18条）重修/缓考开发案例；条件链、易漏范围、“相似段落不足”的五点理由；T05 P01 失败样本的如实记录；“20条”口径核对（10 chunk / 10 规则 / 20 事实 / 16 模板 + Article4 2 事实 = 17 模板 22 事实；“20条”=审核事实数，≠可答题数）。
- `docs/preparation/t08/scoring-counterexamples.md`：CE1–CE5 反例类别、确定性校验与语义判断分工、漏拦截/误拒绝登记规则。
- `tools/t08-acceptance/validate-evidence.mjs`：新增 E-SEM-001/002/003/006/007、W-SEM-001/003/004/005/008 语义检查；`valid-online-sample.json` 升级为含语义快照的 canonical 形状；新增 7 个语义夹具。
- 同步更新：tools README 检查码表、evidence-record-format.md（新增字段与合同增量行）、preparation README 文件清单、scoring-rules.md 第 7 节指引。

## 验证证据（第二轮）

| 验证 | 结果 | 证据 |
| --- | --- | --- |
| `node --test` | **22/22 通过，0 失败 0 跳过** | `synthetic-test-run-round2.log` |
| 语义夹具隔离性 | 6 个反例各只触发目标码（CE1→E-SEM-001、CE2→E-SEM-002、CE3→E-SEM-003、CE4→W-SEM-004、CE5→W-SEM-005、未登记→E-SEM-006） | `validator-fixture-scan-round2.txt` |
| 合法夹具不误报 | valid-online-sample / valid-batch / valid-semantic-sample：ERROR 0、WARN 0，退出码 0 | 同上 |
| 检索链路核对 | 19 个夹具全量扫描 22 条 ERROR 覆盖两轮全部设计码；源码行级依据见 retrieval-scheme.md | 同上 |
| “20条”口径 | 实测审核 JSON：10 chunks / 10 rules / 20 facts / 16 templates；C05 草案 16 条目；Article4 +2 事实 | development-case.md 第 7 节 |

## 未验证范围（第二轮）

- 检索召回/排序质量未测（词法在真实问题上的表现属正式 T08 G3）；
- 案例的“预期行为”未在真实链路执行（T05 P01 未到达 FINISH/发布）；
- CE1–CE5 在真实样本上的发生率与漏拦截率未测（需正式 T08 真实记录）；
- 语义夹具只验证工具逻辑，不构成任何真实证据。
