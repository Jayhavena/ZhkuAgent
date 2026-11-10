package com.zhku.knowledge.rulecheck;

import com.zhku.knowledge.retrieval.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.util.List;

class RuleCheckDisabledRetrievalTest {
    @Test void disabledVectorDoesNotHideLexicalFailure() {
        var outcome=new HybridRetrievalService(new HybridCandidateFuser()).retrieve("合成问题",5,
            (q,n)->{throw new RetrieverUnavailableException("合成故障");},RuleCheckRuntimeFactory.disabledVector());
        assertThat(outcome.fullyDegraded()).isTrue();
    }
    @Test void healthyEmptyLexicalResultRemainsDistinctFromFailure() {
        var outcome=new HybridRetrievalService(new HybridCandidateFuser()).retrieve("合成问题",5,
            (q,n)->List.of(),RuleCheckRuntimeFactory.disabledVector());
        assertThat(outcome.lexicalAvailable()).isTrue();
        assertThat(outcome.vectorAvailable()).isFalse();
        assertThat(outcome.fullyDegraded()).isFalse();
    }
}
