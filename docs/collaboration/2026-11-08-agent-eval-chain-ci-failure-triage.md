# agent-eval-chain CI 失败排查（会话记录）

> 会话：`session-0b9b5a3d`「🔍 探索｜询问内容含义」
> 时间窗：2026-11-08 17:55 – 18:05（Asia/Shanghai）
> 角色：只做解读与排查，未改动代码、未提交、未推送。
> 相关提交：`e3e548ac`（由并行会话 `session-5e7d3e99` 完成）
> 相关运行：[42468972122（失败）](https://github.com/Jayhavena/ZhkuAgent/actions/runs/42468972122)、[42469324308（成功）](https://github.com/Jayhavena/ZhkuAgent/actions/runs/42469324308)

## 起因

用户收到 GitHub Actions 邮件，标题形如 `[Jayhavena/ZhkuAgent] agent-eval-chain workflow run`，正文为
`agent-eval-chain: All jobs have failed`，表格里 `agent-eval-chain / replay` 用红叉标记、
显示 `Failed in 25 seconds` 并带 5 条 annotations。用户询问这封邮件是什么意思。

`agent-eval-chain` 是评测链路回归工作流
（[`.github/workflows/agent-eval-chain.yml`](../../.github/workflows/agent-eval-chain.yml)），
在 push 到 `main` / `feat/zhkuagent-v3-frontend`、以及 PR 和手动触发时运行。
"All jobs have failed" 的含义是**该运行下的全部 job 都失败**（本工作流只有 `replay` 一个 job），
不是每一个 step 都失败。

## 结论先写

失败的不是回归测试，而是紧随其后的**报告生成步骤**：该步骤单独执行
`mvn -pl knowledge dependency:build-classpath` 时缺少 `-am`，
reactor 里没有同仓库模块 `agent-runtime`，干净 runner 的 `~/.m2` 也没有该产物，
于是 Maven 转去中央仓库查找并解析失败。

该问题已由并行会话修复（提交 `e3e548ac`）并在推送后 CI 转绿，本会话只负责确认与复核。

## 证据

### 步骤级结果（run 42468972122）

| 步骤 | 结果 |
| --- | --- |
| Set up job / checkout / setup-java | success |
| 链路回归（归档证据重放，不调模型/数据库） | **success** |
| 生成评测报告 | **failure** |
| upload-artifact | success（但没有文件可传） |

### 失败日志原文

```
[ERROR] Failed to execute goal on project knowledge: Could not resolve dependencies for project com.zhku:knowledge:jar:0.1.0-rc.1
[ERROR] dependency: com.zhku:agent-runtime:jar:0.1.0-rc.1 (compile)
[ERROR] Could not find artifact com.zhku:agent-runtime:jar:0.1.0-rc.1 in central (https://repo.maven.apache.org/maven2)
[ERROR] Process completed with exit code 1.
```

### 5 条 annotation 的实际构成

只有 1 条是真正的失败，其余是告警/提示：

| 级别 | 内容 |
| --- | --- |
| failure | `Process completed with exit code 1.` |
| warning | `No files were found with the provided path: target/agent-eval-chain-report/report.*` |
| warning | Node.js 20 deprecated（checkout@v4 / setup-java@v4 / upload-artifact@v4） |
| warning | setup-java v4 deprecated，建议迁 v5 |
| notice | `ubuntu-latest` 标签迁移说明 |

## 根因

1. 根 [`pom.xml`](../../pom.xml) 分三个平级模块：`agent-runtime`、`agent`、`knowledge`。
2. [`knowledge/pom.xml`](../../knowledge/pom.xml) 依赖同仓的 `com.zhku:agent-runtime:${project.version}`。
3. 第一步用 `mvn -pl knowledge -am ... test`，`-am` 让 Maven 在 reactor 内现场编译
   `agent-runtime` 并使用其 `target/classes`；但 `test` 阶段**不会把产物 install 到本地仓库**。
4. 第二步 `-pl knowledge` **没有 `-am`**，reactor 里只剩 `knowledge` 自己，
   只能去中央仓库找 `com.zhku:agent-runtime` → 失败。
5. 本机之所以长期是绿的：`~/.m2/repository/com/zhku/agent-runtime/0.1.0-rc.1` 里存有旧安装产物，
   本地能解析到；CI runner 是干净环境，没有这份缓存。属于典型的“本地绿、CI 红”。

## 并行会话竞态记录

用户随后要求“把 workflow 里那步加上 `-am` 并提交推送”。实际执行时发现：

| 时间 | 事件 |
| --- | --- |
| 17:56:05 | 本会话读到旧版 workflow（54 行，报告步骤只有 `build-classpath` 一条命令） |
| 17:56:30 | 文件被改写（mtime 变化） |
| 17:56:48 | 并行会话提交 `e3e548ac fix(ci): 报告步骤先安装同仓库模块，修复干净检出依赖解析失败` |
| 17:57:07 | 推送触发新一轮 CI |

本会话的编辑因 `file changed since it was read` 被安全拦截，回读后确认同一处已被他人修改，
因此**没有产生重复提交**，只做了复核：

- 本地 `HEAD` 与远端 `feat/zhkuagent-v3-frontend` 同为 `e3e548ac`，确认已推送；
- 该提交触发 2 次运行，结论均为 success；
- `生成评测报告` 步骤由 failure 转 success，artifact `agent-eval-chain-report`（2561 字节）
  这次真实上传，此前 `No files were found` 的告警随之消失。

这正是把“先读后写”约束写在 `git-commit-workflow` 里的价值：
同一工作目录被两个会话并发改写时，版本守卫先拦住写入，再让人核对，而不是覆盖他人改动。

## 两版修法对比

| 方案 | 写法 | 代价 | 现状 |
| --- | --- | --- | --- |
| A（已合入 `e3e548ac`） | 先 `mvn -B -q -pl knowledge -am -Dmaven.test.skip=true install`，再 `-pl knowledge dependency:build-classpath` | 多一次 Maven 启动/打包 | 已推送，CI 绿 |
| B（本会话最初建议） | 直接在 `dependency:build-classpath` 上加 `-am`，省略 `install` | 少一次 Maven 调用 | 未采用，待用户决定 |

两者都能消除“干净 runner 缺同仓产物”的问题；B 更短，A 与后续可能新增的、需要读仓库产物的步骤兼容性更好。

## 本会话的边界（避免被误读为已完成工作）

- 未修改任何源码或配置文件；
- 未创建提交、未推送；
- 只在工作区内临时创建过 `.dsh-tmp/`（用于下载并解压 CI 日志），排查结束后已删除；
- 已核实的所有结论都来自 GitHub API 返回的运行记录、日志与本地 git 状态，没有推测性断言。

## 遗留

- 是否把方案 A 收敛成方案 B，需用户确认；改动会落在并行会话刚写的代码上，未经同意不动。
- 邮件里的 deprecation 告警（Node20、setup-java v4）是独立的清理项，与本次失败无关，可择期处理。
