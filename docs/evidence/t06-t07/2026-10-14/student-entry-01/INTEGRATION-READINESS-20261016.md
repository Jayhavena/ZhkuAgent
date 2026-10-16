# T07 集成就绪与门禁状态（2026-10-16 核对）

本文件回答两个问题：**统一集成怎么取 T07 的范围**，以及**T07 的真实两窗口浏览器验收现在能不能做**。全部为只读核对，未新建提交以外的任何 ref，未启动容器/宿主/浏览器。

## 1. 门禁状态（T07 的浏览器验收前置）

| 门禁 | 状态 | 依据 |
| --- | --- | --- |
| ① T06 提供可登录装配（真实身份／资料） | **已满足** | `codex/t06-controlled-launch` 的 `RuleAgentSimulatedChainTempDbHttpTest`：真实一次性临时库上的正式身份仓储登录 + 真实已审核资料安装/检索/读取/原文定位 + 真实回环 HTTP；统一入口 `scripts/t06-controlled-acceptance.ps1 -Scenario chain`（`-Scenario all` 三场景通过，退出码 0）。见 `docs/evidence/t06-t07/2026-10-16/t06-handoff/T06-HANDOFF.md`。 |
| ② 验收宿主 Agent 提供稳定提交与退出方式 | **未交付** | 仓库内未见该交付。最后一次宿主相关记录仍是 2026-10-14 的 `reviewed-tools-01/browser/cleanup.json` 及其回执，其中 `hostExitEvidencePresent: false`，未验证项写明"宿主执行句柄失效，不据端口关闭推断线程退出"。`codex/t06-t07-student-http` 仍停在 `88e57d6`，无新的宿主产物。 |

**结论：T07 的真实两窗口浏览器验收仍阻塞在门禁 ②。** T06 交出的是 JUnit 测试（自管生命周期、非长驻可浏览的登录态服务），不是可供浏览器长期连入并具备可证退出的宿主；自建宿主属于宿主 Agent 的交付，且会触碰"不启动不稳定租约／不绕过退出卡点"的约束。**本轮不启动任何宿主与浏览器。**

## 2. 集成就绪（只读合并干跑）

核对对象：`codex/t06-controlled-launch` @ `a03af69`、`codex/t07-student-entry` @ `de96fa4`，共同基线 `88e57d68799d3a5996bb84346090a84040a88387`。

| 项 | 值 |
| --- | --- |
| T07 范围文件数（`88e57d6..de96fa4`） | 21 |
| T06 范围文件数（`88e57d6..a03af69`） | 35 |
| **文件交集** | **0**（源码、测试、脚本、证据目录全部不相交） |
| 合并干跑 | `git merge-tree --write-tree codex/t06-controlled-launch codex/t07-student-entry` → exit 0 |
| 冲突条数 | **0** |
| 合并结果树 | `0fd984e44552c9597b61bb07eb7528880e5f0092` |
| 合并树中 T07 新文件 blob | 与 `de96fa4` 逐一同 OID（未被 T06 侧改写） |
| 合并树中 T06 新文件 blob | 与 `a03af69` 逐一同 OID（未被 T07 侧改写） |

即：**两侧可以干净合成一个统一版本，且互不覆盖对方内容。**

### 集成取用规则（给 PM / 集成方）

- 范围＝「T06 全范围 `88e57d6..a03af69`」＋「T07 全范围 `88e57d6..de96fa4`」，**各自整套取用**。
- ⚠️ T06 侧**不得只取单个提交**：`75274a1` 引入的 `autowireCandidate=false` 会让 ONLINE 入口无法装配，必须带上撤回它的 `7d8b5e4`（T06 交接文档 §1 已标注）。
- 主工作树禁止直接 `git merge`（既有复现故障），按既有工作流：

```
T=$(git merge-tree --write-tree codex/t06-controlled-launch codex/t07-student-entry)
C=$(git commit-tree $T -p codex/t06-controlled-launch -p codex/t07-student-entry -F <提交消息文件>)
git update-ref refs/heads/<目标分支> $C
```

- 合并后源码身份：T07 侧 9 个源码/测试文件与 11 个只读输入指纹见 `source-manifest.json` / `baseline.json` / `STUDENT-FLOW-EVIDENCE-INDEX.md` §5。

## 3. 合并完成后在统一版本上要跑的两窗口验收（保持待验证）

前置：门禁 ② 就位（宿主稳定提交 + 可证退出方式）。执行时只用测试身份与合成问题，不接真实供应商。

1. 同一浏览器开 A、B 两窗口，同一测试身份登录，均落在学生查证页。
2. A 提交合成问题并取得已发布结果（"当前能确认什么"＋相关依据可见）。
3. B 执行退出（或切换身份）。
4. 断言：A 的已发布结果被清空、回到登录态并显示"登录状态已在其他窗口变更，请重新登录。"；`ticket`/`snapshot` 不再保留。
5. 在 A 制造或等待一条**迟到响应**，断言其成功返回后仍不写入、不复活旧结果。
6. 断言：A 必须经**后端确认的新身份**才能继续查证；未确认身份时提交被门禁拒绝。
7. 记录两个窗口的实际页面、接口结果、源码 SHA、合同版本、模式与**资源退出**，且只操作本会话自有的页面/端口。

## 4. 本轮未做与不可表述

- 未启动容器、宿主、浏览器；未新建合并提交；未 push/PR/标签/部署；未改共享合同、后端、宿主或 `vite.config.js`。
- ❌ 不得据本文件表述"T06/T07 已集成通过"或"真实两窗口已验证"——门禁 ② 未满足，两窗口**保持待验证**。
