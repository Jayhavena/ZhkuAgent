package com.zhku.knowledge.rag;

/**
 * 引用标识工厂：为本轮获准证据分配不透明标识（冻结契约）。
 *
 * <p>标识由服务端分配、对模型不可解析：模型只能原样回填，不能据此推断
 * 或构造新标识。实现方须保证标识不可预测、同一轮内不重复。</p>
 */
@FunctionalInterface
public interface CitationIdFactory {

    /** 生成一个新的引用标识。 */
    String next();
}
