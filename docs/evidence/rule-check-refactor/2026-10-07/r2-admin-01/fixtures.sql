-- 仅写本批随机无数据卷临时容器；固定ID用于前后对照，所有内容为合成资料。
INSERT INTO knowledge_submission (id,document_family,knowledge_scope,title,source_version,source_description,applicable_scope,storage_key,original_file_name,content_checksum,submission_status,review_notice)
VALUES ('10000000-0000-0000-0000-000000000001','STUDENT_HANDBOOK','R2_SYNTHETIC','R2 合成分页与安全资料','TEST ONLY','合成测试，非学校规定','仅本轮测试','synthetic-pagination.pdf','synthetic-pagination.pdf',repeat('A',64),'EXTRACTED','合成资料不得用于真实决策'),
('10000000-0000-0000-0000-000000000002','STUDENT_HANDBOOK','R2_WORKFLOW','R2 合成审核发布资料','TEST ONLY','合成测试，非学校规定','仅本轮测试','synthetic-workflow.pdf','synthetic-workflow.pdf',repeat('B',64),'EXTRACTED','仅验证本机审核与发布状态');
INSERT INTO knowledge_candidate (id,submission_id,start_page_number,end_page_number,raw_text,edited_text,section_title,source_locator,review_status,review_note,discard_reason)
SELECT ('20000000-0000-0000-0000-' || lpad(n::text,12,'0'))::uuid,'10000000-0000-0000-0000-000000000001',n,n,
CASE WHEN n=1 THEN '<img src=x onerror=alert(1)> 原文必须按字面显示。' WHEN n=21 THEN '   ' ELSE '合成原文第 ' || n || ' 页，不代表学校规则。' END,
CASE WHEN n IN (23,24) THEN '已保存的合成正文' ELSE NULL END,
CASE WHEN n IN (23,24) THEN '已审核合成条款' ELSE NULL END,
'PDF 第 ' || n || ' 页',
CASE WHEN n IN (23,24) THEN 'APPROVED' WHEN n IN (22,25) THEN 'DISCARDED' ELSE 'PENDING_REVIEW' END,
CASE WHEN n IN (23,24) THEN '合成审核说明' ELSE NULL END,
CASE WHEN n IN (22,25) THEN '合成丢弃原因' ELSE NULL END
FROM generate_series(1,25) n;
INSERT INTO knowledge_candidate (id,submission_id,start_page_number,end_page_number,raw_text,review_status)
VALUES ('30000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000002',1,1,'合成规则：请核对原文。','PENDING_REVIEW'),
('30000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000002',2,2,'','PENDING_REVIEW');
