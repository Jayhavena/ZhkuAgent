# P01_RUNTIME_READY_01 单次真实批次归档

本次冻结包按既定P01工程条件通过。仅一项固定问题、一个task/run的真实检索→读取→FINISH→独立模型核验→完整发布，不扩张为总体可靠性、产品质量或学生收益达标。

## 授权与身份

用户明确批准：“该新包一次 P01、最多5次真实调用、人民币6元上限”。

本轮基线91851838cfd3c93ea415eff05bdb6913a9233ad0；实现043429db69b6596e40bc05340927d99f6dea659c；包850b8b681e140a95e8e861f72fd40a55a8f24db71b982ea2edabf7891410bf77；jar3eb329ca95ab1465f8bdd7a311ba17a01b4f4a2431884e66545c50ad1da7b4f8。主仓库58e1ef7未合入。

334项源码/启动器与提交和批准包逐字节一致，15项资产指纹匹配，实际jar的656项应用类及内嵌共享运行时依赖与target一致。启动器初始被Git状态标记为修改，但工作文件/HEAD/批准包哈希三者相同，git diff无内容变化；未为消除状态标记而修改文件或重建。

操作前重新核对官方北京价格和公开token上限，五次风险预留5.736704元在6元内；刷新进程→用户→系统所需凭证，仅记录可用性。Docker29.8.2 linux、缓存镜像和零调用包检查通过，未使用旧额度。

## 实际执行

一次启动，2026-10-15T00:52:42.9306570Z 至 2026-10-15T00:53:06.6664016Z，外层实测 23.736 秒，启动器退出0；专用随机回环端口20435，数据库zhkuagent_rulecheck_e2e，随机命名临时容器，不挂载用户数据目录。不操作原演示库54329，不执行P02，不重试。

任务：e8ed86a6-7352-4e44-bad4-f44798416307；run：9aa0ee27-c2c6-491b-8737-76cb95f36c68。原始目录：`docs/evidence/rule-agent-provider/2026-10-15/t05-43-request/live-41a9adb842e547e5b9afa0baa4250519`。

4次真实调用，3次DECISION、1次PUBLICATION_REVIEW，全部HTTP200，有requestId、模型身份、输入/输出usage及请求/响应摘要。12行BEGIN/RECEIVED/FINISHED去重为4个attempt，不计为12次调用。

| attempt | 用途 | 请求字节 | 完成耗时ms | 输入token | 输出token |
|---|---|---:|---:|---:|---:|
| 1 | 决策/SEARCH | 4557 | 1452 | 831 | 30 |
| 2 | 决策/READ | 9921 | 1161 | 1475 | 42 |
| 3 | 决策/FINISH | 18308 | 1986 | 3475 | 96 |
| 4 | 独立发布核验 | 12589 | 6937 | 3154 | 402 |

SEARCH返回完整10条候选。一次READ取得第4、8、18条原文，共5个证据片段和16个事实编号；本次没有额外READ，也没有靠强制FINISH或降低门禁通过。最终仅发布两条修读安排事实，未把验收参考答案预置为模型输出，也未预置READ编号；问题本身是申请中已固定的P01输入。

## 独立核对结果

- taskStatus=RESOLVED、runState=SUCCEEDED、terminated=true，ONLINE/LIVE_AGENT、providerResponseReceived=true、replayCoverage=NOT_APPLICABLE。
- 同一run的SEARCH、READ、FINISH、PUBLICATION_REVIEW依次成功，核验与发布拒绝原因均null；没有跨批次拼接。
- coverage=COMPLETE，恰好两条要求事实，无issues/clarification；两条原文证据和两条CLAIM关系完整。
- 已将最终引文UTF-16跨度、正文摘要及2025.9.2版本与独立读取的已审原文对比，匹配第4条，不使用输出自己的quote作期望。
- 原始probe-result的passed=true、resourcesClosed=true、allowanceConsumed=4保留未改。临时容器已不存在，无本轮ProbeHost Java；演示库.State对象前后相同，仍exited。

数据库目录close无异常由成功退出及冻结执行路径支持；本批在临时容器删除前未另查清理后行数，不能把上一轮t05-42的0行记录当作本批实测。资源退出还由独立容器/进程检查支持。

## 金额与额度关闭

报告usage合计输入8935、输出570 token。按[官方北京原价](https://help.aliyun.com/zh/model-studio/qwen3-8-flash)输入0.8元/百万、输出2.7元/百万估算：

`(8935×0.8 + 570×2.7)/1000000 = 0.008687 元`

这是报告usage对应的计价估算，不是实际账单对账。4次usage均已返回；缺usage次数0；发票/账户实际扣费为UNKNOWN/NOT_RECONCILED，不写成已精确消费0.008687元，也不为对账增加模型调用。

授权5次、实际消耗4次；未用1次已经关闭，remaining=0。所有旧授权仍关闭。本批状态CLOSED_AFTER_SINGLE_EXECUTION，自动重试0、追加额度0；不会为补齐第5次而再调用。

## 证据能力与剩余未知

原始轨迹、最终快照、Runner观察、task-results、host日志和原始probe-result按本机受控用途保留，路径与SHA见raw-evidence-index.json；不push、不外发归档。冻结Provider未保存逐次HTTP请求/响应正文，只有请求/响应SHA、白名单诊断、usage及运行结果。不能声称HTTP正文已归档、不能从哈希复原模型原始语义评估文本，也没有为补正文重新调用或伪造。

本次独立核验是同模型另一调用及分离上下文，不证明不同模型/供应商或统计错误独立；本次成功不解释此前无响应头超时的确切原因。connectedMillis仍null，不用本次成功倒推历史根因。本次确实接受了所使用的动作与核验载荷，不推广为所有schema变体、所有网络条件均受支持。

不合并历史不同协议/预算批次计算成功率，不用这一项固定问题证明未预置问题、长期可靠性、语言质量、学生收益或产品上线达标。既有“真实在线验收未通过”记录保留为当时结论；本次新增一个有界完整在线成功证据。

**本冻结包的一次固定P01完整在线工程探针通过；可靠性与产品质量尚未评估。**

批次已归档关闭，不自动修复、补额度、创建下一轮或推进其他阶段。仅新增本地证据提交；源码、原始权限/范围及主仓库既有改动保持不变。恢复时保留历史轨迹和已关闭授权，撤回归档不恢复额度。
