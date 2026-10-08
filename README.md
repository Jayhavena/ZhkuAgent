# 仲园 Agent（ZhkuAgent）

**当前受控校园网页版本已获用户确认：P4 人工功能验收通过，功能开发收口。** 本机 Nginx 下可登录、提交学分任务、查询本人历史和进行重修澄清；[一键启动与短演示](docs/manual/controlled-campus-web-demo.md)。P1 固定真实模型12/12、P4定向回归227项及校园真库11项、后续页面整理36项前端测试有各自执行证据；[人工确认与最终收尾](docs/evidence/controlled-web-demo/2026-10-06/p4/final-closeout/manual-acceptance.md)区分用户报告、自动测试及当前演示退出状态。本次文档收尾没有重跑这些测试，不宣称生产部署或全部浏览器质量检查通过。下文4A/3B的替身说明及计数仅对应历史时点。

当前 **4A＋3B 已验收**，见 [整合核验](docs/evidence/milestone-4a/2026-10-05/A/20-4a-3b-integration-review.md)。Session 恢复仅限本人重修澄清控制状态，不恢复完整聊天记录或自动续跑 Run。项目面向校园资料维护者和本机演示开发者：根据用户提供的虚构公共选修课计算合计，核对已发布的学分上限及出处，证据不足时转人工核实。

**Milestone 4A 本机受控身份与 Run 归属入口**的独立验收见 [4A 历史记录（V19 时点）](docs/evidence/milestone-4a/2026-10-05/A/18-4a-acceptance.md)，后续会话验收见 [3B 记录](docs/evidence/milestone-3b/2026-10-05/B/07-3b-acceptance.md)。合成 USER A/B 只能访问自己的 Run 和历史；MAINTAINER 不继承 USER 权限。实际回环 HTTP、不同 PID、旧 Cookie 失效和归属隔离已验证，该阶段模型为 src/test 替身。原单人匿名入口独立保留；两个模式不可混用，也不能通过隧道或代理变成生产多用户服务。代码未提交、未推送。

前一版 [2026-10-04 三批收口](docs/evidence/single-session/2026-10-04/r22/README.md) 的去重407项及 [4A/V19 测试报告](docs/evidence/milestone-4a/2026-10-05/A/17-final-test-report.md)保留为各自时点的历史结果。当前命令、计数与去重来源见 [3B 最终测试报告](docs/evidence/milestone-3b/2026-10-05/B/08-final-test-report.md)，相关覆盖去重591项，不代表全仓测试。真实SDK对接合成服务端只验证传输协议，不等于真实供应商验证。

## 三个模块，两个应用

| 目录 | 职责 | 启动方式 |
| --- | --- | --- |
| `agent-runtime/` | 共享任务生命周期、Run/Snapshot、ToolCall、Event、RuntimeError | 公共库，无独立服务入口 |
| `agent/` | 通用 Agent、独立主循环、原前端与工具 | 见 [Agent README](agent/README.md)；模型服务不在本阶段验收范围 |
| `knowledge/` | 资料治理、审核发布、问答与校园学分 Agent 独立主循环 | 见 [Knowledge README](knowledge/README.md) |

两个应用分别依赖 `agent-runtime`，不互相依赖。校园政策、证据、资料可见性、工具白名单、调用顺序和转人工规则保留在 knowledge；共享 Runtime 不处理校园业务。两个应用均有模型相关依赖，数据库迁移资源各自隔离。

## 构建与入口

Maven 项目坐标统一使用 `com.zhku`：父项目 `com.zhku:zhku-agent`，模块为 `agent-runtime`、`agent`、`knowledge`，独立图片工具为 `com.zhku:image-search`。当前候选版本为 `0.1.0-rc.1`，制品路径与启动器同步更新。工程验收及真实资料放行状态见 [候选版说明](docs/releases/v0.1.0-rc.1/README.md)。模块构建请从根目录使用 `-pl ... -am`，不要引用旧的 `com.yupi` 本地缓存。项目范围、非校园内容及保留建议见 [内容盘点](docs/zhkuagent/2026-10-06-项目范围与非校园内容盘点.md)。

在仓库根目录的 PowerShell 中使用 JDK 21；首次构建可能需要下载 Maven 和依赖：

