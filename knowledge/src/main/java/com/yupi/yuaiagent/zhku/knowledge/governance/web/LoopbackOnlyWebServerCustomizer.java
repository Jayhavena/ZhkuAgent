package com.yupi.yuaiagent.zhku.knowledge.governance.web;

import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;

import java.net.InetAddress;

/** 强制管理应用只监听本机环回地址，端口覆盖不会改变该边界。 */
public final class LoopbackOnlyWebServerCustomizer
        implements WebServerFactoryCustomizer<ConfigurableWebServerFactory> {

    @Override
    public void customize(ConfigurableWebServerFactory factory) {
        factory.setAddress(InetAddress.getLoopbackAddress());
    }
}
