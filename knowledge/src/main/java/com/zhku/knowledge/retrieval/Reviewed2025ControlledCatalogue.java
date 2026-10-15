package com.zhku.knowledge.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.knowledge.publication.*;
import com.zhku.knowledge.rulecheck.*;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.time.*;
import java.util.*;

/** 已审2025条款的受控本机装配；不代表学校发布服务，仅显式独立入口使用。 */
public class Reviewed2025ControlledCatalogue implements AutoCloseable {
    private final Path root;
    private final Map<String,String> assetHashes=new LinkedHashMap<>();
    static final String REVISION="2025.9.2", SCOPE="RULE_CHECK_PUBLIC_ELECTIVE_2025";
    static final String CUT="handbook-2025-clause-verbatim-v1";
    final UUID source=UUID.randomUUID(), run=UUID.randomUUID();
    final String profile="b04-lexical-"+UUID.randomUUID();
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    final JsonNode bundle, semantics, approval;
    private final JsonNode reviewedSemantics;
    private final JsonNode reviewedApproval;
    private final String qualificationReviewer,qualificationDate;
    final Map<Integer,Binding> bindings=new LinkedHashMap<>();
    final List<RuleCheckApplicationService.CatalogEntry> catalogue=new ArrayList<>();
    DataSource dataSource;

    public Reviewed2025ControlledCatalogue(Path root) throws Exception {
        this.root=root;
        bundle=read(root.resolve("docs/product/rule-check-2025-verbatim-import-draft.json"));
        semantics=read(root.resolve("docs/product/rule-check-2025-b02-semantic-review.json"));
        approval=read(root.resolve("docs/product/rule-check-2025-b03-publication-review.json"));
        for(JsonNode input:approval.path("inputs")) {
            Path path=root.resolve(input.path("path").asText()).normalize();
            require(path.startsWith(root),"审核输入路径超界");
            require(sha(readBytes(path)).equals(input.path("sha256").asText()),"B03输入指纹变化");
        }
        var human=read(root.resolve(approval.path("confirmationEvidence").asText()));
        var presented=root.resolve("docs/evidence/rule-check-refactor/2026-10-09/b03-student-qualification-01");
        require(human.path("authority").asText().equals("DIRECT_USER_MESSAGE")&&human.path("decision").asText().equals("CONFIRMED"),"缺少直接资格确认");
        require(human.path("sharingRightsConfirmedForThisPackage").asBoolean()&&human.path("limitedStudentPresentationQualificationConfirmed").asBoolean(),"本批分享资格未确认");
        require(sha(readBytes(presented.resolve("presented-publication-review.json"))).equals(human.path("reviewedProposalSha256").asText()),"资格审前快照不符");
        require(sha(readBytes(presented.resolve("presented-review.md"))).equals(human.path("reviewedWorksheetSha256").asText()),"资格核对表指纹不符");
        reviewedApproval=read(presented.resolve("presented-publication-review.json"));
        qualificationReviewer=human.path("reviewer").asText();qualificationDate=human.path("reviewedAt").asText();
        var semanticHuman=read(root.resolve("docs/evidence/rule-check-refactor/2026-10-09/b02-semantic-review-01/human-confirmation.json"));
        Path semanticSnapshot=root.resolve("docs/evidence/rule-check-refactor/2026-10-09/b02-semantic-review-01/presented-semantic-draft.json");
        require(semanticHuman.path("authority").asText().equals("DIRECT_USER_MESSAGE")&&sha(readBytes(semanticSnapshot)).equals(semanticHuman.path("reviewedDraftSha256").asText()),"缺少可追溯语义确认");
        reviewedSemantics=read(semanticSnapshot);
        Path pdf=root.resolveSibling("SuperAgent-zhkuagent-mvp").resolve("data/raw/2025版学生手册2025.9.2.pdf");
        require(sha(readBytes(pdf)).equals(bundle.path("sourceFileSha256").asText()),"指定PDF原件变化");
        validate();
    }

