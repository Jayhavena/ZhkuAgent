import com.zhku.knowledge.rag.AnswerEvidenceGuard;
import com.zhku.knowledge.rag.ApprovedEvidence;
import com.zhku.knowledge.rag.GroundedAnswerComposer;
import com.zhku.knowledge.rag.ModelResponse;
import java.util.List;

/** 已知缺口探针：输出结果用于保留风险，不是要求错误答案通过的回归测试。 */
public class SemanticGapProbe {
    public static void main(String[] args) {
        var evidence = List.of(new ApprovedEvidence("synthetic-1", "合成条款：上限4学分。", "合成资料"));
        var response = new ModelResponse("上限40学分。", 0.9, List.of("synthetic-1"), false);
        var guard = new AnswerEvidenceGuard();
        boolean rejected = guard.verify(response, evidence).isPresent();
        var composed = new GroundedAnswerComposer(request -> response, guard).compose("上限是多少？", evidence);
        System.out.printf("{\"fixtureKind\":\"SYNTHETIC_KNOWN_RISK\",\"referenceCredits\":4,"
                + "\"answerCredits\":40,\"citationIdValid\":true,\"expectedRejected\":true,"
                + "\"actualRejected\":%s,\"composerStatus\":\"%s\",\"qualityGatePassed\":%s,"
                + "\"issue\":\"RC-04-SEMANTICS\",\"status\":\"OPEN\"}%n",
                rejected, composed.status(), rejected);
    }
}
