package com.zhku.knowledge.controlled;

import com.zhku.knowledge.security.PreviewPrincipal;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 只读确认入口没有准入或执行能力；缺失记录也不证明原POST从未受理。 */
@RestController @Profile("zhku-controlled-preview")
@RequestMapping("/zhku/controlled-preview/v1/submissions")
public final class ControlledCampusSubmissionController {
    private final CampusRunAccessService access;
    public ControlledCampusSubmissionController(CampusRunAccessService access){this.access=access;}
    @GetMapping("/{submissionKey}") public ResponseEntity<?> find(@AuthenticationPrincipal PreviewPrincipal principal,
            @PathVariable String submissionKey) {
        return ResponseEntity.ok().header("Cache-Control","no-store")
                .body(access.findSubmission(principal.actor(),ControlledCampusAgentController.parseSubmissionKey(submissionKey)));
    }
}
