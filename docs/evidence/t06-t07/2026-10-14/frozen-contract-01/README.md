# T06/T07：冻结工程依赖与共享合同集成检查点

本会话基线7d7533d987ad818be13a210ac8c45594f92dfbbd；T05权威冻结来源58e1ef751e8a659178afaac04830ae3df86ad579（实现99da32c，P01_CLOSEOUT_07失败证据保留）。独立分支codex/t06-t07-student-http；工作目录未与T05共用。

## 已实现结果

按selected-paths.txt的28个明确路径应用5ee26d8到58e1ef7的已提交差异，只导入ruleagent工程依赖、对应测试及共享Java/JS合同。source-identity.json逐项核对Git blob完全一致，没有改写T05提示、事实、主题、发布、循环或预算。没有复制T05未提交工作区、真实探针运行资料或旧外发额度。现有T06 HTTP、安全路由及T07页面/工作流均保留。

统一schemaVersion=3最新增量：QUESTION_UNDERSTANDING_UNRESOLVED与ANSWER_COVERAGE_INCOMPLETE均为PROCESSING_FAILURE；每run最大模型尝试5，T05_CLOSEOUT_BUDGET_04。预算值不是供应商许可；本轮真实调用0。TASK/turn/run/version、澄清、覆盖及依据关系沿用权威合同，未各自定义新语义。

新增本会话模拟HTTP/页面检查：理解未完成返回FAILED/NONE；漏答返回FAILED/PARTIAL并保留独立已确认内容；5次模拟尝试元数据可跨端消费；缺口文案保持处理失败，不伪装成DATA_GAP。Java模拟执行器限定于测试；没有正式在线替身或隐式回退。合并验收入口类登记，保留双方原选类。

## 实际验收

独立后端134项通过：Contract7、TaskStore14、HTTP1、Runner22、Gate15、Provider46（本机假供应商HTTP）、EvidenceTools12、ReviewPayloadBudget1、v2安全16。新增HTTP边界再次通过，http-exchanges.json为实际随机回环HTTP输入/输出；资源队列/activeJobs为0、宿主监听关闭。框架响应202不代表任务完成，取消仍等待最终快照。

前端167项通过，学生构建通过；新增两个原因码及5次尝试样例均经权威验证后实际SSR展示。该组合版本未重跑真实浏览器、nginx或数据库；不能用上轮不同SHA的浏览器证据替代。本轮没有任何真实供应商请求或数据库操作。

首轮选类共93项，其中91项通过、OnlineFactory两项请求包资料检查错误，失败保留于maven-first.log。根因当前隔离目录的B03审核依赖中“公共选修单学期学分上限审核知识片段.json”字节指纹不符，且知识原始资料目录不在worktree；详见external-asset-check.json。未改期望指纹、审核材料、资格守卫或T05测试以凑通过。这两项仍未通过，不包含在134项独立通过统计中；其余32项Factory检查的首轮通过也未重复计入。源码已编译，不代表完整Factory/资料整链通过。

## 仍需装配和确认的边界

| 现有定义 | 具体问题 | 建议增量 | 影响模块 | 对应测试 |
| --- | --- | --- | --- | --- |
| 原文tools由Factory构造回调创建，Work只暴露run/revalidate | HTTP OriginalReader还无正式端口 | 本会话在装配层保存有界Owner/task工具索引，读取仍调用当前tools.original；不改T05工具或放宽资格 | HTTP配置/原文适配 | 跨Owner、跨task、到期、撤回及目录变化拒绝 |
| 既有RuleCheckPreviewApplication是受控回放装配 | 正式Task Factory/原文端口与在线启动profile尚未装配 | 复用已有身份、数据库与HTTP边界，显式在线配置，不得将回放装配伪装成在线 | HTTP装配/启动入口 | 未授权在线默认关闭、缺配置拒绝；模拟限定于测试 |
| 已审核资料引用原字节指纹，worktree没有原PDF | 两个请求包检查无法通过 | 在独立验收范围提供明确只读外部资产映射及身份核对，不复制未完成源文件、不修改原审核定义 | 本机资料装配/验收 | 指纹全部一致、原件资格可复查 |

下一项唯一结果：实现HTTP装配层的Owner/task原文工具索引，接冻结Factory配置，并先用模拟端口证明归属与fresh原文访问；随后再核对资料依赖与同版本浏览器流程。真实模型最终通过仍依赖T05在线成功及新的明确外发授权。本会话不重试P01、不进入P02/T08，不改历史失败、未知usage/费用或已关闭的4次额度。

本检查点只能标“工程局部集成通过，外部资料/真实装配/同版本浏览器待验”；不是T05/T06/T07在线最终完成。恢复以本地检查点为准，外部资料、凭证及许可不随Git恢复。本轮主动时段上限20分钟，不继续追加其他修复。
