package com.zhku.knowledge.campusagent.archive;

import com.zhku.knowledge.web.LocalPreviewConversationFacade.ConversationAnswer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.UUID;
import static com.zhku.knowledge.campusagent.archive.CampusRunArchive.*;
import static org.assertj.core.api.Assertions.*;

class CampusRunHistoryVisibilityIntegrationTest {
    @BeforeAll
    static void migrate() {
        ArchiveTestDatabase.migrate();
    }

    private final CampusRunHistoryVisibility visibility = new CampusRunHistoryVisibility();

    @Test
    void missingSourceIdCannotAuthorizeHistoricalText() throws Exception {
        try (var c = ArchiveTestDatabase.dataSource().getConnection()) {
            assertThat(visibility.evaluate(c, candidate(null)).reason()).isEqualTo(Reason.SOURCE_REFERENCE_MISSING);
            assertThat(visibility.evaluate(c, candidate(UUID.randomUUID())).reason())
                    .isEqualTo(Reason.SOURCE_UNAVAILABLE);
        }
    }

    @Test
    void originalSourceIsRecheckedForVersionVisibilitySharingAndScope() throws Exception {
        try (var c = ArchiveTestDatabase.dataSource().getConnection()) {
            UUID id = insertSource(c);
            try {
                assertThat(visibility.evaluate(c, candidate(id)).status()).isEqualTo(VisibilityStatus.VISIBLE);
                for (var change : new String[] { "source_version='synthetic-v2'", "visibility_scope='INTERNAL_ONLY'",
                        "visibility_scope='INTERNAL_ONLY',sharing_rights_confirmed=false",
                        "visibility_scope='INTERNAL_ONLY',publication_status='ARCHIVED'",
                        "publication_scope='另一适用范围'", "knowledge_scope='LEGACY'", "document_family='LEGACY'" }) {
                    reset(c, id);
                    update(c, id, change);
                    var actual = visibility.evaluate(c, candidate(id));
                    assertThat(actual.status()).as(change).isEqualTo(VisibilityStatus.HIDDEN);
                }
            } finally {
                deleteSource(c, id);
            }
        }
    }

    @Test
    void wrongLocatorAndNullCandidateAreNotAuthorized() throws Exception {
        try (var c = ArchiveTestDatabase.dataSource().getConnection()) {
            UUID id = insertSource(c);
            try {
                try (var p = c.prepareStatement("UPDATE knowledge_chunk SET source_locator='其他位置' WHERE source_id=?")) {
                    p.setObject(1, id);
                    p.executeUpdate();
                }
                assertThat(visibility.evaluate(c, candidate(id)).status()).isEqualTo(VisibilityStatus.HIDDEN);
                assertThat(visibility.encode(null).disposition()).isEqualTo(PayloadDisposition.NOT_APPLICABLE);
            } finally {
                deleteSource(c, id);
            }
        }
    }

    @Test
    void allowedPayloadRoundTripsOnlyWhitelistedFields() {
        var input = candidate(UUID.randomUUID());
        var encoded = visibility.encode(input);
        assertThat(encoded.disposition()).isEqualTo(PayloadDisposition.STORED);
        assertThat(new String(encoded.bytes(), StandardCharsets.UTF_8)).doesNotContain("question", "courses", "steps",
                "prompt", "arguments");
        assertThat(visibility.decode(encoded.bytes())).isEqualTo(input);
    }

    @Test
    void chineseMultibyteAndOverallJsonLimitsSuppressWithoutTruncating() {
        UUID id = UUID.randomUUID();
        var input = candidate(id);
        assertThat(visibility.encode(new PayloadCandidate("中".repeat(2731), input.source())).reason())
                .isEqualTo(Reason.PAYLOAD_TOO_LARGE);
        var largeSource = new ConversationAnswer("中".repeat(682), "中".repeat(682), id,
                "中".repeat(170), "v", "中".repeat(170), "中".repeat(170));
        // 每字段未超限，但 JSON 转义会增加总载荷；总量仍必须按 UTF-8 实际字节判断。
        var large = visibility.encode(new PayloadCandidate("\u0001".repeat(8192), largeSource));
        assertThat(large.disposition()).isEqualTo(PayloadDisposition.SUPPRESSED);
        assertThat(large.reason()).isEqualTo(Reason.PAYLOAD_TOO_LARGE);
        assertThat(large.bytes()).isNull();
        assertThat(visibility.encode(new PayloadCandidate(input.answer(), new ConversationAnswer(
                "中".repeat(683), "basis", id, "title", "v", "loc", "scope"))).bytes()).isNull();
    }

