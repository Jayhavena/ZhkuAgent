package com.zhku.knowledge.ruleagent.web;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.zhku.knowledge.ruleagent.*;
import com.zhku.knowledge.rulecheck.*;
import com.zhku.knowledge.security.*;
import jakarta.servlet.http.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;

/** HTTP只映射任务合同；任务状态、并发续答与取消结束仍由T04负责。 */
@RestController
@RequestMapping("/zhku/controlled-preview/v2/tasks")
public final class RuleAgentTaskController {
    public static final String OWNER_ATTRIBUTE="ZHKU_RULE_AGENT_OWNER";
    @FunctionalInterface public interface OriginalReader {Optional<RuleCheckVerifiedExcerpt> read(Owner owner,UUID taskId,UUID token);}
    private record Binding(TrustedActor actor,Owner owner){}
    public record ErrorReply(String code,String message){}
    public record Capabilities(int schemaVersion,boolean asynchronous){}
    private final RuleAgentTaskStore tasks;private final PreviewIdentityRepository identities;
    private final OriginalReader originals;private final RuleCheckOriginalEvidenceService locator;
    private final ObjectMapper json=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public RuleAgentTaskController(RuleAgentTaskStore tasks,PreviewIdentityRepository identities,OriginalReader originals,RuleCheckOriginalEvidenceService locator){this.tasks=tasks;this.identities=identities;this.originals=originals;this.locator=locator;}
    @GetMapping("/capabilities") public ResponseEntity<?> capabilities(@AuthenticationPrincipal PreviewPrincipal p,HttpServletRequest r){return invoke(p,r,200,()->new Capabilities(SCHEMA_VERSION,true));}
    @PostMapping public ResponseEntity<?> create(@RequestBody byte[] body,@AuthenticationPrincipal PreviewPrincipal p,HttpServletRequest r){
        return invoke(p,r,202,()->{var n=payload(body,"question clientRequestId");return tasks.create(owner(r),new CreateTask(string(n,"question"),uuid(n,"clientRequestId")));});
    }
    @GetMapping("/{taskId}") public ResponseEntity<?> snapshot(@PathVariable UUID taskId,@AuthenticationPrincipal PreviewPrincipal p,HttpServletRequest r){
        return invoke(p,r,200,()->{
            var owner=owner(r);var snapshot=tasks.snapshot(owner,taskId);
            // 发布时有效不代表现在仍可展示；刷新/恢复不能绕过原文入口重放已撤销的证据和结论。
            // 读取在TaskStore锁外，复用现有有界句柄校验，不重新执行Agent或消耗模型预算。
            if(snapshot.result()!=null)for(var proof:snapshot.result().evidence())currentEvidence(owner,taskId,proof);
            return snapshot;
        });
    }
    @GetMapping("/requests/{action}/{requestId}") public ResponseEntity<?> lookup(@PathVariable RequestAction action,@PathVariable UUID requestId,@AuthenticationPrincipal PreviewPrincipal p,HttpServletRequest r){return invoke(p,r,200,()->tasks.lookup(owner(r),action,requestId));}
    @PostMapping("/{taskId}/messages") public ResponseEntity<?> message(@PathVariable UUID taskId,@RequestBody byte[] body,@AuthenticationPrincipal PreviewPrincipal p,HttpServletRequest r){
        return invoke(p,r,202,()->{var n=payload(body,"message continuationToken expectedVersion clientRequestId");return tasks.message(owner(r),taskId,new Message(string(n,"message"),uuid(n,"continuationToken"),version(n),uuid(n,"clientRequestId")));});
    }
    @PostMapping("/{taskId}/cancel") public ResponseEntity<?> cancel(@PathVariable UUID taskId,@RequestBody byte[] body,@AuthenticationPrincipal PreviewPrincipal p,HttpServletRequest r){
        return invoke(p,r,202,()->{var n=payload(body,"runId expectedVersion clientRequestId");return tasks.cancel(owner(r),taskId,new Cancel(uuid(n,"runId"),version(n),uuid(n,"clientRequestId")));});
    }
    @GetMapping("/{taskId}/originals/{token}") public ResponseEntity<?> original(@PathVariable UUID taskId,@PathVariable UUID token,@AuthenticationPrincipal PreviewPrincipal p,HttpServletRequest r){
        return invoke(p,r,200,()->{
            var owner=owner(r);var snapshot=tasks.snapshot(owner,taskId);
            var proof=snapshot.result()==null?null:snapshot.result().evidence().stream().filter(e->e.originalToken().equals(token)).findFirst().orElse(null);
            // 只授权已发布且有用途关系的证据；猜到内部读取句柄不能访问候选或别的任务原文。
            if(proof==null)throw new RuleAgentTaskStore.Failure(RuleAgentTaskStore.Code.NOT_AVAILABLE);
            var quote=currentEvidence(owner,taskId,proof);
            var located=locator.locate(quote);
            if(located.code()==RuleCheckOriginalEvidenceService.Code.UNAVAILABLE)throw new IllegalStateException();
            if(located.code()!=RuleCheckOriginalEvidenceService.Code.FOUND)throw new RuleAgentTaskStore.Failure(RuleAgentTaskStore.Code.EXPIRED);
            return located.original();
        });
    }
    private RuleCheckVerifiedExcerpt currentEvidence(Owner owner,UUID taskId,Evidence proof){
        var quote=originals.read(owner,taskId,proof.originalToken()).orElseThrow(()->new RuleAgentTaskStore.Failure(RuleAgentTaskStore.Code.EXPIRED));
        if(!proof.sourceId().equals(quote.sourceId())||!proof.chunkId().equals(quote.chunkId())||!proof.revision().equals(quote.revision())||!proof.checksum().equalsIgnoreCase(quote.checksum())||proof.startInclusive()!=quote.startInclusive()||proof.endExclusive()!=quote.endExclusive()||!proof.quote().equals(quote.text()))throw new RuleAgentTaskStore.Failure(RuleAgentTaskStore.Code.EXPIRED);
        return quote;
    }
    private ResponseEntity<?> invoke(PreviewPrincipal principal,HttpServletRequest request,int status,Supplier<?> operation){
        if(principal==null)return error(401,"AUTHENTICATION_REQUIRED");if(principal.actor().role()!=TrustedActor.Role.USER)return error(403,"ROLE_DENIED");
        var session=request.getSession(false);Binding binding=null;
        try{
            identities.requireCurrent(principal.actor());
            if(session==null)return error(401,"AUTH_REVOKED");
            synchronized(session){requireActor(session,principal.actor());var old=session.getAttribute(OWNER_ATTRIBUTE);
                if(old instanceof Binding b&&b.actor().equals(principal.actor()))binding=b;
                else{binding=new Binding(principal.actor(),new Owner(principal.actor().userId(),UUID.randomUUID()));session.setAttribute(OWNER_ATTRIBUTE,binding);}}
            request.setAttribute(OWNER_ATTRIBUTE,binding);
            Object value=operation.get();identities.requireCurrent(principal.actor());
            synchronized(session){requireActor(session,principal.actor());if(session.getAttribute(OWNER_ATTRIBUTE)!=binding)return error(401,"AUTH_REVOKED");}
            return ResponseEntity.status(status).header("Cache-Control","no-store").body(value);
        }catch(PreviewIdentityRepository.Failure failure){return error(failure.code()==PreviewIdentityRepository.Code.IDENTITY_UNAVAILABLE?503:401,failure.code().name());}
        catch(RuleAgentTaskStore.Failure failure){return switch(failure.code){
            case NOT_AVAILABLE->error(404,"TASK_NOT_AVAILABLE");case CONFLICT->error(409,"TASK_CONFLICT");case EXPIRED->error(409,"TASK_EXPIRED");
            case CAPACITY->error(429,"TASK_CAPACITY");case NEEDS_CONFIRMATION->error(422,"NEEDS_CONFIRMATION");case NEW_TASK_REQUIRED->error(422,"NEW_TASK_REQUIRED");case CLOSED->error(503,"TASK_SERVICE_UNAVAILABLE");};}
        catch(IllegalArgumentException invalid){return error(400,"INVALID_TASK_REQUEST");}
        catch(IllegalStateException invalidSession){return error(503,"TASK_SERVICE_UNAVAILABLE");}
        catch(RuntimeException unavailable){return error(503,"TASK_SERVICE_UNAVAILABLE");}
    }
    private static Owner owner(HttpServletRequest r){return ((Binding)r.getAttribute(OWNER_ATTRIBUTE)).owner();}
    private static void requireActor(HttpSession session,TrustedActor actor){
        var stored=session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        if(!(stored instanceof SecurityContext context)||context.getAuthentication()==null||!(context.getAuthentication().getPrincipal() instanceof PreviewPrincipal p)||!p.actor().equals(actor))throw new PreviewIdentityRepository.Failure(PreviewIdentityRepository.Code.AUTH_REVOKED);
    }
    private JsonNode payload(byte[] body,String fields){try{var n=json.readTree(body);var expected=Set.of(fields.split(" "));if(n==null||!n.isObject()||n.size()!=expected.size())throw new IllegalArgumentException();n.fieldNames().forEachRemaining(k->{if(!expected.contains(k))throw new IllegalArgumentException();});return n;}catch(Exception invalid){throw new IllegalArgumentException();}}
    private static String string(JsonNode n,String key){if(!n.path(key).isTextual())throw new IllegalArgumentException();return n.path(key).textValue();}
    private static UUID uuid(JsonNode n,String key){String value=string(n,key);if(!value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))throw new IllegalArgumentException();return UUID.fromString(value);}
    private static long version(JsonNode n){var v=n.path("expectedVersion");if(!v.isIntegralNumber()||!v.canConvertToLong())throw new IllegalArgumentException();return v.longValue();}
    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,org.springframework.http.converter.HttpMessageNotReadableException.class}) public ResponseEntity<?> invalid(){return error(400,"INVALID_TASK_REQUEST");}
    private static ResponseEntity<?> error(int status,String code){return ResponseEntity.status(status).header("Cache-Control","no-store").body(new ErrorReply(code,"请求未获确认，请根据任务状态继续操作。"));}
}
