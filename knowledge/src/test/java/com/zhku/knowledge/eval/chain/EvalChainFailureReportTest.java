package com.zhku.knowledge.eval.chain;
import java.nio.file.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
class EvalChainFailureReportTest {
 @Test void missingEvidenceProducesNotEvaluableReportAndNonzeroExit(@TempDir Path root)throws Exception {
  assertThat(AgentEvalChainMain.execute(new String[]{"collect","--evidence",root.resolve("missing").toString(),"--out",root.resolve("report").toString()})).isEqualTo(1);
  verify(root.resolve("report"));
 }
 @Test void malformedSuiteProducesReportWithoutLeakingExceptionBody(@TempDir Path root)throws Exception {
  Path suite=root.resolve("suite.json");Files.writeString(suite,"{ PRIVATE_BODY_SHOULD_NOT_APPEAR");
  assertThat(AgentEvalChainMain.execute(new String[]{"run","--suite",suite.toString(),"--out",root.resolve("report").toString()})).isEqualTo(1);
  verify(root.resolve("report"));assertThat(Files.readString(root.resolve("report/report.json"))).doesNotContain("PRIVATE_BODY_SHOULD_NOT_APPEAR");
 }
 private static void verify(Path out)throws Exception {
  var report=new ObjectMapper().readTree(Files.readString(out.resolve("report.json")));
  assertThat(report.path("regressionPassed").asBoolean()).isFalse();assertThat(report.path("notEvaluable").asInt()).isEqualTo(1);
  assertThat(Files.exists(out.resolve("report.md"))).isTrue();
 }
}
