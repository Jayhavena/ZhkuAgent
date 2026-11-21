# 历史快照开发 Gold v1.0

状态：20 题预期契约已冻结；真实 Agent 效果评测尚未执行。详见 [effect-eval-v1-report.md](effect-eval-v1-report.md)。

本包是独立版本，`knowledge/src/test/resources` 的 v2 及历史 v3 草案保持原样。使用本包必须显式传入本包 dataset 与 manifest，不会自动替换运行时配置。它基于 B02/B03 的 2025.9.2 归档文本，不是当前学年政策基准或独立盲测集。`reviewer=用户` 表示预期契约审核人，不能用作未产生回答的质量评分人。

`acceptance.json` 转录用户对 RFE-017/020 的最终接收，补充历史审核卡中的未决项。`review-decisions.json` 和本包的 `审核决定卡.md` 是该版本的最终决定；历史卡的 PENDING/需修改/部分确认记录不回写。测试要求本版本卡、决定表、dataset 状态完全一致，历史卡以 SHA 保留来源。18 题沿用历史通过，2 题依据最终修订接收，全部预期为 APPROVED。

`manifest.json` 固定 dataset、决定表、最终卡及历史来源的 SHA-256，并记录 frozenAt、reviewer、版本、语料指纹。摘要可检测相对本清单的漂移，不是签名或 WORM。后续修订应另建版本，生成脚本拒绝覆盖不同内容。`frozenAt` 是本地转录/冻结记录时间，不冒充用户消息精确服务器时间。

离线复验（仓库根目录）：

```powershell
python -B docs/evidence/agent-eval/2026-11-17-frozen-gold-v1/freeze.py
python -B -m unittest discover -s docs/evidence/agent-eval/2026-11-17-frozen-gold-v1 -p test_freeze.py -v
mvn -o -q -pl knowledge -am '-Dtest=RuleFactFrozenDatasetTest,RuleFactReviewFlowTest,RuleFactCheckEffectV2DatasetTest,AgentEvalChainRegressionTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

现有 `RuleFactReviewFlow` CLI 参数依次为本包 dataset、manifest 和原 `offline-review-examples.json`；样例源类型仍为 `SYNTHETIC_MANUALLY_ANNOTATED`。本轮实际输出保存在 `synthetic-flow-output.json`，只能证明已有两维评分入口兼容冻结集。没有复制评分逻辑，也没有生成参考答案冒充模型输出。

`verification.json` 保存实际测试汇总、环境和关键源码 SHA；测试日志及阶段报告是冻结后验证产物，不属于 gold 内容清单。没有模型/数据库调用、激活、提交或推送。真实批次仍需按具体请求包身份、调用/token/时间/费用预算及有效期单独授权。
