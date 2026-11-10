# G1 独立签核回执

日期：2026-11-10
结论：**G1_ACCEPTED_FOR_CONTROLLED_ENGINEERING_INTEGRATION**

本回执只补齐 G1 工程的独立签核与身份固化，不重新运行测试、不调用模型、不改产品代码。G1 的工程接收不代表真实模型质量、G2 学生页面扩验、生产部署或正式发布通过。

## 身份

| 项目 | 值 |
| --- | --- |
| G1 源候选 | `6e149df51db23668849d84098d5111016426edae` |
| G1 交付提交 | `94a89aa3b10a3fcd364fe3d96dd15ae67b3bd7e1` |
| 交付基线 | `42d4491d90022a9357f06e247f3300f69b43beb0` |
| 源候选树 | `ad598c57d9ea3de6b7b676a076729a056257af06` |
| 交付树 | `415f01db40b6627da212bbd1b1acf0c26a39459a` |
| 远程分支 | `release/g1-reviewed-20261110` |
| PR | #3，目标 `feat/zhkuagent-v3-frontend` |
| 既有独立审查 | `docs/evidence/g1-formal-review-20261110/REVIEW.md` |
| 机器验收记录 | `docs/evidence/g1-formal-review-20261110/acceptance.json` |

## 逐字节迁移核验

`42d4491d..94a89aa3` 的 `knowledge/src` 共 10 个文件；逐文件比较 `6e149df5` 与 `94a89aa3` 的 Git blob，**10/10 完全一致**：

| 文件 | Blob（短） |
| --- | --- |
| `knowledge/src/main/java/com/zhku/knowledge/ruleagent/RuleAgentProvider.java` | `55212b39edcc` |
| `knowledge/src/main/java/com/zhku/knowledge/ruleagent/RulePublicationGate.java` | `a3925960a9ba` |
| `knowledge/src/main/java/com/zhku/knowledge/ruleagent/RulePublicationProtocol.java` | `73bb916a634d` |
| `knowledge/src/test/java/com/zhku/knowledge/ruleagent/RuleAgentE13InsufficientMaterialTerminalTest.java` | `cf41812b6b84` |
| `knowledge/src/test/java/com/zhku/knowledge/ruleagent/RuleAgentE13PublicationCapabilityTest.java` | `650aff35f8de` |
| `knowledge/src/test/java/com/zhku/knowledge/ruleagent/RuleAgentProviderTest.java` | `48c23f4e22f8` |
| `knowledge/src/test/java/com/zhku/knowledge/ruleagent/RulePublicationGateTest.java` | `f2a0aa74fcb0` |
| `knowledge/src/test/java/com/zhku/knowledge/ruleagent/web/RuleAgentE13Policy.java` | `3c6e3c6a05ae` |
| `knowledge/src/test/java/com/zhku/knowledge/ruleagent/web/RuleAgentPublicationRejectionEvidence.java` | `60464bccaafc` |
| `knowledge/src/test/resources/three-question-regression/e13-review-redacted-20261029T0842.json` | `de8291bf057e` |

未把 `338c1ea1` 的开发问题文档、`f1637ffc` 的冻结包/日志或原始 request-package 带入 G1 交付。

## 验收事实

- 独立只读审查确认阻断性代码 findings 为 **0**。
- 初始“空 requirements 可能流通过”的疑点经协议复核后撤回；未做推测性修复。
- 隔离 Worktree 运行 9 类离线测试，共 **128 项：127 PASS、1 SKIP、0 FAIL、0 ERROR**，Maven `BUILD SUCCESS`。
- 唯一 SKIP 是既有 Host 显式操作分派，运行范围归 G2，不计为通过。
- `databaseOperations=0`，`providerCalls=0`；原始 XML/日志保留本地，不上传环境属性或 stdout。
- 首次运行的 1 个 ERROR 是测试预设诊断目录缺失，补齐目录后重跑全组；未改产品代码或断言，首次失败保留。

## 签核决定

**接受 `94a89aa3` 作为受控工程集成的 G1 交付提交。** 该接受仅覆盖：

1. 上述 10 个文件的逐字节迁移与对应离线回归；
2. G1 PublicationGate 的跨度全覆盖/混合原因 fail-closed 语义；
3. 既有两个独立审查记录和本回执的身份对应。

## 条件与限制

- PR #3 仍需独立 Merge 授权；本回执不构成 Merge 批准。
- G2 候选 `0a08ebff` 以 `94a89aa3` 为父，G2 交付必须先确认本回执与 G1 的对应关系。
- 不声称真实模型质量、G2 浏览器扩验、生产部署、正式发布或学生试用准入已通过。
- 不声称已清除 Git 历史或远端原始对象；本回执只固定新交付提交。
- 签核角色为独立工程复核（Agent），不冒充人类审批或学校业务签章。

## 复核命令

```powershell
git rev-parse 94a89aa3^{tree}                 # 415f01db40b6627da212bbd1b1acf0c26a39459a
git diff --name-status 42d4491d 94a89aa3 -- knowledge/src
git show 94a89aa3:docs/evidence/g1-formal-review-20261110/acceptance.json
```

逐文件 blob 比较可用：

```powershell
$files = git diff --name-only 42d4491d 94a89aa3 -- knowledge/src
foreach ($f in $files) {
  $a = git rev-parse "6e149df5:$f"
  $b = git rev-parse "94a89aa3:$f"
  if ($a -ne $b) { throw "DIFF $f" }
}
"G1_FILE_IDENTITY_OK $($files.Count)/$($files.Count)"
```
