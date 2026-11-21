package com.zhku.knowledge.eval;

import com.zhku.knowledge.ruleagent.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.zhku.knowledge.eval.RuleFactExecutionContract.*;
import static com.zhku.knowledge.eval.RuleFactExecutionEvaluation.*;
import static com.zhku.knowledge.eval.RuleFactOfflineScorer.*;

class RuleFactBoundEvaluationTest {
    Path root(){Path p=Path.of("").toAbsolutePath();while(p!=null&&!Files.isDirectory(p.resolve(DIRECTORY)))p=p.getParent();return Objects.requireNonNull(p);}
    @Test void frozenTurnContractSeparatesClarificationFromBranches()throws Exception {
        var f=load(root());assertThat(f.dataset().cases()).hasSize(20);
        for(String id:f.dataset().cases().keySet())assertThat(f.turns(id).getFirst().alternatives()).isNotEmpty();
        var turns=f.turns("RFE-013");assertThat(turns).hasSize(3);
        assertThat(turns.getFirst().kind()).isEqualTo("ASK");assertThat(turns.getFirst().factIds()).isEmpty();
        assertThat(turns.get(1).factIds()).containsExactly("public-failed-no-retake");
        assertThat(turns.get(2).factIds()).containsExactly("professional-failed-retake-application");
        assertThat(f.locator("2026",f.item("RFE-001").path("requiredEvidence").get(0).path("contentChecksum").asText())).isNull();
    }
    @Test void boundRetrievalNeedsMatchingSidecarAndAnnotationHash(@org.junit.jupiter.api.io.TempDir Path tmp)throws Exception {
        var f=load(root());var turn=f.turns("RFE-001").getFirst();UUID task=UUID.randomUUID(),run=UUID.randomUUID(),obs=UUID.randomUUID(),handle=UUID.randomUUID();
        String checksum=f.item("RFE-001").path("requiredEvidence").get(0).path("contentChecksum").asText();
        var identity=new Identity("RFE-001",task,run,"v1.0",f.sha256(),"test-model","a".repeat(64));
        var trace=new RuleAgentRunTrace(task,UUID.randomUUID(),run,1,"RESOLVED","SUCCEEDED",false,false,
                List.of(new RuleAgentRunTrace.Action("search", "SEARCH_REVIEWED_RULES",1,1,new RuleAgentRunTrace.Observation("SUCCEEDED","CANDIDATE",null),null,null)),
                new RuleAgentRunTrace.ResultView("COMPLETE",List.of(),List.of(),List.of(),List.of()));
        var execution=new Execution(identity,"answer",trace,null,false,null,false,null,false,Map.of());
        var searches=List.of(new RuleFactBoundEvaluation.Search(obs,run,1,"SUCCEEDED",List.of(new RuleAgentContract.Candidate(handle,"excerpt","2025.9.2"))));
        var side=new RuleAgentEvidenceTools.SearchSources(task,run,1,obs,List.of(new RuleAgentEvidenceTools.CandidateSource(handle,UUID.randomUUID(),UUID.randomUUID(),"2025.9.2",checksum)));
        var capture=new RuleFactBoundEvaluation.Captured(f.sha256(),turn,trace.turnId(),1,execution,JSON.createObjectNode(),searches,List.of(side));
        Path file=tmp.resolve("capture.json");RuleFactBoundEvaluation.write(file,capture);
        byte[] bytes=Files.readAllBytes(file);var roundTrip=JSON.readValue(bytes,RuleFactBoundEvaluation.Captured.class);
        var annotation=new RuleFactBoundEvaluation.Annotation(sha(bytes),sha("answer".getBytes()),false,Set.of(),false,Map.of(),Map.of());
        var result=RuleFactBoundEvaluation.evaluate(f,roundTrip,sha(bytes),annotation);
        assertThat(result.dimensions().get("retrievalRecall").verdict()).isEqualTo(Verdict.PASS);
        assertThat(result.outcome()).isNotEqualTo("PASS");
        var missing=new RuleFactBoundEvaluation.Captured(f.sha256(),turn,trace.turnId(),1,execution,JSON.createObjectNode(),searches,List.of());
        assertThat(RuleFactBoundEvaluation.evaluate(f,missing,sha(bytes),annotation).dimensions().get("retrievalRecall").verdict()).isEqualTo(Verdict.UNJUDGEABLE);
        assertThatThrownBy(()->RuleFactBoundEvaluation.evaluate(f,roundTrip,"b".repeat(64),annotation)).isInstanceOf(IllegalArgumentException.class);
        var absentAnswer=new Execution(identity,null,trace,null,false,null,false,null,false,Map.of());
        var noAnswer=new RuleFactBoundEvaluation.Captured(f.sha256(),turn,trace.turnId(),1,absentAnswer,JSON.createObjectNode(),searches,List.of(side));
        var blankReview=new RuleFactBoundEvaluation.Annotation(sha(bytes),sha(new byte[0]),false,Set.of(),false,Map.of(),Map.of());
        var missingAnswerResult=RuleFactBoundEvaluation.evaluate(f,noAnswer,sha(bytes),blankReview);
        assertThat(missingAnswerResult.outcome()).isNotEqualTo("PASS");
        // This synthetic trace also lacks the expected claims; its known safety failure must not be hidden.
        missingAnswerResult.dimensions().forEach((key,value)->{
            if(!"executionSafety".equals(key))assertThat(value.verdict()).isEqualTo(Verdict.UNJUDGEABLE);
        });
        var absentTerminal=new Execution(identity,"answer",null,null,false,null,false,null,false,Map.of());
        var noTerminal=new RuleFactBoundEvaluation.Captured(f.sha256(),turn,trace.turnId(),1,absentTerminal,JSON.createObjectNode(),searches,List.of(side));
        var missingTerminalResult=RuleFactBoundEvaluation.evaluate(f,noTerminal,sha(bytes),annotation);
        assertThat(missingTerminalResult.outcome()).isEqualTo("UNJUDGEABLE");
        assertThat(missingTerminalResult.dimensions().get("executionSafety").reasons()).contains("TERMINAL_MISSING");
    }
}
