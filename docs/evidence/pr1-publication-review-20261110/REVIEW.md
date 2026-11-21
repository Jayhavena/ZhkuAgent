# PR #1 范围与归档边界复核

实时复核：仓库 Jayhavena/ZhkuAgent；PR #1 base main@ee0562af082adee894c95ffb1fe421c39636a8f3，head feat/zhkuagent-v3-frontend@42d4491d90022a9357f06e247f3300f69b43beb0。实际差异 24 commits、235 files，而非初始估计的 26 commits。原两项 replay CI 通过，不构成完整产品审查或学生试用验收。

独立只读审查 `pr1_independent_review`：评测采集器复用白名单 Trace、既有 Grader，缺观察/缺终态维持 NOT_EVALUABLE，未发现确定的评测主链阻断缺陷；但 PR 包含 raw request-package、本机目录与大段原始定价网页归档，且范围覆盖账号、前端、Docker、受控启动和评测，不宜以 eval-only 标题直接合并。未发现明确真实密钥字面值不等于完整 DLP 审计。

此本地后续提交只在新独立 Worktree 中从候选 head 排除 11 个不适合直接归档的文件：3 个原始请求包、2 个相关 sidecar、6 个完整网页/markdown 抓取。完整逐文件摘要见 withheld-manifest.json。保留已有核价/预检文字结论和摘要，原始本地资料、冻结记录、其他 Worktree 不修改。历史 E02 重新 Prepare/Verify 时，仍需在获授权本地资料根恢复这些被保留在本地的原输入并核对原 SHA；干净公开检出不再声称能复验该历史 raw 包。

**这不是历史清除。** 42d4491d 已经在远端，删除新 head 的文件不会让旧 Git 对象消失。没有发现必须立即撤销的真实凭据；若认为已公开的历史规则/提示词必须从远端历史彻底清除，需用户单独决定历史重写、旧分支/PR 引用处理，不能以本补丁冒称已移除所有远端可达副本。

拟将 PR #1 标题与描述改为完整基础整合范围，明确 24 个历史提交及本次归档排除；不重做 Git-01 证据包。Evidence PR #2 与 G1 PR #3 仍以该 feature 分支为目标，新增内容分别为 18/12 文件，不把 24 个历史提交混入其独立 diff。

本候选尚未 Push，不更新 PR #1，也不 Merge。需本地离线回归后提供精确新 SHA、改动清单、敏感审查和描述，再由用户授权远端 feature 分支前进与 PR 文案更新。合并还需对最终 base/head 的 CI、Review、依赖、差异作独立确认。建议保留祖先关系的 merge commit 流程，避免 squash 后堆叠 PR 基线不清；最终策略仍须明确授权。
