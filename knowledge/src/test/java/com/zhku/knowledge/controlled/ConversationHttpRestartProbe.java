package com.zhku.knowledge.controlled;

import com.zhku.knowledge.campusagent.archive.ArchiveTestDatabase;
import com.zhku.knowledge.conversation.ConversationJdbcAccess;
import java.io.*;
import java.time.Duration;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static com.zhku.knowledge.controlled.ControlledCampusTestFixtures.*;

/** test-only子进程；真实Application与安全/JDBC链，仅提供零调用的合成校园模型依赖。 */
public final class ConversationHttpRestartProbe {
    private ConversationHttpRestartProbe() { }
    static Running start(Data data,Model model) {
        long began=System.nanoTime();
        var application=ControlledPreviewApplication.application();
        application.addInitializers(context->((GenericApplicationContext)context)
                .registerBean("conversationTestModel",ChatModel.class,()->model));
        var source=(DriverManagerDataSource)data.source();
        var context=(ServletWebServerApplicationContext)application.run("--server.port=0","--server.address=127.0.0.1",
                "--server.servlet.context-path=/api","--spring.main.banner-mode=off","--logging.level.root=ERROR",
                "--zhku.controlled-preview.enabled=true","--zhku.controlled-preview.conversations.enabled=true",
                "--zhku.campus-agent.enabled=true","--zhku.campus-agent.archive.enabled=true",
                "--zhku.db.url="+source.getUrl(),"--zhku.db.username="+source.getUsername());
        var running=new Running(new ControlledCampusTestFixtures.Running(context));
        if(System.nanoTime()-began>Duration.ofSeconds(20).toNanos()) { running.close(); throw new AssertionError("启动20秒预算耗尽"); }
        return running;
    }
    static final class Running implements AutoCloseable {
        final ControlledCampusTestFixtures.Running app;
        final ConversationJdbcAccess access;
        final int port;
        Running(ControlledCampusTestFixtures.Running app) { this.app=app; port=app.port; access=app.context.getBean(ConversationJdbcAccess.class); }
        User user() { return new User(port); }
        public void close() {
            app.close();
            if(access.status().inFlight()!=0||access.status().liveThreads()!=0||access.status().resourceFailure())
                throw new AssertionError("SESSION_RESOURCES_REMAINING");
            if(Thread.getAllStackTraces().keySet().stream().anyMatch(t->t.isAlive()&&t.getName().startsWith("zhku-conversation-jdbc")))
                throw new AssertionError("SESSION_THREAD_REMAINING");
        }
    }
    public static void main(String[] args) {
        if(args.length!=0) throw new IllegalArgumentException("探针不接受参数");
        var model=new Model(); Running running=null;
        try {
            running=start(new Data(ArchiveTestDatabase.dataSource(),null,null,null,null),model);
            System.out.println("CONVERSATION_READY "+ProcessHandle.current().pid()+" "+running.port); System.out.flush();
            if(!"STOP".equals(new BufferedReader(new InputStreamReader(System.in)).readLine()))
                throw new IllegalArgumentException("关闭协议不符");
            var access=running.access; running.close(); running=null;
            System.out.println("CONVERSATION_CLOSED "+model.calls.get()+" "+model.active.get()+" "+access.status().inFlight()+" "+access.status().liveThreads());
            System.out.flush();
        } catch(Throwable failure) {
            System.out.println("CONVERSATION_FAILED "+failure.getClass().getSimpleName()); System.out.flush();
            if(running!=null) running.close(); System.exit(1);
        }
    }
}
