package com.zhku.knowledge.governance.web;

import com.zhku.knowledge.governance.CandidateEditCommand;
import com.zhku.knowledge.governance.CandidateReviewService;
import com.zhku.knowledge.governance.CandidateReviewStatus;
import com.zhku.knowledge.governance.CandidateSplitCommand;
import com.zhku.knowledge.governance.KnowledgeCandidate;
import com.zhku.knowledge.governance.KnowledgeGovernancePublisher;
import com.zhku.knowledge.governance.KnowledgeGovernanceRepository;
import com.zhku.knowledge.governance.KnowledgeSubmission;
import com.zhku.knowledge.governance.KnowledgeSubmissionService;
import com.zhku.knowledge.governance.PublishedSubmissionResult;
import com.zhku.knowledge.governance.SubmissionCreateCommand;
import com.zhku.knowledge.notice.AcademicTerm;
import com.zhku.knowledge.notice.NoticeFact;
import com.zhku.knowledge.notice.NoticeFactType;
import com.zhku.knowledge.notice.NoticeEvidenceChannel;
import com.zhku.knowledge.notice.NoticeEvidenceOrigin;
import com.zhku.knowledge.notice.NoticeGovernancePublisher;
import com.zhku.knowledge.notice.NoticePublicationCommand;
import com.zhku.knowledge.notice.PublishedNoticeResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * 仅本机管理员使用的资料治理 HTTP 边界。
 *
 * <p>该接口只操作审核暂存区；学生问答仍只能读取已发布知识表。</p>
 */
@RestController
@Profile("zhku-local-admin")
@ConditionalOnProperty(
        prefix = "zhku.local-admin",
        name = "enabled",
        havingValue = "true"
)
@RequestMapping("/zhku/local-admin")
public class ZhkuLocalAdminController {

    static final MediaType JSON_UTF8 = new MediaType(
            MediaType.APPLICATION_JSON,
            StandardCharsets.UTF_8
    );

    private final KnowledgeSubmissionService submissionService;
    private final CandidateReviewService reviewService;
    private final KnowledgeGovernancePublisher publisher;
    private final KnowledgeGovernanceRepository repository;
    private final NoticeGovernancePublisher noticePublisher;

    public ZhkuLocalAdminController(
            KnowledgeSubmissionService submissionService,
            CandidateReviewService reviewService,
            KnowledgeGovernancePublisher publisher,
            KnowledgeGovernanceRepository repository
    ) {
        this(submissionService, reviewService, publisher, repository, null);
    }

    @Autowired
    public ZhkuLocalAdminController(
            KnowledgeSubmissionService submissionService,
            CandidateReviewService reviewService,
            KnowledgeGovernancePublisher publisher,
            KnowledgeGovernanceRepository repository,
            NoticeGovernancePublisher noticePublisher
    ) {
        this.submissionService = Objects.requireNonNull(
                submissionService,
                "投稿服务不能为空"
        );
        this.reviewService = Objects.requireNonNull(reviewService, "审核服务不能为空");
        this.publisher = Objects.requireNonNull(publisher, "发布服务不能为空");
        this.repository = Objects.requireNonNull(repository, "资料治理仓储不能为空");
        this.noticePublisher = noticePublisher;
    }

