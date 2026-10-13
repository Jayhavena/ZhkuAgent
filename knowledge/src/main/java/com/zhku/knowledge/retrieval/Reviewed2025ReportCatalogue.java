package com.zhku.knowledge.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhku.knowledge.rulecheck.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import static com.zhku.knowledge.rulecheck.RuleCheckTopicGuard.Topic.*;

/** C01批准原文的独立报告装配；不改变默认入口，也不读取报告参考答案生成规则。 */
public final class Reviewed2025ReportCatalogue extends Reviewed2025ControlledCatalogue {
    private static final String SUPPLEMENT="docs/product/rule-check-2025-report-source-supplement.json";
    private static final String RECEIPT="docs/evidence/rule-check-refactor/2026-10-09/c01-c02-eval-assets-01/human-confirmation.json";
    private static final String SUPPLEMENT_SHA="7a29472e68e0d1eb2d902484e3f887ab92d78ef833c865b6feca0f2a7ff71ebd";
    private static final String RECEIPT_SHA="fe93ec28e2dc9c00b8ef234727b70ad6529daf4ffd045bd7b36783ac05df99c2";
    private static final Set<Integer> ARTICLES=Set.of(1,3,7,13,14,15,16,17,19,20);
    private final Path root;
    final JsonNode supplement,confirmation;
    private List<RuleCheckApplicationService.CatalogEntry> assembled=List.of();

