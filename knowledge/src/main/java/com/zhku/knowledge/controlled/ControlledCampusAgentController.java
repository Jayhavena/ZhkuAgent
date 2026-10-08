package com.zhku.knowledge.controlled;

import com.zhku.knowledge.campusagent.CampusCreditAgentService;
import com.zhku.knowledge.security.PreviewPrincipal;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("zhku-controlled-preview")
@RequestMapping("/zhku/controlled-preview/v1/runs")
public class ControlledCampusAgentController {
    private final CampusRunAccessService access;
    public ControlledCampusAgentController(CampusRunAccessService access) { this.access=access; }
    @PostMapping public ResponseEntity<?> start(@AuthenticationPrincipal PreviewPrincipal principal,
            @RequestHeader("Idempotency-Key") String submissionKey,@RequestBody CampusCreditAgentService.Request request) {
        var reply=access.submit(principal.actor(),parseSubmissionKey(submissionKey),request);
        return ResponseEntity.status(reply.replayed()?200:202).header("Cache-Control","no-store").body(reply);
    }
    static UUID parseSubmissionKey(String key) {
        if(key==null||!key.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))
            throw new IllegalArgumentException("提交键必须为规范UUID v4");
        return UUID.fromString(key);
    }
    @GetMapping("/{runId}") public ResponseEntity<?> find(@AuthenticationPrincipal PreviewPrincipal principal,
            @PathVariable UUID runId) {
        return ResponseEntity.ok().header("Cache-Control","no-store").body(access.find(principal.actor(),runId));
    }
    @PostMapping("/{runId}/cancel") public ResponseEntity<?> cancel(@AuthenticationPrincipal PreviewPrincipal principal,
            @PathVariable UUID runId,@RequestBody(required=false) java.util.Map<String,Object> body) {
        if(body!=null&&!body.isEmpty()) throw new IllegalArgumentException("取消不接受身份或业务字段");
        return ResponseEntity.ok().header("Cache-Control","no-store").body(access.cancel(principal.actor(),runId));
    }
}