```powershell
$mavenRepo = Join-Path $env:USERPROFILE '.m2/repository'
.\mvnw.cmd "-Dmaven.repo.local=$mavenRepo" -DskipTests package
# 分别构建应用及共享依赖：
.\mvnw.cmd "-Dmaven.repo.local=$mavenRepo" -pl knowledge -am -DskipTests package
.\mvnw.cmd "-Dmaven.repo.local=$mavenRepo" -pl agent -am -DskipTests package
```

打包成功只证明构建，不证明模型、数据库或网络服务可用。knowledge 本机预览默认 8123、管理入口 8124；通用 Agent 并行启动示例使用 18123，避免端口冲突。实际 knowledge 网络地址包含 `/api` 前缀。当前受控网页与真实校园模型由专用演示启动器启用；上述构建命令不会启动它们。RAG和通用Agent模型服务不在本版网页演示范围。

历史构建曾遇默认缓存权限问题；上述命令显式选择用户目录缓存，不修改系统设置或构建文件。当前验收使用缓存wrapper3.9.9及安装版3.9.15，实际参数和失败/重试记录见本版本报告。

## 当前状态与演示导航

| 文档 | 用途 |
| --- | --- |
| [4A 验收与限制](docs/evidence/milestone-4a/2026-10-05/A/18-4a-acceptance.md) | 合成账号、Run归属、真实回环HTTP及进程验收，阶段边界和回退 |
| [4A 运行与演示](knowledge/README.md#milestone-4a本机受控身份与run归属) | 专用临时库脚本、认证路径及当前默认关闭条件 |
| [4A 历史测试报告](docs/evidence/milestone-4a/2026-10-05/A/17-final-test-report.md) | V20激活前的4A/V19独立验收：XML、失败/修复、去重统计、构建与物理退出证据 |
| [上一版三批验收](docs/evidence/single-session/2026-10-04/r22/README.md) | 历史测试、构建、源码差异及当时未验证项 |
| [上一版受控演示](docs/evidence/single-session/2026-10-04/r22/02-local-demo.md) | 原Golden/不可见场景、撤回结果及进程对账路径 |
| [R1.4传输验收](docs/evidence/single-session/2026-10-04/r14-completion/README.md)、[R1.5展示门禁验收](docs/evidence/single-session/2026-10-04/r15/README.md) | 分批RED、修复、差异、回归与资源/许可边界 |
| [Milestone 3A 当前验收](docs/evidence/milestone-3a/05-验收记录.md) | 归档、历史、实际进程中断对账的完成范围与未验证项 |
| [3A 实施与受控演示](docs/evidence/milestone-3a/02-实施记录.md) | 默认关闭、显式目标、只读历史、关闭与故障运维 |
| [3A 本轮测试报告](docs/evidence/milestone-3a/04-测试报告.md) | 基线、修改后回归、真实 JDBC、实际 JVM 重启与原 Golden 分开统计 |
| [3A 后端后续开发计划](docs/superpowers/plans/2026-10-03-post-3a-backend-roadmap.md) | 工程优先：身份与归属、Session、幂等预算、受限恢复、真实模型及非本机 HTTP；尚未实施 |
| [当前状态](docs/evidence/milestone-2.5/01-当前状态.md) | 已实现、验证范围、限制及下一阶段候选 |
| [本地预览与演示指南](docs/evidence/milestone-2.5/02-本地预览与演示指南.md) | 网页预览条件、受控 Golden Task、请求/查询/取消示例 |
| [阶段性发布说明](docs/evidence/milestone-2.5/03-阶段性发布说明.md) | 本机候选的产品范围和兼容性说明 |
| [本阶段范围、基线与验证](docs/evidence/milestone-2.5/04-范围基线与验证.md) | 本轮实测、历史结果、源码审计和未执行项 |
| [Milestone 2.5 验收记录](docs/evidence/milestone-2.5/05-验收记录.md) | 文件差异、完成标准和回滚 |
| [Milestone 2.6 后继修复与复验](docs/evidence/milestone-2.6/05-验收记录.md) | 临时库口令传递修复、本轮数据库验收和回归；保留 2.5 历史证据 |
| [项目目录与代码索引](docs/zhkuagent/项目目录与Agent代码索引.md) | 源码导航；其中历史数字和清理语义以当前 README / 阶段验收为准 |

固定验收证据：[Milestone 1A](docs/evidence/2026-10-02-milestone-1a-验收记录.md)、[Milestone 1B](docs/evidence/milestone-1b/05-验收记录.md)、[Milestone 2](docs/evidence/milestone-2/05-验收记录.md)。原 188 项相关回归包含 12 项组合测试，另有 11 项临时库验收；不是全仓库测试或真实用户效果指标。各阶段报告区分本轮与历史结果，3A 统计以新报告为准。

## 数据与使用边界

原匿名模式只供单人本机预览；4A受控模式提供合成账号的身份与Run归属隔离，但仍只监听回环。身份和ADMITTED同事务绑定；他人/不存在资源统一404，数据库核验失败不返回缓存正文，账号停用或revision变化使旧登录失效。3A归档与中断对账继续保留，不自动续跑。3B已验收本人重修澄清控制状态持久化与跨PID恢复；当前已有有限真实供应商样本及用户报告的本机网页功能验收，非本机/生产多人权限、完整聊天历史持久化、备份恢复和真实学生效果尚未验收。

展示答案时重新检查原来源许可：资料撤回、版本/范围变化或许可无法核验时，隐藏正文、来源及含定位信息的步骤，保留原执行状态；SUCCEEDED不等于当前可以展示。证据仍为REFERENCE_ONLY，不证明同版本正文不可变。校园同步模型有独立资源句柄、最多2个在途、零自动重试及有界关闭；不承诺任意永久阻塞可以在线程内硬终止。

原匿名入口默认仍只有内存运行记录；4A受控入口默认关闭，启用时强制显式归档及专用数据库目标。历史状态 `ADMITTED / FINALIZED / INTERRUPTED` 与执行状态分开；历史没有 `terminated`，存储提交不证明 HTTP 送达或最终事件已发布。默认保留 7 天、最多 10,000 条，正文受来源可见性与 16 KiB 字节上限控制；单实例锁仅约束启用归档的参与者。详见 [使用与限制](docs/evidence/milestone-3a/02-实施记录.md)。

主要演示输入为虚构课程 `ART01=1.5`、`HIS02=2`；受控验收输出为 3.5 学分合计、已发布的 4 学分上限及来源。项目不推断个人选课资格、不访问真实教务成绩、不代办校园事务、不保证模型自主完成所有任务。

本机数据库卷、原始 PDF、上传文件和凭证不随代码目录搬迁。`DataRoot` 指向既存资料目录，不会自动切换数据库；数据库写入验收只能使用专用临时库。2.5 记录的口令命令参数问题已在 [2.6](docs/evidence/milestone-2.6/05-验收记录.md)独立修复：2026-10-03 实测 8 项脚本检查、188 项相关回归和 11 项专用临时库验收通过，模型仍为替身。2.5 原日志及哈希保留，不改写历史结果。

历史文档按其日期和基线理解；其中旧路径、M0/M1/M2 命名及独立模型冒烟不代表当前 1A/1B/2 的运行验证。旧教学技术范围见 [迁移前技术概览](docs/agent/迁移前README.md)。

## 2026-10-05：3B业务Session验收

4A之后按4A3B-C1-FROZEN完成B2～B5：活动V20与匿名NULL隔离、本人JDBC会话、重修两轮澄清、真实HTTP和两个不同PID恢复均通过。旧登录Cookie在旧PID退出后失效，同一稳定用户重新登录可继续数据库中的重修澄清控制状态；不恢复完整聊天记录或自动续跑Run。

3B默认关闭，仅在原受控profile及显式专用目标下开启`zhku.controlled-preview.conversations.enabled=true`；身份、Flyway、Run与Session使用同一目标，最高迁移V20，不回退Memory。四个JSON接口为POST `/api/zhku/controlled-preview/v1/conversations`、GET `/{sessionId}`、POST `/{sessionId}/messages`及POST `/{sessionId}/cancel`，后两者必须显式expectedRevision；不安全请求必须CSRF，维护者无USER权限，没有列表/SSE/Run关联。

本轮相关覆盖去重591项通过，最终3B组91项和三模块打包通过，源码/资源检查完成；模型仍为合成依赖，会话模型调用0，真实供应商和生产多用户未验收。见[3B验收](docs/evidence/milestone-3b/2026-10-05/B/07-3b-acceptance.md)与[新测试报告](docs/evidence/milestone-3b/2026-10-05/B/08-final-test-report.md)。回退只关入口，保留V20及私有归属，不能让旧无NULL过滤二进制读取受控库。
