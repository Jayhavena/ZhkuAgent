package com.zhku.agent.agent;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BaseAgent 对外配置修改边界测试。
 *
 * <p>公共方法集合本身就是调用方可依赖的真实 API 契约。这里使用反射验证外部代码
 * 无法在 Agent 运行期间调用 setter 改写可复用配置，不读取私有字段或源代码文本。</p>
 */
class BaseAgentEncapsulationTest {

    /** Lombok @Data 不应重新生成绕过生命周期规则的公共 setter。 */
    @Test
    void shouldNotExposePublicConfigurationSetters() {
        List<String> publicSetters = Arrays.stream(
                        BaseAgent.class.getDeclaredMethods()
                )
                .filter(method -> Modifier.isPublic(
                        method.getModifiers()
                ))
                .map(Method::getName)
                .filter(name -> name.startsWith("set"))
                .sorted()
                .toList();

        assertThat(publicSetters)
                .as("BaseAgent 不应向外部暴露可变配置 setter")
                .isEmpty();
    }
}
