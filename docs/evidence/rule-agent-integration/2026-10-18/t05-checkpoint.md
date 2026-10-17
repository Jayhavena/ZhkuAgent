# T05 集成局部检查点（2026-10-18）

起点 a687c05，来源 codex/t05-online-evidence@1c270c4；仅在 codex/rule-agent-integration-20261018 隔离分支整合，未改主线或外发。

- 唯一合并冲突：docs/evidence/rule-agent-progress.md；保留10月18日当前汇总，T05新增记录全部放在历史边界下。组合预演的T06、T07后续无文本冲突。
- 初次原基线7项测试通过但新增浏览器测试源文件触发sourceDrift=2，批次拒绝；暂停Java编辑后baseline-contract-02通过7项、漂移0。
- t05-integration-unit-01/02各122项中3项包准备错误，准确原因是原始检出早于新-text属性：一份审核输入被LF转换为CRLF。精确恢复后unit-03通过122项。
- 后续原始字节核对发现637份knowledge/src、knowledge/data受保护文件只有换行差异。全仓强制恢复被自动审核拒绝（可能覆盖未提交修改），没有执行；改为逐文件验证当前原始blob未变、规范LF后严格等于固定合并索引，再只修复该白名单中的636份剩余差异。未改变正文、审核输入期望或进度裁决；Git索引保持870afc2d7aa43e1f82ee28e0314bc936c884c5d5。
- 最终t05-integration-unit-04：四类122项通过、失败/错误/跳过0、sourceDrift=0、resourcesVerified=true；实际假HTTP协议检查，不是真实模型。
- 全部前端178项通过，student/engineering/replay三种构建通过；前端源文件与起点无差异，产物不提交。
- 原PDF未复制。为满足既有相邻路径依赖，本机新建工作区父目录下SuperAgent-zhkuagent-mvp/data/raw目录链接，指向D:/alldesk/Projects/SuperAgent-zhkuagent-mvp/data/raw；仍由原审核SHA核验。该链接是外部恢复条件，不进入Git。

新增供应商调用0、数据库操作0。Docker引擎检查发现未启动，真实临时库/代理/浏览器仍待T06整合后验收。T05分支历史在线证据仍属于其原候选，不是本轮在线复验。T06/T07整体、T08—T10未关闭。恢复使用独立逆向提交，不修改原始批次/旧授权或主线用户改动。