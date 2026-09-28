package com.zhku.knowledge.retrieval;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class IndexPublishedKnowledgeCliTest {
    @Test
    void requiresExplicitLiveScopeAndVersion() {
        assertThatIllegalArgumentException().isThrownBy(() -> IndexPublishedKnowledgeCli.parseArguments(new String[0]));
        assertThatIllegalArgumentException().isThrownBy(() -> IndexPublishedKnowledgeCli.parseArguments(
                new String[]{"--live", "--scope=PUBLIC_ELECTIVE_CREDIT_LIMIT"}));
        var options = IndexPublishedKnowledgeCli.parseArguments(new String[]{"--live",
                "--scope=PUBLIC_ELECTIVE_CREDIT_LIMIT", "--chunking-version=fact-aware-v1"});
        assertThat(options.scope()).isEqualTo("PUBLIC_ELECTIVE_CREDIT_LIMIT");
        assertThat(options.chunkingVersion()).isEqualTo("fact-aware-v1");
    }
}
