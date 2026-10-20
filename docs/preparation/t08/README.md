# T08 验收准备材料（preparation）

**性质：正式 T08 验收前的方案与工具准备。本轮不执行 T08 正式验收、不调用真实供应商、不运行数据库、不编写或曝光新报告题。**

基线身份：分支 `codex/t08-acceptance-prep`，起点 `88e57d68799d3a5996bb84346090a84040a88387`（T06/T07 reviewed-tools 同版本模拟记录提交）。T05 冻结线 `58e1ef7` 与其后权威收口 `1c270c4` 均通过 Git 只读引用，未复制、未修改。状态基线已于 2026-10-16 按各线最新权威记录重校（见 [acceptance-protocol.md](acceptance-protocol.md) §1 与交接说明）。

## 文件清单

| 文件 | 内容 | 对应本轮交付 |
| --- | --- | --- |
| [acceptance-matrix.md](acceptance-matrix.md) | 四组分离（G1 旧回放回归 / G2 已曝光开发问题 / G3 新报告集 / G4 异常注入）+ A–H 类别覆盖 + 混用禁令 | 交付 1、2 |
| [scoring-rules.md](scoring-rules.md) | 三种完成分离、五类结果、八维度评分、关键错误（0 容忍）、通过判据 | 交付 3 |
| [evidence-record-format.md](evidence-record-format.md) | T08_SAMPLE / T08_BATCH 字段规范、未知值规则、合同增量建议（待用户协调） | 交付 4 |
| [acceptance-protocol.md](acceptance-protocol.md) | 启动条件 S1–S8、停止条件 T1–T6、操作清单、依赖清单 | 交付 6 |
| [retrieval-scheme.md](retrieval-scheme.md) | 第二轮：当前链路实际检索路径/单元/资格过滤/候选预算/读取绑定；仓库向量能力 vs 实际使用 | 第二轮交付 1 |
| [development-case.md](development-case.md) | 第二轮：S10 重修/缓考开发案例（条款、条件链、易漏范围、相似段落不足）；“20条”口径核对 | 第二轮交付 2 |
| [scoring-counterexamples.md](scoring-counterexamples.md) | 第二轮：CE1–CE5 反例类别、确定性校验与语义判断分工、漏拦截/误拒绝登记规则 | 第二轮交付 3 |

配套工具：[tools/t08-acceptance/](../../../tools/t08-acceptance/README.md)（证据校验工具 + 合成测试，交付 5）。

交接说明：[docs/evidence/t08-preparation/2026-10-16/T08-HANDOFF.md](../../evidence/t08-preparation/2026-10-16/T08-HANDOFF.md)（2026-10-16 第三轮：已做/未做汇总，供其他 Agent 消费）。

## 状态边界（不因本材料改变）

- T05：**工程验收通过并收口**（`1c270c4`，2026-10-16，按 2026-10-13 原始标准；候选源码 `bc5cdf4`）；当前构建未重跑固定 P01，历史 t05-44 通过单独归档；未预置质量/可靠性评估归 T08；真实外发仍须新授权。
- T06/T07：**工程局部**——T06 SIMULATED 模式真实身份/资料 HTTP 整链未做（`9eb9015` 交接说明）；T07 浏览器证据只覆盖路由与入口（`de96fa4`）。
- T08：候选未冻结、新报告题未形成、无新调用授权。

## 结论边界

本轮唯一合法结论：**T08 准备工具／方案局部通过，正式 T08 未执行。**
