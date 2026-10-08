package com.zhku.knowledge.security;

import java.util.UUID;
import java.util.Objects;

/** 服务端认证快照；公开构造器不是防伪机制，不能用作请求DTO或从header取得。 */
public record TrustedActor(UUID userId, Role role, long authRevision) {
    public TrustedActor {
        Objects.requireNonNull(userId, "身份编号不能为空");
        Objects.requireNonNull(role, "角色不能为空");
        if (authRevision < 0) throw new IllegalArgumentException("身份版本不能为负数");
    }
    public enum Role { USER, MAINTAINER }
}
