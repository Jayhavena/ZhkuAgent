package com.zhku.knowledge.rulecheck;

import java.util.List;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class RuleCheckEvidenceVerifier {
    private final CurrentEvidenceReader reader;

    public RuleCheckEvidenceVerifier(CurrentEvidenceReader reader) {
        this.reader = Objects.requireNonNull(reader);
    }
    /** 只返回当前reader产物；调用者仍须验证身份/hash/正文，不能将本方法包装成已核验事实。 */
    public Optional<RuleCheckEvidenceSnapshot> currentSnapshot(UUID sourceId,UUID chunkId) {
        if(sourceId==null||chunkId==null)return Optional.empty();
        try {var result=reader.read(sourceId,chunkId);return result==null?Optional.empty():result;}
        catch(RuntimeException unavailable){return Optional.empty();}
    }
    /** 纯正文完整性检查，不发查询；仅匹配两个旧hash字段会漏掉未参与决策片段的正文漂移。 */
    public static boolean contentIsIntact(RuleCheckEvidenceSnapshot evidence) {
        return evidence!=null&&evidence.text()!=null&&evidence.checksum()!=null
            &&wellFormedUtf16(evidence.text())&&validChecksum(evidence);
    }

    public VerificationResult verify(RuleCheckEvidenceSpan proposal, List<RuleCheckEvidenceSnapshot> approved) {
        if (proposal == null || proposal.citationId() == null || proposal.citationId().isBlank()
                || proposal.quote() == null || proposal.quote().isBlank()) {
            return VerificationResult.rejected(RejectionReason.INVALID_PROPOSAL);
        }
        if (approved == null) {
            return VerificationResult.rejected(RejectionReason.INVALID_EVIDENCE);
        }
        var ids = new HashSet<String>();
        RuleCheckEvidenceSnapshot captured = null;
        for (var evidence : approved) {
            if (evidence == null || evidence.citationId().isBlank() || evidence.revision().isBlank()) {
                return VerificationResult.rejected(RejectionReason.INVALID_EVIDENCE);
            }
            // 本轮标识有歧义时不取第一条，否则模型可把相同标识绑定到另一个片段。
            if (!ids.add(evidence.citationId())) {
                return VerificationResult.rejected(RejectionReason.DUPLICATE_CITATION);
            }
            if (evidence.citationId().equals(proposal.citationId())) {
                captured = evidence;
            }
        }
        if (captured == null) {
            return VerificationResult.rejected(RejectionReason.UNKNOWN_CITATION);
        }
        if (!captured.eligible()) {
            return VerificationResult.rejected(RejectionReason.INELIGIBLE_EVIDENCE);
        }
        if (!wellFormedUtf16(captured.text())) {
            return VerificationResult.rejected(RejectionReason.INVALID_EVIDENCE);
        }
        if (!validChecksum(captured)) {
            return VerificationResult.rejected(RejectionReason.INVALID_CHECKSUM);
        }
        int start = proposal.startInclusive();
        int end = proposal.endExclusive();
        if (start < 0 || end <= start || end > captured.text().length()
                || splitsSurrogate(captured.text(), start) || splitsSurrogate(captured.text(), end)) {
            return VerificationResult.rejected(RejectionReason.INVALID_SPAN);
        }
        String actualQuote = captured.text().substring(start, end);
        // 只核对引用ID会放行4→40；必须逐字符相等，不能以trim、模糊匹配代替。
        if (!actualQuote.equals(proposal.quote())) {
            return VerificationResult.rejected(RejectionReason.QUOTE_MISMATCH);
        }

        Optional<RuleCheckEvidenceSnapshot> current;
        try {
            current = reader.read(captured.sourceId(), captured.chunkId());
        } catch (RuntimeException unavailable) {
            // 不返回底层异常或提议正文，读取失败只能阻断，不能沿用过期快照。
            return VerificationResult.rejected(RejectionReason.CURRENT_EVIDENCE_UNAVAILABLE);
        }
        if (current == null || current.isEmpty()) {
            return VerificationResult.rejected(RejectionReason.CURRENT_EVIDENCE_UNAVAILABLE);
        }
        var latest = current.get();
        // citationId是本轮别名，持久身份由source/chunk决定；文字相同也不能省略版本复核。
        if (!latest.eligible() || !captured.sourceId().equals(latest.sourceId())
                || !captured.chunkId().equals(latest.chunkId()) || !captured.revision().equals(latest.revision())
                || !captured.text().equals(latest.text()) || !validChecksum(latest)
                || !captured.checksum().equalsIgnoreCase(latest.checksum())) {
            return VerificationResult.rejected(RejectionReason.CURRENT_EVIDENCE_CHANGED);
        }
        // 产物来自核验过的服务端原文；摘录来源一致不等于条件/例外完整或个人适用。
        return new VerificationResult(new RuleCheckVerifiedExcerpt(captured.citationId(), captured.sourceId(),
                captured.chunkId(), captured.revision(), captured.checksum(), start, end, actualQuote), null);
    }

    private static boolean validChecksum(RuleCheckEvidenceSnapshot evidence) {
        if (!evidence.checksum().matches("[0-9a-fA-F]{64}")) {
            return false;
        }
        try {
            var hash = MessageDigest.getInstance("SHA-256").digest(evidence.text().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).equalsIgnoreCase(evidence.checksum());
        } catch (NoSuchAlgorithmException unsupportedRuntime) {
            throw new IllegalStateException("运行时缺少SHA-256", unsupportedRuntime);
        }
    }

    private static boolean splitsSurrogate(String text, int position) {
        return position > 0 && position < text.length() && Character.isHighSurrogate(text.charAt(position - 1))
                && Character.isLowSurrogate(text.charAt(position));
    }

    private static boolean wellFormedUtf16(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i))) {
                    return false;
                }
            } else if (Character.isLowSurrogate(c)) {
                return false;
            }
        }
        return true;
    }

    @FunctionalInterface
    public interface CurrentEvidenceReader {
        Optional<RuleCheckEvidenceSnapshot> read(UUID sourceId, UUID chunkId);
    }

    public enum RejectionReason {
        INVALID_PROPOSAL, INVALID_EVIDENCE, UNKNOWN_CITATION, DUPLICATE_CITATION,
        INELIGIBLE_EVIDENCE, INVALID_CHECKSUM, INVALID_SPAN, QUOTE_MISMATCH,
        CURRENT_EVIDENCE_UNAVAILABLE, CURRENT_EVIDENCE_CHANGED
    }

    public record VerificationResult(RuleCheckVerifiedExcerpt excerpt, RejectionReason rejectionReason) {
        public VerificationResult {
            // 失败不得携带提议文字，避免调用者把“部分验证”误当可下发。
            if ((excerpt == null) == (rejectionReason == null)) {
                throw new IllegalArgumentException("核验结果必须且只能包含成功摘录或拒绝原因");
            }
        }

        public boolean accepted() { return excerpt != null; }

        public static VerificationResult rejected(RejectionReason reason) {
            return new VerificationResult(null, Objects.requireNonNull(reason));
        }
    }
}
