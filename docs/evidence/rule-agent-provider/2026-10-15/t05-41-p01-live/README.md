# P01实际启动失败：运行时审核资产缺失，零模型调用

2026-10-15，用户明确批准新包5400bbf7…849d6a一次P01、最多5次真实调用、6元上限。起点cb96646；源码/依赖22项、jar、包重新核对均无漂移，所需凭证本轮重新按Process→User→Machine核对，仅留可用性。CheckPackage通过。

## 最早失败及证据

启动一次，08:11:37—08:11:45，外层退出1。专用临时库45064成功应用18个迁移，受控资料导入后，宿主加载reviewedCatalogue时抛NoSuchFileException：docs/product/rule-check-c05-topic-catalogue.json。

路径：RuleAgentOnlineProbeHost.java:73 → Reviewed2025ControlledCatalogue.java:197 → Reviewed2025TopicCatalogue.java:23。新工作树缺该文件；主仓库存在但未被Git跟踪。没有复制它，没有重跑。其资格和所有运行时依赖的完整清单须在后续本地任务重新核对，不能直接把未跟踪草案当作新的外发依据。

CheckPackage在构造数据库/模型前返回，仅运行packageNode/构造器资格核对，没有运行reviewedCatalogue加载路径，所以本地包核对通过不能证明运行时资产完整。这是已证实的交付/预检覆盖缺口，不能归为模型随机、在线超时或Docker不可用。

## 调用、费用与退出

模型工厂未创建，run未创建，无provider-traces、无outcome；DECISION/SEARCH/READ/FINISH/独立核验/结果发布均未执行。实际模型尝试0、响应0、费用0（未派发可证）；全部5次余量关闭，不复用旧额度、不补测、不修复、不进入P02。

数据库操作仅在专用临时库：18个迁移及受控资料导入/清理，SQL操作总数未逐条计数。临时容器已删除，探针Java残留0，原演示库仍exited且启停时间与预检完全一致。

原始probe-result.resourcesClosed=false原样保留；独立进程/容器核对没有发现本轮资源残留，不能把原始false改写成true。完整原始host.log及probe-result仍留本机，摘要/指纹见actual-outcome-sanitized.json。验证通过仅指失败归档、身份与退出一致性，不是P01验收通过。

本轮源码、构建和请求包未修改。后续若处理运行时资产完整性，必须先做本地修正/验收及新包决策；本次额度已关闭。

**工程成果已冻结，真实在线验收未通过。**
