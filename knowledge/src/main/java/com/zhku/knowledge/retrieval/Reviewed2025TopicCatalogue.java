package com.zhku.knowledge.retrieval;

import com.zhku.knowledge.rulecheck.*;
import java.nio.file.*;
import java.util.*;

/** 仅本地试验显式使用的选题描述草案，不修改已审事实或正式发布目录。 */
final class Reviewed2025TopicCatalogue {
    static List<RuleCheckApplicationService.CatalogEntry> loadWithReviewedLimits(Path root,Reviewed2025ControlledCatalogue trial) throws Exception {
        var entries=load(root,trial);trial.validate();var result=new ArrayList<RuleCheckApplicationService.CatalogEntry>();
        for(var entry:entries) {
            String semanticId=entry.template().ruleId().split("/",2)[0];com.fasterxml.jackson.databind.JsonNode reviewed=null;
            for(var rule:trial.semantics.path("rules"))if(rule.path("reviewItemId").asText().equals(semanticId))reviewed=rule;
            Reviewed2025ControlledCatalogue.require(reviewed!=null,"缺少已审核的规则解释边界");
            var limits=Reviewed2025ControlledCatalogue.strings(reviewed.path("exceptionsAndUnknowns"));
            // 仅复制与人工审核快照核对过的说明；不扩张事实、对象或当期适用资格。
            String scope=entry.questionScope()+(limits.isEmpty()?"":" 已审核资料边界："+String.join("；",limits));
            result.add(new RuleCheckApplicationService.CatalogEntry(entry.template(),scope,entry.coverage(),entry.topicCoverage()));
        }
        return List.copyOf(result);
    }
    static com.fasterxml.jackson.databind.JsonNode validateAsset(Path root,Reviewed2025ControlledCatalogue trial) throws Exception {
        trial.validate();var draft=trial.read(root.resolve("docs/product/rule-check-c05-topic-catalogue.json"));
        Reviewed2025ControlledCatalogue.require(draft.path("authority").asText().equals("DEVELOPER_PROPOSED_LOCAL_ENGINEERING_OVERLAY_NOT_HUMAN_POLICY_APPROVAL")
            &&draft.path("status").asText().equals("LOCAL_DRAFT_NOT_AUTHORIZED_FOR_TRANSFER_OR_PUBLICATION"),"草案用途不符");
        Reviewed2025ControlledCatalogue.require(draft.path("sourceSemanticPath").asText().equals("docs/product/rule-check-2025-b02-semantic-review.json")
            &&Reviewed2025ControlledCatalogue.sha(Files.readAllBytes(root.resolve(draft.path("sourceSemanticPath").asText()))).equals(draft.path("sourceSemanticSha256").asText()),"草案所依语义输入变化");
        var expected=new LinkedHashSet<String>();
        for(var rule:trial.semantics.path("rules")) {
            String id=rule.path("reviewItemId").asText();
            expected.add(id+"/"+(id.equals("S10")?"COMBINED":"GENERAL"));
            if(id.equals("S10")){expected.add(id+"/RETAKE_ONLY");expected.add(id+"/DEFERRED_EXAM_ONLY");}
            else for(var gap:rule.path("gapPoliciesDraft"))expected.add(id+"/"+gap.path("appliesToIntent").asText());
        }
        var seen=new LinkedHashSet<String>();
        Reviewed2025ControlledCatalogue.require(draft.path("entries").isArray(),"草案目录格式不符");
        for(var item:draft.path("entries")) {
            Reviewed2025ControlledCatalogue.require(seen.add(item.path("ruleId").asText()),"重复规则");
            Reviewed2025ControlledCatalogue.require(!item.path("questionScope").asText().isBlank()&&item.path("topics").isArray()&&!item.path("topics").isEmpty(),"草案范围或主题为空");
            var topics=EnumSet.noneOf(RuleCheckTopicGuard.Topic.class);
            for(var value:item.path("topics"))Reviewed2025ControlledCatalogue.require(topics.add(RuleCheckTopicGuard.Topic.valueOf(value.asText())),"重复主题");
        }
        Reviewed2025ControlledCatalogue.require(seen.equals(expected)&&seen.size()==16,"草案目录不完整或规则身份变化");
        return draft;
    }
    static List<RuleCheckApplicationService.CatalogEntry> load(Path root,Reviewed2025ControlledCatalogue trial) throws Exception {
        var draft=validateAsset(root,trial);
        var declared=new LinkedHashMap<String,RuleCheckApplicationService.CatalogEntry>();
        for(var item:draft.path("entries")) {
            String id=item.path("ruleId").asText();var original=trial.catalogue.stream().filter(e->e.template().ruleId().equals(id)).findFirst().orElseThrow();
            var topics=EnumSet.noneOf(RuleCheckTopicGuard.Topic.class);
            for(var topic:item.path("topics"))Reviewed2025ControlledCatalogue.require(topics.add(RuleCheckTopicGuard.Topic.valueOf(topic.asText())),"重复主题");
            var entry=new RuleCheckApplicationService.CatalogEntry(original.template(),item.path("questionScope").asText()+" 引用仅限2025.9.2文本，现行适用性未核实。",original.coverage(),new RuleCheckTopicGuard.Coverage(topics,true));
            Reviewed2025ControlledCatalogue.require(declared.putIfAbsent(id,entry)==null,"重复规则");
        }
        Reviewed2025ControlledCatalogue.require(declared.size()==trial.catalogue.size()&&declared.size()==16,"草案目录不完整");
        return trial.catalogue.stream().map(e->declared.get(e.template().ruleId())).toList();
    }
}
