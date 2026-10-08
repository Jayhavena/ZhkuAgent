package com.zhku.knowledge.controlled;

import com.zhku.knowledge.campusagent.archive.*;
import com.zhku.knowledge.campusagent.submission.CampusRunSubmission;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.springframework.beans.factory.config.BeanPostProcessor;
import static com.zhku.knowledge.controlled.ControlledCampusTestFixtures.*;

/** 仅test可装配提交后屏障；生产配置没有故障开关，也没有供应商调用。 */
public final class SubmissionHttpRestartProbe {
    public static void main(String[] args) {
        if(args.length!=1||!java.util.Set.of("NORMAL","BEFORE_DISPATCH").contains(args[0]))throw new IllegalArgumentException("探针模式不符");
        var model=new Model();Running app=null;
        try {
            var data=new Data(ArchiveTestDatabase.dataSource(),null,null,null,null);
            app=start(data,model,context->{
                if(args[0].equals("BEFORE_DISPATCH")) context.registerBean("submissionRestartBarrier",BeanPostProcessor.class,()->new BeanPostProcessor(){
                    @Override public Object postProcessAfterInitialization(Object bean,String name) {
                        if(!(bean instanceof CampusRunArchiveStore store))return bean;
                        return Proxy.newProxyInstance(store.getClass().getClassLoader(),new Class<?>[]{CampusRunArchiveStore.class},(proxy,method,values)->{
                            Object result;
                            try {result=method.invoke(store,values);}catch(InvocationTargetException failure){throw failure.getCause();}
                            if(method.getName().equals("admitSubmissionOwned")&&result instanceof CampusRunSubmission.Claim claim
                                    &&claim.decision()==CampusRunSubmission.Decision.CREATED) {
                                System.out.println("SUBMISSION_COMMITTED 0 0");System.out.flush();
                                // commit已确认，Manager尚未拿到Admission；故障进程只会被明确的崩溃注入退出。
                                new CountDownLatch(1).await();
                            }
                            return result;
                        });
                    }
                });
            });
            System.out.println("SUBMISSION_READY "+ProcessHandle.current().pid()+" "+app.port);System.out.flush();
            var command=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8)).readLine();
            if(command!=null&&!java.util.Set.of("STOP","q").contains(command))throw new IllegalArgumentException("关闭协议不符");
            app.close();app=null;
            System.out.println("SUBMISSION_CLOSED "+model.calls.get()+" "+model.active.get());System.out.flush();
        }catch(Throwable failure){
            System.out.println("SUBMISSION_FAILED "+failure.getClass().getSimpleName());System.out.flush();
            if(app!=null)app.close();System.exit(1);
        }
    }
}
