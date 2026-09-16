-- 学校正式材料可经受控校内渠道下发；不得以虚构官网链接换取学生可见资格。
ALTER TABLE official_notice ALTER COLUMN official_url DROP NOT NULL;
ALTER TABLE official_notice
    ADD COLUMN evidence_channel VARCHAR(32) NOT NULL DEFAULT 'PUBLIC_WEB',
    ADD COLUMN issuing_unit VARCHAR(120),
    ADD COLUMN delivery_channel VARCHAR(160),
    ADD COLUMN material_reference VARCHAR(160);

ALTER TABLE official_notice
    ADD CONSTRAINT ck_official_notice_evidence_origin CHECK (
        (evidence_channel = 'PUBLIC_WEB' AND official_url IS NOT NULL)
        OR (evidence_channel = 'INTERNAL_OFFICIAL' AND official_url IS NULL
            AND issuing_unit IS NOT NULL AND issuing_unit <> ''
            AND delivery_channel IS NOT NULL AND delivery_channel <> ''
            AND material_reference IS NOT NULL AND material_reference <> '')
    );
