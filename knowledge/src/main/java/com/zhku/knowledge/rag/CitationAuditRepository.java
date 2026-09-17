package com.zhku.knowledge.rag;

/**
 * 引用审计写入端。
 *
 * <p>审计写入失败绝不能静默：没有审计就失去了"引用是否伪造"的判定依据，
 * 因此实现方在写入失败时应抛异常而不是吞掉。</p>
 */
@FunctionalInterface
public interface CitationAuditRepository {

    void record(CitationAuditRecord record);
}
