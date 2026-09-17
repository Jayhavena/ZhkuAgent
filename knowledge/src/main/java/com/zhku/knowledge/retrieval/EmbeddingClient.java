package com.zhku.knowledge.retrieval;

/**
 * provider-neutral 的 embedding 调用抽象。
 *
 * <p>实现方（各供应商适配器或本机 mock）必须保证 {@link #dimensions()} 与实际
 * 返回向量长度一致；索引任务据此拒绝维度不符的写入。</p>
 */
public interface EmbeddingClient {

    /**
     * 计算文本向量。
     *
     * @param text 待向量化文本
     * @return 向量，长度必须等于 {@link #dimensions()}
     */
    float[] embed(String text);

    /** 本客户端产出的向量维度。 */
    int dimensions();
}
