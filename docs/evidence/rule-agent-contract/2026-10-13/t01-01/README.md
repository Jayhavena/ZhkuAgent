# T01共享合同：实施与验收

日期：2026-10-13。状态：**T01局部验收通过，成果未提交Git。** 本项依据T00工作区候选推进；用户已明确“进入下一步”，故不以待提交状态重复阻塞，但保留T00/T01保存边界。不标记Agent、实际HTTP/页面、学生收益或产品目标完成。

## 问题与实施对应

| 要解决的问题 | 本次改动 | 证据与边界 |
| --- | --- | --- |
| 运行结束、资料不足与问题完成混同 | task/run/result分层；混合子问题、缺口及澄清可以共存 | [正式合同说明](../../../../design/rule-agent-contract-v3.md)、Java类型；不是实际TaskStore |
| 同一结果两端解释不同 | 同一组10个合法快照、44个非法变体；前端消费校验与Java解析 | [共享样例](../../../../../knowledge/src/test/resources/ruleagent/contract-v3-fixtures.json)；合成数据，不是固定回答题库 |
| 模型动作能混入规则正文/越权字段 | 封闭4类NextAction；5个动作样例、3个请求样例；拒绝多余mode/tool/政策正文字段 | RuleAgentJson、RuleAgentContractTest；动作未实际执行，权限守卫待T02/T03 |
| 检索失败被解释成资料缺失 | ToolObservation区分候选/原文/事实，NO_MATCH/UNAVAILABLE/REJECTED有类型对应 | 6个工具观察样例及反例；未连接数据库 |
| 原文合格但展示用途不明确 | evidenceRelations必须指向主张/必要条件/冲突/范围原因，拒绝无关及重复关系；冲突至少双方关系 | 结构检查通过不证明原文语义支持，留T02及独立人工评估 |
| 取消/迟到/续答容易伪报终止 | STOP_REQUESTED不能terminated；当前发布与run/version一致，续答允许保留较早结果 | 合同样例及状态转换定义；实际CAS、幂等、owner执行待T04 |
| 新测试跑不了或进入旧日期目录 | 登记RuleAgentContractTest，新增EvidenceRoot，保留原默认路径与防覆盖/选类限制 | [验收入口48项](runner-contract-result.json)；不是供应商准入变化 |

## 实际验收

- [最终后端批次](../t01-contract-final-05/results.json)：93项通过，失败/错误/跳过0；其中新合同7个JUnit测试方法，内部遍历共享样例与反例；旧证据核验19、决策42、控制器25项通过。fresh XML、源码无漂移、资源退出均通过。
- [前端记录](frontend-green.json)及[日志](frontend-green.log)：84项通过；新合同43项、旧前端41项。不是浏览器E2E，页面没有接入新校验器。
- [验收入口记录](runner-contract-result.json)：48项通过，包含旧模式与新测试登记、显式证据根、危险路径和旧报告/来源/锁检查。其Maven是既有计数替身，真实Maven证据在最终后端批次，二者不能混用。
- [变更清单](change-manifest.json)：9个交付文件；既有文件仅修改knowledge/scripts/verify-rule-check-refactor.ps1及scripts/tests/verify-rule-check-refactor-contract.ps1，其余为本项新增文件。与T00快照比较，不把旧dirty差异算作本次成果。
- [本项脚本差异](t01-tracked-scripts.patch)对照T00文件，不是HEAD总差异；before目录保留原文件。所有业务入口、POM/锁文件、学生页面、旧审核和回放不在本项改动范围。

新合同样例数量不等于自然语言验收题数量，也不声称“93题查证成功”。判断的是类型、关系及状态边界，不是模型理解、原文语义或用户决策正确性。

## 失败、定位与复验记录

1. 前端能力不存在时先建立样例；[行为RED](frontend-red-behavior.log)显示合法样例被未实现校验器拒绝，不把仅有模块缺失当作最终行为证明。
2. [后端环境失败](java-red-environment-attempt-01.json)：增量编译时受限会话无法读Maven缓存。经本机授权环境离线复验，未修改缓存权限或下载依赖。
3. [新测试编译失败](java-red-test-compile-attempt-02.log)：AssertJ泛型重载不明确；明确JsonNode类型后再运行。此失败不是合同RED，不计通过。
4. [后端行为RED](java-red.log)：合法合同/动作及预算能力未实现，5个方法实际执行，3个错误；非法输入拒绝方法通过不代表合同完成。
5. [首轮实现失败](../t01-contract-green-01/results.json)：Java long与fixture IntNode导致内存节点相等性误判；改为比较实际序列化再解析的JSON，没有放宽字段或覆盖要求。
6. [故障分类RED](../t01-observation-red-02/results.json)：NO_MATCH曾能配检索故障reason；增加状态—原因关系后复验。候选也不能被改标签变成核验事实。
7. [前端字节预算RED](frontend-byte-budget-red.log)：前端漏查UTF-8原文总字节；补同一32KiB限制后，84项通过。
8. [null边界RED](../t01-null-red-04/results.json)：JSON null跳过record构造校验；所有入口统一拒绝。最终93项通过。

所有失败记录保留，没有降低参考或重写旧通过记录。真实供应商调用0、数据库操作0；正式在线装配、学生页面接线均未实施。

## 设计取舍与约束

- 新合同集中在knowledge/ruleagent职责层，Java records+严格Jackson解码；不新增微服务、不引入旧通用Agent工具、不升级框架。
- schemaVersion=3沿原设计，旧回放保持自己的协议和ObjectMapper；新增前端校验器尚未被页面导入。
- 首期采用原建议4次决策/6动作/25秒等默认防御预算；不是外发授权、性能SLA或真实供应商已可行的证明。T05实测不成立时记录合同变更。
- 静态合同只定义owner、version、幂等键、请求、状态；没有预先实现TaskStore、HTTP接口或执行循环。
- 严格字节、字段、状态及引用关系不等同“事实受控制”。合法factId但不支持所问范围的反例仍需T02/T03及语义评估发现。
- T00的Git保存仍待完成，T01也未暂存/提交；本地恢复身份使用T00原包+T01变更清单/增量包，不补造SHA。上传许可不由本项通过自动获得。

## 唯一后续任务

下一项为原计划T02/D2：把SEARCH/READ接入真实合格目录和reader，建立task句柄、原文核验及必要增量事实审核。T01合同与样例供它使用；不新增另一个测试输入集阶段，不提前接真实供应商或修改学生界面。
