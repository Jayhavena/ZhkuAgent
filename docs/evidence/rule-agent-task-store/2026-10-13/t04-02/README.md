# T04第二轮：完成容量边界修复与定向验收

本轮2026-10-13 16:45:33开始，25分钟截止17:10:33。目标仅T04；未进入T05。最终保存身份见后续commit-receipt.json，不回改上一轮到期记录。

## 问题与修正

上一轮已实际发现：索引满时，admit先拒绝容量，导致同键重试无法获得已有受理记录。本轮把新键容量检查移至重复查找之后、状态改变之前，分别覆盖create/message/cancel；不扩大任务容量，不新增外发。

新增内部TaskStore同时解决：主体/Session隔离、短期最小历史、澄清后同task新run、锁外证据重验与二次版本检查、响应未知的请求索引查询、重复提交、活动run取消和调度失败表达。设计及限制见docs/design/rule-agent-task-store.md。

## 实际验证

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 最终后端定向单元 | 68项，失败/错误/跳过均0 | ../t04-final-unit-03/results.json及xml；14个Store方法、18个循环方法、36个工具/合同/原文回归 |
| 前端相关回归 | 84项通过 | frontend-green-t04-contract-final-01.log/json |
| 验收入口合同 | 48项通过 | runner-contract-result.json；入口外部执行器为替身 |
| Java调度失败输出跨端消费 | FAILED / TASK_EXECUTION_UNAVAILABLE，0模型尝试 | ../t04-final-unit-03/dispatch-failure-snapshot.json、dispatch-contract-result.json及verify-dispatch-snapshot.mjs |
| 执行身份 | sourceDrift=0、资源检查通过、未剩新Java进程 | 最终results.json及java-green-t04-final-unit-03.json |

Store方法覆盖：创建/回复幂等、同键异载荷、丢响应查询、同task新run且不重复已知追问、并发回复仅一个成功、其他Session/错误token/过期token、改变目标/不明补充、资料撤回、排队取消、容量、重启未知、满索引幂等、调度失败、阻塞工作不持锁且取消需等真实退出。T03循环回归同时检查迟到决策不执行/发布。

先前131项通过为上一轮源码身份；本轮只报告fresh67项，不合并为一个虚构总数。旧MVC曾通过，不因本轮Store容量调整再次运行整个MVC套件。

## 保留的失败记录与未验证范围

上一轮t04-01/README.md和capacity-red-task-store.xml保留实际失败。此次第一次定向运行在编译271个测试源期间达到辅助程序150秒上限，进程树退出，未产生通过结果：java-green-t04-final-unit-01.json及../t04-final-unit-01/maven.log。第二次使用已完成的编译缓存，无源码改动，89秒取得fresh67项通过。

本项没有真实供应商/数据库调用，也未操作浏览器。合成类别规则与决策替身不能证明真实自然语言能力；尚未接HTTP和学生页面。原审核、原评分和原始受控资料不改写。Git只选本项实现、测试、设计、进度与最小验收记录，排除源码重复快照、缓存和其他既有改动。

退出条件：上述检查通过、源码身份匹配、秘密与暂存差异自审通过、本地提交成功。提交前不写“已提交”。下一项T05，单次20分钟；剩余T05—T10共6项。


限定只读评审发现：第三轮仍可能提出第三次澄清，形成无续答额度的等待。本轮补充发布上限：第三轮可结束答案；第三次追问改为STOPPED/BUDGET_EXCEEDED，不发继续token。fresh t04-final-unit-03新增真实三条件三轮测试，最终68项通过，sourceDrift=0、资源退出。此前67项是修复该边界前的历史结果，不能代替最终68项。
