package com.zhku.knowledge.controlled;

import com.zhku.knowledge.campusagent.archive.ArchiveTestDatabase;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import static com.zhku.knowledge.controlled.ControlledCampusTestFixtures.*;

/** test-only实际JVM入口；同一生产Application工厂，不向参数/文件输出Cookie或凭据。 */
public final class ControlledCampusIdentityProcessProbe {
    private ControlledCampusIdentityProcessProbe() { }
    public static void main(String[] args) {
        if (args.length != 0) throw new IllegalArgumentException("探针不接受参数");
        var model = new Model();
        model.onEntered = () -> { System.out.println("CONTROLLED_BLOCKED"); System.out.flush(); };
        Running app = null;
        try {
            var data = new Data(ArchiveTestDatabase.dataSource(), null, null, null, null);
            app = start(data, model);
            System.out.println("CONTROLLED_READY " + ProcessHandle.current().pid() + " " + app.port);
            System.out.flush();
            String command = new BufferedReader(new InputStreamReader(System.in)).readLine();
            if (!"STOP".equals(command)) throw new IllegalArgumentException("探针关闭协议不符");
            // 宿主先走原有有界排空；随后释放测试模型屏障，不能谎称shutdown已释放模型。
            app.close(model.release::countDown); app = null;
            System.out.println("CONTROLLED_CLOSED " + model.calls.get() + " " + model.active.get());
            System.out.flush();
        } catch (Throwable failure) {
            // 启动异常可能携URL/SQL/账号；探针只给有限阶段和异常类型。
            String category = failure instanceof AssertionError
                    && "CONTROLLED_THREADS_REMAINING_TIMER".equals(failure.getMessage()) ? "CloseTimerStillAlive"
                            : failure.getClass().getSimpleName();
            System.out.println("CONTROLLED_FAILED " + category);
            System.out.flush();
            model.release.countDown();
            if (app != null) app.close();
            System.exit(1);
        }
    }
}
