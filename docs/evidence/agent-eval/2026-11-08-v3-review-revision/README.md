# v3 审核修订草案

本目录依据 v2-dev 和审核卡中用户确认的意见生成；v2 资源及清单未修改。草案不在测试资源目录，不代表已接入评测、已冻结金标准或已确认当期适用性。

- **RFE-017**：将 `schedule-previous-semester` 从 `expectedFacts` 移到新增的 `optionalContextFacts`，并从 `requiredResponseElements` 删除对应必答项。安排顺序仍为必答事实；额外正确说明排课时间可接受，不应成为通过条件。消费者必须显式支持 `optionalContextFacts`，不得把它当必答事实。
- **RFE-020**：保留 `CITATION_MISMATCH`，以覆盖主动给出错误或虚构引用的情况。没有要求引用且回答未引用时，引用维度为 `NOT_APPLICABLE`，不计该维度分母；提供引用时逐项核验，无法核验为 `UNJUDGEABLE`。该处理不自动决定整题通过，其余适用关键维度继续按原规则判定。
- `NOT_APPLICABLE` 是本草案的**维度适用性标记**，不是已有整题结果枚举的新值。新增 `RuleFactOfflineScorer` 可离线加载本草案，并针对预先核验的事实和引用观察计算这两个维度；它不自动理解回答、不核验引用，也不生成整题通过结论。现有路由评测器及 Agent 尚未接入该组件。
- 审核卡已记录逐题意见，但草案保留数据集 `DRAFT_NEEDS_HUMAN_CASE_REVIEW` 及每题原有 `PENDING`，不将对话记录伪装为数据集冻结或运行时资料发布。

来源 v2 SHA-256：`277e6904b52bb03ed9d18328c357c512e764f6ccb81278d07ae3ceda5bc742dc`。运行 `python revise.py` 生成本目录草案和清单；若目标文件已被修改，脚本拒绝覆盖。运行 `python -m unittest -v test_revision.py` 检查修订和清单一致性。

此前 `RuleFactOfflineScorerTest` 的源码目前不在本工作区，旧测试记录不能视作当前可重跑入口；请使用下方现有的流程测试。测试使用 v3 草案及人工构造的核验观察，不调用模型或数据库；结果不代表真实 Agent 表现。

## 合成回答离线流程

`knowledge/src/test/resources/agent-effect-eval/offline-review-examples.json` 包含六条合成回答及人工模拟标注。`RuleFactReviewFlow` 读取数据集、清单及标注样例，检查事实和引用片段确实出现在回答里，再将标注交给 `RuleFactOfflineScorer`。本目录 `offline-review-output.json` 是这批样例的实际输出。输出中的事实、引用及理由仅体现模拟标注，不判断其他维度或整题是否通过。

标注需要明确记录完整性、事实 ID、回答片段、引用定位、人工核验状态与理由；`citationsComplete` 必须为 true。片段存在只证明文字锚点存在，不证明语义正确，也不能机器证明标注覆盖了所有引用；使用者须人工保证标注完整。事实或引用无法判断时应保留不确定性，不能标记为已核验支持。这个入口仅接受 `SYNTHETIC_MANUALLY_ANNOTATED`，不能冒充真实 Agent 评测。

验证命令：`mvn -o -q "-Dmaven.repo.local=$env:USERPROFILE/.m2/repository" -pl knowledge -am '-Dtest=RuleFactReviewFlowTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`。运行 CLI 时传入三个路径：v3 数据集、v3 清单、标注样例；其主类是 `com.zhku.knowledge.eval.RuleFactReviewFlow`，JSON 输出到标准输出。调用方需在 Java classpath 中包含 `knowledge/target/classes` 及 Jackson 依赖。
