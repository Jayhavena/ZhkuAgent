package com.zhku.knowledge.campusagent.archive;

import java.sql.Connection;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.zhku.knowledge.web.LocalPreviewConversationFacade.ConversationAnswer;
import static com.zhku.knowledge.campusagent.archive.CampusRunArchive.*;

/** 按当时的来源引用重新授权，不拿最新资料替代旧来源，不证明同版本正文不可变。 */
public final class CampusRunHistoryVisibility {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public Visibility evaluate(Connection connection, PayloadCandidate candidate) {
        if (!complete(candidate))
            return hidden(Reason.SOURCE_REFERENCE_MISSING);
        ConversationAnswer source = candidate.source();
        try (var p = connection.prepareStatement("""
                SELECT source.source_version,source.publication_status,source.visibility_scope,
                source.sharing_rights_confirmed,source.document_family,source.knowledge_scope,source.publication_scope,
                EXISTS(SELECT 1 FROM knowledge_chunk chunk WHERE chunk.source_id=source.id AND chunk.source_locator=?)
                FROM knowledge_source source WHERE source.id=?
                """)) {
            p.setQueryTimeout(1);
            p.setString(1, source.sourceLocator());
            p.setObject(2, source.sourceId());
            try (var r = p.executeQuery()) {
                if (!r.next())
                    return hidden(Reason.SOURCE_UNAVAILABLE);
                if (!source.sourceVersion().equals(r.getString(1)))
                    return hidden(Reason.SOURCE_VERSION_CHANGED);
                if (!"PUBLISHED".equals(r.getString(2)) || !"STUDENT_VISIBLE".equals(r.getString(3))
                        || !r.getBoolean(4) || !"STUDENT_HANDBOOK".equals(r.getString(5))
                        || !"PUBLIC_ELECTIVE_CREDIT_LIMIT".equals(r.getString(6))
                        || !source.applicableScope().equals(r.getString(7)) || !r.getBoolean(8))
                    return hidden(Reason.SOURCE_NOT_VISIBLE);
                return new Visibility(VisibilityStatus.VISIBLE, Reason.NONE);
            }
        } catch (SQLException e) {
            // 查询失败不等于资料不存在，更不能当成授权成功返回缓存正文。
            throw new Failure(Code.ARCHIVE_READ_UNAVAILABLE);
        }
    }

    public Encoded encode(PayloadCandidate candidate) {
        if (candidate == null)
            return new Encoded(PayloadDisposition.NOT_APPLICABLE, Reason.NO_RESULT_FOR_OUTCOME, null);
        if (!complete(candidate))
            return suppressed(Reason.SOURCE_REFERENCE_MISSING);
        // 限额按 UTF-8 字节计量，不能把中文字数当作实际归档大小。
        var source = candidate.source();
        if (!fits(candidate.answer(), 8192) || !fits(source.conclusion(), 2048) || !fits(source.basis(), 2048)
                || !fits(source.sourceTitle(), 512) || !fits(source.sourceVersion(), 128)
                || !fits(source.sourceLocator(), 512) || !fits(source.applicableScope(), 512))
            return suppressed(Reason.PAYLOAD_TOO_LARGE);
        try {
            byte[] bytes = JSON.writeValueAsBytes(candidate);
            if (bytes.length > 16384)
                return suppressed(Reason.PAYLOAD_TOO_LARGE);
            return new Encoded(PayloadDisposition.STORED, Reason.NONE, bytes);
        } catch (Exception e) {
            return suppressed(Reason.UNSUPPORTED_SCHEMA);
        }
    }

    public PayloadCandidate decode(byte[] bytes) {
        if (bytes == null || bytes.length > 16384)
            throw new IllegalArgumentException("历史载荷不受支持");
        try {
            var candidate = JSON.readValue(bytes, PayloadCandidate.class);
            if (!complete(candidate) || encode(candidate).disposition() != PayloadDisposition.STORED)
                throw new IllegalArgumentException("历史载荷不受支持");
            return candidate;
        } catch (Exception e) {
            throw new IllegalArgumentException("历史载荷不受支持");
        }
    }

    private static boolean complete(PayloadCandidate candidate) {
        if (candidate == null || candidate.source() == null || !present(candidate.answer()))
            return false;
        var source = candidate.source();
        return source.sourceId() != null && present(source.conclusion()) && present(source.basis())
                && present(source.sourceTitle()) && present(source.sourceVersion())
                && present(source.sourceLocator()) && present(source.applicableScope());
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean fits(String value, int maxBytes) {
        return value.getBytes(StandardCharsets.UTF_8).length <= maxBytes;
    }

    private static Visibility hidden(Reason reason) {
        return new Visibility(VisibilityStatus.HIDDEN, reason);
    }

    private static Encoded suppressed(Reason reason) {
        return new Encoded(PayloadDisposition.SUPPRESSED, reason, null);
    }
}
