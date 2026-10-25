# 单用例 Java → JSON → Node 离线桥接契约

状态：**UNVERIFIED**。只检查一个 G2 development 用例的功能链，不是可信执行、在线适配器或模型质量验收。

## 边界

Java测试 `RuleAgentOfflineTraceExportTest` 用已有Runner、TaskStore和本地确定性工具执行单轮，产生 `T08_JAVA_TRACE_EXPORT` v1。Node使用现有 `cli.mjs run --mode java-offline`，依次解析原始字节、调用 `importJavaTraceExport`、评测和聚合，不启动数据库、Docker或模型服务。

只支持一个development/G2 case、空supplements、一个trace、局部标签task-1/turn-1/run-1及首次发布version2。其他模式、数量或版本明确拒绝。24个action和四条历史保留上限不构成多轮完整覆盖证明。

header绑定candidateId、sourceSha、caseId、原始dataset字节SHA-256；sourceLayer为ACTUAL_JAVA_RUNNER_OFFLINE_SIMULATED，executionMode为OFFLINE_SIMULATED，providerCalls为0。sourceSha由调用方提供；脏工作区另记录diff/文件hash，不声称执行了该SHA的干净树。

导出仅含局部动作/证据/claim标签、受限状态、版本、覆盖标记与关系，排除UUID、问题/回答/证据正文、sourceId/chunkId、continuationToken。revision只允许2025.9.2，factId只允许sha256摘要。step连续、modelAttempt不递减、动作观察一一对应，工具观察与控制事件NOT_OBSERVED分开。缺字段、错类型、未知字段、重复/悬空关系或状态矛盾必须拒绝。

## 输入、输出与失败

- Java输出为工作树内新文件；验收前确认父目录不是junction/symlink，使用CREATE_NEW。Node保留冻结pin、路径/符号链接检查、输入字节复核和独占输出。
- 原始Java JSON最多2 MiB、24层容器，严格UTF-8，拒绝重复键（含转义等价键）和危险原型键。只输出安全码，不打印输入片段或原始异常。
- 缺文件为JAVA_EXPORT_MISSING；格式/编码错误为JAVA_EXPORT_FORMAT；重复/危险键和超限分别为DUPLICATE_KEY/UNSAFE_KEY/LIMIT后缀。CLI返回2，不生成成功结果。
- JSON合法但字段/身份/关联违约：保存脱敏FAIL报告，CLI返回1；截断或异常轨迹不能PASS。未知Java执行/评测错误以JAVA_EVALUATION_FAILED停止，不编造工具结果。
- run退出0才表示本次工程case通过；aggregate退出0只表示报告可汇总，仍须检查byEngineeringStatus。编排器不能用最后一条命令覆盖先前失败。

结果mode/sourceLayer保持UNVERIFIED_IMPORTED_JAVA_TRACE，javaExecutionVerified=false、verifiedJavaCandidate=false；qualityStatus为NOT_EVALUABLE，usage/cost未知为null，rubric保持PROPOSED_NOT_APPROVED。JSON字段和SHA相等不能证明执行来源。

## 显式跨语言验收

默认Node单元测试位于 `tools/t08-acceptance/test/`。`integration/java-roundtrip.test.mjs` 必须在实际Maven导出后显式运行，提供T08_JAVA_EXPORT_FILE、全新T08_BRIDGE_RUN_DIR、独立传入的T08_JAVA_EXPECTED_SOURCE/T08_JAVA_EXPECTED_CANDIDATE、实际产物的T08_JAVA_EXPORT_SHA256和本次Java开始时间T08_JAVA_NOT_BEFORE（Unix毫秒）。缺输入失败，不skip、不生成成功替身。

测试通过真实CLI创建独立bridge manifest并固定pin，读取实际Java文件，再run/aggregate；负例由本次真实导出派生，核对非零退出及没有成功记录。官方20例/freeze不修改。证据记录命令、导出/结果hash、开始时间、失败和跳过数。

结果含claims/evidence时，必须至少存在一次成功的verified-read观察；先失败后成功的合法恢复仍可通过，只有失败观察不能支撑完整结果。错误码采用确切白名单，任意同前缀异常不成为可输出的安全码。

TaskStore、RunTrace、Dispatcher和P1隐私生产实现保持不变。导出测试类新增junction反例后共有3项JUnit测试；后续DSH控制器的固定2项测试计数必须另行版本化适配，本批不修改其协议。既有6个数据库初始化错误仍标记未验证。DSH摘要回执、本地Ed25519签名回执是其他协议，本桥接不建立可信执行回执；签名控制器仍受ISOLATION_REQUIRED阻塞。
