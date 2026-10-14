package com.zhku.knowledge.ruleagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.security.MessageDigest;
import java.util.*;

/** 内部发布合同：模型描述需求和对应关系，完整性由宿主聚合，不能由模型自报。 */
public final class RulePublicationContract {
    private RulePublicationContract() {}
    public static final String REVISION="RULE_PUBLICATION_REVIEW_01";
    public static final int RESERVED_REVIEW_CALLS=1;
    public static final int MAX_DECISION_CALLS=RuleAgentContract.DEFAULT_BUDGET.maxModelAttemptsPerRun()-RESERVED_REVIEW_CALLS;
    public static final int MAX_REQUIREMENTS=8;
    public static final int MAX_BINDINGS=16;
    private static final ObjectMapper JSON=new ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true);

    public enum Dimension { CATEGORY,STUDY_ARRANGEMENT,HOURS_NORMAL,HOURS_LIMIT,SEMESTER_CREDIT_LIMIT,DEFERRED_EXAM,RETAKE,UNKNOWN }
    public enum Subject { PUBLIC_ELECTIVE,PROFESSIONAL_ELECTIVE,GENERAL_UNDERGRADUATE,UNSPECIFIED }
    public enum BindingRole { CORE,CONDITION_EXCEPTION }
    public enum Recognition { IDENTIFIED,AMBIGUOUS,UNCLASSIFIED }
    public enum UnresolvedReason { AMBIGUOUS_SCOPE,UNSUPPORTED_DIMENSION,UNCLASSIFIED_DEMAND }
    public enum Verdict { COMPLETE,PARTIAL,UNRESOLVED }
    public enum Code { REVIEW_SCHEMA_INVALID,REVIEW_BINDING_INVALID,FACT_NOT_READ,FACT_NOT_RELEVANT,
        CAPABILITY_UNAVAILABLE,WHEN_NOT_SATISFIED,ACTION_UNSUPPORTED,CONDITION_NOT_NECESSARY,CONDITION_ALREADY_PROVIDED }

    public static final class Rejected extends RuntimeException {
        public final Code code;
        public Rejected(Code code) {
            super(code.name(),null,false,false);
            this.code=code;
        }
    }

    public record FactCapability(String factId,Dimension dimension,Subject subject,Set<BindingRole> allowedRoles,
                                 Set<Dimension> auxiliaryDimensions,String sourceFingerprint) {
        public FactCapability {
            identifier(factId);
            require(dimension!=null&&dimension!=Dimension.UNKNOWN&&subject!=null&&subject!=Subject.UNSPECIFIED);
            allowedRoles=Set.copyOf(allowedRoles);
            auxiliaryDimensions=Set.copyOf(auxiliaryDimensions);
            require(!allowedRoles.isEmpty());
            digest(sourceFingerprint);
        }
    }
    public record DemandSpan(String subquestionId,int startInclusive,int endExclusive) {
        public DemandSpan {
            identifier(subquestionId);
            require(startInclusive>=0&&endExclusive>startInclusive);
        }
    }
    public record Requirement(String id,DemandSpan span,Dimension dimension,Subject subject,
                              Recognition recognition,List<String> necessaryConditionIds) {
        public Requirement {
            identifier(id);
            require(span!=null&&dimension!=null&&subject!=null&&recognition!=null);
            necessaryConditionIds=items(necessaryConditionIds,0,16);
            necessaryConditionIds.forEach(RulePublicationContract::identifier);
            unique(necessaryConditionIds);
            require(recognition!=Recognition.IDENTIFIED||(dimension!=Dimension.UNKNOWN&&subject!=Subject.UNSPECIFIED));
        }
    }
    public record SemanticBinding(String requirementId,String factId,BindingRole role) {
        public SemanticBinding {
            identifier(requirementId);
            identifier(factId);
            require(role!=null);
        }
    }
    public record UnresolvedDemand(DemandSpan span,UnresolvedReason reason) {
        public UnresolvedDemand {require(span!=null&&reason!=null);}
    }
    public record SemanticAssessment(String protocolRevision,String inputFingerprint,List<Requirement> requirements,
                                     List<SemanticBinding> bindings,List<UnresolvedDemand> unresolvedDemands) {
        public SemanticAssessment {
            require(REVISION.equals(protocolRevision));
            digest(inputFingerprint);
            requirements=items(requirements,0,MAX_REQUIREMENTS);
            bindings=items(bindings,0,MAX_BINDINGS);
            unresolvedDemands=items(unresolvedDemands,0,MAX_REQUIREMENTS);
            require(!requirements.isEmpty()||!unresolvedDemands.isEmpty());
            unique(requirements.stream().map(Requirement::id).toList());
            unique(bindings);
            unique(unresolvedDemands);
        }
    }
    public record ReviewQuote(String revision,String quote) {}
    public record SemanticFact(String id,String ruleId,String text,Map<String,String> when,List<ReviewQuote> quotes) {
        public SemanticFact {
            when=Map.copyOf(when);
            quotes=List.copyOf(quotes);
        }
    }
    public record SemanticInput(String protocolRevision,String capabilityVersion,String inputFingerprint,
                                List<RuleAgentRunner.Question> questions,Map<String,String> userConditions,
                                List<SemanticFact> facts,Map<String,FactCapability> capabilities) {
        public SemanticInput {
            require(REVISION.equals(protocolRevision));
            digest(inputFingerprint);
            questions=List.copyOf(questions);
            userConditions=Map.copyOf(userConditions);
            facts=List.copyOf(facts);
            capabilities=Map.copyOf(capabilities);
        }
    }
    public record QuestionAssessment(Set<String> coveredRequirementIds,Set<String> uncoveredRequirementIds,
                                     boolean demandUnresolved,boolean hasProposedIssues) {
        public QuestionAssessment {
            coveredRequirementIds=Set.copyOf(coveredRequirementIds);
            uncoveredRequirementIds=Set.copyOf(uncoveredRequirementIds);
        }
    }
    public record PublicationAssessment(Verdict verdict,List<SemanticBinding> acceptedBindings,
                                       Map<String,QuestionAssessment> questions) {
        public PublicationAssessment {
            acceptedBindings=List.copyOf(acceptedBindings);
            questions=Map.copyOf(questions);
        }
    }

    public static SemanticInput input(RuleAgentRunner.Input input,List<RuleAgentEvidenceTools.VerifiedFact> facts,
                                      RuleFactCapabilityCatalogue catalogue) {
        var safeFacts=facts.stream().map(f->new SemanticFact(f.id(),f.ruleId(),f.definition().text(),f.definition().when(),
            f.definition().evidenceBindings().stream().map(b->new ReviewQuote(b.revision(),b.quote())).toList())).toList();
        var capabilities=new TreeMap<String,FactCapability>();
        for(var fact:facts) catalogue.find(fact.id()).ifPresent(c->capabilities.put(fact.id(),c));
        var safe=Map.of("questions",input.questions(),"conditions",input.userConditions(),"facts",safeFacts,
            "capabilities",capabilities,"version",catalogue.version(),"protocol",REVISION);
        // 任务绑定仅进入摘要，原始主体/任务/原文令牌不出现在核验输入中。
        String hash=fingerprint(Map.of("taskBinding",input.taskId(),"taskVersion",input.version(),"safeInput",safe));
        return new SemanticInput(REVISION,catalogue.version(),hash,input.questions(),input.userConditions(),safeFacts,capabilities);
    }

    static String fingerprint(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsBytes(value)));
        } catch(Exception invalid) {
            throw new Rejected(Code.REVIEW_SCHEMA_INVALID);
        }
    }
    static void require(boolean valid) {if(!valid)throw new Rejected(Code.REVIEW_SCHEMA_INVALID);}
    static void identifier(String value) {require(value!=null&&value.matches("[A-Za-z0-9][A-Za-z0-9_./:-]{0,127}"));}
    static void digest(String value) {require(value!=null&&value.matches("[a-f0-9]{64}"));}
    static <T> List<T> items(List<T> values,int min,int max) {
        require(values!=null&&values.size()>=min&&values.size()<=max&&values.stream().noneMatch(Objects::isNull));
        return List.copyOf(values);
    }
    static void unique(List<?> values) {require(new HashSet<>(values).size()==values.size());}
}
