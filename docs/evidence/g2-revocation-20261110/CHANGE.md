# C2 失效证据快照修复

真实临时 PostgreSQL + Spring + 学生页面复现：同一未过期任务，原文正向对照 HTTP 200；仅撤销 source 分享/学生可见资格后，original 返回 409，但 snapshot 仍 200，含旧 claim/evidence 各 1 条，浏览器刷新后将旧结论重新展示。此为确定的资格边界阻断，前一次仅验证原文拒绝不能计为完整 C2 通过。

新增现有真实 HTTP 生命周期回归中的 snapshot 断言，得到有效 RED：1 test / 1 assertion failure / 0 errors，expected 409 but 200；没有依赖过期来伪造资格撤销证据。

最小修复仅在 Controller 获取快照后、返回正文前，对 snapshot.result.evidence 逐条复用既有 OriginalReader 当前资格/原文读取及原 original 端点的 proof 字段核对。已撤销或版本/校验/跨度/正文不一致则沿用 EXPIRED→409，不返回旧快照。真实读取在 TaskStore 锁外；最多 8 条证据，不跑模型，不改 Owner、权限合同、TaskStore 数据或发布评分。原文端点共享同一个 currentEvidence 核对方法。

独立只读 `revoked_snapshot_review` 未确认阻断级问题：跨账号先过 owned 守卫；pending previous result 同样放在 snapshot.result，经过相同循环。建议的 pending previous 撤销专门回归为非阻断剩余项；当前校验为有界逐条读取而非跨证据原子数据库快照，不作更强一致性声明。

另修复真实浏览器已复现的恢复失败提示不可见：无 conversation 时展示 state.error alert。SSR 断言先失败后通过。前端 200 tests PASS；后端扩展 32 tests，31 PASS、1 个既有显式资料装配 SKIP、0 FAIL/ERROR。扩展首次因测试诊断目录参数缺失而出错，补齐参数后重跑同组选类，未改产品逻辑或断言绕过。

本提交完成代码与离线验证；最终真实数据库/浏览器 GREEN 仍另行记录。原始失败、SQL资格撤销回执、租约正常停止和资源回收保留本地，不上传审核原文或权限 Token。

快照新增 404/409 拒绝后，前端 refresh 也必须将其与 503/网络不明区分：新增回归证明原行为仍保留旧快照与原文（有效断言 RED），最小修正为清除当前显示及该条历史元数据，明确提示旧结果撤回，允许新建查证；503 等仍保留查询入口，不重放 POST。全 Node 201 PASS，构建通过。
