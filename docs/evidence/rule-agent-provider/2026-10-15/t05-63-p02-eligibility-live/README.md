# T05-63：新包唯一一次P02，失败归档

包`1e44705ae88a7fea23852862bc365da1c764357ef3664ed5139b96ace9cf2fd0`，生产实现56d33efd，jar01f2e79e，执行前HEAD6002b5a。用户明确批准一次P02/两run/10次/12元；实际仅一run、4次调用，余6次全部关闭，剩余0。

最早失败是独立核验的REVIEW_BINDING / REVIEW_CONDITION_UNKNOWN。输入条件原键是courseType，模型在两个重修需求中都输出S10/RETAKE_ONLY:courseType；宿主拒绝，未进入后续语义发布门禁，未形成合格WAITING_USER，PUBLIC未注入，第二run未执行。SEARCH和READ成功，ASK使用原键且支持事实均在publicationFactIds中；此前能力资格边界没有在本次提议处再次触发，但不能据此声称所有Gate检查已通过。

4次HTTP均200且无传输超时，3决策+1核验。输入6578、输出620token，按公开原价估算0.0069364元，实际账单未知。BEGIN/RECEIVED/FINISHED和本机快照按同一尝试去重，不把超时或缺usage记零/补调用。Provider耗时合计13834ms，启动器整批45.379秒。

原始轨迹和请求/响应保存在../t05-62-eligibility-request/live-22bd438b933a48ad9859fef337e4068d/，本归档attempt-ledger.json逐次关联动作、核验、诊断和费用；原始请求/响应不改写。实际任务仓失败快照未独立落盘，不以Runner outcome替代声称发布快照已核对。

临时容器已删除、端口释放、探针Java退出；宿主resourcesClosed=true，现有16个容器身份/镜像/状态/端口不变，原演示容器仍为exited。Docker原已运行，本轮未启动Desktop；临时库删除前清理行数未额外查询，保持未知。

本轮无修复、归一化、零外发重放、模型重试、补额度或下一批。模型为何加前缀仍未知；若未来继续，应另行界定一次零外发合同回放，不能凭本次响应推定改键后即可整链通过。本批到此结束。62/approval-state.json保留准备阶段未授权快照；本轮生效授权及已关闭状态以本目录authorization.json为准。

工程成果已冻结，真实在线验收未通过。