    public Reviewed2025ReportCatalogue(Path root) throws Exception {
        super(root);this.root=root;
        supplement=read(root.resolve(SUPPLEMENT));confirmation=read(root.resolve(RECEIPT));
        validateSupplement();
    }
    void validateSupplement() throws Exception {
        validate();
        require(sha(Files.readAllBytes(root.resolve(SUPPLEMENT))).equals(SUPPLEMENT_SHA)
                &&sha(Files.readAllBytes(root.resolve(RECEIPT))).equals(RECEIPT_SHA),"C01冻结输入或直接确认指纹变化");
        // 还核对内存对象；构造时通过不能覆盖写入前被篡改的许可或正文。
        require(supplement.equals(read(root.resolve(SUPPLEMENT)))&&confirmation.equals(read(root.resolve(RECEIPT))),"C01内存输入偏离冻结来源");
        require(confirmation.path("authority").asText().equals("DIRECT_USER_MESSAGE")&&confirmation.path("decision").asText().equals("CONFIRMED")
                &&strings(confirmation.path("confirmedDecisions")).contains("C01-D01"),"缺少新增原文资格确认");
        JsonNode scope=confirmation.path("scope");
        require(scope.path("allowedUse").asText().equals("CONTROLLED_LOCAL_STUDENT_PILOT_EXCERPTS_AND_REVIEWED_EXPLANATIONS")
                &&scope.path("referenceRevision").asText().equals(REVISION)
                &&scope.path("currentAcademicYearApplicability").asText().equals("UNKNOWN"),"补充用途或当期适用边界变化");
        require(supplement.path("humanQualificationConfirmed").asBoolean()
                &&supplement.path("requestedUse").equals(scope.path("allowedUse"))
                &&supplement.path("referenceRevision").asText().equals(REVISION)
                &&supplement.path("sourceFileSha256").equals(bundle.path("sourceFileSha256"))
                &&supplement.path("knowledgeScope").asText().equals(SCOPE)
                &&supplement.path("currentAcademicYearApplicability").asText().equals("UNKNOWN"),"补充来源资格或范围不符");
        var allowed=new HashSet<Integer>();scope.path("newArticles").forEach(a->allowed.add(a.asInt()));
        require(allowed.equals(ARTICLES)&&supplement.path("chunks").size()==10,"补充条款许可数量不符");
        var seen=new HashSet<Integer>();
        for(JsonNode c:supplement.path("chunks")) {
            int article=c.path("article").asInt();String text=c.path("content").asText();
            require(ARTICLES.contains(article)&&seen.add(article),"补充条款越界或重复");
            require(c.path("referenceRevision").asText().equals(REVISION)&&c.path("knowledgeScope").asText().equals(SCOPE)
                    &&c.path("chunkingVersion").asText().equals(CUT),"补充片段身份不符");
            require(sha(text.getBytes(StandardCharsets.UTF_8)).equals(c.path("contentChecksum").asText())
                    &&c.path("span").path("unit").asText().equals("UTF16_CODE_UNIT")
                    &&c.path("span").path("startInclusive").asInt(-1)==0
                    &&c.path("span").path("endExclusive").asInt()==text.length(),"补充正文或UTF16跨度不符");
            require(c.path("sourceLocator").asText().equals("PDF物理第"+c.path("physicalPage").asInt()+"页／印刷第"+c.path("printedPage").asInt()+"页，第"+article+"条"),"补充定位不符");
        }
    }
    @Override void importAndBind(javax.sql.DataSource target) throws Exception {
        validateSupplement();
        var chunks=new ArrayList<JsonNode>();bundle.path("chunks").forEach(chunks::add);supplement.path("chunks").forEach(chunks::add);
        chunks.sort(Comparator.comparingInt(c->c.path("article").asInt()));
        require(chunks.stream().map(c->c.path("article").asInt()).distinct().count()==20,"同一办法20条不完整");
        importChunksAndBaseRules(target,chunks);
        // 旧16项覆盖先按原审核说明装配；补充事实整条逐字引用，避免从参考答案倒推运行模板。
        var entries=new ArrayList<>(super.reviewedCatalogue(root));
        for(JsonNode c:supplement.path("chunks")) {
            int article=c.path("article").asInt();Binding b=bindings.get(article);
            var fact=new RuleCheckRuleTemplate.Fact("article-%02d-verbatim-rule".formatted(article),b.text(),true,Map.of(),
                    List.of(new RuleCheckRuleTemplate.EvidenceBinding(source,b.chunkId(),REVISION,b.checksum(),0,b.text().length(),b.text())));
            var rule=new RuleCheckRuleTemplate("A%02d/GENERAL".formatted(article),REVISION,RuleCheckRuleTemplate.ReviewState.APPROVED,
                    confirmation.path("reviewer").asText(),LocalDate.parse(confirmation.path("reviewedAt").asText()),List.of(fact),List.of(),List.of(),List.of());
            entries.add(new RuleCheckApplicationService.CatalogEntry(rule,scopeFor(article)+" 仅解释一般条文及前提，不认定个人事实、当期通知、实际批准或具体截止日期。引用仅限2025.9.2文本，现行适用性未核实。原文："+b.text(),
                    new RuleCheckApplicabilityGuard.Coverage(Set.of(RuleCheckApplicabilityGuard.Audience.UNDERGRADUATE)),coverageFor(article)));
        }
        assembled=List.copyOf(entries);
    }
    private static String scopeFor(int article) {
        return switch(article) {
            case 1->"院部和教师承担选修课教学任务的责任";
            case 3->"选修教学管理要求及教学质量标准，与必修课比较";
            case 7->"课程建设责任、定期检查与反馈";
            case 13->"选退补改选所依通知，不提供当期安排";
            case 14->"已选课未正式退改选又不参加学习考核的成绩登记前提";
            case 15->"考勤、提前请假、累计缺课总学时超过30%的处理";
            case 16->"平时与期末综合成绩构成，条文未给具体权重";
            case 17->"任课教师提交成绩要求，条文未给具体截止日期";
            case 19->"专业计划外学分申请折算公选学分的一般前提，须本人申请和教务审核同意";
            case 20->"办法公布执行、教务处解释及旧办法废止；未给具体公布日期";
            default->throw new IllegalArgumentException("未批准条款");
        };
    }
    private static RuleCheckTopicGuard.Coverage coverageFor(int article) {
        return switch(article) {
            case 1->RuleCheckTopicGuard.Coverage.declared(TEACHING_DUTY);
            case 3->RuleCheckTopicGuard.Coverage.declared(QUALITY_REQUIREMENTS);
            case 7->RuleCheckTopicGuard.Coverage.declared(COURSE_CONSTRUCTION);
            case 13->RuleCheckTopicGuard.Coverage.declared(SELECTION_CHANGES);
            case 14->RuleCheckTopicGuard.Coverage.declared(SELECTED_ABSENCE);
            case 15->RuleCheckTopicGuard.Coverage.declared(ATTENDANCE);
            case 16->RuleCheckTopicGuard.Coverage.declared(GRADE_COMPOSITION);
            case 17->RuleCheckTopicGuard.Coverage.declared(GRADE_SUBMISSION);
            case 19->RuleCheckTopicGuard.Coverage.declared(CREDIT_CONVERSION);
            case 20->RuleCheckTopicGuard.Coverage.declared(EFFECTIVE_INTERPRETATION);
            default->throw new IllegalArgumentException("未批准条款");
        };
    }
    @Override public List<RuleCheckApplicationService.CatalogEntry> reviewedCatalogue(Path suppliedRoot) throws Exception {
        validateSupplement();require(root.equals(suppliedRoot),"报告根目录不符");
        return assembled.isEmpty()?super.reviewedCatalogue(root):assembled;
    }
}
