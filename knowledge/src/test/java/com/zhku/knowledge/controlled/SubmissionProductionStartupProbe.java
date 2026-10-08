package com.zhku.knowledge.controlled;
import java.util.*;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
/** 只构造产品模型工厂、不调用model；只输出有限异常类型和bean名，排除自由错误正文。 */
public final class SubmissionProductionStartupProbe {
    public static void validateDatabaseTarget(String url) {
        // 探针只挂载这一个class；父测试的系统属性校验不能保护子进程继承的环境URL。
        if(url==null||!url.matches("^jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{4,5}/zhkuagent_run_archive_e2e$"))
            throw new IllegalArgumentException("产品启动探针仅允许显式archive专用临时库");
        int port=Integer.parseInt(url.substring("jdbc:postgresql://127.0.0.1:".length(),url.lastIndexOf('/')));
        if(port<1024||port>65535||port==54329)throw new IllegalArgumentException("产品启动探针拒绝原库端口或非法端口");
    }
    public static void main(String[] args) {
        try { validateDatabaseTarget(System.getenv("ZHKU_DB_URL")); }
        catch(IllegalArgumentException invalid) {System.out.println("PRODUCTION_PROBE_FAILURE IllegalArgumentException target");System.exit(1);return;}
        var settings=new ArrayList<String>(List.of("--server.port=0","--server.address=127.0.0.1","--server.servlet.context-path=/api",
                "--spring.main.banner-mode=off","--logging.level.root=OFF","--zhku.controlled-preview.enabled=true","--zhku.controlled-preview.conversations.enabled=true",
                "--zhku.campus-agent.enabled=true","--zhku.campus-agent.archive.enabled=true","--zhku.db.username=zhkuagent","--zhku.db.url="+System.getenv("ZHKU_DB_URL"),"--zhku.rag.active-profile=dashscope"));
        String[][] provider={{"base-url","https://dashscope.aliyuncs.com/compatible-mode/v1"},{"api-key-env-var","DASHSCOPE_API_KEY"},{"chat-model","qwen-plus"},
                {"embedding-model","text-embedding-v4"},{"embedding-dimensions","1024"},{"timeout-seconds","30"},{"index-profile","dashscope-text-embedding-v4-1024"}};
        for(var entry:provider)settings.add("--zhku.rag.profiles.dashscope."+entry[0]+"="+entry[1]);
        try(var context=(ServletWebServerApplicationContext)ControlledPreviewApplication.application().run(settings.toArray(String[]::new))) {
            context.getBean(com.zhku.knowledge.tools.modelcall.CourseCreditModelTransport.class);
            context.getBean(com.zhku.knowledge.conversation.ConversationJdbcAccess.class);
            System.out.println("PRODUCTION_PROBE_READY");
        }catch(Throwable failure){
            var seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
            for(var current=failure;current!=null&&seen.add(current);current=current.getCause()) {
                String bean=current instanceof org.springframework.beans.factory.BeanCreationException creation?creation.getBeanName()
                        :current instanceof org.springframework.beans.factory.support.BeanDefinitionOverrideException override?override.getBeanName():"none";
                System.out.println("PRODUCTION_PROBE_FAILURE "+current.getClass().getSimpleName()+" "+(bean!=null&&bean.matches("[A-Za-z0-9]+")?bean:"none"));
            }System.exit(1);
        }
    }
}
