package com.zhku.knowledge.ruleagent;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
class ProbeBatchClaimTest {
 @Test void claimIsAtomicPersistentAndNeverRestoresQuotaAfterFailure(@TempDir Path state)throws Exception {
  String sha="a".repeat(64);
  ProbeBatchClaim.acquire(state,sha);
  assertThat(Files.readString(state.resolve(sha+".used"))).contains(sha);
  assertThatThrownBy(()->ProbeBatchClaim.acquire(state,sha)).isInstanceOf(FileAlreadyExistsException.class);
  ProbeBatchClaim.acquire(state,"b".repeat(64));
 }
 @Test void invalidIdentityCannotEscapeStateDirectory(@TempDir Path state) {
  assertThatThrownBy(()->ProbeBatchClaim.acquire(state,"../escape")).isInstanceOf(IllegalArgumentException.class);
 }
}
