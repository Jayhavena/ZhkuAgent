# Sprint02 题集与政策证据

本目录只交付离线准备材料：20 例，development 6 / regression 6 / holdout 8。G2 已曝光开发与回归共 9 例，G3 新设计自然问法 8 例，G4 受控故障 3 例。设计自然问法不是故障合成；案例均为作者构造，不是学生日志。

## 冻结身份与使用边界

- 源码及政策输入固定为 `828c55530013dc417b03ab178f83d8867538c928` 的 Git 对象。题集 ID 为 `t08-s02-20261020`，revision `1`，rubricRevision `t08-s02-rubric-v1`。
- 依据 2025.9.2 学生手册《仲恺农业工程学院选修课程管理办法》（仲字〔2021〕65号），物理页 76–77 / 印刷页 64–65。仅使用基础 10 条逐字原文与 Article4 两项已确认增量，不将 C05 工程覆盖层当政策批准。
- 本机原件文件 SHA-256 由历史记录引用；本轮不读取整本 PDF。主控报告已核对官方发布页元数据，见 ledger 的 `officialPageObservation`；本子 Agent 没有联网获取。发布页存在不证明本机 PDF 与在线附件字节一致。
- currentApplicability 始终 UNKNOWN。无 2026 现行制度、适用入学年级或真实双来源冲突证据；相关案例仅检查范围诚实与缺证行为，不创造第二份政策。
- 保留集在源码基线固定后新设计。案例作者可见全文，主控只接收聚合统计/指纹，工程实现 Agent 不接收保留正文。共享 Git 与工具历史并非强访问控制，故只记录分工隔离，**不称严格盲测**。
- 已曝光固定问法及自然近义变体只进入 development/regression。holdout 不是把原固定题换数字；仍与开发集共享政策条款，不声称独立同分布或完全消除相关性。

## 字段语义

`expected.taskStatus` 和 `expected.coverage` 都是**按轮次顺序的预期**，不是允许值集合。长度必须等于 `1 + input.supplements.length`；先运行 question，再依序补充。跨轮案例必须保留同 task、新 run 的真实轨迹。保留集最多一项补充，每题至多两轮。

`expected.requiredToolTypes` 表示内容回答所需工具类型，不代表具体次数。`evidenceRequired` 约束可发布结论的引用；安全缺口可以合法 `RESOLVED/NONE`。任务终态、coverage 或 SEARCH/READ 出现本身都不能证明质量成功，仍须独立按参考要点、禁说结论及评分规则核对。

`reference.verifiedBeforeRun=true` 仅表示本案例作者在任何执行之前检查了冻结历史依据或安全预期。`HISTORICAL_SOURCE_CHECKED` 是 Agent 对历史审核原文的离线核对；历史人工确认路径在账本中，但**新题参考没有人工逐题签字**。`SAFETY_EXPECTATION_ONLY` 只确认不编造、不过界、不发布失效证据等安全行为。`PENDING_OFFICIAL_SOURCE` 必须 false，不能用于伪造已审当前政策。

`provenance.sourceCommit` 是知识/开发接触基线，不是新问题存在于旧提交的断言。development/regression 的 EXPOSED_DEVELOPMENT 表示已曝光主题/固定问法的回归用途，不能计新泛化。风险标签覆盖 12 类，详见聚合自检结果；不同 G 组不合并在线成功率。

三个 G4 案例额外使用 `faultInjection:{type,point,payload}`，分别模拟搜索空结果、读取 checksum 失效与搜索工具异常。它们是执行器的注入输入，**本目录自检不执行生产代码，也不证明故障被生产门禁拦截**。故障模式不得外发供应商，且不得与自然题混统。

## 证据账本

`../policy-ledger.json` 的 evidenceId 是稳定引用键。`sourceSha256` 对应原始 Git blob 的 SHA-256；`contentChecksum` 对应逐字条款正文 UTF-8 摘要。跨度为 UTF-16 code unit，包含冻结原文换行。基础证据引用整条；Article4 两项增量引用父条款的准确局部跨度。`sourceJsonPointer` 可定位被冻结 JSON 内的值；语义确认文档另有摘要。

## 离线自检

在已安装 Python 3 的终端、工作树根目录执行：

```powershell
python docs/preparation/t08/sprint02/cases/validate_cases.py
```

脚本仅用 Python 标准库与 Git，只读取冻结 Git 对象和本目录 JSON；不读取凭证，不使用网络、数据库、模型或 Judge。它验证文件/正文摘要、原文字节、UTF-16 跨度、审核文件摘要、证据引用、每轮数组、分组、12 类标签及保留隔离。7 个内存变异反例分别破坏摘要、跨度、引用、轮次数、保留来源、标签与待审标记，必须被拒绝。

结果写入 `validation-summary.json`，只含聚合数、指纹和反例结果，不输出保留问题/答案正文。该脚本核对数据契约，不是语义评分器、线上验收或 T08 正式执行。

撤销本任务可恢复/回退本次独立本地提交；不要改写历史审核输入。没有数据库或进程资源需要清理。
