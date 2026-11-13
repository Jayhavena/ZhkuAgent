package com.zhku.knowledge.controlled;

import com.zhku.knowledge.ruleagent.demo.RuleAgentSimulatedDemoFactory;
import com.zhku.knowledge.ruleagent.web.RuleAgentControlledLaunchConfiguration;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 只披露当前宿主的执行模式和模拟覆盖范围，复用Task路径的身份/角色守卫。 */
@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="zhku.persistent-student.enabled",havingValue="true")
public final class PersistentStudentRuntimeController {
    private final String mode;
    public PersistentStudentRuntimeController(Environment env) {
        mode=env.getRequiredProperty(RuleAgentControlledLaunchConfiguration.MODE_PROPERTY);
    }
    public record Info(String mode,String simulatedQuestion) {}
    @GetMapping("/zhku/controlled-preview/v2/tasks/runtime")
    public ResponseEntity<Info> runtime() {
        return ResponseEntity.ok().header("Cache-Control","no-store")
            .body(new Info(mode,mode.equals("SIMULATED")?RuleAgentSimulatedDemoFactory.QUESTION:null));
    }
}
