# OBS-02E：目标部署环境日志政策核对回执

- 核对日期：2026-10-23
- 基线：OBS-02E `4826a47bbacbad3423850d300779161806185bea`
- 结果：**TARGET_DEPLOYMENT_NOT_CONFIRMED / LOG_COLLECTION_UNVERIFIED / LOG_READ_ACCESS_UNDEFINED / LOG_RETENTION_DELETION_UNDEFINED**
- 决策状态：**ADR-OBS-001 PROPOSED；OBS-02F 生产 Sink 启用及真实 /rules 接线 NO-GO**
- 核对方式：Windows 受授权本地项目 Git 工作树的只读静态核对；未访问远程主机、Docker 实际运行日志、凭证、原始学生请求或日志正文。

## 一、对三项日志政策逐项作答

| 项目 | 实际可确认信息 | 正式结论 |
|---|---|---|
| stdout 是否被安全采集、采集到哪里 | 本机演示脚本存在进程 stdout/stderr 重定向和本机 build/web/demo 日志输出；当前 Git 仓库没有证据证明部署环境存在应用日志采集器、日志接收平台、生产输出路径或采集成功回执。 | **目标生产环境未明确／不可确认**；不能把本机临时日志等同于生产日志采集 |
| 哪些角色可以读取日志 | 已有受控 HTTP Owner/Session 访问设计针对业务记录，不是日志读者权限政策。未找到面向目标部署日志的访问角色清单、权限授予/撤销、访问审计或第三方查看范围决议。 | **未明确**，不能默认“只有开发者可看” |
| 日志保留多久、谁负责删除 | 仓库存在其他业务归档、匿名反馈的各自保留约束，但它们并不自动适用于 Runtime 匿名事件日志。未找到目标部署日志轮转、保留期限、删除责任人、清理任务及删除验证回执。 | **未明确**，不得引用其他业务数据的 7 天或 90 天政策作替代 |

## 二、直接证据

1. `README.md`：项目的当前规则 Agent 状态为本机受控验收、独立分支工程成果；说明“非本机/生产多人权限、完整聊天历史持久化、备份恢复和真实学生效果尚未验收”，并将历史 HTTP/模型成果限制在本机/受控范围。
2. `knowledge/infra/docker-compose.yml`：Compose 仅包含 PostgreSQL 服务，数据库端口限定 `127.0.0.1`，没有 ZhkuAgent 应用 stdout 的采集服务或日志驱动策略配置。
3. `agent/frontend/compose.preview.yaml`：仅用于本机 `127.0.0.1` 前端 Nginx 预览，未配置应用日志采集器、保留与日志读权限。
4. `knowledge/scripts/start-controlled-web-demo.ps1`：启动子进程时重定向 stdout/stderr，写出 `web-*.log`、`build.log`、`frontend-build.log` 和 `live-summary.log`；这些是本机受控演示记录，不是实际生产部署的收集、角色与保留政策。
5. `knowledge/scripts/start-rule-agent-http-check.ps1`：使用 `%TEMP%` 下的临时启动日志名进行本机探针；不能证明日志有指定的安全收集端和到期删除责任。
6. `knowledge/src/main/java/com/zhku/knowledge/observability/ObservationSink.java`：`noop()` 只返回 `DISABLED`；`BoundedObservationBuffer.java` 明确注释“离线测试用容量界限”，两者都不是生产日志处理方案。
7. `docs/decisions/ADR-OBS-001-anonymous-observation-sink.md`：有界异步结构化 stdout 日志只是 `PROPOSED` 候选，明确要求先确认接收位置、角色、保留与删除等；尚未批准生产。
8. `docs/design/2026-10-22-obs-02e-shutdown-contract-and-test-matrix.md`：运行环境负责人书面确认 stdout 采集、可读角色、保留/删除和外发范围仍是未勾选门禁。
9. 当前跟踪文件清单未发现项目自有的 `logback.xml` / `log4j2.xml`、Fluent Bit / Vector / Promtail / OpenTelemetry Collector、Logrotate 或 Loki 配置。**未发现不等于证明未来外部运行环境没有这些设施**，因为此次没有连接任何生产主机。

## 三、目标环境本身的来源边界

本轮没有取得 ZhkuAgent 正式部署在特定云主机、容器平台或日志服务的已批准来源配置。不能从用户拥有其他云服务器的事实推定本项目已在该服务器部署，也不能因为本地 Docker 可用就推定容器 stdout 已被安全收集。

**需要确认的最小环境身份**：部署类型（本机演示／云主机 systemd／Docker Compose／平台托管服务）、应用进程所在主机/服务名、预期接收日志的系统；不需要在聊天或回执中提交主机密钥、原始日志、个人账号或私密地址。

## 四、建议的最小决策（均为待批准，不是已生效政策）

- **用途**：第一阶段仅存储 `ObservationEventV1` 的匿名元数据，且保持缺省不开启；原始 prompt、工具参数、Task/Run/Owner/Session ID、T08 受限评测 Trace 均不得出现在日志中。
- **落点**：首选候选为应用 stdout → 已确认的运维采集服务；如果没有采集端，不得把“写出 stdout”记作“采集成功”。
- **读权限**：建议最小化到指定维护者/运维角色，并要求禁止普通学生访问；具体角色和权限控制系统必须由运行环境负责人确认。
- **保留与删除**：建议选择短期的固定保留期，并明确“谁执行轮转/自动删除、如何核验”；此处不擅自指定 7、14 或 30 天。
- **故障**：日志目的地不可用时，匿名 Sink 自身限时/有界失败，正常业务不中断；但 mandatory provider audit 依旧保持独立 fail-closed。
- **启用**：只有采集位置、访问角色、保留期限、删除负责人、外发边界五项均有证据，再评审 ADR 状态和 OBS-02F 实现/启用范围。

## 五、审查裁定与下一动作

**本次核对结论为三项均没有可用的生产政策确认依据，并且 ZhkuAgent 的正式目标运行环境尚未被已查资料明确绑定。**

继续保留 `ADR-OBS-001 PROPOSED`、`OBS-02F PRODUCTION-SINK-NO-GO`、`REAL_RULES_WIRING_NO_GO`。这不妨碍独立合成测试或设计工作，但不得默认上报生产日志、放松隐私/身份控制或用本地临时日志证明生产可观测性。

下一步只需确定 **ZhkuAgent 实际计划部署的位置／方式**，之后才能针对那个具体环境只读核实日志驱动、采集器、角色 ACL 与删除配置，并让负责人正式批准保留期限。