    @PostMapping(value = "/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createSubmission(
            @RequestParam("file") MultipartFile file,
            @RequestParam String title,
            @RequestParam String sourceVersion,
            @RequestParam String sourceDescription,
            @RequestParam(required = false) String sourceUrl,
            @RequestParam String applicableScope,
            @RequestParam(required = false) LocalDate publicationDate,
            @RequestParam(defaultValue = "false") boolean publicationDateConfirmed,
            @RequestParam(required = false) LocalDate effectiveDate,
            @RequestParam(defaultValue = "false") boolean effectiveDateConfirmed,
            @RequestParam String reviewNotice,
            @RequestParam String knowledgeScope,
            @RequestParam(defaultValue = "STUDENT_HANDBOOK") String documentFamily
    ) throws IOException {
        if (!isPdf(file)) {
            return ResponseEntity.badRequest()
                    .contentType(JSON_UTF8)
                    .body(new ErrorResponse(
                            "INVALID_PDF",
                            "请上传可读取的 PDF 文件。"
                    ));
        }
        KnowledgeSubmission submission = submissionService.createSubmission(
                new SubmissionCreateCommand(
                        title,
                        sourceVersion,
                        sourceDescription,
                        sourceUrl,
                        applicableScope,
                        publicationDate,
                        publicationDateConfirmed,
                        effectiveDate,
                        effectiveDateConfirmed,
                        reviewNotice,
                        knowledgeScope,
                        file.getOriginalFilename(),
                        documentFamily
                ),
                file.getInputStream()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .contentType(JSON_UTF8)
                .body(SubmissionResponse.from(submission));
    }

    @GetMapping("/submissions")
    public ResponseEntity<List<SubmissionResponse>> listSubmissions() {
        List<SubmissionResponse> submissions = repository.listSubmissions()
                .stream()
                .map(SubmissionResponse::from)
                .toList();
        return ResponseEntity.ok().contentType(JSON_UTF8).body(submissions);
    }

    @GetMapping("/submissions/{submissionId}")
    public ResponseEntity<SubmissionDetailResponse> submissionDetail(
            @PathVariable UUID submissionId
    ) {
        KnowledgeSubmission submission = repository.findSubmission(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("资料投稿不存在"));
        List<CandidateResponse> candidates = repository.listCandidates(submissionId)
                .stream()
                .map(CandidateResponse::from)
                .toList();
        return ResponseEntity.ok()
                .contentType(JSON_UTF8)
                .body(SubmissionDetailResponse.from(submission, candidates));
    }

    @PostMapping("/submissions/{submissionId}/extract")
    public ResponseEntity<ExtractionResponse> extractCandidates(
            @PathVariable UUID submissionId
    ) throws IOException {
        int candidateCount = submissionService.extractCandidates(submissionId).size();
        return ResponseEntity.ok()
                .contentType(JSON_UTF8)
                .body(new ExtractionResponse("EXTRACTED", candidateCount));
    }

    @PatchMapping("/candidates/{candidateId}")
    public ResponseEntity<CandidateResponse> updateCandidate(
            @PathVariable UUID candidateId,
            @RequestBody CandidateEditRequest request
    ) {
        KnowledgeCandidate candidate = reviewService.update(
                candidateId,
                new CandidateEditCommand(
                        request.startPageNumber(),
                        request.endPageNumber(),
                        request.editedText(),
                        request.sectionTitle(),
                        request.sourceLocator()
                )
        );
        return ResponseEntity.ok()
                .contentType(JSON_UTF8)
                .body(CandidateResponse.from(candidate));
    }

    @PostMapping("/candidates/{candidateId}/review")
    public ResponseEntity<CandidateResponse> reviewCandidate(
            @PathVariable UUID candidateId,
            @RequestBody CandidateReviewRequest request
    ) {
        String decision = requireText(request.decision(), "审核结论不能为空")
                .toUpperCase(Locale.ROOT);
        KnowledgeCandidate candidate = switch (decision) {
            case "APPROVE" -> reviewService.approve(candidateId, request.note());
            case "DISCARD" -> reviewService.discard(candidateId, request.note());
            default -> throw new IllegalArgumentException(
                    "审核结论只能是 APPROVE 或 DISCARD"
            );
        };
        return ResponseEntity.ok()
                .contentType(JSON_UTF8)
                .body(CandidateResponse.from(candidate));
    }

    @PostMapping("/candidates/{candidateId}/split")
    public ResponseEntity<SplitResponse> splitCandidate(
            @PathVariable UUID candidateId,
            @RequestBody CandidateSplitRequest request
    ) {
        List<CandidateResponse> candidates = reviewService.split(
                        candidateId,
                        new CandidateSplitCommand(
                                request.firstEditedText(),
                                request.secondEditedText()
                        )
                )
                .stream()
                .map(CandidateResponse::from)
                .toList();
        return ResponseEntity.ok()
                .contentType(JSON_UTF8)
                .body(new SplitResponse(candidates));
    }

    @PostMapping("/submissions/{submissionId}/publish")
    public ResponseEntity<PublishResponse> publish(
            @PathVariable UUID submissionId,
            @RequestParam(defaultValue = "false") boolean studentVisible,
            @RequestParam(defaultValue = "false") boolean sharingRightsConfirmed
    ) {
        PublishedSubmissionResult result = publisher.publish(
                submissionId,
                studentVisible,
                sharingRightsConfirmed
        );
        return ResponseEntity.ok()
                .contentType(JSON_UTF8)
                .body(new PublishResponse(
                        result.submissionId(),
                        result.sourceId(),
                        "PUBLISHED"
                ));
    }

    /**
     * 仅将已审核的官方 PDF 通知发布为当前选课事实。
     *
     * <p>本机管理员必须显式提供学期、有效期、官方链接和每项办理窗口；不能把
     * 文档标题或模型推测当作动态日期事实。</p>
     */
    @PostMapping("/submissions/{submissionId}/publish-course-selection-notice")
    public ResponseEntity<NoticePublishResponse> publishCourseSelectionNotice(
            @PathVariable UUID submissionId,
            @RequestBody CourseSelectionNoticePublishRequest request
    ) {
        if (noticePublisher == null) {
            throw new IllegalStateException("本机通知发布服务尚未启用");
        }
        PublishedNoticeResult result = noticePublisher.publish(
                submissionId,
                request.toCommand()
        );
        return ResponseEntity.ok()
                .contentType(JSON_UTF8)
                .body(new NoticePublishResponse(
                        result.submissionId(),
                        result.sourceId(),
                        result.notice().noticeId(),
                        result.notice().academicTerm().value(),
                        result.notice().type().name(),
                        result.notice().status().name()
                ));
    }

    private static boolean isPdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        String filename = file.getOriginalFilename();
        return filename != null
                && filename.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.strip();
    }

