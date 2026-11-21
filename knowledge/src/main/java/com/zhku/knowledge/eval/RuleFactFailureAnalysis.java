package com.zhku.knowledge.eval;

import java.nio.file.*;
import java.util.*;
import static com.zhku.knowledge.eval.RuleFactExecutionContract.*;
import static com.zhku.knowledge.eval.RuleFactOfflineScorer.*;

/** 只分类既有评分和绑定审阅，不产生或覆盖评分；观察到失败层不等于证明根因。 */
public final class RuleFactFailureAnalysis {
    public static final String REVISION="OBSERVED_LAYER_V1";
    public enum Category { RETRIEVAL_FAILURE, CONDITION_JUDGMENT_ERROR, INSUFFICIENT_EVIDENCE,
        CITATION_ERROR, EXECUTION_EXCEPTION, ANSWER_INCOMPLETE, ATTRIBUTION_UNKNOWN }
    public record Finding(Category category,String subtype,boolean defect,String reason,List<String> evidenceRefs) {}
    public record Review(String caseId,String capturePath,String captureSha256,Category category,String subtype,
                         boolean defect,String reviewer,String reason,String answerExcerpt) {}
    public record Analysis(String revision,Map<String,List<Finding>> cases,Map<Category,Long> affectedCases,Map<Category,Long> nonDefectCases) {}
    static Analysis classify(RuleFactBatchReport.Report report,List<Review> reviews) {
        var cases=new LinkedHashMap<String,List<Finding>>();
        report.cases().forEach((id,c)->{
            var findings=new ArrayList<Finding>();var refs=c.turns().stream().filter(t->t.evidencePath()!=null)
                    .map(t->t.evidencePath()+" sha256="+t.evidenceSha()).toList();
            if(c.dimensions().get("retrievalRecall").verdict()==Verdict.FAIL)
                findings.add(new Finding(Category.RETRIEVAL_FAILURE,"REQUIRED_LOCATOR_NOT_RETRIEVED",true,"完整检索观察未覆盖冻结必要定位；不据此推断查询或索引根因。",refs));
            if(c.turns().stream().anyMatch(t->t.error()!=null&&t.error().startsWith("ERROR:")))
                findings.add(new Finding(Category.EXECUTION_EXCEPTION,"HARNESS_OR_REVIEW_ERROR",false,
                        String.join(";",c.dimensions().get("executionSafety").reasons()),refs));
            if(c.dimensions().get("executionSafety").reasons().stream().anyMatch(r->r.endsWith(":EXECUTION_FAILED_OR_STOPPED")))
                findings.add(new Finding(Category.EXECUTION_EXCEPTION,"AGENT_FAILED_OR_STOPPED",true,
                        String.join(";",c.dimensions().get("executionSafety").reasons()),refs));
            // 引用缺失与错误引用在语义复核中分别标注；维度FAIL本身不能证明伪造来源。
            if(c.dimensions().get("citationIntegrity").verdict()==Verdict.FAIL)
                findings.add(new Finding(Category.CITATION_ERROR,"CITATION_REQUIREMENT_FAILED_UNTYPED",false,
                        String.join(";",c.dimensions().get("citationIntegrity").reasons()),refs));
            for(var review:reviews)if(id.equals(review.caseId()))findings.add(new Finding(review.category(),review.subtype(),review.defect(),
                    review.reviewer()+": "+review.reason()+"; excerpt="+review.answerExcerpt(),List.of(review.capturePath()+" sha256="+review.captureSha256())));
            if(!"PASS".equals(c.outcome())&&findings.isEmpty())findings.add(new Finding(Category.ATTRIBUTION_UNKNOWN,
                    "REVIEW_REQUIRED",false,"评分异常或不完整，尚无足够证据判定失败类别；缺观察不能自动认定检索失败。",refs));
            cases.put(id,List.copyOf(findings));
        });
        var counts=new EnumMap<Category,Long>(Category.class);var nonDefects=new EnumMap<Category,Long>(Category.class);for(var category:Category.values()) {
            counts.put(category,cases.values().stream().filter(fs->fs.stream().anyMatch(f->f.category()==category&&f.defect())).count());
            nonDefects.put(category,cases.values().stream().filter(fs->fs.stream().anyMatch(f->f.category()==category&&!f.defect())&&fs.stream().noneMatch(f->f.category()==category&&f.defect())).count());
        }
        return new Analysis(REVISION,cases,counts,nonDefects);
    }
    static List<Review> loadReviews(Path base,RuleFactBatchReport.Report report)throws Exception {
        Path file=base.resolve("failure-reviews.json");if(!Files.isRegularFile(file))return List.of();
        var reviews=List.of(JSON.readValue(Files.readAllBytes(file),Review[].class));
        for(var r:reviews){
            var c=report.cases().get(r.caseId());if(c==null||r.category()==null||r.subtype()==null||r.subtype().isBlank()||
                    r.reviewer()==null||r.reviewer().isBlank()||r.reason()==null||r.reason().isBlank()||r.answerExcerpt()==null||r.answerExcerpt().isBlank())
                throw new IllegalArgumentException("incomplete failure review");
            var turn=c.turns().stream().filter(t->Objects.equals(t.evidencePath(),r.capturePath())&&Objects.equals(t.evidenceSha(),r.captureSha256())&&t.result()!=null).findFirst().orElseThrow(()->new IllegalArgumentException("unbound failure review"));
            Path capture=base.resolve(turn.evidencePath()).toRealPath();if(!capture.startsWith(base.toRealPath())||!sha(Files.readAllBytes(capture)).equals(r.captureSha256()))throw new IllegalArgumentException("failure review capture drift");
            var observed=JSON.readValue(Files.readAllBytes(capture),RuleFactBoundEvaluation.Captured.class);
            if(observed.execution().answer()==null||!observed.execution().answer().contains(r.answerExcerpt()))throw new IllegalArgumentException("unanchored failure review");
        }return reviews;
    }
    public static String markdown(Analysis analysis){
        var out=new StringBuilder("\n## 失败分类（"+REVISION+"）\n\n多标签按题去重，类别之间可重叠；宿主/标注错误、待归因项和预期数据边界不计Agent缺陷。自动引用失败需独立细分后才能计缺陷。非缺陷/待归因列排除同类已确认缺陷的题，保留未知可见性。\n\n| 类别 | 已证实Agent缺陷题数 | 非缺陷/待归因题数 |\n| --- | --- | --- |\n");
        analysis.affectedCases().forEach((k,v)->out.append("| ").append(k).append(" | ").append(v).append(" | ").append(analysis.nonDefectCases().get(k)).append(" |\n"));
        analysis.cases().forEach((id,fs)->fs.forEach(f->out.append("\n- ").append(id).append(" / ").append(f.category()).append(" / ").append(f.subtype()).append(" / defect=").append(f.defect()).append(": ").append(f.reason()).append("；证据：").append(f.evidenceRefs()).append('\n')));
        return out.toString();
    }
}
