# T08 证据校验工具（validate-evidence）

只读检查 T08 验收证据记录（`T08_SAMPLE` / `T08_BATCH`，格式见
[`docs/preparation/t08/evidence-record-format.md`](../../docs/preparation/t08/evidence-record-format.md)）。
**不修改、不回填任何证据文件**；唯一输出是 finding 清单与退出码。

## 运行环境

- Node.js（本机开发用 22.22.2；无需任何依赖，纯标准库）。
- Windows / PowerShell 或 Git Bash 均可。

## 用法

```bash
# 单样本或批次（按 recordType 自动识别）
node tools/t08-acceptance/validate-evidence.mjs docs/evidence/t08-acceptance/<候选>/<组>/<批次>/batch.json

# 批次 + 期望参数（推荐：归档前固定候选身份）
node tools/t08-acceptance/validate-evidence.mjs <batch.json> --batch \
  --expect-group G3 \
  --expect-source-sha <候选源码SHA> \
  --expect-contract-version rule-agent-contract-v3 \
  --expect-budget-revision T05_REVIEW_BUDGET_03 \
  --max-attempts 4

# 机器可读输出 / 严格模式（WARN 也不通过）
node tools/t08-acceptance/validate-evidence.mjs <file> --json
node tools/t08-acceptance/validate-evidence.mjs <file> --strict
```

退出码：`0` 通过；`1` 存在 ERROR（`--strict` 时含 WARN）；`2` 用法或文件错误。
ERROR 必须清零才能归档；WARN 必须逐条人工确认并记录理由。

## 检查项与 finding 码

| 检查 | 码 | 级别 |
| --- | --- | --- |
| 身份缺失（候选/SHA/合同/目录/预算） | E-ID-001、E-ID-005 | ERROR |
| 组/类别/模式/问题标识非法 | E-ID-002/003/004/006/007 | ERROR |
| 版本混用（批次内多身份、批次头不符、预期不符） | E-VER-001/002/003/006/007/008 | ERROR |
| 模拟冒充在线（无响应称在线、回放称在线、simulated 称 ONLINE） | E-MODE-001/002/003/004/005/006 | ERROR |
| 缺失轨迹（无动作/无观察/无结果/主张无依据关系/核验缺 runId） | E-TRJ-001/002/003/004/005 | ERROR |
| 重复计数（同 run 双计、同键异载荷、声明数不符） | E-DUP-001/003/004 | ERROR |
| 失败样本遗漏（期望清单缺失、汇总不符、失败无归类、剔除无理由） | E-OMIT-001/002/003/004/005/006/007 | ERROR |
| 未知记为 0（响应后 usage 全 0、未响应却记用量） | E-UNK-001/004 | ERROR |
| 评分舞弊（关键错误记成功、outcome 非法、维度越界） | E-SCR-001/002/003 | ERROR |
| 事实映射错误（factId 不在本次观察集合） | E-SEM-001 | ERROR |
| 条件遗漏（主张丢失事实声明的 when 条件键） | E-SEM-002 | ERROR |
| 问题未覆盖（答案要点未显式登记覆盖映射） | E-SEM-003 | ERROR |
| 反例未登记拦截方式 / 工具误判缺理由 | E-SEM-006/007 | ERROR |
| 预算超限（尝试次数超上限） | E-BUD-001 | ERROR |
| 合成记录未声明 / 批次含合成 | E-SYN-001 | ERROR |
| 组别语义（G3 必须在线、G1 不得在线） | E-GRP-001/002/005 | ERROR |
| 语义核对转人工（缺 observedFacts / coverageMap / 禁止结论未核对 / 零召回拒答） | W-SEM-001/003/004/005、W-SCR-004 | WARN |
| 需人工确认项 | W-* | WARN |
| 提示（合成记录、回放指纹建议等） | I-* | INFO |

反例类别（CE1–CE5）与评分规则见 [docs/preparation/t08/scoring-counterexamples.md](../../docs/preparation/t08/scoring-counterexamples.md)。

## 测试（只用显式标记的合成记录）

```bash
node --test "tools/t08-acceptance/test/*.test.mjs"
```

- 15 项测试，夹具全部位于 `test/fixtures/` 且带 `"synthetic": true`；
  覆盖合法形状不误报、上述每类反例必报、CLI 退出码与 `--json` 输出。
- 夹具是**工具测试数据**，不是真实证据；正式证据不得带 `synthetic` 标记，
  含合成记录的批次必须声明 `containsSynthetic: true`，否则 E-SYN-001。
- 本机若禁止 spawn 子进程，CLI 测试以进程内调用 `runCli` 方式覆盖（见测试文件注释）。

## 边界

- 本工具只检查记录的结构与声明一致性，**不证明语义正确**（事实、相关性、范围仍由人工评分，见 scoring-rules.md）。
- 本工具不执行任何供应商、数据库或网络操作。
- 正式 T08 未启动前，本工具只以上述合成夹具验证；不得用本工具"生成"证据。
