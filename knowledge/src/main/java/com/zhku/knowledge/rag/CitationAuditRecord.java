package com.zhku.knowledge.rag;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 一条服务端引用审计事件：关联本轮标识与知识片段，不单独证明回答已经送达学生。
 *
 * <p>旧回答路由保留原语义；RULE_CHECK_CAPTURE只记录取得证据的尝试，
 * 后续仍须通过规则决策和输出边界，不能将此事件计为学生收到回答。</p>
 *
 * @param citationId 服务端分配的不透明标识
 * @param chunkId 该标识对应的知识片段
 * @param route 服务端审计事件类型，不能由学生指定
 * @param issuedAt 审计事件时刻；CAPTURE下是捕获审计时刻，不是回答送达时刻
 * @param sourceId CAPTURE的持久来源身份，旧四参路由不提供此绑定
 * @param sourceRevision CAPTURE时的来源版本，不能自动替换为当前版本
 * @param contentChecksum CAPTURE全文的UTF-8 SHA-256，不是PDF文件指纹
 * @param content CAPTURE时已核验的全文，供写审计时再次匹配；不是模型生成文本
 */
public record CitationAuditRecord(String citationId, UUID chunkId, String route, Instant issuedAt,
                                  UUID sourceId, String sourceRevision, String contentChecksum, String content) {

    // 旧路由仍用四参；新查证捕获必须由服务端填完整绑定，不能凭chunk选择“最新”索引。
    public CitationAuditRecord(String citationId, UUID chunkId, String route, Instant issuedAt) {
        this(citationId, chunkId, route, issuedAt, null, null, null, null);
    }

    public CitationAuditRecord {
        Objects.requireNonNull(citationId, "引用标识不能为空");
        Objects.requireNonNull(chunkId, "片段标识不能为空");
        Objects.requireNonNull(route, "路由级别不能为空");
        Objects.requireNonNull(issuedAt, "下发时刻不能为空");
    }
}
