package com.zhku.knowledge.ruleagent;
import java.time.Clock;
import java.util.*;
import java.util.function.*;
import java.util.concurrent.atomic.AtomicInteger;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
/** 仅装配ONLINE，工具资格由调用方的实际证据工厂提供，不加载回放。 */
public final class RuleAgentOnlineFactory implements RuleAgentTaskStore.Factory,AutoCloseable {
    private final RuleAgentProvider.Settings settings;private final Supplier<String> key;private final BiFunction<Owner,UUID,RuleAgentEvidenceTools> tools;
    private final Consumer<RuleAgentProvider.Trace> audit;private final AtomicInteger allowance;private final Clock clock;
    private final Set<RuleAgentProvider> ports=new HashSet<>();private boolean closed;
    public RuleAgentOnlineFactory(RuleAgentProvider.Settings settings,Supplier<String> key,BiFunction<Owner,UUID,RuleAgentEvidenceTools> tools,Consumer<RuleAgentProvider.Trace> audit,int allowance,Clock clock){
        if(allowance<1||allowance>24)throw new IllegalArgumentException("本批调用额度非法");
        this.settings=Objects.requireNonNull(settings);this.key=Objects.requireNonNull(key);this.tools=Objects.requireNonNull(tools);this.audit=Objects.requireNonNull(audit);this.allowance=new AtomicInteger(allowance);this.clock=Objects.requireNonNull(clock);
    }
    public RuleAgentTaskStore.Work open(Owner owner,UUID task){
        synchronized(ports){if(closed)throw new IllegalStateException("在线装配已关闭");}
        var actual=Objects.requireNonNull(tools.apply(owner,task));
        return new RuleAgentTaskStore.Work(){
            public RuleAgentRunner.Outcome run(com.zhku.agent.agent.runtime.AgentRunContext context,RuleAgentRunner.Input input){
                RuleAgentProvider port;synchronized(ports){if(closed)throw new IllegalStateException("在线装配已关闭");ports.removeIf(RuleAgentProvider::isTerminated);
                    // 端口归每run；批次额度归宿主，澄清后不能重新获得外发额度。
                    if(ports.size()>=2)throw new RuleAgentProvider.Failure(ReasonCode.TASK_EXECUTION_UNAVAILABLE,false);
                    port=new RuleAgentProvider(settings,task,context.runId(),key,audit,allowance);ports.add(port);}
                return new RuleAgentRunner(port,new RuleActionBroker(actual),clock).run(context,input);
            }
            public boolean revalidate(ResultEnvelope previous){return previous.evidence().stream().allMatch(e->actual.original(owner,task,e.originalToken()).isPresent());}
        };
    }
    public int remainingCalls(){return allowance.get();}
    public boolean resourcesClosed(){synchronized(ports){return ports.stream().allMatch(RuleAgentProvider::isTerminated);}}
    public void close(){List<RuleAgentProvider> owned;synchronized(ports){closed=true;owned=List.copyOf(ports);}owned.forEach(RuleAgentProvider::close);}
}
