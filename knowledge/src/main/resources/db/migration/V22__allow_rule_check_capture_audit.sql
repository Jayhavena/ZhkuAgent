-- 捕获表示证据取得尝试，不能用旧回答路由混淆成学生已经看到结果。
-- 只扩充枚举，保留全部旧路由；历史迁移及既有审计行不重写。
ALTER TABLE citation_audit DROP CONSTRAINT ck_citation_audit_route;
ALTER TABLE citation_audit ADD CONSTRAINT ck_citation_audit_route CHECK (route IN (
    'AWAITING_CLARIFICATION', 'CURRENT_NOTICE', 'HIGH_RISK_RULE',
    'RETRIEVAL_AUGMENTED', 'INSUFFICIENT_EVIDENCE', 'RULE_CHECK_CAPTURE'
));
