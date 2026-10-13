# T00工程基线验收与恢复记录

日期：2026-10-13。执行范围：仅T00，不含T01合同开发或在线Agent实现。

**结论：工程检查通过，已保存可核对的本地工作区候选；Git保存尚未完成。** 不把当前HEAD等同于本次验收全部成果，也不标记新Agent、学生收益或产品冻结通过。

## 本次解决了什么

| 原问题 | 本次动作与证据 | 结果与边界 |
| --- | --- | --- |
| HEAD无法定位当前全部成果 | [候选清单](candidate-manifest.json)、[初始状态](git-status-before.txt)、[实际选定差异](candidate-changes.txt) | 起点zhkuagent-modules / 139194224d12b954fe9425d63a42a3de11e1dd43；候选870个文件，不能直接git add整个仓库 |
| 不知道最小后端合同是否仍能运行 | [knowledge检查记录](knowledge.json)、[实际fresh报告](../../../rule-check-refactor/2026-10-07/agent-baseline-20261013-01/results.json)、[runtime报告](runtime-tests.json) | knowledge三类86项、runtime两类10项通过；失败/错误/跳过均0；共96项后端选定测试 |
| 前端测试名容易冒充浏览器 | [前端测试](frontend-tests.log)、[构建记录](frontend-build.json) | 41项通过，静态build通过；合同/渲染测试，不是浏览器E2E |
| Spring AI声明不同，是否需要先升级未知 | [实际依赖树](dependencies.log)、[打包](package.json) | runtime独立解析1.0.0；knowledge将runtime传递的Spring AI统一管理到1.0.9，打包及选定合同通过。尚未验证新动作适配器，当前不要求升级 |
| Codex PATH不能证明用户终端能运行 | [PS5.1终端探针](terminal.log)、[真实启动记录](replay-smoke.json) | 从Windows系统+用户PATH重建子进程环境；PS5.1 UTF-8环境检查通过，PS7普通PATH环境完成真实回放启动。未借此声称用户亲自点击验收 |
| 旧回放启动与资源回收需复验 | [Smoke结果](../../../rule-check-refactor/2026-10-10/package-02/startup/20261013-093852-90ccb9c6/results.json) | 两次实际启动、登录、R01回答、原文核对、正常退出；临时库55513、后端58133；providerCalls=0、sourceDrift=false、resourcesClosed=true |
| 本地资产不是Git提交的一部分 | [外部资产清单](external-assets.json)、[恢复包核对](recovery-check.json) | 原PDF存在且hash匹配；未复制整本PDF、数据库或环境密钥；依赖缓存仍需另行准备 |
| 缺可复核的工作区快照 | [源码恢复包](workspace-source-recovery.zip)、[构建产物身份](build-artifacts.json) | 870个归档条目逐个SHA256与manifest一致；包约2MB，限本机受控恢复，未批准公开上传；没有执行异地重建 |
| 容易把测试过程改动算作业务修复 | [最终指纹](source-final-check.json) | 所检查778项实现源码/脚本/配置无变化；只新增本批证据与辅助程序 |

## 执行中的失败与处理

1. 本批PS5.1辅助探针最初使用无BOM中文脚本，解析失败；[失败记录](terminal-attempt-01.json)和日志保留。仅将本批探针改为UTF-8 BOM，复验通过，不修改系统编码或正式启动器。
2. 本批辅助程序最初用Push-Location却未切换.NET进程目录，npm在仓库根寻找package.json；[失败记录](frontend-build-attempt-01.json)保留。仅修辅助程序实际子进程目录，随后原npm build通过，前端配置未改。
3. 受限会话不能读取CIM进程命令行，且凭证环境返回值与普通会话不同。使用获准的本机读取重新检查，用户级数据库/供应商变量可读，仅输出布尔值；未打印密钥，未要求重新提供。每次数据库操作由正式启动器再次检查凭证。
4. Docker引擎最初未运行，正常启动Docker Desktop后复验；[启动记录](docker-start.json)。未关闭Codex、浏览器或既有服务。完成后保留Docker引擎，仅回收本批临时容器和宿主。

这些是验收辅助/运行环境问题，不能宣传为修复了学生查证能力。本次数据库数据是专用临时库中的既有规则与合成账号，不写原演示库；模型外发0。

## Git与凭证检查的剩余事项

- 当前暂存区仍为空、HEAD未变，没有执行branch/add/commit/tag/push。
- [候选差异文件](candidate-changes.txt)与[未选择的其他差异](other-changes-not-selected.txt)已分开。候选包含必要历史审核及回放依赖，**本地恢复资格不等于可公开提交资格**；提交时源码/构建合同与受限历史资料应分别审阅。
- 用户级global ignore无法读取；盘点使用命令级core.excludesfile空值，以核对仓库自身规则，不修改全局配置。这不是全局ignore检查通过。
- [模式检查](secret-pattern-check.json)命中测试脚本中的伪造私钥样例；源码已核对它用于验证拒绝私钥，并非真实私钥。
- [配置值扫描](configured-secret-check.json)未发现DashScope配置值；数据库配置值子串命中了10个测试文件。这只是子串命中，不能直接认定实际凭证泄漏，也不能无证据清除；提交前需按测试数据/字段上下文核对。未经此项自审，不称“候选可公开上传”。检查没有覆盖整个Git历史。
- T00的Git保存退出项保持待完成。可恢复身份暂为“实际基底SHA + 工作区manifest + 本地恢复包SHA”，不是新提交SHA；后续实施应引用此身份，不能误写“所有成果已经提交”。

## 如何恢复与复验

1. 保留现有工作区，不直接解压覆盖。新建单独恢复目录，核对zip SHA256及安全相对路径，恢复manifest所列870项；已有同名内容须先比较，不删除用户其他文件。
2. 恢复仓库结构及根POM中声明的模块目录；本包包含本次knowledge/runtime/frontend构建与回放输入，并非全项目所有历史模块。若需要完整工程历史，另取实际基底Git提交；本包也不是可公开发布仓库。
3. 2025版原PDF必须仍位于外部资产清单指定相邻项目路径且hash匹配；审核/回放材料保持指纹。需要换路径时先处理装配合同，不能随意改资格hash。
4. Java21、Maven3.9.9缓存、本机前端依赖及Docker镜像需要准备；本次未下载或升级依赖。密码从本机环境重新导入，不放在包或命令参数中。
5. 按[原计划验证入口](../../../../superpowers/plans/2026-10-13-rule-check-agent-plan-review.md:317)选择最小检查；知识验收Phase必须换唯一批次名。当前证据辅助程序记录本次实际命令，不保证可以覆盖本批目录重复运行。
6. 旧回放Smoke仅能证明既定R01及启动/出处/退出，不能证明完整40题重新通过、任意自然问题能力或新Agent在线。新Agent相关能力留待T01–T09。

## 退出状态

| 检查 | 状态 |
| --- | --- |
| 选定后端/前端测试、构建、依赖解析 | PASS |
| 旧回放临时启动、原文、两次正常退出 | PASS |
| 候选清单、外部资产、归档逐条hash、源码无漂移 | PASS |
| 新目录下重新构建 | NOT_RUN；本次只验证归档内容 |
| 整套旧40题、真实浏览器、新Agent、学生收益 | NOT_RUN；不属于本项最小基线验收 |
| 当前成果Git提交及提交范围最终凭证/资料自审 | PENDING |

下一项仍是原计划T01共享合同，**没有新增开发阶段**。开始前先明确当前候选的保存身份及上述Git待办；本次不提前实施T01。
