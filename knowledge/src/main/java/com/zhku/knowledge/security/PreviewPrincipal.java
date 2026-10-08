package com.zhku.knowledge.security;

import java.security.Principal;
import java.util.Objects;

/** 登录会话只保存身份快照，不保存用户名、密码或密码哈希。 */
public record PreviewPrincipal(TrustedActor actor) implements Principal {
    public PreviewPrincipal {
        Objects.requireNonNull(actor, "认证主体不能为空");
    }
    @Override
    public String getName() {
        return actor.userId().toString();
    }
}
