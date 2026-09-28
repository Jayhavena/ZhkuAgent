package com.zhku.knowledge.web;

import com.zhku.agent.agent.runtime.AgentRunState;
import com.zhku.knowledge.campusagent.CampusCreditAgentManager;
import com.zhku.knowledge.campusagent.CampusCreditAgentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

/** v1 独立 JSON 合同，不改变既有 `/conversations` JSON/SSE 事件。 */
@RestController
@Profile("zhku-local-preview")
@ConditionalOnProperty(prefix = "zhku.campus-agent", name = "enabled", havingValue = "true")
@RequestMapping("/zhku/local-preview/campus-agent/v1/runs")
public class CampusAgentPreviewController {
    private final CampusCreditAgentManager manager;

    public CampusAgentPreviewController(CampusCreditAgentManager manager) {
        this.manager = manager;
    }

    @PostMapping
    public ResponseEntity<?> start(@RequestBody(required = false) CampusCreditAgentService.Request request) {
        if (request == null) return ResponseEntity.badRequest().body(new ErrorReply("INVALID_REQUEST", "问题不能为空"));
        try {
            var snapshot = manager.start(request);
            return ResponseEntity.status(snapshot.state() == AgentRunState.FAILED
                    ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.ACCEPTED).body(snapshot);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new ErrorReply("INVALID_REQUEST", "课程输入无效"));
        } catch (RejectedExecutionException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ErrorReply("EXECUTOR_REJECTED", "校园 Agent 当前繁忙，请稍后重试"));
        }
    }

    @GetMapping("/{runId}")
    public ResponseEntity<?> find(@PathVariable UUID runId) {
        try { return ResponseEntity.ok(manager.find(runId)); }
        catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorReply("RUN_NOT_FOUND", "运行不存在"));
        }
    }

    @PostMapping("/{runId}/cancel")
    public ResponseEntity<?> cancel(@PathVariable UUID runId) {
        try { return ResponseEntity.ok(manager.cancel(runId)); }
        catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorReply("RUN_NOT_FOUND", "运行不存在"));
        }
    }

    public record ErrorReply(String code, String message) { }
}
