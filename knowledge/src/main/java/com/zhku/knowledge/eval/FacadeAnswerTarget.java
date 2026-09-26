package com.zhku.knowledge.eval;

import com.zhku.knowledge.web.LocalPreviewAnswerChain;
import com.zhku.knowledge.web.LocalPreviewConversationFacade;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 真链路适配器：把"本机预览门面的一次问答"映射成评测的"实际"一侧。
 *
 * <p><b>为什么引用要同时取两处</b>：规则级/通知级的回答卡把条级定位放在 `answer.sourceLocator`
 * （如「PDF 第 77 页，第 9、13 条」），而检索级的引用在 `citations` 列表里。只取一处会让
 * 另一类用例永远"引用未命中"——评测就会把实现细节当成缺陷报出来。两处都取，去重后一并比对。</p>
 *
 * <p>依赖以 `Function<String, ConversationReply>` 注入而不是直接持有门面：适配器的映射逻辑
 * 就能用离线替身测死，接真链路只是把真实门面传进来。</p>
 */
public final class FacadeAnswerTarget implements AnswerTarget {

    private final Function<String, LocalPreviewConversationFacade.ConversationReply> ask;

    public FacadeAnswerTarget(
            Function<String, LocalPreviewConversationFacade.ConversationReply> ask) {
        this.ask = Objects.requireNonNull(ask, "问答入口不能为空");
    }

    @Override
    public ObservedAnswer answer(String question) {
        LocalPreviewConversationFacade.ConversationReply reply =
                Objects.requireNonNull(ask.apply(question), "问答入口返回了空回复");
        List<String> locators = new ArrayList<>();
        if (reply.answer() != null && reply.answer().sourceLocator() != null
                && !reply.answer().sourceLocator().isBlank()) {
            locators.add(reply.answer().sourceLocator());
        }
        reply.citations().stream()
                .map(LocalPreviewAnswerChain.CitationView::sourceLocator)
                .filter(locator -> locator != null && !locator.isBlank())
                .distinct()
                .filter(locator -> !locators.contains(locator))
                .forEach(locators::add);
        // 拒答/转人工以 handoffRequired 为准：链路把"证据不足"与"会话异常"都置为 true。
        return new ObservedAnswer(reply.route(), List.copyOf(locators), reply.handoffRequired());
    }
}
