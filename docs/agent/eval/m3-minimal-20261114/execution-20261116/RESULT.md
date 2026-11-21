# M3 最小真实复测结果：失败，未完成两轮

本次由用户在2026-11-16明确“确认授权”：新包244c5d272cdd2e3bf2d39601bf1254c8a75a63ee189e6786ebfbdd3a26cdc596，1元上限、两run最多10次、无自动重试。只启动一次Probe；实际执行HEAD ffab531a，冻结源身份93dc4a1c，生产代码为d6d3a2f6。执行前CheckPackage再次通过；未重建或改写冻结jar、包、模板。

## 实际结果与失败点

供应商实际返回2次HTTP 200，responseModel均qwen3.8-flash，均为DECISION；没有独立核验、没有续答。首轮task 0b636425-3da2-4139-94ac-b50caff17f18，run f0bfb967-0bd1-42cb-94dc-0e7192274a17，以FAILED/FAILED/NONE结束，0 Claim。SEARCH_REVIEWED_RULES实际执行且观察为SUCCEEDED，随后模型没有READ，第二次直接提议FINISH_CHECK，并产生kind=CONFLICT、reasonCode=CANCELLED的不匹配组合。CANCELLED对应PROCESSING_FAILURE；CONFLICT本身是合法枚举，不能把单独的CONFLICT值误说成非法类型。宿主在ACTION_PARAMETERS/ACTION_PARAMETER_INVALID拒绝该组合，最终INVALID_MODEL_RESPONSE，未发布规则结论或执行额外调用。

暴露的缺陷M3-LIVE-01：动作Schema对IssueKind与ReasonCode分别列举全部值，没有表达二者配对约束，而领域ProposedIssue构造器要求reasonCode.kind==kind。构造器正确失败关闭，但Schema允许组合与宿主值约束不一致。另有模型跳过读取而尝试结束的行为，本批不能证明必要澄清正确、续答正确、工具选择可靠或真实质量提高。没有为得到PASS改模板、删失败、补题或重试。

## Grader报告的准确含义

绑定器exit 3、NOT_EVALUABLE/TASK_RESULTS_MISSING。宿主在完整任务快照写入前因首轮失败停止，没有task-results.json，绑定器未伪造两轮runId。现有Grader仍运行：exit 1、regressionPassed=false，两case均NOT_EVALUABLE/CASE_RUN_ID_MISSING；Collector保留PARTIAL_TERMINAL_FALLBACK及一条真实失败run。原生报告为report.json/report.md，没有改写。suiteId=M2和M2-CLARIFY/RESOLVE是复用冻结技术合同的ID，不是重用旧M2结果。

因此：实际执行结果为FAILED；冻结两轮评分合同无法完整判定，不能误写为Grader给出了两条FAIL或把NOT_EVALUABLE当作质量通过。现有失败采集/绑定器需要另行改进，原报告必须保留。

## 用量与费用

本批2次调用：输入2547 tokens、输出95 tokens、缓存命中0；无核验调用。按执行前重新核对的北京目录原价输入0.8/输出2.7元每百万tokens，估算0.0022941元。这是[官方目录价](https://help.aliyun.com/zh/model-studio/qwen3-8-flash)估算，不是账单、应付或实际支付。

实际账单UNVERIFIED/null，成功任务数0，每成功任务实际成本null。尝试访问用户指定百炼费用控制台，但当前Preview默认profile显示“登录”，没有已认证账单会话，无法读取Key标识/本批账单；没有用推理密钥冒充账单访问。页面已关闭。不要求再调用模型来核账。

## 证据与资源

原始10文件（两次请求/响应、动作、失败outcome、trace、host日志、Probe结果及授权消费票据）保留本机私有目录，已归档至用户.zhkuagent/m3/evidence/<包SHA>/m3-failed-evidence.zip，逐条摘要验证通过。这里只保存脱敏审计、用量、原生Grader报告、绑定回执、原始文件摘要和归档摘要，不提交受控原文/请求响应/凭据。

Probe resourcesClosed=true；启动器已返回，专用55549无监听，按该发布端口筛选无残留临时容器。冻结包单次票据已消耗，剩余8次不可复用。Docker首次只读就绪检查超时，较长超时只读检查确认linux引擎后标准入口就绪；这不是Probe重试，也未操作共享演示库。

本次实际见证“真实模型非法动作→明确失败→不再调用→关闭资源”的路径，补足该特定响应校验停止证据；不替代超时、网络断开、取消等所有供应商失败验证。M3仍不能按真实任务成功结项。下一步先离线修复/验证契约配对和失败采集，再准备独立新包；本批授权不延长或迁移。