    void validate() {
        // hash只证明审前文件没变；当前许可用途/声明也必须仍是用户看过的内容。
        for(String field:List.of("inputs","decisionId","referenceRevision","sourceFileSha256","knowledgeScope","chunkingVersion",
                "allowedUseIfConfirmed","clauses","historicalApprovalBoundary","withdrawalConditions","targetVisibilityIfConfirmed"))
            require(approval.path(field).equals(reviewedApproval.path(field)),"当前许可范围偏离实际审核快照");
        require(approval.path("reviewer").asText().equals(qualificationReviewer)&&approval.path("reviewedAt").asText().equals(qualificationDate),"资格审核责任变化");
        require(approval.path("decision").asText().equals("CONFIRMED")&&approval.path("humanQualificationConfirmed").asBoolean()
                &&approval.path("sharingRightsConfirmed").asBoolean(),"本批学生展示资格未确认");
        require(semantics.path("semanticDecision").asText().equals("CONFIRMED"),"语义未确认");
        require(bundle.path("chunks").size()==10&&semantics.path("rules").size()==10,"条款数量不符");
        require(bundle.path("referenceRevision").asText().equals(REVISION)&&approval.path("referenceRevision").asText().equals(REVISION)
                &&semantics.path("referenceRevision").asText().equals(REVISION)&&bundle.path("sourceVersion").asText().equals(REVISION)
                &&bundle.path("chunkingVersion").asText().equals(CUT)&&bundle.path("knowledgeScope").asText().equals(SCOPE),"范围或版本不符");
        for(int i=0;i<10;i++) {
            JsonNode rule=semantics.path("rules").get(i), before=reviewedSemantics.path("rules").get(i);
            for(String field:List.of("questionScopeDraft","conditionsDraft","gapPoliciesDraft","forbiddenConclusions","exceptionsAndUnknowns","reviewItemId","article"))
                require(rule.path(field).equals(before.path(field)),"已核对范围发生变化");
            require(rule.path("facts").size()==before.path("facts").size(),"主张数量变化");
            require(rule.path("humanReviewer").asText().equals(semantics.path("semanticReviewer").asText())
                    &&rule.path("humanReviewedAt").asText().equals(semantics.path("semanticReviewedAt").asText()),"审核责任不一致");
            for(int j=0;j<rule.path("facts").size();j++)
                for(String field:List.of("id","text","when","evidenceDraft","proposedIndependentlyAnswerable"))
                    require(rule.path("facts").get(j).path(field).equals(before.path("facts").get(j).path(field)),"已核对主张发生变化");
            JsonNode chunk=bundle.path("chunks").get(i), allowed=approval.path("clauses").get(i);
            for(String field:List.of("article","locatorId","content","contentChecksum","span","physicalPage","printedPage"))
                require(chunk.path(field).equals(allowed.path(field)),"资格允许的条款与导入正文不符");
            String text=chunk.path("content").asText();
            String locator="PDF物理第"+chunk.path("physicalPage").asInt()+"页／印刷第"+chunk.path("printedPage").asInt()+"页，第"+chunk.path("article").asInt()+"条";
            require(chunk.path("sourceLocator").asText().equals(locator),"原文定位与审核页码/条款不符");
            require(sha(text.getBytes(StandardCharsets.UTF_8)).equals(chunk.path("contentChecksum").asText()),"正文hash不符");
            require(chunk.path("span").path("unit").asText().equals("UTF16_CODE_UNIT")&&chunk.path("span").path("startInclusive").asInt(-1)==0&&chunk.path("span").path("endExclusive").asInt()==text.length(),"UTF16跨度不符");
            for(JsonNode fact:semantics.path("rules").get(i).path("facts")) {
                require(fact.path("semanticReviewState").asText().equals("CONFIRMED"),"主张未确认");
                JsonNode draft=fact.path("evidenceDraft");
                require(draft.path("quote").asText().equals(text)&&draft.path("contentChecksum").asText().equals(chunk.path("contentChecksum").asText())
                        &&draft.path("span").equals(chunk.path("span"))&&draft.path("revision").asText().equals(REVISION),"规则证据与原文不符");
            }
        }
    }

