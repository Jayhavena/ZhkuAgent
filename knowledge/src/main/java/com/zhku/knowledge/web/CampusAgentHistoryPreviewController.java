package com.zhku.knowledge.web;

import com.zhku.knowledge.campusagent.archive.CampusRunHistoryService;
import org.springframework.web.bind.annotation.*;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.util.UUID;
import com.zhku.knowledge.campusagent.archive.CampusRunArchive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@Profile("zhku-local-preview")
@ConditionalOnProperty(name = { "zhku.campus-agent.enabled",
        "zhku.campus-agent.archive.enabled" }, havingValue = "true")
@RequestMapping("/zhku/local-preview/campus-agent/v1/history")
public class CampusAgentHistoryPreviewController {
    private final CampusRunHistoryService service;

    public CampusAgentHistoryPreviewController(CampusRunHistoryService service) {
        this.service = service;
    }

    @GetMapping("/{runId}")
    public CampusRunHistoryService.Detail find(@PathVariable UUID runId) {
        return service.find(runId);
    }

    @GetMapping
    public CampusRunHistoryService.Listing list(@RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor) {
        return service.list(limit, cursor);
    }

    @ExceptionHandler(CampusRunArchive.Failure.class)
    public ResponseEntity<CampusAgentPreviewController.ErrorReply> failure(CampusRunArchive.Failure error) {
        int status = error.code() == CampusRunArchive.Code.ARCHIVE_NOT_FOUND ? 404
                : error.code() == CampusRunArchive.Code.INVALID_REQUEST ? 400 : 503;
        return ResponseEntity.status(status)
                .body(new CampusAgentPreviewController.ErrorReply(error.code().name(), error.safeMessage()));
    }

    @ExceptionHandler({ IllegalArgumentException.class, MethodArgumentTypeMismatchException.class })
    public ResponseEntity<CampusAgentPreviewController.ErrorReply> invalid() {
        var error = new CampusRunArchive.Failure(CampusRunArchive.Code.INVALID_REQUEST);
        return ResponseEntity.badRequest()
                .body(new CampusAgentPreviewController.ErrorReply(error.code().name(), error.safeMessage()));
    }
}
