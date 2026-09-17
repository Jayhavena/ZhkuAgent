package com.zhku.knowledge.rag;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 生产用引用标识工厂：不可预测、同轮不重复。
 *
 * <p>用 SecureRandom 而非序列号：标识一旦可预测，外部就能构造"看起来合法"的
 * 引用去试探白名单；不透明性本身就是防伪造的一部分。</p>
 */
public final class RandomCitationIdFactory implements CitationIdFactory {

    private static final int BYTES = 12;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String next() {
        byte[] bytes = new byte[BYTES];
        random.nextBytes(bytes);
        return "cit-" + HexFormat.of().formatHex(bytes);
    }
}
