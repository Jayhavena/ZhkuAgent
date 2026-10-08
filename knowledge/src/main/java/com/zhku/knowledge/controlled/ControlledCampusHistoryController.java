package com.zhku.knowledge.controlled;

import com.zhku.knowledge.campusagent.archive.CampusRunHistoryService;
import com.zhku.knowledge.security.PreviewIdentityRepository;
import com.zhku.knowledge.security.PreviewPrincipal;
import com.zhku.knowledge.security.TrustedActor;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("zhku-controlled-preview")
@RequestMapping("/zhku/controlled-preview/v1/history")
public class ControlledCampusHistoryController {
    private final CampusRunHistoryService history;
    private final PreviewIdentityRepository identities;
    public ControlledCampusHistoryController(CampusRunHistoryService history,PreviewIdentityRepository identities) {
        this.history=history;this.identities=identities;
    }
    @GetMapping public ResponseEntity<?> list(@AuthenticationPrincipal PreviewPrincipal principal,
            @RequestParam(defaultValue="20") int limit,@RequestParam(required=false) String cursor) {
        requireUser(principal.actor());
        return ResponseEntity.ok().header("Cache-Control","no-store").body(history.listOwned(principal.actor(),limit,cursor));
    }
    @GetMapping("/{runId}") public ResponseEntity<?> find(@AuthenticationPrincipal PreviewPrincipal principal,
            @PathVariable UUID runId) {
        requireUser(principal.actor());
        return ResponseEntity.ok().header("Cache-Control","no-store").body(history.findOwned(principal.actor(),runId));
    }
    private void requireUser(TrustedActor actor) {
        if(actor.role()!=TrustedActor.Role.USER)
            throw new PreviewIdentityRepository.Failure(PreviewIdentityRepository.Code.ROLE_DENIED);
        identities.requireCurrent(actor);
    }
}
