# 校园 Agent 专用临时库验收 v2（2026-09-28）

本记录追加在[首轮受控验收](2026-09-28-campus-agent-v1-验收记录.md)之后，不修改 `five-level-routing-v1` 历史语料及其 2/5 结论。测试资料为《本科生学生手册（2025版）》审核片段，策展 JSON SHA-256：`B3A5A78162D895643098E559267A8141723BD2C7075B85356BC4DBE9A59036A7`。课程 `ART01=1.5`、`HIS02=2` 均为虚构输入。

## 环境与复现

从仓库根目录执行 `./knowledge/scripts/verify-campus-temp-db.ps1`。脚本使用本机 `pgvector/pgvector:0.8.6-pg16-bookworm` 镜像、随机数据库密码和独立的 `zhkuagent_e2e` 库，默认仅映射 `127.0.0.1:55432`，无持久卷；结束后容器自动删除并恢复原进程数据库环境变量。原演示库的 `54329` 端口不可用于本脚本。本轮两次实际执行均退出 0，未访问原演示库。

| 验收 | 预期 | 实际 |
| --- | --- | --- |
| Flyway 与资料表 | 专用空库能应用正式迁移 | `KnowledgeSchemaTest` 1/1 通过，13 个迁移校验通过，数据库为 PostgreSQL 16.15 |
| 审核发布与政策读取 | 仅独立范围的学生可见片段可提供学分上限 | `JdbcCreditLimitEvidenceFinderIntegrationTest` 1/1 通过；读取原文“每学期公共选修课最高不得超过 4 学分”及 `PDF 第 76—77 页，第 8、10 条` |
| 索引与两路候选 | 已发布、学生可见、分享权确认者入索引；无权限者不得召回 | `JdbcRetrievalIndexingTest` 3/3、`JdbcCandidateRetrieverTest` 4/4 通过；问题向量为测试固定向量，非真实供应商 embedding |
| 检索黄金集 | 明确记录降级与指标 | `RetrievalAcceptanceEvalTest` 1/1 通过；`retrieval-golden-v1` 的 Recall@3=1、MRR=1、引用准确率=0.875，**仅词法路可用**，不得解读为语义检索效果 |
| 校园 HTTP 闭环 | 同一已审核来源经五级规则链、工具求和后带出处作答；撤回可见性后转人工 | `CampusCreditAgentJdbcEndToEndTest` 1/1 通过：正常答复含 3.5 学分、4 学分规则、策展来源 ID 和原文定位；改为 `INTERNAL_ONLY` 后为 `HANDOFF/INSUFFICIENT_EVIDENCE`，无计算结论或出处 |

合计 **11 运行 / 0 失败 / 0 错误 / 0 跳过**（结构 1 + 集成 10）。HTTP 闭环使用真实 PostgreSQL、审核发布器、JDBC 证据查询器、五级规则链、纯计算工具与控制器；`ChatModel` 由可控替身选择工具。本轮未配置当期通知夹具，因此没有证明通知优先级的真库场景。

## 尚未验证

- 环境没有 `DASHSCOPE_API_KEY` 或其他供应商密钥：未运行 `index.jar --live` 的真实 embedding、真实 ChatModel 工具选择和供应商冒烟。
- 现有检索器还未强制只使用 `ACTIVE` profile；临时库测试的固定向量候选与词法黄金集不能替代完成索引后的真实语义质量评测。
- 本轮 HTTP 测试经 MockMvc 调用真实数据库链，不等于已启动公网服务、接入真实教务系统或上线。

项目表述：**已实现**校园学分 Agent 受控工具闭环；**已接线并在专用临时 PostgreSQL 验证**审核发布、JDBC 政策证据、五级规则链、HTTP 计算与引用、权限撤回后的转人工；**已在真实供应商验证**：无。
