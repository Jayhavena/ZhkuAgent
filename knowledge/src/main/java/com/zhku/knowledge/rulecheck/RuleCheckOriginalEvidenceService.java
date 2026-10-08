package com.zhku.knowledge.rulecheck;

import java.util.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/** 内部原文定位；未来HTTP必须从服务端会话取摘录，公开record构造器不是客户端授权。 */
public final class RuleCheckOriginalEvidenceService {
    public enum Code { FOUND,INVALID_REFERENCE,REFERENCE_CHANGED,UNAVAILABLE }
    public record View(String citationId,UUID sourceId,UUID chunkId,String revision,String title,String sourceLocator,
        Integer recordedPageNumber,String pageNumberKind,String sectionTitle,String safeSourceUrl,
        String contextText,int contextStartInclusive,int quoteStartInclusive,int quoteEndExclusive,List<String> unknownItems) { }
    public record Result(Code code,View original) {
        public Result {if(code==null||(code==Code.FOUND)!=(original!=null))throw new IllegalArgumentException("原文结果必须成功或无正文拒绝");}
    }
    @FunctionalInterface public interface Reader {Optional<RuleCheckLocatedEvidence> read(UUID sourceId,UUID chunkId);}
    private final Reader reader;
    private final String referenceRevision;
    private final Set<String> allowedHosts;
    public RuleCheckOriginalEvidenceService(Reader reader,String referenceRevision,Set<String> allowedHosts) {
        this.reader=Objects.requireNonNull(reader);
        if(referenceRevision==null||referenceRevision.isBlank())throw new IllegalArgumentException("原文引用版本必须固定");
        this.referenceRevision=referenceRevision;
        var hosts=new HashSet<String>();
        for(String host:Objects.requireNonNull(allowedHosts)) {
            if(host==null||!host.matches("[a-zA-Z0-9](?:[a-zA-Z0-9.-]*[a-zA-Z0-9])?")||host.contains(".."))
                throw new IllegalArgumentException("原件地址只接受显式精确host");
            hosts.add(host.toLowerCase(Locale.ROOT));
        }
        this.allowedHosts=Set.copyOf(hosts);
    }
    public Result locate(RuleCheckVerifiedExcerpt reference) {
        if(reference==null||reference.citationId()==null||reference.citationId().isBlank()||reference.sourceId()==null||reference.chunkId()==null
                ||!referenceRevision.equals(reference.revision())||reference.checksum()==null||!reference.checksum().matches("[0-9a-fA-F]{64}")
                ||reference.text()==null||reference.text().isBlank()||reference.startInclusive()<0||reference.endExclusive()<=reference.startInclusive()
                ||(long)reference.endExclusive()-reference.startInclusive()!=reference.text().length())return refused(Code.INVALID_REFERENCE);
        try {
            var found=reader.read(reference.sourceId(),reference.chunkId());
            if(found==null||found.isEmpty()||found.get().snapshot()==null)return refused(Code.UNAVAILABLE);
            var row=found.get();var snapshot=row.snapshot();
            if(!snapshot.eligible()||!reference.sourceId().equals(snapshot.sourceId())||!reference.chunkId().equals(snapshot.chunkId())
                    ||!reference.revision().equals(snapshot.revision())||!reference.checksum().equalsIgnoreCase(snapshot.checksum()))
                return refused(Code.REFERENCE_CHANGED);
            // 当前正文/元数据来自一次资格SQL；这里memo只复用Verifier的hash/span/Unicode校验，不是假第二次fresh SQL。
            var captured=new RuleCheckEvidenceSnapshot(reference.citationId(),snapshot.sourceId(),snapshot.chunkId(),snapshot.revision(),snapshot.checksum(),snapshot.text(),snapshot.eligible());
            var verified=new RuleCheckEvidenceVerifier((s,c)->Optional.of(captured)).verify(
                new RuleCheckEvidenceSpan(reference.citationId(),reference.startInclusive(),reference.endExclusive(),reference.text()),List.of(captured));
            if(!verified.accepted())return refused(Code.REFERENCE_CHANGED);
            int start=Math.max(0,reference.startInclusive()-256),end=(int)Math.min((long)snapshot.text().length(),(long)reference.endExclusive()+256);
            // 向内收缩避免切代理对，并严格保留每侧最多256单位；quote本身边界已核验。
            if(splits(snapshot.text(),start))start++;if(splits(snapshot.text(),end))end--;
            String context=snapshot.text().substring(start,end);
            // 完整核验摘录不能为了展示预算被截断；大段引用须重新切片或改为更小的核验跨度。
            if(context.getBytes(StandardCharsets.UTF_8).length>8192)return refused(Code.UNAVAILABLE);
            var unknown=new ArrayList<String>();
            String title=bounded(row.title(),300),locator=bounded(row.sourceLocator(),500),section=bounded(row.sectionTitle(),2048),url=safeUrl(row.sourceUrl());
            if(title==null)unknown.add("来源标题尚未记录");if(locator==null)unknown.add("条款定位尚未记录");
            Integer page=row.recordedPageNumber();if(page!=null&&page<1)page=null;
            if(page==null)unknown.add("来源页码尚未记录");else unknown.add("记录页码的物理页与印刷页映射尚未核实");
            if(url==null)unknown.add("暂无已验证安全的原件地址");
            return new Result(Code.FOUND,new View(reference.citationId(),reference.sourceId(),reference.chunkId(),reference.revision(),title,locator,
                page,page==null?"UNKNOWN":"RECORDED_SOURCE_PAGE",section,url,context,start,reference.startInclusive(),reference.endExclusive(),List.copyOf(unknown)));
        } catch(RuntimeException unavailable) {
            // 读库、映射与坏数据都不能回显旧正文/底层cause；下发后的撤回窗口仍须HTTP层单独处理。
            return refused(Code.UNAVAILABLE);
        }
    }
    private String safeUrl(String raw) {
        if(raw==null||raw.length()>2048)return null;
        try {
            URI uri=URI.create(raw);
            if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getRawUserInfo()!=null
                    ||(uri.getPort()!=-1&&uri.getPort()!=443)||!allowedHosts.contains(uri.getHost().toLowerCase(Locale.ROOT)))return null;
            // 仅返回已审核字段的允许链接，不服务端抓取/跟随重定向，也不擅加未经核对的#page。
            return raw;
        } catch(IllegalArgumentException invalid){return null;}
    }
    private static boolean splits(String text,int position){return position>0&&position<text.length()&&Character.isHighSurrogate(text.charAt(position-1))&&Character.isLowSurrogate(text.charAt(position));}
    private static String bounded(String value,int max){return value==null||value.isBlank()||value.length()>max?null:value;}
    private static Result refused(Code code){return new Result(code,null);}
}
