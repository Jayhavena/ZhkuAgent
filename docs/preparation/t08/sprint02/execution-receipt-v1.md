# T08 普通离线回执接入

状态：**UNVERIFIED / SELF_REPORTED**。回执摘要用于检查本地材料自洽，不是签名或可信执行证明。签名控制器的ISOLATION_REQUIRED门禁保持不变。

## 计数与来源

选择性移植 `dsh/t08-controller-receipt-v1-20261021@3dfe5565d8e9aced7c2c55655216fec9b6183350` 的receipt/observe实现，沿用现有Java导出和Node评测器。`dsh/s02-t08-offline@f3414eee62025ef026d5eccc059b3aa4cd644553` 的RunTrace已在主线，无须覆盖TaskStore/Observer生产实现。

| 控制器版本 | Surefire完整测试集合 | 使用限制 |
| --- | --- | --- |
| t08-controller-v1a | actualRunnerTraceHistoryExportsOnlySafeT08Fields；rejectsHiddenUuidAndUnreviewedRevision，共2项 | 可以检查旧格式；当前闭环拒绝以它代替新契约 |
| t08-controller-v1b | 上述2项加rejectsParentJunctionBeforeCreatingExport(Path)，共3项 | 当前执行和闭环必须使用此版本 |

第3项由P2-B加入，实际创建受控临时Windows junction，证明导出不能沿父目录链接写到仓库外。不是多算了业务case，也不允许以“2或3都行”放过漏测。必须匹配测试类、完整唯一方法名集合、计数和0失败/错误/跳过；损坏XML和隐藏失败标签同样拒绝。

## 执行和关联

`tools/t08-acceptance/receipt/observe.mjs` 接受固定commit/tree、干净候选、新挑战及其SHA pin、包含dataset/rubric/Java测试源码的冻结清单。`--execute --maven <本机现有Maven绝对路径>` 才运行固定离线Maven参数；不使用真实模型或数据库。输出必须是候选树外的新目录，临时检出不注册为Worktree。回执记录的是实际执行的干净候选，不把开发中的Node改动冒充干净源码树。

完成时保存receipt.json、java-export.json、surefire.xml；退出失败/超时、报告或导出缺失/非法时保存非成功回执。stdout/stderr只计字节数，不将原文写入回执。命令不带凭证，子进程剥离凭证环境。实际Maven使用现有JDK21和本地依赖；wrapper及依赖的外部可信度未被本实现认证。

回执格式仍为T08_EXECUTION_RECEIPT v1，新增的控制器v1b负责3项测试语义；receiptId是规范序列化SHA256，externalRegistration固定未pin。SELF_REPORTED不会自动升级为CONTROLLER_OBSERVED或SIGNED_ATTESTED。

## 现有CLI闭环

将实际回执打包为 `{"recordType":"T08_RECEIPT_BUNDLE","schemaVersion":1,"receipts":[回执对象]}`；单case只接受1份，空包、重复和多余回执均不能通过。将回执包、挑战、实际导出和Surefire报告放到评测root内的新证据目录，避免绕过既有路径/junction检查。manifest必须包含dataset、rubric和Java测试源码。

在已有run参数上添加：

```text
--mode java-offline --case-id <caseId> --java-export <root相对JSON路径>
--receipts <root相对回执包路径> --challenge <root相对挑战路径>
--surefire-report <root相对XML路径>
```

java-offline默认要求回执；不得自动降级。旧P2-B单纯导入验证须显式 `--receipt-policy trace-only`，其结果标记receiptStatus=NOT_REQUESTED，不能作为完整回执闭环。

默认闭环结果是T08_RECEIPTED_RESULT，内含已有T08_S02_RESULT作为traceResult。绑定验证覆盖原始challenge/manifest/dataset/rubric/test源码/Surefire/export字节摘要、case、candidate/source、命令中的身份参数、3项完整测试及严格Java轨迹契约。原始JSON采用既有严格UTF8、重复/危险键、大小和深度校验。

aggregate对闭环结果必须再次提供同一root、manifest pin、dataset/rubric/candidate/source/case、回执包、挑战、Surefire和导出参数；它重验材料并使用同一评测器复核，不只相信报告里写的PASS或receiptId。只有receiptStatus=VALID且evaluationStatus=PASS才退出0；无效/缺失/执行失败/评测失败退出非零，不能用最后一步汇总覆盖之前失败。已有输出独占创建，不能覆盖历史。

receiptStatus区分VALID、MISSING、INVALID、EXECUTION_FAILED、NOT_EXECUTED；evaluationStatus区分PASS、FAIL、NOT_EXECUTED、EVALUATOR_FAILED。错误只保留固定安全码。合法执行回执不等于业务成功，更不等于模型质量通过；quality仍NOT_EVALUABLE，usage/cost未知保持null。

同一包内防重复不等于跨运行受保护的防重放登记。没有数字签名、可信隔离、外部信任根或真实模型质量验收。既有6个数据库初始化错误继续未验证。
