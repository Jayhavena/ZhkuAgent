# T05 本地复现与发布契约检查

步骤1已完成，发布契约候选见publication-contract-review.md；生产修正及真实补测未开始。截止预算为20分钟，开始14:12:07 UTC；结束时间见closeout.json。此次保存可复核的诊断检查点，不代表T05完成。

## 问题—改动—证据

| 问题 | 本次改动/操作 | 验证证据 |
| --- | --- | --- |
| P01真实资料已读，合法发布仍失败 | 在既有RuleAgentLoopDatabaseTest增加一个六案例诊断方法，复用受控10条目录、实际检索/reader/引用审计/发布器，只替换模型 | case-matrix.json、sanitized-reproduction.json |
| 组合规则风险未复现 | C05读第5/10条，一般规则联合覆盖HOURS和SEMESTER_CREDITS，逐规则检查仍拒绝 | C05实际事实ID、topicAssessments、MATERIAL_SCOPE_MISMATCH |
| 不完整回答可能误标完成 | C04只提议第4条专业选修事实，实际COMPLETE/1 claim | C04轨迹与实际快照 |
| 防止相关性守卫被误删 | C02正常发布两项事实；C06仅选择公共选修重修事实且补齐课程类别条件，仍拒绝分类问题 | C02/C06对照、第二批复验 |
| 不混淆复现通过与业务通过 | 新方法断言当前缺陷确实发生；矩阵单独标业务GAP/CONTROL_PASS，不把JUnit通过当修复 | verification.json、publication-contract-review.md |
| 数据与资源隔离 | 两次运行各用随机临时库，无卷、原库不操作；迁移22、测试后连接0、容器退出 | 两批database.json/results.json |

本轮只有一个测试源文件修改，没有生产源码、配置、预算、供应商提示或旧记录修改。首次复现选到了一个个人资格别名，第二次仅调整模拟事实选择，去掉该干扰；无关事实反例补齐courseType=PUBLIC，排除条件缺失造成的另一拒绝路径。首次记录保留，不重写。

最终实际验证：JUnit两个方法通过（原整链回归＋新增六案例复现），失败/错误/跳过0，源码漂移0，六个实际快照均通过前端结构合同；这不是八道学生题通过。四个业务案例仍为GAP，两个为正常/拒绝对照。供应商没有构造或调用，端口为本地模拟；没有供应商usage响应，字段保留null，不将模拟次数写成真实外发或0 token账单。

原P01的同一发布缺口已稳定复现；模拟拒绝后重复FINISH触发NO_PROGRESS，与真实模型随后READ并耗尽预算的尾部轨迹不同，不能声称整个真实运行逐步重放一致。候选UUID每次由真实工具动态签发；模拟按实验指定条款选择当次句柄，再从READ观察选择实际factId。它不证明模型会选择正确材料或理解未预置问题，也不是预置学生答案。

## 证据与恢复

- 原始证据：../t05-publication-reproduction-01及../t05-publication-reproduction-02，均不覆盖旧文件。
- 原始publication-reproduction.json留本机；sanitized-reproduction.json是删去原文令牌的派生副本，包含原始SHA及删字段说明；不冒充原始输出。
- run-check.ps1调用项目已有verify-rule-check-refactor.cmd，显式Database、RuleAgentLoopDatabaseTest、专用端口55519、providerAllowed=false。不得把它改为供应商测试或原演示库。
- 测试源码与生产相关文件指纹见source-manifest.json。Git只保存明确测试/文档和必要小型结果；不保存凭证、数据库、PDF、jar、全仓恢复包、原文令牌或全部源码快照。
- 生产没有修正，T05/T06—T10状态不因此通过。下一步的未验证项、规则责任和取舍已列入契约候选，不立即实施或外发。
