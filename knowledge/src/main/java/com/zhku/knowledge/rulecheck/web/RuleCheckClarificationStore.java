package com.zhku.knowledge.rulecheck.web;

import com.zhku.knowledge.rulecheck.RuleCheckScopedResult.Continuation;
import com.zhku.knowledge.security.TrustedActor;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.Objects;

/** 每个登录Session只保存一个必要上下文，不保存回复正文或完整咨询历史。 */
public final class RuleCheckClarificationStore {
    public static final class Failure extends RuntimeException {public Failure(){super("需要重新查证",null,false,false);}}
    private static final String ATTRIBUTE=RuleCheckClarificationStore.class.getName()+".active";
    private static final Duration TTL=Duration.ofMinutes(5);
    private final Clock clock;
    private record Stored(String token,TrustedActor owner,Instant expiresAt,Continuation context) {}
    public RuleCheckClarificationStore(Clock clock) {this.clock=Objects.requireNonNull(clock);}
    public record Ticket(Continuation context,String generation) {}
    public String begin(HttpSession session,TrustedActor actor) {
        if(session==null||actor==null||actor.role()!=TrustedActor.Role.USER)throw new Failure();
        String generation=UUID.randomUUID().toString();
        try {synchronized(session){session.setAttribute(ATTRIBUTE,new Stored(generation,actor,clock.instant().plus(TTL),null));}return generation;}
        catch(IllegalStateException invalidSession){throw new Failure();}
    }
    public Ticket consumeAndBegin(HttpSession session,TrustedActor actor,String token,String question,Map<String,String> delta) {
        if(session==null)throw new Failure();
        // 消费旧轮与占据下一轮slot必须同一锁，否则新问题可能插入交接窗口被旧续答覆盖。
        synchronized(session){var context=consume(session,actor,token,question,delta);return new Ticket(context,begin(session,actor));}
    }
    public String finish(HttpSession session,TrustedActor actor,String generation,Continuation context) {
        if(session==null||actor==null)throw new Failure();
        try {
            synchronized(session) {
                Object item=session.getAttribute(ATTRIBUTE);
                if(!(item instanceof Stored saved)||!saved.token().equals(generation)||!saved.owner().equals(actor))throw new Failure();
                if(!clock.instant().isBefore(saved.expiresAt())){session.removeAttribute(ATTRIBUTE);throw new Failure();}
                if(saved.context()!=null)throw new Failure();
                // 较早慢请求既不能发布旧上下文，也不能在终态/异常时清掉较新问题。
                if(context==null){session.removeAttribute(ATTRIBUTE);return null;}
                session.setAttribute(ATTRIBUTE,new Stored(generation,actor,saved.expiresAt(),context));return generation;
            }
        } catch(IllegalStateException invalidSession){throw new Failure();}
    }
    public String put(HttpSession session,TrustedActor actor,Continuation context) {
        if(session==null||actor==null||actor.role()!=TrustedActor.Role.USER||context==null)throw new Failure();
        synchronized(session){String generation=begin(session,actor);return finish(session,actor,generation,context);}
    }
    public Continuation consume(HttpSession session,TrustedActor actor,String token,String question,Map<String,String> delta) {
        if(session==null||actor==null)throw new Failure();
        try {
            synchronized(session) {
                Object item=session.getAttribute(ATTRIBUTE);
                if(!(item instanceof Stored saved))throw new Failure();
                if(!clock.instant().isBefore(saved.expiresAt())) {
                    session.removeAttribute(ATTRIBUTE);throw new Failure();
                }
                if(!saved.owner().equals(actor)) {
                    // 只清被本token指向的旧身份上下文，迟到的旧账号请求不能清新账号的新问题。
                    if(saved.token().equals(token))session.removeAttribute(ATTRIBUTE);throw new Failure();
                }
                if(saved.context()==null)throw new Failure();
                // 错误旧token不清除同账户的新问题；期限绝不因错误尝试延长。
                if(!saved.token().equals(token))throw new Failure();
                var context=saved.context();String key=context.nextCondition().key();
                if(!context.question().equals(question)||delta==null||delta.size()!=1||!delta.containsKey(key)
                        ||!context.nextCondition().allowedValues().contains(delta.get(key)))
                    throw new IllegalArgumentException("请只声明本轮待问条件，其他更改须重新查证");
                // 校验后在同一Session锁内一次消费；并发不能复用nonce混合两轮条件。
                session.removeAttribute(ATTRIBUTE);return context;
            }
        } catch(IllegalStateException invalidSession) {throw new Failure();}
    }
    public void clear(HttpSession session) {
        if(session==null)return;
        try {synchronized(session){session.removeAttribute(ATTRIBUTE);}}
        catch(IllegalStateException invalidSession) { }
    }
    public void clearOwn(HttpSession session,TrustedActor actor,String generation) {
        if(session==null||actor==null||generation==null)return;
        try {synchronized(session) {
            Object item=session.getAttribute(ATTRIBUTE);
            // 包括已完成的上下文；迟到异常只能清本轮，不能删新登录或新问题。
            if(item instanceof Stored saved&&saved.owner().equals(actor)&&saved.token().equals(generation))session.removeAttribute(ATTRIBUTE);
        }} catch(IllegalStateException invalidSession) { }
    }
}
