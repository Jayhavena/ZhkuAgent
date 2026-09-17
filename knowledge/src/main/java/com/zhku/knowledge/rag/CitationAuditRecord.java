package com.zhku.knowledge.rag;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 一条引用下发审计记录：证明"这个标识是本轮服务端为该片段分配的"。
 *
 * <p>审计的意义在于可反查：学生端看到的任一引用都能定位到具体索引条目；
 * 若模型输出了审计中不存在的标识，可直接判定为伪造。</p>
 *
 * @param citationId 服务端分配的不透明标识
 * @param chunkId 该标识对应的知识片段
 * @param route 下发时的路由级别（枚举名，便于审计聚合）
 * @param issuedAt 下发时刻
 */
public record CitationAuditRecord(String citationId, UUID chunkId, String route, Instant issuedAt) {

    public CitationAuditRecord {
        Objects.requireNonNull(citationId, "引用标识不能为空");
        Objects.requireNonNull(chunkId, "片段标识不能为空");
        Objects.requireNonNull(route, "路由级别不能为空");
        Objects.requireNonNull(issuedAt, "下发时刻不能为空");
    }
}
