# T05-64：P02 合规核验本地回放（零外发）

本轮零真实外发、零数据库、零 Docker。回答 t05-63 unknowns 第一项：**“若独立核验返回原键（改键后）的合规响应，宿主能否通过剩余门禁走到发布”**。只改一个测试文件，未动任何生产代码、提示词、模型、预算或门禁。

## 为什么是本地回放

P02 三次在线失败（t05-52 ASK 键前缀、t05-57 引用未登记事实、t05-63 核验键前缀）全部被宿主一票否决，从未观察到“全写对之后会发生什么”。在线批次每批一次机会且需授权，先用真实批次载荷（t05-55 live 快照）在本地替身上回放该路径，零成本锁定剩余风险。

## TDD 过程（红 → 诊断 → 绿）

1. **红**：新增两个测试（run2 单分支全对→发布；run2 双分支核验→不谎称完整），首跑 2 失败，均为 `expected RESOLVED but was FAILED`。
2. **诊断**：决策替身打印显示第三次决策已返回 Finish 且 `FINISH_CHECK` 在允许列表，但无第三条事件——异常逃到 Runner 最外层 `catch(RuntimeException)`。同包直调复现拿到堆栈：`NoSuchElementException` 出自测试助手 `knownValid`——夹具是 **t05-55** 批次，需求 ID 为 `req_q1_public_retake`，误写成 t05-62 批次的 `r1_public_retake`，`orElseThrow` 异常从核验端口逃逸。
3. **顺带实证**：核验端口抛出非合同异常时，Runner 不留任何事件即收敛为 `INVALID_MODEL_RESPONSE`——在线排障时“无事件的 INVALID_MODEL_RESPONSE”应优先怀疑核验/决策端口内部异常，而非仅归因模型输出。
4. **绿**：修正 ID 后 10/10 通过。

## 锁定的行为（真实批次载荷 + 本机内存原文端口）

- **run2 单分支全对 → 发布**：`userConditions={courseType:PUBLIC}`，FINISH 引用已登记事实 `S10/RETAKE_ONLY:public-failed-no-retake`，核验返回与已知条件一致的单分支需求（原键 `courseType`、CORE 绑定）→ 4 次调用内走完 `SEARCH→READ→FINISH_CHECK(attempt=3)→PUBLICATION_REVIEW(attempt=4)`，`RESOLVED/COMPLETE`，主张含该事实文本、无 issue、无澄清、continuationToken 为空、evidence 非空。**即：改键后的合规核验在宿主侧可以通过剩余门禁并发布。**
- **run2 双分支核验 → 不谎称完整**：核验仍按条件值展开两个分支而 FINISH 只选一侧 → Gate 判 `PARTIAL`，发布器记 `ANSWER_COVERAGE_INCOMPLETE`、任务 `FAILED`，已确认主张保留。漏报一侧不能借通过。

## 回归

11 个显式单元类 **204 项，0 失败 0 错误**（含本机回环工厂；无 DB 测试）。首次回归漏传 `-Dzhku.rulecheck.evidenceDir` 致 4 类失败（与 t05-49 同因），补参后全绿；两类日志均保留于 regression/。

## 边界（不得外推）

- 本机替身与真实合成输入证明的是**宿主链路在合规输入下可走通**，不证明真实模型会输出合规响应、不证明总体可靠性或学生收益。
- t05-63 其余 unknowns 未触及：第二 run 能否完成、真实账单、临时库行数、失败任务仓快照。
- 未修复、未归一化、未重放、未补额度；历史在线失败记录原样保留。P02 真实在线验收仍未通过，T05 未收口。
