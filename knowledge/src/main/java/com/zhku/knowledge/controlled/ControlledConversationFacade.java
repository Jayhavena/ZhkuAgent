package com.zhku.knowledge.controlled;

import com.zhku.knowledge.conversation.*;
import com.zhku.knowledge.security.*;
import com.zhku.knowledge.web.LocalPreviewConversationFacade;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import com.zhku.knowledge.conversation.elective.*;
import com.zhku.knowledge.conversation.publicelective.*;
import com.zhku.knowledge.routing.AnswerRoute;
import com.zhku.knowledge.web.LocalPreviewAnswerChain;
import static com.zhku.knowledge.conversation.ControlledConversationAccessStore.Code.*;

/** 只恢复重修澄清控制状态；actor、同一strict view和请求预算贯穿全部领域层。 */
public final class ControlledConversationFacade {
    private static final Duration TTL = Duration.ofMinutes(10);
    private final ConversationJdbcAccess access;
    private final PreviewIdentityRepository identities;
    private final Clock clock;
    public ControlledConversationFacade(ConversationJdbcAccess access, PreviewIdentityRepository identities, Clock clock) {
        this.access=Objects.requireNonNull(access); this.identities=Objects.requireNonNull(identities); this.clock=Objects.requireNonNull(clock);
    }
    public ConversationState createConversation(TrustedActor actor) {
        var budget = new ConversationOperationBudget();
        var owned = owned(actor,budget);
        UUID candidate = UUID.randomUUID();
        var result = owned.create(ConversationState.active(candidate,clock.instant().plus(TTL)));
        // 单次服务端候选，冲突不循环生成ID，也不把他人的冲突状态投影给调用者。
        if (result.outcome()!=StoreResult.Outcome.CREATED) throw failure(SESSION_CREATE_CONFLICT);
        var confirmed = requireReadable(owned.find(candidate,clock)); budget.check(); return confirmed;
    }
    public ConversationState findConversation(TrustedActor actor, UUID id) {
        Objects.requireNonNull(id);
        var budget=new ConversationOperationBudget();
        var result=requireReadable(owned(actor,budget).find(id,clock)); budget.check(); return result;
    }
    public LocalPreviewConversationFacade.ConversationReply sendMessage(TrustedActor actor, UUID id, long revision, String message) {
        var budget=new ConversationOperationBudget();
        Objects.requireNonNull(id); validateRevision(revision);
        if (message==null || message.isBlank() || message.strip().length()>500) throw new IllegalArgumentException("消息须为1至500字");
        var strict=new ControlledConversationAccessStore(owned(actor,budget),id,revision);
        strict.find(id,clock);
        var finder=new JdbcElectiveRetakeEvidenceFinder(access,budget);
        // 四层必须用同一个strict对象；任何不可见/过期/竞争都抛有限异常，不能落入隐式创建。
        var elective=new ElectiveRetakeConversationService(strict,finder,clock,TTL);
        var unavailableCredit=new PublicElectiveCreditLimitService(() -> Optional.empty());
        var router=new PublicElectiveConversationRouter(strict,elective,unavailableCredit,null,null,clock);
        var chain=new LocalPreviewAnswerChain(strict,router,null,null,clock);
        var original=new LocalPreviewConversationFacade(strict,chain,clock,TTL);
        var reply=original.sendMessage(id,message);
        boolean visible=true;
        if (reply.answer()!=null) {
            var answer=reply.answer();
            visible=finder.isCurrentlyVisible(new ElectiveRetakeAnswer(answer.conclusion(),answer.basis(),answer.sourceId(),
                    answer.sourceTitle(),answer.sourceVersion(),answer.sourceLocator(),answer.applicableScope()));
        }
        // 返回前最后一次Session事务同时核验当前actor；只接受本请求自己的成功revision。
        strict.find(id,clock); budget.check();
        if (!visible) return new LocalPreviewConversationFacade.ConversationReply(ElectiveRetakeReplyType.EVIDENCE_UNAVAILABLE,
                "当前依据已不可核验，请通过人工渠道咨询。",null,AnswerRoute.INSUFFICIENT_EVIDENCE,true,List.of(),List.of(),null);
        return reply;
    }
    public ConversationState cancelConversation(TrustedActor actor, UUID id, long revision) {
        var budget=new ConversationOperationBudget(); Objects.requireNonNull(id); validateRevision(revision);
        var store=owned(actor,budget); var result=store.cancel(id,revision,clock);
        if (result.outcome()==StoreResult.Outcome.STALE_WRITE) throw failure(SESSION_STATE_CHANGED);
        var cancelled=requireReadable(result);
        var current=requireReadable(store.find(id,clock));
        if (!current.equals(cancelled)) throw failure(SESSION_STATE_CHANGED);
        budget.check(); return current;
    }
    private ConversationStore owned(TrustedActor actor, ConversationOperationBudget budget) {
        Objects.requireNonNull(actor); budget.check();
        if (actor.role()!=TrustedActor.Role.USER) throw new ConversationStoreFailure(ConversationStoreFailure.Code.ROLE_DENIED);
        identities.requireCurrent(actor); budget.check();
        return ConversationStoreFactory.createOwned("jdbc",access,actor,clock,budget);
    }
    private static ConversationState requireReadable(StoreResult result) {
        return switch(result.outcome()) {
            case FOUND, CREATED, CANCELLED -> result.state();
            case NOT_FOUND -> throw failure(SESSION_NOT_FOUND);
            case EXPIRED -> throw failure(SESSION_EXPIRED);
            default -> throw failure(SESSION_STATE_CHANGED);
        };
    }
    private static void validateRevision(long revision) { if(revision<0) throw new IllegalArgumentException("revision须非负"); }
    private static ControlledConversationAccessStore.Failure failure(ControlledConversationAccessStore.Code code) {
        return new ControlledConversationAccessStore.Failure(code);
    }
}
