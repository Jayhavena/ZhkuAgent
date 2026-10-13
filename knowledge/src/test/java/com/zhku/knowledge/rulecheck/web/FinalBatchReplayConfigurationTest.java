package com.zhku.knowledge.rulecheck.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.knowledge.rulecheck.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

/** 合成批次只验证离线身份装配，不能作为真实外发许可或产品评分。 */
class FinalBatchReplayConfigurationTest {
    @TempDir Path root;
    final ObjectMapper json=new ObjectMapper();
    String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    byte[] write(Path p,Object data)throws Exception{Files.createDirectories(p.getParent());byte[] b=json.writeValueAsBytes(data);Files.write(p,b);return b;}
    @Test void explicitBatchReplaysOnlyExactQuestionAndEvidence()throws Exception {
        String directory="docs/evidence/synthetic-final";Path base=root.resolve(directory);
        var option=new RuleCheckTaskPlan.Option("A13/GENERAL","合成范围",List.of("合成事实"),List.of(RuleCheckTaskPlan.Action.EXPLAIN_RULE),List.of());
        String payload=json.writeValueAsString(Map.of("question","合成问题", "evidenceOptions",List.of(option)));
        byte[] packet=write(base.resolve("request-package.json"),Map.of("endpoint","https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions","retryCount",0,"requests",List.of(Map.of("caseId","N25","body",Map.of("messages",List.of(Map.of("content",BoundedRuleTaskPlanner.SYSTEM),Map.of("content",payload)))))));
        byte[] response=write(base.resolve("provider-capture/N25.json"),Map.of("caseId","N25","status","RESPONSE_RECEIVED","rawModelContent","{\"tasks\":[{\"ruleId\":\"A13/GENERAL\",\"action\":\"EXPLAIN_RULE\",\"operand\":\"\",\"value\":\"\"}]}"));
        byte[] summary=write(base.resolve("provider-capture/summary.json"),Map.of("completedAllRequests",true,"retryCount",0,"sourceUnchanged",true,"records",List.of(Map.of("caseId","N25","path","N25.json","sha256",sha(response)))));
        write(base.resolve("external-transfer-authorization.json"),Map.of("authority","DIRECT_USER_MESSAGE","decision","CONFIRMED","packageSha256",sha(packet),"maxCalls",1,"endpoint","https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions","model","qwen-plus","fixtureOnly",true));
        var e=new MockEnvironment().withProperty("zhku.rule-check-preview.selector-mode","TASK_PLAN_REPLAY")
            .withProperty("zhku.rule-check-preview.catalogue-mode","REVIEWED_2025_CONTROLLED")
            .withProperty("zhku.rule-check-preview.repository-root",root.toString()).withProperty("zhku.rule-check-preview.task-batch-directory",directory)
            .withProperty("zhku.rule-check-preview.task-package-sha256",sha(packet)).withProperty("zhku.rule-check-preview.task-summary-sha256",sha(summary));
        try(var replay=new RuleCheckPreviewConfiguration().ruleCheckTaskReplay(e)) {
            assertThat(replay.plan("合成问题",List.of(option)).tasks()).hasSize(1);
            assertThat(replay.plan("另一个问题",List.of(option))).isNull();
            var changed=new RuleCheckTaskPlan.Option("A13/GENERAL","合成范围",List.of("变更事实"),option.actions(),option.operands());
            assertThat(replay.plan("合成问题",List.of(changed))).isNull();
        }
        // 即使请求和授权指纹一起更新，旧提示也不能混进当前运行协议。
        var altered=json.readTree(packet);
        ((com.fasterxml.jackson.databind.node.ObjectNode)altered.path("requests").get(0).path("body").path("messages").get(0)).put("content","旧提示");
        byte[] changedPacket=write(base.resolve("request-package.json"),altered);
        var auth=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(Files.readAllBytes(base.resolve("external-transfer-authorization.json")));
        auth.put("packageSha256",sha(changedPacket));write(base.resolve("external-transfer-authorization.json"),auth);
        e.withProperty("zhku.rule-check-preview.task-package-sha256",sha(changedPacket));
        assertThatThrownBy(()->new RuleCheckPreviewConfiguration().ruleCheckTaskReplay(e)).isInstanceOf(IllegalArgumentException.class);
        e.withProperty("zhku.rule-check-preview.task-batch-directory","../outside");
        assertThatThrownBy(()->new RuleCheckPreviewConfiguration().ruleCheckTaskReplay(e)).isInstanceOf(IllegalArgumentException.class);
    }
}
