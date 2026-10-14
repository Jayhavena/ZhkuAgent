package com.zhku.knowledge.ruleagent;
import java.util.*;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;
/** 仅允许规则证据工具；原文是数据，不进入旧通用工具注册表。每轮独立实例。 */
public final class RuleActionBroker {
    final RuleAgentEvidenceTools tools;
    private final List<ToolObservation> observations=new ArrayList<>();
    private final Map<String,List<ToolObservation>> byQuestion=new HashMap<>();
    private final Map<UUID,String> handles=new HashMap<>();
    private final Map<String,Map<String,RuleAgentEvidenceTools.VerifiedFact>> facts=new HashMap<>();
    private final Map<String,List<Evidence>> evidence=new HashMap<>();
    private final Set<String> seenCandidates=new HashSet<>(),seenFacts=new HashSet<>();
    private final Set<UUID> seenEvidence=new HashSet<>();
    private List<Candidate> currentCandidates=List.of();
    private boolean progressed; private final Set<String> readSources=new HashSet<>();
    public RuleActionBroker(RuleAgentEvidenceTools tools){this.tools=Objects.requireNonNull(tools);}
    ToolObservation execute(NextAction action,RuleAgentRunner.Input input,UUID run,int step){
        ToolObservation observation;String q;
        if(action instanceof Search s){q=s.subquestionId();observation=tools.search(input.owner(),input.taskId(),run,step,s);currentCandidates=observation.candidates();for(var c:observation.candidates())handles.put(c.handle(),q);}
        else if(action instanceof Read r){
            q=r.subquestionId();
            if(r.handles().stream().anyMatch(h->!handles.containsKey(h)))observation=new ToolObservation(UUID.randomUUID(),run,step,ObservationStatus.REJECTED,ReasonCode.EVIDENCE_CHANGED,DataLevel.NONE,tools.catalogueVersion(),List.of(),List.of(),List.of());
            else{var read=tools.read(input.owner(),input.taskId(),run,step,r);observation=read.observation();
                if(observation.status()==ObservationStatus.SUCCEEDED)for(var selected:r.handles())readSources.add(tools.candidateIdentity(selected));
                for(var f:read.facts())facts.computeIfAbsent(q,k->new LinkedHashMap<>()).put(f.id(),f);
                evidence.computeIfAbsent(q,k->new ArrayList<>()).addAll(observation.evidence());}
        }else throw new RuleAgentRunner.Rejected(ReasonCode.UNKNOWN_TOOL);
        progressed=false;
        // 进展由实际观察的新来源/事实/证据决定，不能用换查询词、换随机句柄冒充新证据。
        if(observation.status()==ObservationStatus.SUCCEEDED){
            for(var candidate:observation.candidates())progressed|=seenCandidates.add(tools.candidateIdentity(candidate.handle()));
            for(var fact:observation.factIds())progressed|=seenFacts.add(fact);
            for(var proof:observation.evidence())progressed|=seenEvidence.add(proof.originalToken());
        }
        observations.add(observation);byQuestion.computeIfAbsent(q,k->new ArrayList<>()).add(observation);return observation;
    }
    boolean progressed(){return progressed;}
    RuleAgentRunner.ActionState actionState(int decisions,int steps,long millis){
        var current=currentCandidates;
        var states=current.stream().map(c->new RuleAgentRunner.CandidateReadState(c.handle(),readSources.contains(tools.candidateIdentity(c.handle())))).toList();
        var allowed=new ArrayList<String>();long reserve=DEFAULT_BUDGET.maxModelMillis()+5000L;
        // 保留后续动作和独立核验的位置；这是资源可行性约束，不是答案完整性判定。
        if(decisions>=3&&steps>=3&&millis>=reserve+3L*DEFAULT_BUDGET.maxModelMillis())allowed.add("SEARCH_REVIEWED_RULES");
        if(decisions>=2&&steps>=2&&millis>=reserve+2L*DEFAULT_BUDGET.maxModelMillis()&&!states.isEmpty()
            &&(states.stream().anyMatch(c->!c.alreadyRead())||tools.hasUnreadFact(current.stream().map(Candidate::handle).toList(),seenFacts)))allowed.add("READ_VERIFIED_RULE");
        if(!facts().isEmpty()&&millis>reserve)allowed.add("ASK_NECESSARY_CONDITION");
        if(!observations.isEmpty()&&millis>reserve)allowed.add("FINISH_CHECK");
        return new RuleAgentRunner.ActionState(List.copyOf(allowed),states,tools.remainingEvidenceFragments(),tools.remainingEvidenceBytes(),Math.max(0,millis),DEFAULT_BUDGET.maxModelMillis(),5000);
    }
    List<ToolObservation> observations(){return List.copyOf(observations);}
    List<ToolObservation> observations(String q){return List.copyOf(byQuestion.getOrDefault(q,List.of()));}
    List<RuleAgentEvidenceTools.VerifiedFact> facts(){return facts.values().stream().flatMap(m->m.values().stream()).distinct().toList();}
    // 同任务可复用已核验资料；每个子问题是否适用必须由发布守卫独立核对，不能靠读取标签授权。
    List<RuleAgentEvidenceTools.VerifiedFact> facts(String q){return facts();}
    RuleAgentEvidenceTools.VerifiedFact fact(String q,String id){return facts().stream().filter(f->f.id().equals(id)).findFirst().orElseThrow(()->new RuleAgentRunner.Rejected(ReasonCode.INVALID_MODEL_RESPONSE));}
    List<Evidence> proofs(String q,RuleAgentEvidenceTools.VerifiedFact f,RuleAgentRunner.Input input){
        var result=new ArrayList<Evidence>();
        for(var b:f.definition().evidenceBindings()){
            var proof=evidence.values().stream().flatMap(List::stream).filter(e->e.sourceId().equals(b.sourceId())&&e.chunkId().equals(b.chunkId())&&e.revision().equals(b.revision())&&e.checksum().equalsIgnoreCase(b.checksum())&&e.startInclusive()==b.startInclusive()&&e.endExclusive()==b.endExclusive()&&e.quote().equals(b.quote())).findFirst().orElseThrow(()->new RuleAgentRunner.Rejected(ReasonCode.EVIDENCE_CHANGED));
            if(tools.original(input.owner(),input.taskId(),proof.originalToken()).isEmpty())throw new RuleAgentRunner.Rejected(ReasonCode.EVIDENCE_CHANGED);result.add(proof);
        }
        return List.copyOf(result);
    }
}
