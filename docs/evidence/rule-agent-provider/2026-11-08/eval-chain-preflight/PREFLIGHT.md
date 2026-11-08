# 评测链路真实执行：零调用预检记录（2026-11-08）

- 批次目录：`docs/evidence/rule-agent-provider/2026-11-08/eval-chain-preflight/`
- 性质：**零调用预检（PACKAGE_CHECK_ONLY）**。没有执行 `--Probe`，没有外发模型请求，没有创建数据库或容器。
- 目的：在 2026-10-16 冻结包被后续提示词/协议变更淘汰后，用当前构建重新生成可授权包，并证明包身份与当前源码一致。

## 1. 为什么旧包不能用

零调用 `--CheckPackage`（历史包 + 当前重建 jar）失败：

```text
java.lang.IllegalArgumentException: 请求包预算、问题或协议身份变化
    at com.zhku.knowledge.ruleagent.RuleAgentOnlineProbeHost.validatePackage(RuleAgentOnlineProbeHost.java:51)
    at com.zhku.knowledge.ruleagent.RuleAgentOnlineProbeHost.main(RuleAgentOnlineProbeHost.java:69)
```

版本号（`RULE_AGENT_ACTION_CONTRACT_06`、`RULE_PUBLICATION_REVIEW_02`、`T05_CLOSEOUT_BUDGET_04`）未变，漂移发生在逐字 payload（`systemPrompt`/`actionContract`/`reviewSystemPrompt`/`budget`/`cases`），由 2026-10-16 之后的协议与提示词提交造成。重建 jar 不能修复历史包的 payload，因此不能复用旧包、也不能覆盖它。

## 2. 本轮改动（入口参数化，不改变历史包）

- `knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentOnlineProbeHost.java`：新增 `packagePath(root)`，通过环境变量 `ZHKU_RULE_AGENT_PROBE_PACKAGE` 接受仓库相对路径；默认仍指向历史包；越出 `docs/evidence/rule-agent-provider` 直接拒绝。`--PrepareProbe`/`--CheckPackage`/`--Probe` 统一使用该解析。
- `knowledge/scripts/start-rule-agent-probe.ps1`：新增 `-PackageRelativePath`（默认历史包），校验非绝对路径且位于证据根下，并向宿主注入同一环境变量。
- 历史包 `2026-10-16/t05-69-review-condition-request/request-package.json` 字节未改。

## 3. 预检结果

构建：`mvn -o -q -pl knowledge -am -DskipTests package`（2026-11-08 17:40 +08:00）

| 对象 | 值 |
| --- | --- |
| controlled jar | 44,088,366 字节，SHA-256 `674d2c50eb2eb48b62d5b68e74d303dbb4ea8bc3b4a7206975b95f44115444ad` |
| 新请求包 | 89,904 字节，SHA-256 `a7557cb4063c5cf98ed9fc6185ca8ee516c056616a37f8bfd5c147de057d5329` |
| 新包路径 | `docs/evidence/rule-agent-provider/2026-11-08/eval-chain-preflight/request-package.json` |

`--PrepareProbe` 输出：

```text
PROBE_PACKAGE_SHA256=a7557cb4063c5cf98ed9fc6185ca8ee516c056616a37f8bfd5c147de057d5329
```

`--CheckPackage` 输出（零调用）：

```json
{"databaseOperations":0,"providerCalls":0,"mode":"PACKAGE_CHECK_ONLY","passed":true,"maximumProviderCalls":10,"protocolRevision":"RULE_AGENT_ACTION_CONTRACT_06","packageSha256":"a7557cb4063c5cf98ed9fc6185ca8ee516c056616a37f8bfd5c147de057d5329"}
```

Docker 预检：

- 引擎可达时 `docker images` 显示本地已有 `pgvector/pgvector:0.8.6-pg16-bookworm`（image id `ccc6e83d6e35`），无需 `docker pull`。
- 注意：受限 sandbox 内访问 Docker 命名管道会被拒绝（`permission denied ... dockerDesktopLinuxEngine`）；预检是在放宽权限的单次只读命令中完成，未启动任何容器。

## 4. 边界与下一步

- 本批次授权状态仍为 `NOT_AUTHORIZED`；`approval-state.json` 所属的历史批次已 `CLOSED_PASSED`、`remainingAuthorizedCalls=0`，不能复用。
- 真正执行 `--Probe` 需要：用户明确授权本批次外发（上限 10 次调用 / ¥12）、可访问 Docker 与凭证的受控终端、专用临时库端口（脚本默认 55518，拒绝原库端口）。
- 执行后产物写入新的 `live-<guid>/` 目录，再用 `AgentEvalChainMain collect` + `run` 生成夹具与报告。

## 5. 未执行声明

本轮**没有**：调用 DashScope、创建/迁移数据库、启动业务容器、执行 `--Probe`、写入业务数据、推送历史包覆盖。
