package com.zhku.knowledge.retrieval;

import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.util.*;

/** 只校验开发采集准入，不加载报告问题给系统或调用数据库。 */
final class RuleCheckDevelopmentInputs {
    static final String CONTROL="docs/product/rule-check-c03-control-inputs.json";
    static final String DEVELOPMENT="docs/product/rule-check-2025-human-reference-development.json";
    static final String BASE="docs/evidence/rule-check-refactor/2026-10-09/c01-c02-eval-assets-01/";
    static final Set<String> FROZEN=Set.of("docs/product/rule-check-2025-report-source-supplement.json",
        "docs/product/rule-check-2025-human-reference-report.json","docs/product/rule-check-2025-scoring-rubric.json",
        "docs/product/rule-check-2025-c01-c02-review.md");
    record Loaded(JsonNode reference,JsonNode controls,String controlsSha256){}
    static Loaded load(Path root) throws Exception {
        var mapper=new ObjectMapper();byte[] controlBytes=Files.readAllBytes(root.resolve(CONTROL));
        var controls=mapper.readTree(controlBytes);
        require(DEVELOPMENT.equals(controls.path("referencePath").asText()),"采集只允许指定开发参考");
        var freeze=mapper.readTree(root.resolve(BASE+"freeze-manifest.json").toFile());
        require("FROZEN_WITH_ACTUAL_USER_CONFIRMATION".equals(freeze.path("status").asText()),"未冻结");
        var frozenPaths=new HashSet<String>();
        for(var item:freeze.path("files")){require(frozenPaths.add(item.path("path").asText()),"重复冻结项");verify(root,item);}
        require(frozenPaths.equals(FROZEN),"冻结文件集合不完整");
        verifyHash(root.resolve(BASE+"human-confirmation.json"),freeze.path("humanConfirmationSha256").asText());
        verifyHash(root.resolve(BASE+"review-request.json"),freeze.path("reviewRequestSha256").asText());
        var receipt=mapper.readTree(root.resolve(BASE+"human-confirmation.json").toFile());
        var request=mapper.readTree(root.resolve(BASE+"review-request.json").toFile());
        require("DIRECT_USER_MESSAGE".equals(receipt.path("authority").asText())&&"CONFIRMED".equals(receipt.path("decision").asText()),"没有实际用户确认");
        var decisions=new HashSet<String>();receipt.path("confirmedDecisions").forEach(n->decisions.add(n.asText()));
        require(decisions.equals(Set.of("C01-D01","C01-D02","C02-D01")),"确认范围不完整");
        require(receipt.path("presentedFiles").equals(request.path("presentedFiles"))&&receipt.path("presentedFiles").equals(freeze.path("originalPresentedFiles")),"呈审指纹链断裂");
        var presentedPaths=new HashSet<String>();
        for(var item:receipt.path("presentedFiles")){
            require(presentedPaths.add(item.path("path").asText()),"重复呈审项");
            verifyHash(root.resolve(BASE+"presented-"+Path.of(item.path("path").asText()).getFileName()),item.path("sha256").asText());
        }
        require(presentedPaths.equals(FROZEN),"呈审文件不完整");
        for(var item:request.path("protectedInputs"))verify(root,item);
        // 报告仅核对冻结元数据；其问题不会进入开发采集或调优。
        var report=mapper.readTree(root.resolve("docs/product/rule-check-2025-human-reference-report.json").toFile());
        require(report.path("locked").asBoolean()&&report.path("humanReferenceApproved").asBoolean(),"报告参考未确认冻结");
        require(DEVELOPMENT.equals(report.path("developmentReference").path("path").asText()),"冻结开发指针错误");
        byte[] referenceBytes=Files.readAllBytes(root.resolve(DEVELOPMENT));String referenceSha=Reviewed2025Trial.sha(referenceBytes);
        require(referenceSha.equals(controls.path("referenceSha256").asText())&&referenceSha.equals(report.path("developmentReference").path("sha256").asText()),"开发参考指纹变化");
        var reference=mapper.readTree(referenceBytes);var ids=new HashSet<String>();
        require(reference.path("humanReferenceApproved").asBoolean()&&reference.path("records").size()==20,"开发参考不完整");
        for(var item:reference.path("records")){
            require(ids.add(item.path("reviewId").asText()),"开发题重复");
            require("DEVELOPMENT".equals(item.path("split").asText())&&"HUMAN_CONFIRMED".equals(item.path("annotationStatus").asText())
                &&"DIRECT_USER_MESSAGE".equals(item.path("humanReview").path("authority").asText())&&"CONFIRMED".equals(item.path("humanReview").path("decision").asText()),"开发题未确认");
        }
        var expected=new HashSet<String>();for(int i=1;i<=20;i++)expected.add("Q%02d".formatted(i));
        var controlIds=new HashSet<String>();controls.path("controls").fieldNames().forEachRemaining(controlIds::add);
        require(ids.equals(expected)&&controlIds.equals(expected),"不是指定20题");
        require("DEVELOPMENT".equals(controls.path("split").asText())&&controls.path("candidateLimit").asInt()==20&&controls.path("resultLimit").asInt()==5
            &&controls.path("suppliedConditions").isObject()&&controls.path("suppliedConditions").isEmpty()
            &&"CONFIGURED_DISABLED_NO_PROVIDER".equals(controls.path("vectorMode").asText())&&controls.path("providerCalls").asInt(-1)==0
            &&"EXPLICIT_MANUAL_CATALOGUE_ID_CONTROL_NOT_NATURAL_LANGUAGE_ROUTING".equals(controls.path("selectionMode").asText()),"控制配置与实际路径不符");
        return new Loaded(reference,controls,Reviewed2025Trial.sha(controlBytes));
    }
    static void verifyUnchanged(Path root,Loaded loaded) throws Exception {
        verifyHash(root.resolve(CONTROL),loaded.controlsSha256());
        verifyHash(root.resolve(DEVELOPMENT),loaded.controls().path("referenceSha256").asText());
    }
    private static void verify(Path root,JsonNode item) throws Exception {
        Path file=root.resolve(item.path("path").asText()).normalize();require(file.startsWith(root.normalize()),"指纹路径越界");verifyHash(file,item.path("sha256").asText());
    }
    private static void verifyHash(Path file,String hash) throws Exception {require(hash.matches("[0-9a-f]{64}")&&Reviewed2025Trial.sha(Files.readAllBytes(file)).equals(hash),"输入指纹变化");}
    private static void require(boolean okay,String message){if(!okay)throw new IllegalArgumentException(message);}
}