    public record CandidateEditRequest(
            int startPageNumber,
            int endPageNumber,
            String editedText,
            String sectionTitle,
            String sourceLocator
    ) {
    }

    public record CandidateReviewRequest(String decision, String note) {
    }

    public record CandidateSplitRequest(
            String firstEditedText,
            String secondEditedText
    ) {
    }

    public record SubmissionResponse(
            UUID id,
            String documentFamily,
            String title,
            String sourceVersion,
            String knowledgeScope,
            String status,
            java.time.Instant updatedAt
    ) {
        static SubmissionResponse from(KnowledgeSubmission submission) {
            return new SubmissionResponse(
                submission.id(),
                submission.documentFamily(),
                submission.title(),
                    submission.sourceVersion(),
                    submission.knowledgeScope(),
                    submission.status().name(),
                    submission.updatedAt()
            );
        }
    }

    public record SubmissionDetailResponse(
            UUID id,
            String documentFamily,
            String title,
            String sourceVersion,
            String knowledgeScope,
            String status,
            java.time.Instant updatedAt,
            List<CandidateResponse> candidates
    ) {
        static SubmissionDetailResponse from(
                KnowledgeSubmission submission,
                List<CandidateResponse> candidates
        ) {
            return new SubmissionDetailResponse(
                submission.id(),
                submission.documentFamily(),
                submission.title(),
                    submission.sourceVersion(),
                    submission.knowledgeScope(),
                    submission.status().name(),
                    submission.updatedAt(),
                    candidates
            );
        }
    }

    public record CandidateResponse(
            UUID id,
            int startPageNumber,
            int endPageNumber,
            String editedText,
            String sectionTitle,
            String sourceLocator,
            String reviewStatus,
            String reviewNote,
            String discardReason
    ) {
        static CandidateResponse from(KnowledgeCandidate candidate) {
            return new CandidateResponse(
                    candidate.id(),
                    candidate.startPageNumber(),
                    candidate.endPageNumber(),
                    candidate.editedText(),
                    candidate.sectionTitle(),
                    candidate.sourceLocator(),
                    candidate.reviewStatus().name(),
                    candidate.reviewNote(),
                    candidate.discardReason()
            );
        }
    }

    public record ExtractionResponse(String status, int candidateCount) {
    }

    public record SplitResponse(List<CandidateResponse> candidates) {
    }

    public record PublishResponse(UUID submissionId, UUID sourceId, String status) {
    }

    public record CourseSelectionNoticePublishRequest(
            String academicTerm,
            Instant validFrom,
            Instant validTo,
            String officialUrl,
            List<NoticeFactRequest> facts,
            String evidenceChannel,
            String issuingUnit,
            String deliveryChannel,
            String materialReference
    ) {
        NoticePublicationCommand toCommand() {
            List<NoticeFact> noticeFacts = List.copyOf(
                    Objects.requireNonNull(facts, "通知事实不能为空")
            ).stream().map(NoticeFactRequest::toFact).toList();
            return new NoticePublicationCommand(
                    new AcademicTerm(academicTerm),
                    validFrom,
                    validTo,
                    officialUrl,
                    noticeFacts,
                    evidenceOrigin()
            );
        }

        private NoticeEvidenceOrigin evidenceOrigin() {
            NoticeEvidenceChannel channel = evidenceChannel == null || evidenceChannel.isBlank()
                    ? NoticeEvidenceChannel.PUBLIC_WEB
                    : NoticeEvidenceChannel.valueOf(evidenceChannel);
            return channel == NoticeEvidenceChannel.PUBLIC_WEB
                    ? NoticeEvidenceOrigin.publicWeb(officialUrl)
                    : NoticeEvidenceOrigin.internalOfficial(
                            issuingUnit, deliveryChannel, materialReference
                    );
        }
    }

    public record NoticeFactRequest(
            NoticeFactType type,
            Instant startsAt,
            Instant endsAt,
            String officialEntryUrl,
            boolean startsAtConfirmed,
            boolean endsAtConfirmed
    ) {
        NoticeFact toFact() {
            return new NoticeFact(
                    type, startsAt, endsAt, officialEntryUrl,
                    startsAtConfirmed, endsAtConfirmed
            );
        }
    }

    public record NoticePublishResponse(
            UUID submissionId,
            UUID sourceId,
            UUID noticeId,
            String academicTerm,
            String noticeType,
            String status
    ) {
    }

    public record ErrorResponse(String code, String message) {
    }
}
