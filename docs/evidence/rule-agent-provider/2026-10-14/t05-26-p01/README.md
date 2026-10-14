# T05 P01 真实探针（工具调用协议 P01_TOOL_CALL_04）：未通过并停止

本轮 2026-10-14 15:07 开始，依据用户本会话明确许可「批准 P01 批次，SHA 78909e8a」执行一次 P01 单独批次。
**本批实际外发 2 次并立即停止**，无重试、无 P02、无追加额度。T05 真实通过仍未达成。

## 一、授权与实际身份

- 授权范围：仅 P01 单题；本批最多 4 次外发；无自动重试；不得追加额度或进入 P02。本任务书不构成外发许可，许可靠用户逐项确认。
- 冻结包 `docs/evidence/rule-agent-provider/2026-10-14/t05-25/request-package.json`，
  SHA256：`78909e8a3f3a37a4db8ea6934e4d86a46cf6869f2fcab243359a51e962a432fe`。
- 北京地域 `chat/completions` / `qwen-plus`（非思考模式），协议 `RULE_AGENT_ACTION_CONTRACT_04`。
- 启动前零调用核对：`--CheckEnvironment` 与 `--CheckPackage` 均通过（`providerCalls=0`、`databaseOperations=0`）。
- 专用无卷临时库 `zhkuagent_rulecheck_e2e`（随机端口 55641，镜像 `pgvector/pgvector:0.8.6-pg16-bookworm`）；
  演示库容器 `zhkuagent-postgres` 全程未启动、未触碰。

## 二、执行与资源

- 实际启动 15:12:35—15:12:47，退出码 1，未超时（11.8 秒）；Flyway 成功应用 18 个迁移。
- 专属宿主与临时容器已退出：无 java 残留进程，临时容器已删除，`resourcesClosed=true`。
- 两次**未产生任何模型调用**的本机前置失败已记录（均为环境问题，非真实结果）：
  1. `FLYWAY_MIGRATION`：首用镜像 `postgres:16-alpine` 缺 `pgvector` 扩展 → V1 建库失败；
  2. `EVIDENCE_DIRECTORY`：上一轮失败留下的空证据目录被探针拒绝覆盖。
  修正镜像与目录后以同一授权、同一包 SHA 重跑，外发额度未受影响。

## 三、事实与受控类别

| 环节 | 实际结果 | 可得结论 |
| --- | --- | --- |
| 第 1 次决策（SEARCH） | HTTP200，动作**被宿主接受**并实际执行；事件 `SEARCH_REVIEWED_RULES / SUCCEEDED / CANDIDATE` | **工具调用协议在真实端点成立**：`tools`/`tool_choice`/`parallel_tool_calls` 被接受，动作名与参数结构由供应商结构性承担；结束原因落在本地接受的集合内（具体取值未单独留存） |
| 第 2 次决策（READ） | HTTP200 完整响应，在 `ACTION_PARAMETERS / READ_HANDLE_COUNT` 拒绝；受控类别 **`READ_HANDLE_COUNT_ABOVE_MAX`**（数组为 ARRAY，数量超过 3） | 此前"空选择还是超量"的未知**现已确定为超量**；数量约束仍不在供应商承诺范围，宿主守卫生效 |
| READ / FINISH / 独立核验 / 发布 | 均未执行 | 真实编号映射、事实适用与最终发布仍未验证 |
| 最终状态 | `FAILED / FAILED / terminated`；`INVALID_MODEL_RESPONSE`；无证据、无主张发布 | 未把系统失败冒充资料不足或政策结论 |
| P02 | NOT_EXECUTED | 不评分、不扩大批次 |

安全诊断仅保留 `elementIndex=null`、`jsonType=ARRAY`、`stringLength=null`、`failureCategory=READ_HANDLE_COUNT_ABOVE_MAX`、
`currentCandidate=null`；轨迹逐项核对**不含**自由正文、`tool_calls`/`arguments`/`content` 原文、候选 UUID、编号或集合代号。

## 四、用量与停止

- 实际 2 次均为 `DECISION`；`PUBLICATION_REVIEW` 为 0。按每次 `RECEIVED` 计量：输入 2044+2761=4805 token，输出 45+61=106 token；费用金额未知。动作非法未丢失已知 usage。
- 本批剩余 2 次**关闭、不补测**，不与任何旧余量合并。真实模型历史调用计数由 20 增至 **22**。

## 五、结论（按任务书收口）

**本地通过，真实在线验收未通过。工程成果已冻结，停止追加零散修补。**

- 本轮达成的工程事实：决策侧动作生成已由「自由文本 JSON」升级为**供应商结构化的工具调用**，且该机制在真实端点上**被接受并实际执行**（第 1 次 SEARCH 即为真机通过）。
- **唯一剩余缺口**：真实模型在 READ 动作上选择的候选数量**超过宿主 1—3 上限**，而供应商对工具参数不承担任何数量强制（官方 `parameters` 仅作入参描述、无 `strict` 语义）。宿主守卫按设计正确拒绝了该动作，且未执行、未发布。
- 该缺口**不能**通过放宽 1—3 上限、自动截取前三个候选、默认选择 `c1`、固定答案、静默回退旧协议或换模型来消除；这些均被本任务书禁止。

## 六、后续取舍（需用户决定，本轮不自行实施）

1. **改用支持 JSON Schema 严格模式的模型**（官方仅列 Qwen3.7-Plus / 3.7-Flash / 3.8-Flash / 3.7-Max / 3.8-Max），
   以 `minItems=1/maxItems=3` 由供应商强制数量。**属切换模型，超出本任务授权**，需用户另行决定与授权。
2. **维持 qwen-plus 与 1—3 上限**：接受在线 P01 可能失败，结论为"工程链路成立、真实在线验收未通过"。
3. **改变领域读取粒度**（例如每次只读 1 个编号、分多次读）：属领域合同与工具预算变更，需单独设计与验收，不在本轮范围。
4. **把数量越界当作资料不足或范围限制降级**：**不允许**，会把执行故障冒充资料缺失。

## 七、证据与 Git

- `authorization-and-preflight.json`：用户许可、包/协议身份、预算、零调用回执、专用库与凭证边界。
- `actual-outcome-sanitized.json`：两次前置失败的准确阶段、外发次数、用量、最终决策轨迹、快照、事件、资源退出与泄漏核对。
- 原始轨迹与原始 outcome：`provider-traces.jsonl`、`58f65eb0-…-outcome.json`，**只保留本机、不暂存**。
- 本轮只保存受控证据的本地提交，不是完成提交；未 push/PR/标签/部署；未修改任何旧评分或失败记录。
- 恢复只能依据明确检查点与新许可，不重置工作区、不回放旧响应、不恢复旧外发额度。
