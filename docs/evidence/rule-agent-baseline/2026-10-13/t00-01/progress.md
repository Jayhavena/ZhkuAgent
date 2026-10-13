# T00 执行记录 — docs/superpowers/plans/2026-10-13-rule-check-agent-plan-review.md

- 范围：仅T00工程基线；T01–T10不执行。
- 起点：zhkuagent-modules / 139194224d12b954fe9425d63a42a3de11e1dd43；暂存区为空，350项既有工作区状态。
- 已确认当前目录本来就是linked worktree，无需新建；新工作树无法包含当前未提交成果。
- Ruling：采用仓库证据目录记录T00，不使用会生成Git/提交任务的完整实现工作流；本项仅验收既有能力，业务实现及TDD变更不在范围。
- Ruling：Git读取使用命令级core.excludesfile空值，以查清项目自身忽略规则；不修改用户配置。原全局ignore访问失败仍保留限制。
- 初始检查：778项源码/脚本/关键配置指纹已记录；当前会话CIM进程命令行读取被拒绝，未据此杀进程或声称不存在其他构建。
- 运行约束：每个外部检查最多150秒、仅终止自有进程树；所有纯单元/构建子进程清除数据库与模型凭证。模型调用0；数据库仅后续获准的旧回放专用临时库。
- 待完成：合同测试、前端测试/构建、打包/依赖检查、回放Smoke、候选和恢复清单、最终源码身份与状态核对。
- 辅助终端探针首次失败：Windows PowerShell5.1按ANSI解析无BOM中文脚本。原失败记录保留terminal-attempt-01；仅将本批探针写为UTF-8 BOM，业务/启动器源码未改。
- 辅助构建首轮失败：Push-Location不保证.NET子进程继承该目录，npm从仓库根查找package.json。保留frontend-build-attempt-01；只修本批辅助程序实际进程工作目录，不改前端构建配置。
- 最终工程检查PASS：knowledge86/runtime10/frontend41；前后端build、实际依赖、普通PATH环境旧回放两次Smoke及退出、870条恢复归档hash、778项源码无变化。Git保存仍PENDING，不实施T01；报告和results.json保留未知/凭证自审限制。
