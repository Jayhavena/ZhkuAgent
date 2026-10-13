package com.zhku.knowledge.rulecheck;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class SealedRuleTaskReplayTest {
    @TempDir Path root;
    @Test void absentOrUnpinnedBatchCannotEnableNewRuntime() {
        assertThatThrownBy(()->new SealedRuleTaskReplay(root,"","")) .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new SealedRuleTaskReplay(root,"a".repeat(64),"b".repeat(64))).isInstanceOf(Exception.class);
    }
}
