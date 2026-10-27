-- v2.0.0-rc2 source URL repair.
-- Apply ONLY to the dedicated isolated database zhkuagent_eval_v2_rc2.
-- The URL is the official 2025 handbook publication page, not a direct PDF attachment URL.
-- The guard requires exactly the three expected PUBLISHED 2025 source rows to have NULL source_url.
BEGIN;
DO $guard$
DECLARE
    matched_count INTEGER;
    updated_count INTEGER;
BEGIN
    IF current_database() <> 'zhkuagent_eval_v2_rc2' THEN
        RAISE EXCEPTION 'Refusing repair outside dedicated candidate database; current_database=%', current_database();
    END IF;

    SELECT COUNT(*) INTO matched_count
    FROM knowledge_source
    WHERE id IN (
        '72e1a0a5-4c9e-4f86-b5fd-2fe2a3957c14',
        '6b2f4c18-0d5a-4e73-9a41-7c1e8f2b6d05',
        'f4d0b0a0-c78a-4c7a-94e0-9b2942d0b8f5'
    )
      AND source_url IS NULL
      AND publication_status = 'PUBLISHED'
      AND source_version LIKE '2025%';
    IF matched_count <> 3 THEN
        RAISE EXCEPTION 'Refusing repair: expected exactly 3 published 2025 source rows with NULL source_url; got %', matched_count;
    END IF;

    UPDATE knowledge_source
       SET source_url = 'https://xsc.zhku.edu.cn/info/1154/15962.htm'
     WHERE id IN (
        '72e1a0a5-4c9e-4f86-b5fd-2fe2a3957c14',
        '6b2f4c18-0d5a-4e73-9a41-7c1e8f2b6d05',
        'f4d0b0a0-c78a-4c7a-94e0-9b2942d0b8f5'
     )
       AND source_url IS NULL;

    GET DIAGNOSTICS updated_count = ROW_COUNT;
    IF updated_count <> 3 THEN
        RAISE EXCEPTION 'Refusing repair: expected 3 updated rows; got %', updated_count;
    END IF;
END
$guard$;
COMMIT;