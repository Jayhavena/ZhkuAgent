-- 旧通知被替代后仍需保留其“曾对学生可见”的审核事实用于审计；学生检索由 lifecycle_status 过滤，
-- 不能通过清空可见标记来破坏历史记录。
ALTER TABLE official_notice
    DROP CONSTRAINT ck_official_notice_student_visibility;

ALTER TABLE official_notice
    ADD CONSTRAINT ck_official_notice_student_visibility CHECK (
        NOT student_visible OR sharing_rights_confirmed
    );
