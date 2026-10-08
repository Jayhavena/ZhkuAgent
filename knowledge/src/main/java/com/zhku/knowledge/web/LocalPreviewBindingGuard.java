package com.zhku.knowledge.web;

/** 未建立身份与资源归属控制前，只允许单人本机预览；禁止外部配置把入口暴露到任意网卡。 */
public final class LocalPreviewBindingGuard {
    public LocalPreviewBindingGuard(String configuredAddress) {
        // 精确允许回环字面量，不做 DNS 解析，避免主机名或解析配置改变网络边界。
        if (!"127.0.0.1".equals(configuredAddress) && !"::1".equals(configuredAddress)
                && !"0:0:0:0:0:0:0:1".equals(configuredAddress)) {
            throw new IllegalArgumentException("本机预览要求 server.address 显式设置为回环地址；非本机访问须先完成独立的身份与权限验收");
        }
    }
}
