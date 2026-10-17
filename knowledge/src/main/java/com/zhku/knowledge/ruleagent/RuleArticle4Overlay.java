package com.zhku.knowledge.ruleagent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.knowledge.rulecheck.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;

/** 独立增量审核，不改写历史许可或解释；使用者仍须fresh提供合格基础目录。 */
public final class RuleArticle4Overlay {
    private RuleArticle4Overlay(){}
    private static final String BASE="docs/evidence/rule-agent-evidence/2026-10-13/t02-01";
    private static final String DRAFT="274079977305d649883ebd45f20409380806417039bd5ae20b4d7fe8ce7a80ab",RECEIPT="64d67b1f31440f7922c621341f0bc8d44915ad4ab14993cb2e9b5e60afd947c9";
    /** 与运行装配共用的增量审核准入；不依赖数据库绑定。 */
    public static com.fasterxml.jackson.databind.JsonNode validateAssets(Path root) throws Exception {
            byte[] draftBytes=Files.readAllBytes(root.resolve(BASE+"/article4-overlay-draft.json")),receiptBytes=Files.readAllBytes(root.resolve(BASE+"/human-confirmation.json"));
            if(!sha(draftBytes).equals(DRAFT)||!sha(receiptBytes).equals(RECEIPT))throw new IllegalArgumentException("审核身份变化");
            var json=new ObjectMapper();var draft=json.readTree(draftBytes);var receipt=json.readTree(receiptBytes);
            if(!receipt.path("authority").asText().equals("DIRECT_USER_MESSAGE")||!receipt.path("decision").asText().equals("CONFIRMED")||!receipt.path("reviewedDraftSha256").asText().equals(DRAFT))throw new IllegalArgumentException("未确认");
            return draft;
    }
    public static List<RuleCheckApplicationService.CatalogEntry> apply(Path root,List<RuleCheckApplicationService.CatalogEntry> base){
        try{
            var draft=validateAssets(root);
            // 发布器以大写存储SHA-256；大小写是编码表示差异，仍须逐字跨度和同一摘要匹配。
            var original=base.stream().filter(e->e.template().reviewState()==RuleCheckRuleTemplate.ReviewState.APPROVED).filter(e->e.template().facts().stream().flatMap(f->f.evidenceBindings().stream()).anyMatch(b->b.checksum().equalsIgnoreCase(draft.path("sourceContentChecksum").asText())&&b.startInclusive()==0&&b.endExclusive()==78)).findFirst().orElseThrow();
            var binding=original.template().facts().stream().flatMap(f->f.evidenceBindings().stream()).filter(b->b.checksum().equalsIgnoreCase(draft.path("sourceContentChecksum").asText())&&b.startInclusive()==0&&b.endExclusive()==78).findFirst().orElseThrow();
            var facts=new ArrayList<RuleCheckRuleTemplate.Fact>();
            for(var item:draft.path("facts")){
                int start=item.path("startInclusive").asInt(),end=item.path("endExclusive").asInt();String quote=item.path("quote").asText();
                if(!binding.quote().substring(start,end).equals(quote))throw new IllegalArgumentException("跨度变化");
                facts.add(new RuleCheckRuleTemplate.Fact(item.path("id").asText(),item.path("text").asText(),true,Map.of(),List.of(new RuleCheckRuleTemplate.EvidenceBinding(binding.sourceId(),binding.chunkId(),binding.revision(),binding.checksum(),start,end,quote))));
            }
            var rule=new RuleCheckRuleTemplate("T02/ARTICLE4",binding.revision(),RuleCheckRuleTemplate.ReviewState.APPROVED,"DIRECT_USER_MESSAGE:T02-D01-D02",LocalDate.of(2026,10,13),facts,List.of(),List.of(),List.of());
            var result=new ArrayList<>(base);result.add(new RuleCheckApplicationService.CatalogEntry(rule,"仅解释2025第4条修读安排，不认定个人资格、课表、当期安排或现行适用。",original.coverage(),original.topicCoverage()));return List.copyOf(result);
        }catch(Exception invalid){throw new IllegalArgumentException("增量资格或原文不可用",invalid);}
    }
    private static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
}
