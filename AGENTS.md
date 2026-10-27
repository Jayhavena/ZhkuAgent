# ZhkuAgent 开发约定

- 在关键业务边界、设计取舍、状态约束和容易误用的位置添加简明、准确的中文注释。
- 注释应说明“为什么这样设计”或“不这样做会有什么风险”，不要机械复述代码，也不要逐行注释。
- 注释随代码同步维护；代码语义或约束变化时必须一并更新相关注释。

## 本机凭证约定

- 每次执行数据库或 DashScope 操作前，都重新检查该操作所需的 `ZHKU_DB_PASSWORD` 或 `DASHSCOPE_API_KEY`；当前进程没有值时，再检查 Windows 用户级、系统级环境变量，并把可用值注入本次子进程。不要沿用上一次对话中观察到的环境状态，也不要要求用户重复提供已配置的密钥。
- 不在命令参数、仓库文件、日志、测试报告或答复中打印密钥值。数据库写入验收只使用专用临时测试库，不能因为取得密码就写入原演示库。
- 本机启动脚本应优先使用已配置的环境变量；既有数据目录中的 `infra/.env` 只作为数据库密码的兼容回退。启用 RAG / 校园 Agent 时必须由环境变量提供 `DASHSCOPE_API_KEY`。
- 若进程、用户级和系统级环境均无法读取所需变量，应先检查启动器的环境继承与运行目录，再准确报告当前会话的访问边界；不能声称已调用数据库或真实供应商，也不能编造凭证。

## 用户终端与 Codex 运行时

- Codex注入的PATH不等于用户Windows PowerShell/CMD的PATH。2026-10-06确认用户终端无法识别`pwsh`，但本机`%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe`存在。每次使用先检查实际文件，不假设缓存路径永久有效。
- 人工演示优先使用`knowledge\scripts\start-controlled-manual-demo.cmd`；它查找可用PowerShell 7并保留参数和退出码，不修改全局PATH。`-CheckEnvironment`仅检查启动环境，不运行数据库、模型或Maven。
- 交付给用户的命令须在普通用户终端环境验证；不能用Codex中的`Get-Command pwsh/rg`成功来证明用户也能调用。不必要的辅助CLI依赖应移除或提供明确替代路径。
- 中文终端验收还须覆盖Windows PowerShell5.1的GBK解码边界；Java的file.encoding不等于终端/父进程编码。发生乱码时先q退出，在调用启动器前把当前会话Console.InputEncoding、Console.OutputEncoding及OutputEncoding一并设置为UTF8Encoding(false)，再启动；不修改系统语言或全局编码。演示内层也显式使用UTF-8，不依赖Agent终端的默认值。

## 开发检查点与 Git 提交

- 执行 [Git提交流程](docs/git-commit-workflow.md)：明确结果 → 验收通过 → 核对暂存差异 → 本地提交 → 记录SHA → 下一任务。
- 用户于2026-10-13授权本项目后续按该规则自动保存本地提交，不再逐次询问；push、PR、标签、部署和历史改写不在此授权内。
- 只暂存本任务及必要依赖的明确文件清单，不混入既有无关改动、凭证、数据库、整本PDF、构建产物或临时恢复包。验收未通过不得标成完成提交。
- 提交记录说明问题、实现、实际验证、未验证范围和恢复方式；源码与验收身份须一致。工程检查点不等于产品上线或收益达标。

## Agent skills

### Issue tracker

GitHub Issues in this repo, driven by the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Default five-role vocabulary: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

single-context. See `docs/agents/domain.md`.
