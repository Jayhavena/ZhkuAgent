# T06 统一受控验收脚本 · B 轮脚本级验收（chain 场景两方法，本轮通过）

> 时间 2026-10-16 21:19。这是 [`../t06-cancel-capacity-01/`](../t06-cancel-capacity-01/) 的
> **脚本级复跑**：把「真实库上的取消/容量/并发/终态矩阵」并入统一受控验收脚本的 chain 场景后，
> 用脚本重跑两个方法。全部为本地提交，未 push／PR／标签／部署／合并。

## 0. 一句话状态

**`-Scenario chain` 两个方法全部通过（2 项，15.62s，0 失败 0 错误 0 跳过，脚本退出码 0）**：
主链方法（真实身份/资料/CSRF/幂等/隔离）+ 新方法（取消/413 容量/4 线程并发/终态矩阵）。
双证据落盘，脚本级核对通过。

## 1. 运行明细

| 项 | 值 |
| --- | --- |
| 运行 | `t06-unified-20261016-211949`，`-Scenario chain`，退出码 **0** |
| maven 结果 | `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 15.62 s` |
| 临时库 | 容器 `zhku-t06-tmpdb-20261016-211949-1`（127.0.0.1:55399，随机端口避开 54329）；18 个迁移至 v22；空库证明通过；**按名回收** |
| 证据 1 | [`chain/simulated-chain-tempdb.json`](chain/simulated-chain-tempdb.json)（8888 字节，主链方法） |
| 证据 2 | [`chain/cancel-capacity-terminal.json`](chain/cancel-capacity-terminal.json)（14068 字节，新方法；与手动绿轮逐字节一致） |
| Mockito 警告 | 纯文本（脚本已把 maven 调用期间 stderr 的 ErrorRecord 转纯文本，不再触发 `NativeCommandError` 终止） |

## 2. 脚本改动（相对 `87d0d1c`）

| 改动 | 原因 |
| --- | --- |
| chain 场景 surefire 过滤器改为 `#realIdentityAndReviewedAssetsOverActualHttpWithCsrfIdempotencyAndIsolation+realTempDbCancelCapacityAndTerminalStatesOverActualHttp` | 一个场景收两个方法，避免分两轮建临时库 |
| `Test-EvidenceLanded` 参数由 `[string]$ExpectedFile` 改 `[string[]]$ExpectedFiles`，缺任一文件即警告并返回 false | 两方法两证据，缺一即指出"测试可能被假设跳过（跳过不算通过）或未写证据" |
| 头注释 chain 用法行补"两个方法"说明 | 用法自描述 |

## 3. 记账边界（不变）

- chain 场景：真实一次性临时库 + 真实已审核资料 + 真实回环 HTTP；**决策段是宿主显式模拟执行器，
  模型尝试 0、供应商调用 0**，不得读作在线可用。
- 脚本判据只有 maven 退出码 + 证据落盘；**在线链路未运行**。
- 本轮未跑 existing／proxy 场景；其证据以 [`../t06-unified-20261016-204306/`](../t06-unified-20261016-204306/) 为准。

## 4. 资源退出核对

| 项 | 结果 |
| --- | --- |
| 临时库容器 | `zhku-t06-tmpdb-20261016-211949-1` 按名 `docker rm -f` 回收；`docker ps -a --filter name=zhku-t06` 计数 **0** |
| 监听端口 | 55399 监听数 **0** |
| java 进程 | 无残留（禁止按进程名批量终止） |
| 写入范围 | 仅本轮一次性临时库；原演示库未启动、未写入 |
