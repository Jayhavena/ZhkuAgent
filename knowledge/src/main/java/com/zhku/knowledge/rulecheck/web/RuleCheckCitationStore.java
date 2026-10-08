package com.zhku.knowledge.rulecheck.web;

import com.zhku.knowledge.rulecheck.RuleCheckVerifiedExcerpt;
import com.zhku.knowledge.security.TrustedActor;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 服务端引用凭据组件；HTTP装配不得接受客户端自建VerifiedExcerpt。 */
public final class RuleCheckCitationStore {
    public static final class Failure extends RuntimeException {public Failure(){super("引用凭据已失效",null,false,false);}}
    public record Ticket(String generation,String token,RuleCheckVerifiedExcerpt excerpt) {}
    private static final String ATTRIBUTE=RuleCheckCitationStore.class.getName()+".current";
    private static final Duration TTL=Duration.ofMinutes(5);
    private final Clock clock;
    private record Stored(String generation,TrustedActor owner,Instant expiresAt,Map<String,RuleCheckVerifiedExcerpt> references) { }
    public RuleCheckCitationStore(Clock clock) {this.clock=Objects.requireNonNull(clock);}
    public String begin(HttpSession session,TrustedActor actor) {
        requireOwner(session,actor);
        try {synchronized(session) {
            String generation=UUID.randomUUID().toString();
            // begin即替换旧引用；慢请求不能把上一题正文恢复成当前题的可读凭据。
            session.setAttribute(ATTRIBUTE,new Stored(generation,actor,clock.instant().plus(TTL),null));return generation;
        }}catch(IllegalStateException invalidSession){throw new Failure();}
    }
    public Map<RuleCheckVerifiedExcerpt,String> publish(HttpSession session,TrustedActor actor,String generation,List<RuleCheckVerifiedExcerpt> excerpts) {
        requireOwner(session,actor);
        try {synchronized(session) {
            var saved=current(session,actor);
            if(!saved.generation().equals(generation)||saved.references()!=null)throw new Failure();
            var unique=bounded(excerpts);var references=new LinkedHashMap<String,RuleCheckVerifiedExcerpt>();
            var handles=new LinkedHashMap<RuleCheckVerifiedExcerpt,String>();
            for(var quote:unique.values()) {String token=UUID.randomUUID().toString();references.put(token,quote);handles.put(quote,token);}
            // 校验与handle构造也耗时；不能只在入口检查TTL而在期限后发布已完成凭据。
            current(session,actor);
            // 不延长开始时的期限；空引用终态仍标完成，不能迟到补写另一份答案的引用。
            session.setAttribute(ATTRIBUTE,new Stored(generation,actor,saved.expiresAt(),Map.copyOf(references)));
            return Map.copyOf(handles);
        }}catch(IllegalStateException invalidSession){throw new Failure();}
    }
    public Ticket resolve(HttpSession session,TrustedActor actor,String token) {
        requireOwner(session,actor);
        if(!opaque(token))throw new Failure();
        try {synchronized(session) {
            var saved=current(session,actor);
            var quote=saved.references()==null?null:saved.references().get(token);
            if(quote==null)throw new Failure();return new Ticket(saved.generation(),token,quote);
        }}catch(IllegalStateException invalidSession){throw new Failure();}
    }
    public void requireCurrent(HttpSession session,TrustedActor actor,Ticket ticket) {
        requireOwner(session,actor);
        if(ticket==null||!opaque(ticket.token()))throw new Failure();
        try {synchronized(session) {
            var saved=current(session,actor);
            // 原文读取后再次比对完整服务端条目；公开Ticket构造器不能自证其正文和轮次。
            if(!saved.generation().equals(ticket.generation())||saved.references()==null||ticket.excerpt()==null
                    ||!ticket.excerpt().equals(saved.references().get(ticket.token())))throw new Failure();
        }}catch(IllegalStateException invalidSession){throw new Failure();}
    }
    public void clearOwn(HttpSession session,TrustedActor actor,String generation) {
        if(session==null||actor==null||generation==null)return;
        try {synchronized(session) {
            Object value=session.getAttribute(ATTRIBUTE);
            // 迟到旧actor/旧generation的异常清理不能删除新账号或新问题的引用。
            if(value instanceof Stored saved&&saved.owner().equals(actor)&&saved.generation().equals(generation))session.removeAttribute(ATTRIBUTE);
        }}catch(IllegalStateException invalidSession){ }
    }
    private Stored current(HttpSession session,TrustedActor actor) {
        Object value=session.getAttribute(ATTRIBUTE);
        if(!(value instanceof Stored saved)||!saved.owner().equals(actor))throw new Failure();
        if(!clock.instant().isBefore(saved.expiresAt())){session.removeAttribute(ATTRIBUTE);throw new Failure();}
        return saved;
    }
    private static void requireOwner(HttpSession session,TrustedActor actor) {
        if(session==null||actor==null||actor.role()!=TrustedActor.Role.USER)throw new Failure();
    }
    private static boolean opaque(String token) {
        if(token==null||token.length()!=36)return false;
        try{return UUID.fromString(token).toString().equals(token);}catch(IllegalArgumentException invalid){return false;}
    }
    private static Map<String,RuleCheckVerifiedExcerpt> bounded(List<RuleCheckVerifiedExcerpt> excerpts) {
        // 先限制输入数量再去重，重复引用不能绕过遍历预算；只留摘录，不留问题/条件/历史回复。
        if(excerpts==null||excerpts.size()>32)throw invalid();
        var unique=new LinkedHashMap<String,RuleCheckVerifiedExcerpt>();int bytes=0;
        for(var quote:excerpts) {
            if(quote==null||!text(quote.citationId(),128)||quote.sourceId()==null||quote.chunkId()==null
                    ||!text(quote.revision(),128)||quote.checksum()==null||!quote.checksum().matches("[0-9a-fA-F]{64}")
                    ||!text(quote.text(),4096)||quote.startInclusive()<0||quote.endExclusive()<=quote.startInclusive()
                    ||(long)quote.endExclusive()-quote.startInclusive()!=quote.text().length())throw invalid();
            int size=quote.text().getBytes(StandardCharsets.UTF_8).length;if(size>8192)throw invalid();
            var prior=unique.putIfAbsent(quote.citationId(),quote);
            if(prior!=null&&!prior.equals(quote))throw invalid();
            if(prior==null){bytes+=size;if(bytes>32768)throw invalid();}
        }
        return unique;
    }
    private static boolean text(String value,int limit) {
        if(value==null||value.isBlank()||value.length()>limit)return false;
        for(int i=0;i<value.length();i++) {
            char item=value.charAt(i);
            if(Character.isHighSurrogate(item)){if(++i>=value.length()||!Character.isLowSurrogate(value.charAt(i)))return false;}
            else if(Character.isLowSurrogate(item))return false;
        }
        return true;
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("服务端引用超出受控存储边界");}
}