    void importAndBind(DataSource target) throws Exception {
        importChunksAndBaseRules(target,java.util.stream.StreamSupport.stream(bundle.path("chunks").spliterator(),false).toList());
    }
    void importChunksAndBaseRules(DataSource target,List<JsonNode> approvedChunks) throws Exception {
        // 对象可在测试里被修改；写入前再次核对，不能用构造时通过替代最后一次准入。
        validate();dataSource=target;
        var chunks=new ArrayList<ReviewedKnowledgeChunk>();
        for(int i=0;i<approvedChunks.size();i++) {
            JsonNode c=approvedChunks.get(i);
            chunks.add(new ReviewedKnowledgeChunk(i,"第"+c.path("article").asInt()+"条",c.path("sourceLocator").asText(),c.path("physicalPage").asInt(),c.path("content").asText()));
        }
        var publication=new ReviewedKnowledgePublication(source,bundle.path("sourceTitle").asText(),"STUDENT_HANDBOOK",REVISION,null,
                "B03确认的受控本机学生试点；不是公网发布",approval.path("allowedUseIfConfirmed").path("sourceNotice").asText(),
                true,true,ReviewedKnowledgePublication.APPROVED_STATUS,
                LocalDate.parse(approval.path("reviewedAt").asText()).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),chunks);
        new ReviewedKnowledgePublisher(dataSource).publishReplacingScope(publication,"STUDENT_HANDBOOK",SCOPE);
        try(var connection=dataSource.getConnection();var p=connection.prepareStatement("SELECT id,chunk_index,content,content_checksum,source_locator,page_number FROM knowledge_chunk WHERE source_id=? ORDER BY chunk_index")) {
            p.setObject(1,source);try(var rows=p.executeQuery()) {while(rows.next()) {
                JsonNode draft=approvedChunks.get(rows.getInt("chunk_index"));
                require(rows.getString("content").equals(draft.path("content").asText())&&rows.getString("content_checksum").equalsIgnoreCase(draft.path("contentChecksum").asText()),"发布器改变逐字正文");
                // 回读要对审核输入，而不是把数据库错误的位置再当成自己的期望。
                require(rows.getInt("page_number")==draft.path("physicalPage").asInt()&&rows.getString("source_locator").equals(draft.path("sourceLocator").asText()),"发布器原文定位与已审输入不符");
                bindings.put(draft.path("article").asInt(),new Binding(source,rows.getObject("id",UUID.class),draft.path("article").asInt(),
                        rows.getString("content_checksum"),rows.getString("content"),rows.getInt("page_number"),draft.path("printedPage").asInt(),rows.getString("source_locator")));
            }}
        }
        require(bindings.size()==approvedChunks.size(),"实际片段不完整");
        // schema沿用embedding_profile；这里只登记词法身份。NULL向量不代表模型产物，维度1仅为必填保留值。
        try(var c=dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                execute(c,"INSERT INTO embedding_profile(profile_id,provider,model,dimensions,status) VALUES(?,'NONE','LEXICAL_ONLY_NO_EMBEDDINGS',1,'BUILDING')",profile);
                execute(c,"INSERT INTO retrieval_index_run(id,profile_id,chunking_version,status) VALUES(?,?,?,'RUNNING')",run,profile,CUT);
                for(Binding b:bindings.values()) execute(c,"INSERT INTO retrieval_index_entry(id,run_id,profile_id,chunk_id,source_version,chunking_version,content_checksum,embedding) VALUES(?,?,?,?,?,?,?,NULL)",UUID.randomUUID(),run,profile,b.chunkId(),REVISION,CUT,b.checksum());
                execute(c,"UPDATE retrieval_index_run SET status='COMPLETED',finished_at=CURRENT_TIMESTAMP,indexed_entries=? WHERE id=?",bindings.size(),run);
                execute(c,"UPDATE embedding_profile SET status='ACTIVE',activated_at=CURRENT_TIMESTAMP WHERE profile_id=?",profile);
                c.commit();
            }catch(Exception failure){c.rollback();throw failure;}
        }
        for(JsonNode rule:semantics.path("rules")) {
            String item=rule.path("reviewItemId").asText();
            addRule(rule,item.equals("S10")?"COMBINED":"GENERAL",rule.path("facts"),List.of(),item.equals("S10"));
            if(!item.equals("S10"))for(JsonNode gap:rule.path("gapPoliciesDraft"))
                addRule(rule,gap.path("appliesToIntent").asText(),gap.path("expectedOutcome").asText().equals("INSUFFICIENT_EVIDENCE")?mapper.createArrayNode():rule.path("facts"),List.of(gap.path("gap").asText()),false);
            if(item.equals("S10")) {
                var retake=mapper.createArrayNode();var defer=mapper.createArrayNode();
                for(JsonNode fact:rule.path("facts"))if(fact.path("id").asText().contains("retake"))retake.add(fact);else defer.add(fact);
                addRule(rule,"RETAKE_ONLY",retake,List.of(),false);
                addRule(rule,"DEFERRED_EXAM_ONLY",defer,List.of(),true);
            }
        }
        var reader=reader();
        for(Binding b:bindings.values()) require(reader.read(source,b.chunkId()).isPresent(),"未取得当前合格索引依据");
    }

    private void addRule(JsonNode rule,String intent,JsonNode selectedFacts,List<String> gaps,boolean conditionalGap) {
        var facts=new ArrayList<RuleCheckRuleTemplate.Fact>();
        for(JsonNode f:selectedFacts) {
            Binding b=bindings.get(rule.path("article").asInt());JsonNode e=f.path("evidenceDraft");
            facts.add(new RuleCheckRuleTemplate.Fact(f.path("id").asText(),f.path("text").asText(),f.path("proposedIndependentlyAnswerable").asBoolean(),values(f.path("when")),
                    List.of(new RuleCheckRuleTemplate.EvidenceBinding(source,b.chunkId(),REVISION,b.checksum(),e.path("span").path("startInclusive").asInt(),e.path("span").path("endExclusive").asInt(),e.path("quote").asText()))));
        }
        var conditions=new ArrayList<RuleCheckRuleTemplate.Condition>();
        for(JsonNode c:rule.path("conditionsDraft")) conditions.add(new RuleCheckRuleTemplate.Condition(c.path("key").asText(),c.path("question").asText(),strings(c.path("allowedValues"))));
        var branchGaps=new ArrayList<RuleCheckRuleTemplate.ConditionalEvidenceGap>();
        if(conditionalGap)for(JsonNode g:rule.path("gapPoliciesDraft"))branchGaps.add(new RuleCheckRuleTemplate.ConditionalEvidenceGap(g.path("gap").asText(),values(g.path("when"))));
        var template=new RuleCheckRuleTemplate(rule.path("reviewItemId").asText()+"/"+intent,REVISION,RuleCheckRuleTemplate.ReviewState.APPROVED,
                rule.path("humanReviewer").asText(),LocalDate.parse(rule.path("humanReviewedAt").asText()),facts,conditions,gaps,List.of(),branchGaps);
        // 沿已审核本科对象收窄整批使用范围；不新增研究生/其他类别的资料许可。
        catalogue.add(new RuleCheckApplicationService.CatalogEntry(template,rule.path("questionScopeDraft").asText()+" 本项限定用途："+intent+"。 "+approval.path("allowedUseIfConfirmed").path("sourceNotice").asText(),
            new RuleCheckApplicabilityGuard.Coverage(Set.of(RuleCheckApplicabilityGuard.Audience.UNDERGRADUATE))));
    }

    JdbcRuleCheckEvidenceReader reader(){return new JdbcRuleCheckEvidenceReader(dataSource,profile,CUT,SCOPE);}
    RuleCheckOriginalEvidenceService originals(){return new RuleCheckOriginalEvidenceService(reader()::readLocated,REVISION,Set.of());}
    void update(String sql,Object...arguments) throws SQLException {try(var c=dataSource.getConnection()){execute(c,sql,arguments);}}
    static void execute(java.sql.Connection c,String sql,Object...values) throws SQLException {
        try(var p=c.prepareStatement(sql)){for(int i=0;i<values.length;i++)p.setObject(i+1,values[i]);p.executeUpdate();}
    }
    private byte[] readBytes(Path path) throws Exception {
        byte[] bytes=Files.readAllBytes(path);
        assetHashes.put(root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString().replace('\\','/'),sha(bytes));
        return bytes;
    }
    JsonNode read(Path path) throws Exception{return mapper.readTree(readBytes(path));}
    /** 建库前读取实际运行依赖；草案只作本机选题描述，hash不授予外发或政策解释资格。 */
    public List<Map<String,String>> runtimeAssetManifest() throws Exception {
        var draft=Reviewed2025TopicCatalogue.validateAsset(root,this);
        com.zhku.knowledge.ruleagent.RuleArticle4Overlay.validateAssets(root);
        readBytes(root.resolve("docs/evidence/rule-agent-evidence/2026-10-13/t02-01/article4-overlay-draft.json"));
        readBytes(root.resolve("docs/evidence/rule-agent-evidence/2026-10-13/t02-01/human-confirmation.json"));
        var result=new ArrayList<Map<String,String>>();
        assetHashes.forEach((path,hash)->result.add(Map.of("path",path,"sha256",hash,"useBoundary",path.endsWith("rule-check-c05-topic-catalogue.json")?draft.path("status").asText():"EXISTING_REVIEW_BOUNDARY_UNCHANGED")));
        return List.copyOf(result);
    }
    static String sha(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException failure){throw new AssertionError(failure);}}
    static Map<String,String> values(JsonNode node){var m=new LinkedHashMap<String,String>();node.fields().forEachRemaining(e->m.put(e.getKey(),e.getValue().asText()));return m;}
    static List<String> strings(JsonNode node){var list=new ArrayList<String>();node.forEach(n->list.add(n.asText()));return list;}
    static void require(boolean valid,String message){if(!valid)throw new IllegalArgumentException(message);}
    public RuleCheckRuntimeSettings settings() {return new RuleCheckRuntimeSettings(profile,CUT,SCOPE,REVISION,20,5);}
    public List<RuleCheckApplicationService.CatalogEntry> reviewedCatalogue(Path root) throws Exception {
        return Reviewed2025TopicCatalogue.loadWithReviewedLimits(root,this);
    }
    public void installOnIsolatedDatabase(DataSource target) throws Exception {
        // URL与空库双重约束：不能归档已有来源，受控入口不承担资料发布管理职责。
        require(target instanceof org.springframework.jdbc.datasource.DriverManagerDataSource,"只接受专用直接数据源");
        String url=((org.springframework.jdbc.datasource.DriverManagerDataSource)target).getUrl();
        var matcher=java.util.regex.Pattern.compile("^jdbc:postgresql://127\\.0\\.0\\.1:([0-9]{4,5})/zhkuagent_rulecheck_e2e$").matcher(url==null?"":url);
        require(matcher.matches(),"只允许专用回环临时库");int port=Integer.parseInt(matcher.group(1));
        require(port>=1024&&port<=65535&&port!=54329,"拒绝原库或非法端口");
        runtimeAssetManifest();
        try(var connection=target.getConnection();var statement=connection.createStatement();
                var rows=statement.executeQuery("SELECT (SELECT count(*) FROM knowledge_source)+(SELECT count(*) FROM embedding_profile)")) {
            rows.next();require(rows.getLong(1)==0,"受控资料导入只允许空临时库");
        }
        try {importAndBind(target);reviewedCatalogue(root);} catch(Exception failure) {close();throw failure;}
    }
    @Override public void close() throws Exception {
        if(dataSource==null)return;
        update("DELETE FROM citation_audit WHERE index_entry_id IN (SELECT id FROM retrieval_index_entry WHERE profile_id=?)",profile);
        update("DELETE FROM retrieval_index_entry WHERE profile_id=?",profile);update("DELETE FROM retrieval_index_run WHERE profile_id=?",profile);
        update("DELETE FROM embedding_profile WHERE profile_id=?",profile);update("DELETE FROM knowledge_chunk WHERE source_id=?",source);
        update("DELETE FROM knowledge_source WHERE id=?",source);
    }
    record Binding(UUID sourceId,UUID chunkId,int article,String checksum,String text,int physicalPage,int printedPage,String locator){}
}