    @Test
    void unknownOrMaliciousPayloadCannotDecode() {
        assertThatThrownBy(() -> visibility.decode(
                "{\"prompt\":\"PRIVATE\",\"@class\":\"java.lang.ProcessBuilder\"}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sameIdVersionContentChangeOnlyRetainsReferencePermission() throws Exception {
        try (var c = ArchiveTestDatabase.dataSource().getConnection()) {
            UUID id = insertSource(c);
            try {
                try (var p = c
                        .prepareStatement("UPDATE knowledge_chunk SET content='同版本原地更改的合成正文' WHERE source_id=?")) {
                    p.setObject(1, id);
                    p.executeUpdate();
                }
                assertThat(visibility.evaluate(c, candidate(id)).status()).isEqualTo(VisibilityStatus.VISIBLE);
                // 门禁只授权引用，不承诺证据字节未变化；历史服务统一声明 REFERENCE_ONLY。
            } finally {
                deleteSource(c, id);
            }
        }
    }

    static PayloadCandidate candidate(UUID id) {
        return new PayloadCandidate("当时的合成结果", new ConversationAnswer("每学期公共选修课最高不得超过 4 学分",
                "仅合成验证", id, "合成学分规则", "synthetic-v1", "合成第1条", "合成本科生"));
    }

    static UUID insertSource(Connection c) throws Exception {
        UUID id = UUID.randomUUID();
        try (var p = c.prepareStatement(
                """
                INSERT INTO knowledge_source(id,title,source_type,source_version,publication_status,published_at,
                content_checksum,visibility_scope,publication_scope,sharing_rights_confirmed,document_family,knowledge_scope)
                VALUES(?,'合成学分规则','PDF','synthetic-v1','PUBLISHED',clock_timestamp(),?,'STUDENT_VISIBLE',
                '合成本科生',true,'STUDENT_HANDBOOK','PUBLIC_ELECTIVE_CREDIT_LIMIT')
                """)) {
            p.setObject(1, id);
            p.setString(2, "0".repeat(64));
            p.executeUpdate();
        }
        try (var p = c.prepareStatement("""
                INSERT INTO knowledge_chunk(id,source_id,chunk_index,source_locator,content,content_checksum)
                VALUES(?,?,0,'合成第1条','每学期公共选修课最高不得超过 4 学分',?)
                """)) {
            p.setObject(1, UUID.randomUUID());
            p.setObject(2, id);
            p.setString(3, "0".repeat(64));
            p.executeUpdate();
        }
        return id;
    }

    private static void reset(Connection c, UUID id) throws Exception {
        update(c, id,
                "source_version='synthetic-v1',publication_status='PUBLISHED',visibility_scope='STUDENT_VISIBLE',sharing_rights_confirmed=true,publication_scope='合成本科生',knowledge_scope='PUBLIC_ELECTIVE_CREDIT_LIMIT',document_family='STUDENT_HANDBOOK'");
    }

    private static void update(Connection c, UUID id, String assignments) throws Exception {
        try (var p = c.prepareStatement("UPDATE knowledge_source SET " + assignments + " WHERE id=?")) {
            p.setObject(1, id);
            p.executeUpdate();
        }
    }

    static void deleteSource(Connection c, UUID id) throws Exception {
        for (var table : new String[] { "knowledge_chunk", "knowledge_source" }) {
            try (var p = c.prepareStatement("DELETE FROM " + table + " WHERE "
                    + (table.equals("knowledge_chunk") ? "source_id" : "id") + "=?")) {
                p.setObject(1, id);
                p.executeUpdate();
            }
        }
    }
}
