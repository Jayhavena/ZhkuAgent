# T05-65 新请求包执行收口（CLOSED_FAILED）

包SHA256：`ce4ddb851fd59328bd039e75e3e837e5891e08d6011a83ba954530ed0692754e`；jar：`79a22f1c24c0a893d52a888c7e5678e4988f3e8484088ee2ead85ba89259ed70`；实现提交9e28e45。用户于2026-10-16明确批准：一次P02、同task最多2 run、最多10次真实调用、12元上限。执行1个run后失败关闭，**剩余6次调用已关闭不复用**。

[授权申请](REQUEST-APPLICATION.md)｜[完整披露](EXTERNAL-DATA-DISCLOSURE.md)｜[执行记录](execution.json)｜[诊断](diagnosis.json)｜[结果摘要](result-summary.json)。尝试台账见attempt-ledger.json，原始证据见live-9e82970ee7d048adb14fa7f666e8019b/。

执行结果：4次调用全部HTTP 200（SEARCH/READ/ASK/REVIEW）。**决策侧ASK合格**（裸courseType+两个已登记事实，t05-52式前缀失败未复现）；**核验侧失败**：模型在necessaryConditionIds输出带规则前缀键`S10/RETAKE_ONLY:courseType`，宿主以REVIEW_CONDITION_UNKNOWN一票否决（不静默归一化）。run未形成合格WAITING_USER，PUBLIC未注入，第二run未启动。与t05-63死因相同。

attempt-3事件上的INVALID_MODEL_RESPONSE是attempt-4核验失败按设计回填到决策事件的（RuleAgentRunner保留提议与核验双侧失败），不是ASK本身被拒——ASK已通过precheck、能力校验并落盘核验输入快照。

费用：usage估算0.0069754元（6566输入/638输出token，按北京公开价），**实际账单未知不记零**；12元上限未触及。资源：临时容器已删除、专用55518端口临时库已回收、演示库54329未动、探针JVM已退出（残留java进程为Qoder IDE语言服务器，与本探针无关，未按名终止）。

工程结论：T05仍未完成，真实在线验收未通过。剩余缺口是模型对核验合同的条件键命名空间混淆，非宿主工程问题（t05-64本地回放已证明模型输出全对时宿主4次调用内可完成发布）。换模型/改合同/再批须重新授权。

恢复：停留在关闭状态，不修改/覆盖本包及live证据；实现回退须独立逆向提交并重新申请新包，不恢复历史额度。
