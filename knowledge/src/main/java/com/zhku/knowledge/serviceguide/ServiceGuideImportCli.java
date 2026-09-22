package com.zhku.knowledge.serviceguide;

import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 办事清单的导入入口：`{清单策展 JSON} → service_guide + 子表`。
 *
 * <p>与知识侧的 `ReviewedKnowledgeScopeImportCli` 同构（自包含、可重复执行、走审核留痕），
 * 但写入的是办事清单的四张表，与资料/片段完全解耦——清单是"怎么办"的办理路径，
 * 资料是"依据是什么"的规则证据，两者不共用表也不互相替代。</p>
 *
 * <p>密码纪律与既有 CLI 一致：**只从环境变量 `ZHKU_DB_PASSWORD` 读取**，不接受系统属性
 * （系统属性会进命令行与进程列表，属泄漏向量）。</p>
 *
 * <p>用法：{@code java -cp <classpath> com.zhku.knowledge.serviceguide.ServiceGuideImportCli <策展JSON路径>}</p>
 */
public final class ServiceGuideImportCli {

    private ServiceGuideImportCli() {
    }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("用法：ServiceGuideImportCli <清单策展JSON路径>");
        }
        String password = System.getenv("ZHKU_DB_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("缺少数据库密码：请设置环境变量 ZHKU_DB_PASSWORD");
        }
        DataSourceFactory dataSourceFactory = new DataSourceFactory(
                System.getenv().getOrDefault(
                        "ZHKU_DB_URL", "jdbc:postgresql://127.0.0.1:54329/zhkuagent"),
                System.getenv().getOrDefault("ZHKU_DB_USER", "zhkuagent"),
                password);

        // 读取器强制校验"审核通过"、渠道来源定位、步骤连续性等，未审核文本无法借本入口入库。
        ServiceGuide guide = new ReviewedServiceGuideJsonReader().read(Path.of(arguments[0]));
        new JdbcServiceGuidePublisher(dataSourceFactory.create()).publish(guide);

        System.out.println("已发布办事清单：" + guide.guideIdentifier() + " / " + guide.guideVersion());
        System.out.println("标题：" + guide.title());
        System.out.println("学生可见：" + guide.studentVisible() + "，发布时间：" + guide.publishedAt());
        System.out.println("步骤 " + guide.steps().size() + " 条，材料 " + guide.materials().size()
                + " 条，官方渠道 " + guide.channels().size() + " 条，风险提示 "
                + guide.riskNotices().size() + " 条");
        for (OfficialChannel channel : guide.channels()) {
            System.out.println("  - [" + channel.channelType() + "] " + channel.channelName()
                    + " = " + channel.channelValue() + "（来源：" + channel.sourceLocator() + "）");
        }
    }

    /** 极小的数据源工厂：把环境变量解析集中在一处，便于将来换成共享工厂。 */
    private record DataSourceFactory(String url, String user, String password) {

        private DataSourceFactory {
            Objects.requireNonNull(url, "数据库地址不能为空");
            Objects.requireNonNull(user, "数据库用户不能为空");
            Objects.requireNonNull(password, "数据库密码不能为空");
        }

        DriverManagerDataSource create() {
            return new DriverManagerDataSource(url, user, password);
        }
    }
}
