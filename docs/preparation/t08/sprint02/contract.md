# Sprint02 共同评测契约 v1

基线为 `828c555`。原 T08 准备精确复用 `578a9eb` 的文档与工具，保留其历史结论；T09 `18abd66` 的准入仍依赖T08，不运行学生对照。此次只准备20例的小批试点，不声称完成旧T08全部G3矩阵。

## 数据与执行边界

每个split文件为 `T08_S02_DATASET/schemaVersion=1`，datasetId=`t08-s02-20261020`、revision=`1`，分开发6/回归6/保留8。每case有唯一caseId、G2/G3/G4、A–H类别、问题来源、输入、参考要点/禁止结论/必要条件/证据ID/允许缺口、逐轮预期、政策范围和rubricRevision。

`input.question`和`input.supplements`是执行器唯一可传给Agent的问题数据；reference/expected/Rubric不能加入模型输入。`expected.taskStatus`与coverage数组统一为按轮次预期，每轮一个，长度等于1+supplements.length，不是允许值集合。终态、覆盖率和线程退出均不能单独证明回答正确。

设计的学生问法不是已采集真实学生问题；合成工具故障单列G4。版本/年级/冲突没有官方源时，只评估范围诚实或安全行为，不虚构规则。policy-ledger记录审核原文摘要和UTF16区间；网页发布信息不等于附件字节复核，更不等于现行适用性。

Node离线引擎用自身合成夹具验证程序，真实案例只生成 `NOT_EXECUTED` 计划；合成工程PASS不进入模型质量。质量状态另列 `NOT_EVALUABLE`，用量/费用未知为null。按group/split/candidate/dataset/rubric版本分开统计，不省略未执行、异常或缺轨迹案例。

原 validate-evidence 的G3要求ONLINE，不能把合成G3伪装成旧T08_SAMPLE。新外层单独记录离线计划和合成工程结果，旧工具继续跑原兼容测试。

G4声明faultInjection时，必须同时具备注入点实际触达、注入生效回执和对应输出。适配器回传faultReceipt的type/point必须匹配案例，reached/applied均为true，指定SEARCH/READ动作与观察须同actionId关联；缺一项为INSUFFICIENT_EVIDENCE，质量仍NOT_EVALUABLE，不能用任意普通FAILED冒充故障验证通过。预期本来就是FAILED的故障，在有效注入且安全输出匹配时仅工程机制可PASS。

## 独立性与冻结

案例/政策Agent独立工作树准备保留题；执行器Agent只看schema及自造合成样本。主控不读取保留正文/参考答案，只读聚合元数据和文件摘要。最终评分复核Agent可读取参考，但不改生产实现。大家在同一台机器有访问权限，未建立技术访问控制，且原历史规则已用于开发，因此只能称分工隔离，不能声称严格盲测、无污染或统计独立。

候选生产源码先冻结在828c555，保留题在其后形成；本轮只新增eval适配器和测试，不据保留答案改业务。若后续修复用了保留题，该题转下一版开发集，原结果保留，另补独立题。文件冻结用独立清单SHA256和用户批准的外部pin，拒绝篡改、缺失及路径逃逸；重算清单不能继承旧批准。

## Rubric与权限

Rubric继承原D1–D8并加D9明确失败/不确定性，覆盖用户七维要求。确定性、需要审阅与混合维度明确区分，反例可复核。阈值均为PROPOSED_NOT_APPROVED，用户批准前不当作已确认产品标准。

无真实供应商或Judge授权。构包、校包、plan、synthetic不能读取密钥或启动供应商。真实执行须包摘要、题号、问题摘要、候选、模型、调用数、费用和数据范围一起批准；一次占用失败不返配额。默认拒绝先于Key/DB访问。仅专用空临时库，禁止54329、旧业务库及生产操作。
