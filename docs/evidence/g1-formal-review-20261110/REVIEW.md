# G1 正式独立复核与迁移接收

结论：**ACCEPTED_FOR_CONTROLLED_ENGINEERING_CANDIDATE**，范围仅限本次迁移的生产/测试差异；不代表真实模型质量、G2 扩展验收或学生试用准入已经通过。

源候选 `6e149df51db23668849d84098d5111016426edae`，迁移基线 `42d4491d90022a9357f06e247f3300f69b43beb0`。新分支 `release/g1-reviewed-20261110`；实际接收 commit/tree 由该分支提交记录及后续统一回执确定。不能仅凭基线 HEAD 代表源候选的完整输入。

## 审查范围与决定

独立只读审查由 `g1_independent_review` 执行，协调执行者另行运行测试。审查覆盖 42d4491d→6e149df5 的 Provider、PublicationGate、PublicationProtocol 及对应测试，不把原 G1 回执中“仅 Gate 生产修改”扩展到跨基线总差异。

核心 Gate 约束：同子问题混合其他 unresolved 原因不可判纯资料缺口；显式未识别 requirement 须由自己的 UNSUPPORTED_DIMENSION 跨度完整覆盖，部分交叠不能通过。回归覆盖 Gate 以及 Runner→Publisher。

独立审查初始提出“空 requirements/all identified 时 unsupportedDemand 可能为空流通过”的疑点。经协议异议复核，审查者撤回阻断结论：协议允许真正未知需求仅以 unresolvedDemands 表达，不要求所有需求都对应 requirement。强制新增该要求会误拒合法资料不足；当前没有证实越权发布主张，因此不做推测性修复。后续可单独补充 standalone unresolved 的文档/测试，不作为本轮阻断。

当前确认的阻断性代码 findings 为 0。独立审查不冒称运行测试；执行结果见 acceptance.json。

## 迁移与离线验收

只迁移 `knowledge/src` 内 10 个必要生产/测试文件，逐字节匹配源候选。明确排除 `338c1ea1` 的开发问题文档、`f1637ffc` 的原始冻结包/日志与 G1 原始 request-package；这些历史记录继续本地保留，不直接推送。

新隔离 Worktree 正常 Fork 运行 9 类离线测试，共 128 项：127 PASS、1 SKIP、0 FAIL、0 ERROR，Maven BUILD SUCCESS。SKIP 是既有 Host 显式操作分派，不计通过，运行范围归 G2。JUnit 各类计数、每例名称和 XML 原始摘要保存在 acceptance.json；原始 XML 及日志保留本地 target，不上传环境 properties/stdout。

首次运行有 1 ERROR：测试预设的诊断输出目录未建立。创建本次 target 目录后重跑全部选类；没有修改产品代码或测试断言，首次失败不隐去。

此接收只完成 G1 工程候选的独立审查与限定离线接收。G1 远程交付需另行精确 Push/PR 授权，PR #1 原始包/范围阻断未解决前不授权其直接合并；G2 扩展浏览器验收和最终试用决定单独进行。
