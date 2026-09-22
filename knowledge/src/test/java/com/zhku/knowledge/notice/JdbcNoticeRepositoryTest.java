package com.zhku.knowledge.notice;

import com.zhku.knowledge.ZhkuDatabaseTestConfig;
import com.zhku.knowledge.ZhkuFlywayTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcNoticeRepositoryTest {

    private static final String DATABASE_URL = System.getProperty(
            "zhku.db.url", "jdbc:postgresql://127.0.0.1:54329/zhkuagent"
    );
    private static final String DATABASE_USER = System.getProperty(
            "zhku.db.user", "zhkuagent"
    );
    private static final String DATABASE_PASSWORD =
            ZhkuDatabaseTestConfig.databasePassword();
    private static final UUID OLD_SOURCE_ID = UUID.fromString(
            "ec6364e1-7bd5-4f25-9e88-22d7f302c04e"
    );
    private static final UUID NEW_SOURCE_ID = UUID.fromString(
            "08f4e433-b355-4613-9352-d7ca1969c907"
    );
    private static final UUID OLD_NOTICE_ID = UUID.fromString(
            "d0a55052-5f5d-4d0d-ac5f-73e8e0656853"
    );
    private static final UUID NEW_NOTICE_ID = UUID.fromString(
            "aa6c5c08-1ca4-4e3a-8be4-63a652ee4a78"
    );

    private JdbcNoticeRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        ZhkuFlywayTestSupport.migrate(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD);
        deleteFixtures();
        insertSource(OLD_SOURCE_ID, "旧选课通知");
        insertSource(NEW_SOURCE_ID, "新选课通知");
        repository = new JdbcNoticeRepository(new DriverManagerDataSource(
                DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD
        ));
    }

    @AfterEach
    void tearDown() throws SQLException {
        deleteFixtures();
    }

    @Test
    void shouldAtomicallySupersedeOldNoticeWhenPublishingReplacement() {
        repository.publishReplacingScope(notice(
                OLD_NOTICE_ID,
                OLD_SOURCE_ID,
                "2026-09-01T00:00:00Z",
                "2026-09-15T00:00:00Z"
        ));

        repository.publishReplacingScope(notice(
                NEW_NOTICE_ID,
                NEW_SOURCE_ID,
                "2026-09-05T00:00:00Z",
                "2026-09-20T00:00:00Z"
        ));

        List<PublishedNotice> notices = repository.listNotices();
        assertThat(notices).extracting(PublishedNotice::noticeId)
                .containsExactlyInAnyOrder(OLD_NOTICE_ID, NEW_NOTICE_ID);
        assertThat(notices).filteredOn(notice ->
                notice.noticeId().equals(OLD_NOTICE_ID)
        ).singleElement().extracting(PublishedNotice::status)
                .isEqualTo(NoticeLifecycleStatus.SUPERSEDED);
        assertThat(notices).filteredOn(notice ->
                notice.noticeId().equals(NEW_NOTICE_ID)
        ).singleElement().extracting(PublishedNotice::status)
                .isEqualTo(NoticeLifecycleStatus.PUBLISHED);
    }

    private static PublishedNotice notice(
            UUID noticeId,
            UUID sourceId,
            String validFrom,
            String validTo
    ) {
        return new PublishedNotice(
                noticeId,
                sourceId,
                new AcademicTerm("2026-2027-1"),
                NoticeType.COURSE_SELECTION,
                NoticeLifecycleStatus.PUBLISHED,
                Instant.parse(validFrom),
                Instant.parse(validTo),
                new OfficialWebSnapshot(
                        "https://jwc.zhku.edu.cn/notices/" + noticeId,
                        noticeId + ".html",
                        "B".repeat(64),
                        Instant.parse("2026-08-30T00:00:00Z"),
                        "text/html"
                ),
                true,
                true,
                List.of(new NoticeFact(
                        NoticeFactType.COURSE_SELECTION,
                        Instant.parse("2026-09-06T01:00:00Z"),
                        Instant.parse("2026-09-08T10:00:00Z"),
                        "https://jwc.zhku.edu.cn/course-selection",
                        true,
                        true
                ))
        );
    }

    private static void insertSource(UUID sourceId, String title)
            throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD
        ); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO knowledge_source (
                    id, title, source_type, source_url, source_version,
                    publication_status, published_at, content_checksum,
                    visibility_scope, publication_scope, review_notice,
                    sharing_rights_confirmed, document_family, knowledge_scope
                ) VALUES (?, ?, 'OFFICIAL_NOTICE', ?, 'test', 'PUBLISHED',
                          CURRENT_TIMESTAMP, ?, 'STUDENT_VISIBLE',
                          '本机通知测试', '测试资料', TRUE,
                          'OFFICIAL_NOTICE', 'COURSE_SELECTION')
                """)) {
            statement.setObject(1, sourceId);
            statement.setString(2, title);
            statement.setString(3, "https://jwc.zhku.edu.cn/notices/" + sourceId);
            statement.setString(4, "C".repeat(64));
            statement.executeUpdate();
        }
    }

    private static void deleteFixtures() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD
        ); PreparedStatement facts = connection.prepareStatement("""
                DELETE FROM official_notice_fact
                WHERE notice_id IN (?, ?)
                """); PreparedStatement notices = connection.prepareStatement("""
                DELETE FROM official_notice WHERE id IN (?, ?)
                """); PreparedStatement sources = connection.prepareStatement("""
                DELETE FROM knowledge_source WHERE id IN (?, ?)
                """)) {
            facts.setObject(1, OLD_NOTICE_ID);
            facts.setObject(2, NEW_NOTICE_ID);
            facts.executeUpdate();
            notices.setObject(1, OLD_NOTICE_ID);
            notices.setObject(2, NEW_NOTICE_ID);
            notices.executeUpdate();
            sources.setObject(1, OLD_SOURCE_ID);
            sources.setObject(2, NEW_SOURCE_ID);
            sources.executeUpdate();
        }
    }
}
