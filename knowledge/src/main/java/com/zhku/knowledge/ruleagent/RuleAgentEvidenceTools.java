package com.zhku.knowledge.ruleagent;
import com.zhku.knowledge.rulecheck.*;
import com.zhku.knowledge.retrieval.*;
import com.zhku.knowledge.rag.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.security.MessageDigest;
import static com.zhku.knowledge.ruleagent.RuleAgentContract.*;

/** 任务内证据工具，不负责模型决策、HTTP或学校业务操作。 */
public final class RuleAgentEvidenceTools {
    public record VerifiedFact(String id,String ruleId,RuleCheckRuleTemplate.Fact definition){}
    public record ReadOutcome(ToolObservation observation,List<VerifiedFact> facts){public ReadOutcome{facts=List.copyOf(facts);}}
    private final Owner owner;
    private final UUID taskId;
    private final RuleCheckRuntimeSettings settings;
    private final Supplier<List<RuleCheckApplicationService.CatalogEntry>> catalogue;
    private final ChunkCandidateRetriever lexical;
    private final RuleCheckEvidenceVerifier.CurrentEvidenceReader reader;
    private final CitationAuditRepository audit;
    private final Clock clock;
    private final Instant expires;
    private final Map<UUID,Handle> handles=new HashMap<>();
    private final Map<UUID,Original> originals=new HashMap<>();
    private record Handle(RetrievedChunk chunk,String version,Instant expires){}
    private record Original(RuleCheckVerifiedExcerpt quote,String version){}
    private record Directory(List<RuleCheckApplicationService.CatalogEntry> entries,String version){}
    public RuleAgentEvidenceTools(Owner owner,UUID taskId,RuleCheckRuntimeSettings settings,
        Supplier<List<RuleCheckApplicationService.CatalogEntry>> catalogue,ChunkCandidateRetriever lexical,
        RuleCheckEvidenceVerifier.CurrentEvidenceReader reader,CitationAuditRepository audit,Clock clock){
        this.owner=Objects.requireNonNull(owner);this.taskId=Objects.requireNonNull(taskId);this.settings=Objects.requireNonNull(settings);
        this.catalogue=Objects.requireNonNull(catalogue);this.lexical=Objects.requireNonNull(lexical);this.reader=Objects.requireNonNull(reader);
        this.audit=Objects.requireNonNull(audit);this.clock=Objects.requireNonNull(clock);expires=clock.instant().plusMillis(DEFAULT_BUDGET.taskTtlMillis());
    }
    public ToolObservation search(Owner actor,UUID task,UUID run,int step,Search action){
        if(!owns(actor,task))return fail(run,step,ReasonCode.MATERIAL_SCOPE_MISMATCH,"unavailable");
        try{
            var directory=directory();var values=new ArrayList<Candidate>();var additions=new HashMap<UUID,Handle>();
            for(var chunk:lexical.retrieve(action.query(),Math.min(settings.candidateLimit(),20))){
                if(values.size()==20)break;
                if(!allowed(chunk,directory))continue;
                var current=reader.read(chunk.sourceId(),chunk.chunkId());
                if(current.isEmpty()||!current.get().eligible()||!RuleCheckEvidenceVerifier.contentIsIntact(current.get())
                        ||!current.get().text().equals(chunk.content())||!current.get().revision().equals(chunk.sourceVersion())
                        ||!current.get().checksum().equalsIgnoreCase(chunk.contentChecksum()))continue;
                // 候选只留有界摘要，不能由同名内容借用其他来源的资格。
                UUID token=UUID.randomUUID();String excerpt=chunk.content().length()>512?chunk.content().substring(0,512):chunk.content();
                if(Character.isHighSurrogate(excerpt.charAt(excerpt.length()-1)))excerpt=excerpt.substring(0,excerpt.length()-1);
                values.add(new Candidate(token,excerpt,chunk.sourceVersion()));additions.put(token,new Handle(chunk,directory.version(),clock.instant().plusMillis(DEFAULT_BUDGET.waitingTtlMillis())));
            }
            if(!directory().version().equals(directory.version()))return fail(run,step,ReasonCode.EVIDENCE_CHANGED,directory.version());
            synchronized(handles){handles.entrySet().removeIf(e->!clock.instant().isBefore(e.getValue().expires()));
                if(handles.size()+additions.size()>120)return fail(run,step,ReasonCode.BUDGET_EXCEEDED,directory.version());handles.putAll(additions);}
            if(values.isEmpty())return fail(run,step,ReasonCode.RETRIEVAL_NO_MATCH,directory.version());
            return new ToolObservation(UUID.randomUUID(),run,step,ObservationStatus.SUCCEEDED,null,DataLevel.CANDIDATE,directory.version(),values,List.of(),List.of());
        }catch(IllegalArgumentException revoked){return fail(run,step,ReasonCode.EVIDENCE_CHANGED,"unavailable");}
        catch(RuntimeException unavailable){return fail(run,step,ReasonCode.RETRIEVAL_UNAVAILABLE,"unavailable");}
    }
    public ReadOutcome read(Owner actor,UUID task,UUID run,int step,Read action){
        if(!owns(actor,task))return rejected(run,step,ReasonCode.MATERIAL_SCOPE_MISMATCH,"unavailable");
        try{
            var directory=directory();var chunks=new ArrayList<RetrievedChunk>();
            for(UUID token:action.handles()){
                Handle handle;synchronized(handles){handle=handles.get(token);}
                if(handle==null||!clock.instant().isBefore(handle.expires())||!handle.version().equals(directory.version())||!allowed(handle.chunk(),directory))
                    return rejected(run,step,ReasonCode.EVIDENCE_CHANGED,directory.version());
                if(chunks.stream().noneMatch(c->c.chunkId().equals(handle.chunk().chunkId())))chunks.add(handle.chunk());
            }
            var readerFailed=new java.util.concurrent.atomic.AtomicBoolean();
            var verifier=new RuleCheckEvidenceVerifier((source,chunk)->{try{return reader.read(source,chunk);}catch(RuntimeException e){readerFailed.set(true);throw e;}});
            var capture=new RuleCheckEvidenceCapture(verifier,()->UUID.randomUUID().toString(),audit,clock).capture(chunks);
            if(!capture.accepted())return rejected(run,step,capture.rejectionReason()==RuleCheckEvidenceCapture.RejectionReason.AUDIT_UNAVAILABLE?ReasonCode.AUDIT_UNAVAILABLE:
                readerFailed.get()?ReasonCode.RETRIEVAL_UNAVAILABLE:ReasonCode.EVIDENCE_CHANGED,directory.version());
            var proofs=new LinkedHashMap<String,Evidence>();var facts=new ArrayList<VerifiedFact>();var tokens=new HashMap<UUID,Original>();
            for(var entry:directory.entries())for(var fact:entry.template().facts()){
                var verified=new ArrayList<RuleCheckVerifiedExcerpt>();boolean complete=true;
                for(var binding:fact.evidenceBindings()){
                    var captured=capture.snapshots().stream().filter(s->s.sourceId().equals(binding.sourceId())&&s.chunkId().equals(binding.chunkId())&&s.revision().equals(binding.revision())&&s.checksum().equalsIgnoreCase(binding.checksum())).findFirst();
                    if(captured.isEmpty()){complete=false;break;}
                    var checked=verifier.verify(new RuleCheckEvidenceSpan(captured.get().citationId(),binding.startInclusive(),binding.endExclusive(),binding.quote()),capture.snapshots());
                    if(!checked.accepted()){complete=false;break;}verified.add(checked.excerpt());
                }
                if(!complete)continue;
                String id=entry.template().ruleId()+":"+fact.id();facts.add(new VerifiedFact(id,entry.template().ruleId(),fact));
                for(var quote:verified){String key=quote.citationId()+":"+quote.startInclusive()+"-"+quote.endExclusive();
                    if(!proofs.containsKey(key)){UUID token=UUID.randomUUID();tokens.put(token,new Original(quote,directory.version()));
                        proofs.put(key,new Evidence(key,quote.sourceId(),quote.chunkId(),quote.revision(),quote.checksum().toLowerCase(Locale.ROOT),quote.startInclusive(),quote.endExclusive(),quote.text(),token));}}
            }
            if(readerFailed.get())return rejected(run,step,ReasonCode.RETRIEVAL_UNAVAILABLE,directory.version());
            if(!directory().version().equals(directory.version()))return rejected(run,step,ReasonCode.EVIDENCE_CHANGED,directory.version());
            if(facts.isEmpty()){
                for(var snapshot:capture.snapshots()){
                    UUID token=UUID.randomUUID();var quote=new RuleCheckVerifiedExcerpt(snapshot.citationId(),snapshot.sourceId(),snapshot.chunkId(),snapshot.revision(),snapshot.checksum(),0,snapshot.text().length(),snapshot.text());
                    tokens.put(token,new Original(quote,directory.version()));proofs.put(snapshot.citationId(),new Evidence(snapshot.citationId(),snapshot.sourceId(),snapshot.chunkId(),snapshot.revision(),snapshot.checksum().toLowerCase(Locale.ROOT),0,snapshot.text().length(),snapshot.text(),token));
                }
            }
            int bytes=proofs.values().stream().mapToInt(e->e.quote().getBytes(java.nio.charset.StandardCharsets.UTF_8).length).sum();
            synchronized(originals){if(originals.size()+tokens.size()>8||bytes>DEFAULT_BUDGET.maxEvidenceBytes())return rejected(run,step,ReasonCode.BUDGET_EXCEEDED,directory.version());originals.putAll(tokens);}
            return new ReadOutcome(new ToolObservation(UUID.randomUUID(),run,step,ObservationStatus.SUCCEEDED,null,facts.isEmpty()?DataLevel.VERIFIED_EVIDENCE:DataLevel.VERIFIED_FACT,
                directory.version(),List.of(),List.copyOf(proofs.values()),facts.stream().map(VerifiedFact::id).toList()),facts);
        }catch(IllegalArgumentException revoked){return rejected(run,step,ReasonCode.EVIDENCE_CHANGED,"unavailable");}
        catch(RuntimeException unavailable){return rejected(run,step,ReasonCode.RETRIEVAL_UNAVAILABLE,"unavailable");}
    }
    /** 内部原文句柄必须再次核验；尚不等于学生HTTP引用凭据，T06另行接线。 */
    public Optional<RuleCheckVerifiedExcerpt> original(Owner actor,UUID task,UUID token){
        if(!owns(actor,task))return Optional.empty();Original original;synchronized(originals){original=originals.get(token);}if(original==null)return Optional.empty();
        try{if(!directory().version().equals(original.version()))return Optional.empty();var quote=original.quote();var now=reader.read(quote.sourceId(),quote.chunkId());if(now.isEmpty())return Optional.empty();
            var result=new RuleCheckEvidenceVerifier(reader).verify(new RuleCheckEvidenceSpan(now.get().citationId(),quote.startInclusive(),quote.endExclusive(),quote.text()),List.of(now.get()));
            if(!result.accepted()||!quote.revision().equals(now.get().revision())||!quote.checksum().equalsIgnoreCase(now.get().checksum()))return Optional.empty();return Optional.of(quote);
        }catch(RuntimeException unavailable){return Optional.empty();}
    }
    /** 发布守卫必须重新取得当前审核范围，不能靠读取时的事实ID永久授权。 */
    public Optional<RuleCheckApplicationService.CatalogEntry> reviewedEntry(String ruleId){return directory().entries().stream().filter(e->e.template().ruleId().equals(ruleId)).findFirst();}
    public String catalogueVersion(){return directory().version();}
    public String referenceRevision(){return settings.referenceRevision();}
    private boolean owns(Owner actor,UUID task){return owner.equals(actor)&&taskId.equals(task)&&clock.instant().isBefore(expires);}
    private Directory directory(){
        var entries=List.copyOf(catalogue.get()).stream().filter(e->e.template().reviewState()==RuleCheckRuleTemplate.ReviewState.APPROVED&&e.template().referenceRevision().equals(settings.referenceRevision()))
            .sorted(Comparator.comparing(e->e.template().ruleId())).toList();
        try{byte[] bytes=new ObjectMapper().findAndRegisterModules().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsBytes(entries);
            return new Directory(entries,"cat-"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));}
        catch(Exception unsupported){throw new IllegalStateException("目录身份不可用");}
    }
    private boolean allowed(RetrievedChunk chunk,Directory d){return chunk.sourceId()!=null&&chunk.sourceVersion().equals(settings.referenceRevision())
        &&d.entries().stream().flatMap(e->e.template().facts().stream()).flatMap(f->f.evidenceBindings().stream()).anyMatch(b->b.sourceId().equals(chunk.sourceId())&&b.chunkId().equals(chunk.chunkId())&&b.revision().equals(chunk.sourceVersion())&&b.checksum().equalsIgnoreCase(chunk.contentChecksum()));}
    private ReadOutcome rejected(UUID run,int step,ReasonCode reason,String version){return new ReadOutcome(fail(run,step,reason,version),List.of());}
    private ToolObservation fail(UUID run,int step,ReasonCode reason,String version){return new ToolObservation(UUID.randomUUID(),run,step,
        reason==ReasonCode.RETRIEVAL_NO_MATCH?ObservationStatus.NO_MATCH:Set.of(ReasonCode.RETRIEVAL_UNAVAILABLE,ReasonCode.AUDIT_UNAVAILABLE).contains(reason)?ObservationStatus.UNAVAILABLE:ObservationStatus.REJECTED,
        reason,DataLevel.NONE,version,List.of(),List.of(),List.of());}
}
