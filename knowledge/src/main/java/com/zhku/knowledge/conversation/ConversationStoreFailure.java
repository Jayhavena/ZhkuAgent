package com.zhku.knowledge.conversation;

import java.util.Objects;

/** 有限失败分类；驱动异常可能包含SQL/地址/凭据，不能进入HTTP、日志或调用方消息。 */
public final class ConversationStoreFailure extends RuntimeException {
    public enum Code { AUTH_REVOKED, ROLE_DENIED, STORE_UNAVAILABLE, CREATION_UNCONFIRMED, WRITE_UNCONFIRMED }
    private final Code code;

    public ConversationStoreFailure(Code code) {
        super(Objects.requireNonNull(code).name(), null, false, false);
        this.code = code;
    }

    public Code code() { return code; }
}
