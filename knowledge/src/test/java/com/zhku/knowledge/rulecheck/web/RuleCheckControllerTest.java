package com.zhku.knowledge.rulecheck.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.knowledge.rulecheck.*;
import com.zhku.knowledge.security.*;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RuleCheckControllerTest {
    static final String P="/zhku/controlled-preview/v2/rule-check";
    static final ObjectMapper JSON=new ObjectMapper();
    RuleCheckApplicationService service;MockMvc mvc;
    MockHttpSession session;TrustedActor actor;
    PreviewIdentityRepository identities;RuleCheckOriginalEvidenceService originals;
    @BeforeEach void setup() {
        service=mock(RuleCheckApplicationService.class);
        session=new MockHttpSession();actor=new TrustedActor(UUID.randomUUID(),TrustedActor.Role.USER,0);authenticate(actor);
        session.setAttribute(org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
            org.springframework.security.core.context.SecurityContextHolder.getContext());
        identities=mock(PreviewIdentityRepository.class);originals=mock(RuleCheckOriginalEvidenceService.class);
        // 这组只测Controller协议，最终规则/资料guard明确替身；真实Runtime/JDBC另独立HTTP类。
        when(service.isCurrent(any())).thenReturn(true);when(service.isReferenceApproved(any())).thenReturn(true);
        when(originals.locate(any())).thenAnswer(i->{RuleCheckVerifiedExcerpt quote=i.getArgument(0);
            return new RuleCheckOriginalEvidenceService.Result(RuleCheckOriginalEvidenceService.Code.FOUND,
                new RuleCheckOriginalEvidenceService.View(quote.citationId(),quote.sourceId(),quote.chunkId(),quote.revision(),"合成标题","合成条款",1,
                    "RECORDED_SOURCE_PAGE",null,null,quote.text(),quote.startInclusive(),quote.startInclusive(),quote.endExclusive(),List.of("合成映射未知")));});
        var builder=Jackson2ObjectMapperBuilder.json();new RuleCheckPreviewConfiguration().ruleCheckStrictRequests().customize(builder);
        mvc=MockMvcBuilders.standaloneSetup(new RuleCheckController(service,settings(),new RuleCheckClarificationStore(Clock.systemUTC()),
            new RuleCheckCitationStore(Clock.systemUTC()),originals,identities))
            .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
            .defaultRequest(post("/").session(session))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(builder.build())).build();
    }
    @AfterEach void clearIdentity(){org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    static void authenticate(TrustedActor actor) {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(new PreviewPrincipal(actor),null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));
    }
    static RuleCheckRuntimeSettings settings(){return new RuleCheckRuntimeSettings("p","c","SYNTHETIC_SCOPE","2025.9.2",40,5);}
    static RuleCheckDecision.SupportedFact fact(String id,String text) {
        return new RuleCheckDecision.SupportedFact(id,text,List.of(new RuleCheckVerifiedExcerpt("cite-"+id,UUID.randomUUID(),UUID.randomUUID(),"2025.9.2","a".repeat(64),3,5,"原文")));
    }
    static Stream<RuleCheckDecision> states() {
        var a=fact("a","合成事实含条件与例外");var b=fact("b","另一合成证据侧");
        return Stream.of(
            new RuleCheckDecision(RuleCheckDecision.Status.ANSWERED,"2025.9.2",List.of(a),List.of(),List.of(),List.of()),
            new RuleCheckDecision(RuleCheckDecision.Status.PARTIALLY_ANSWERED,"2025.9.2",List.of(a),List.of(),List.of("无法确定的项目"),List.of()),
            new RuleCheckDecision(RuleCheckDecision.Status.NEED_CLARIFICATION,"2025.9.2",List.of(),List.of(new RuleCheckDecision.Clarification("type","哪一类型",List.of("ordinary","other"))),List.of(),List.of()),
            new RuleCheckDecision(RuleCheckDecision.Status.INSUFFICIENT_EVIDENCE,"2025.9.2",List.of(),List.of(),List.of("暂无已审核规则"),List.of()),
            new RuleCheckDecision(RuleCheckDecision.Status.CONFLICTING_EVIDENCE,"2025.9.2",List.of(),List.of(),List.of(),List.of(new RuleCheckDecision.ConflictEvidence("尚未解决的矛盾",List.of(a,b)))));
    }
    @ParameterizedTest @MethodSource("states") void preservesAllFiveDecisionsAndExplicitClaims(RuleCheckDecision decision) throws Exception {
        when(service.checkWithContext("中文问题",Map.of("year","A"))).thenReturn(scoped(decision,"中文问题",Map.of("year","A")));
        var result=mvc.perform(post(P).contentType("application/json").content("{\"question\":\"中文问题\",\"confirmedConditions\":{\"year\":\"A\"}}"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn();
        var response=JSON.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(response.path("status").asText()).isEqualTo(decision.status().name());
        assertThat(response.path("referenceRevision").asText()).isEqualTo("2025.9.2");
        assertThat(response.path("knowledgeScope").asText()).isEqualTo("SYNTHETIC_SCOPE");
        assertThat(response.path("conditionSource").asText()).isEqualTo("USER_DECLARED");
        assertThat(response.path("confirmedConditions").path("year").asText()).isEqualTo("A");
        assertThat(response.path("confirmedFacts").size()).isEqualTo(decision.confirmedFacts().size());
        assertThat(response.path("clarificationQuestions").size()).isEqualTo(decision.clarificationQuestions().size());
        assertThat(response.path("unknownItems").size()).isEqualTo(decision.unknownItems().size());
        assertThat(response.path("conflicts").size()).isEqualTo(decision.conflicts().size());
        if(!decision.confirmedFacts().isEmpty()) {
            var cite=response.path("confirmedFacts").get(0).path("citations").get(0);
            assertThat(cite.path("text").asText()).isEqualTo("原文");
            assertThat(cite.path("startInclusive").asInt()).isEqualTo(3);assertThat(cite.path("endExclusive").asInt()).isEqualTo(5);
            assertThat(cite.has("checksum")).isFalse();assertThat(cite.has("qualified")).isFalse();
        }
        verify(service).checkWithContext("中文问题",Map.of("year","A"));
    }
    @Test void unknownTrustedFieldsAreRejectedWithoutServiceInvocation() throws Exception {
        for(String field:List.of("userId","reviewer","qualified","snapshots","sourceId","ruleId"))bad("{\"question\":\"q\",\"confirmedConditions\":{},\""+field+"\":\"untrusted\"}");
        verifyNoInteractions(service);
    }
    @Test void requiredObjectAndStringShapesDoNotCoerceScalars() throws Exception {
        for(String body:List.of("null","[]","\"q\"","{}","{\"question\":\"q\"}",
            "{\"question\":12,\"confirmedConditions\":{}}","{\"question\":\"q\",\"confirmedConditions\":null}",
            "{\"question\":\"q\",\"confirmedConditions\":{\"x\":12}}","{\"question\":\"q\",\"confirmedConditions\":{\"x\":true}}",
            "{\"question\":\"q\",\"confirmedConditions\":{\"x\":null}}"))bad(body);
        verifyNoInteractions(service);
    }
    @Test void allBusinessInputBoundsAreCheckedBeforeService() throws Exception {
        bad(payload(" ",Map.of()));bad(payload("x".repeat(2049),Map.of()));
        bad(payload("q",Map.of(" ","a")));bad(payload("q",Map.of("x".repeat(65),"a")));bad(payload("q",Map.of("x","a".repeat(129))));
        var tooMany=new HashMap<String,String>();for(int i=0;i<17;i++)tooMany.put("k"+i,"a");bad(payload("q",tooMany));
        verifyNoInteractions(service);
    }
    @Test void duplicatePropertiesAndTrailingJsonAreRejected() throws Exception {
        for(String body:List.of("{\"question\":\"first\",\"question\":\"second\",\"confirmedConditions\":{}}",
            "{\"question\":\"q\",\"confirmedConditions\":{\"x\":\"a\",\"x\":\"b\"}}",
            "{\"question\":\"q\",\"confirmedConditions\":{}} {}"))bad(body);
        verifyNoInteractions(service);
    }
    @Test void invalidJsonAndServiceErrorsNeverEchoInputOrCauses() throws Exception {
        bad("{\"do-not-echo\":");
        when(service.checkWithContext("q",Map.of())).thenThrow(new IllegalStateException("do-not-echo private cause"));
        mvc.perform(post(P).contentType("application/json").content(payload("q",Map.of())))
            .andExpect(status().isServiceUnavailable()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("do-not-echo"))));
    }
    @Test void mediaTypeGuardRejectsFormsWithoutReadingBody() throws Exception {
        for(String type:List.of("application/x-www-form-urlencoded","text/plain","application/json; charset=UTF-16","application/problem+json")) {
            var request=new MockHttpServletRequest("POST","/api"+P);request.setContextPath("/api");request.setContentType(type);
            request.setContent("do-not-echo".getBytes(java.nio.charset.StandardCharsets.UTF_8));var response=new MockHttpServletResponse();
            var called=new java.util.concurrent.atomic.AtomicBoolean();
            new RuleCheckMediaTypeFilter().doFilter(request,response,(a,b)->called.set(true));
            assertThat(response.getStatus()).isEqualTo(415);assertThat(called).isFalse();
            assertThat(response.getContentAsString()).doesNotContain("do-not-echo");
            assertThat(request.getInputStream().read()).isEqualTo((int)'d');
        }
    }
    static RuleCheckScopedResult scoped(RuleCheckDecision decision,String question,Map<String,String> known) {
        if(decision.status()==RuleCheckDecision.Status.INSUFFICIENT_EVIDENCE)return new RuleCheckScopedResult(decision,null,null);
        var year=new RuleCheckRuleTemplate.Condition("year","哪一年级",List.of("A","B"));
        var type=new RuleCheckRuleTemplate.Condition("type","哪一类型",List.of("ordinary","other"));
        var when=new LinkedHashMap<String,Map<String,String>>();decision.confirmedFacts().forEach(f->when.put(f.id(),Map.of("year","A")));
        var scope=new RuleCheckScopedResult.Scope("fixture-rule","合成规则覆盖范围",List.of(year,type),when);
        RuleCheckScopedResult.Continuation context=null;
        if(decision.status()==RuleCheckDecision.Status.NEED_CLARIFICATION) {
            var quote=fact("pending","合成确认事实").evidence().getFirst();
            var binding=new RuleCheckRuleTemplate.EvidenceBinding(quote.sourceId(),quote.chunkId(),quote.revision(),quote.checksum(),quote.startInclusive(),quote.endExclusive(),quote.text());
            var rule=new RuleCheckRuleTemplate("fixture-rule","2025.9.2",RuleCheckRuleTemplate.ReviewState.APPROVED,"合成责任",LocalDate.of(2026,10,8),
                List.of(new RuleCheckRuleTemplate.Fact("pending","合成确认事实",false,Map.of("year","A","type","ordinary"),List.of(binding))),List.of(year,type),List.of(),List.of());
            context=new RuleCheckScopedResult.Continuation(settings(),new RuleCheckApplicationService.CatalogEntry(rule,"合成规则覆盖范围"),question,known,type);
        }
        return new RuleCheckScopedResult(decision,scope,context);
    }
    void clarificationService() {
        var need=states().filter(d->d.status()==RuleCheckDecision.Status.NEED_CLARIFICATION).findFirst().orElseThrow();
        when(service.check(anyString(),eq(Map.of("year","A")))).thenReturn(need);
        when(service.checkWithContext(anyString(),eq(Map.of("year","A")))).thenAnswer(i->scoped(need,i.getArgument(0),i.getArgument(1)));
        var answer=new RuleCheckDecision(RuleCheckDecision.Status.ANSWERED,"2025.9.2",List.of(fact("a","合成确认事实")),List.of(),List.of(),List.of());
        when(service.resume(any(),eq(Map.of("type","ordinary")))).thenReturn(scoped(answer,"q",Map.of("year","A","type","ordinary")));
    }
    String token(String question) throws Exception {
        var response=mvc.perform(post(P).contentType("application/json").content(payload(question,Map.of("year","A"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.clarificationToken").isString()).andReturn();
        var token=JSON.readTree(response.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("clarificationToken").asText();
        assertThat(token).isNotBlank();return token;
    }
    String continuation(String question,String token,Map<String,String> delta) throws Exception {return JSON.writeValueAsString(Map.of("question",question,"confirmedConditions",delta,"clarificationToken",token));}
    @Test void clarificationConsumesServerContextOnceAndReturnsMergedExplicitConditions() throws Exception {
        clarificationService();String token=token("q");String body=continuation("q",token,Map.of("type","ordinary"));
        mvc.perform(post(P).contentType("application/json").content(body)).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ANSWERED")).andExpect(jsonPath("$.confirmedConditions.year").value("A"))
            .andExpect(jsonPath("$.confirmedConditions.type").value("ordinary"));
        mvc.perform(post(P).contentType("application/json").content(body)).andExpect(status().isConflict());
        verify(service,times(1)).resume(any(),eq(Map.of("type","ordinary")));
    }
    @Test void clarificationCannotCrossAuthenticatedActors() throws Exception {
        clarificationService();String token=token("q");authenticate(new TrustedActor(UUID.randomUUID(),TrustedActor.Role.USER,0));
        mvc.perform(post(P).contentType("application/json").content(continuation("q",token,Map.of("type","ordinary")))).andExpect(status().isConflict());
        authenticate(actor);mvc.perform(post(P).contentType("application/json").content(continuation("q",token,Map.of("type","ordinary")))).andExpect(status().isConflict());
        verify(service,never()).resume(any(),any());
    }
    @Test void invalidDeltaOrDifferentQuestionKeepsCurrentClarificationCorrectable() throws Exception {
        clarificationService();String token=token("q");
        bad(continuation("q",token,Map.of("year","B")));bad(continuation("other question",token,Map.of("type","ordinary")));
        mvc.perform(post(P).contentType("application/json").content(continuation("q",token,Map.of("type","ordinary")))).andExpect(status().isOk());
        verify(service,times(1)).resume(any(),eq(Map.of("type","ordinary")));
    }
    @Test void beginningNewQuestionMakesEarlierTokenUnusable() throws Exception {
        clarificationService();String old=token("q");String current=token("new question");
        mvc.perform(post(P).contentType("application/json").content(continuation("q",old,Map.of("type","ordinary")))).andExpect(status().isConflict());
        mvc.perform(post(P).contentType("application/json").content(continuation("new question",current,Map.of("type","ordinary")))).andExpect(status().isOk());
        var saved=org.mockito.ArgumentCaptor.forClass(RuleCheckScopedResult.Continuation.class);
        verify(service).resume(saved.capture(),eq(Map.of("type","ordinary")));assertThat(saved.getValue().question()).isEqualTo("new question");
    }
    void bad(String body) throws Exception {
        var result=mvc.perform(post(P).contentType("application/json").content(body)).andExpect(status().isBadRequest())
            .andExpect(header().string("Cache-Control","no-store")).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("do-not-echo","untrusted");
    }
    static String payload(String question,Map<String,String> conditions) throws Exception {return JSON.writeValueAsString(Map.of("question",question,"confirmedConditions",conditions));}
    RuleCheckScopedResult answerService() {
        var decision=states().filter(d->d.status()==RuleCheckDecision.Status.ANSWERED).findFirst().orElseThrow();
        var result=scoped(decision,"q",Map.of());when(service.checkWithContext("q",Map.of())).thenReturn(result);return result;
    }
    String originalToken()throws Exception {
        answerService();var response=mvc.perform(post(P).contentType("application/json").content(payload("q",Map.of())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.confirmedFacts[0].citations[0].originalToken").isString()).andReturn();
        return JSON.readTree(response.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("confirmedFacts").get(0).path("citations").get(0).path("originalToken").asText();
    }
    @Test void issuedOpaqueReferenceOpensOriginalOnlyFromSameSession()throws Exception {
        String token=originalToken();assertThat(UUID.fromString(token).toString()).isEqualTo(token);
        mvc.perform(get(P+"/originals/"+token)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.pageNumberKind").value("RECORDED_SOURCE_PAGE")).andExpect(jsonPath("$.contextText").value("原文"));
        mvc.perform(get(P+"/originals/"+token).session(new MockHttpSession())).andExpect(status().isNotFound());
    }
    @Test void resultChangedBeforeFinalReleaseReturnsNoOldFacts()throws Exception {
        var result=answerService();when(service.isCurrent(result)).thenReturn(false);
        mvc.perform(post(P).contentType("application/json").content(payload("q",Map.of()))).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RULE_CHECK_CHANGED")).andExpect(jsonPath("$.confirmedFacts").doesNotExist());
    }
    @Test void postDecisionRevokedIdentityOrUnavailableIdentityNeverReturnsPolicyText()throws Exception {
        answerService();doThrow(new PreviewIdentityRepository.Failure(PreviewIdentityRepository.Code.AUTH_REVOKED)).when(identities).requireCurrent(actor);
        mvc.perform(post(P).contentType("application/json").content(payload("q",Map.of()))).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.confirmedFacts").doesNotExist());
        doThrow(new PreviewIdentityRepository.Failure(PreviewIdentityRepository.Code.IDENTITY_UNAVAILABLE)).when(identities).requireCurrent(actor);
        mvc.perform(post(P).contentType("application/json").content(payload("q",Map.of()))).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.confirmedFacts").doesNotExist());
    }
    @Test void sessionActorReplacementDuringDecisionCannotPublishOldResponse()throws Exception {
        var result=answerService();when(service.checkWithContext("q",Map.of())).thenAnswer(i->{
            var other=new TrustedActor(UUID.randomUUID(),TrustedActor.Role.USER,0);
            var context=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
            context.setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(new PreviewPrincipal(other),null,List.of()));
            session.setAttribute(org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,context);return result;});
        mvc.perform(post(P).contentType("application/json").content(payload("q",Map.of()))).andExpect(status().isConflict()).andExpect(jsonPath("$.confirmedFacts").doesNotExist());
        assertThat(session.isInvalid()).isFalse();
    }
    @Test void newQueryInvalidatesOriginalHandlesAndUnknownHandlesHaveNoText()throws Exception {
        String old=originalToken(),next=originalToken();assertThat(next).isNotEqualTo(old);
        mvc.perform(get(P+"/originals/"+old)).andExpect(status().isNotFound());
        mvc.perform(get(P+"/originals/"+UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get(P+"/originals/"+next)).andExpect(status().isOk());
    }
    @Test void originalRejectsClientParametersAndChangedCurrentReference()throws Exception {
        String token=originalToken();clearInvocations(originals);
        mvc.perform(get(P+"/originals/"+token).param("sourceId",UUID.randomUUID().toString())).andExpect(status().isBadRequest());verifyNoInteractions(originals);
        when(service.isReferenceApproved(any())).thenReturn(false);mvc.perform(get(P+"/originals/"+token)).andExpect(status().isConflict());
        when(service.isReferenceApproved(any())).thenReturn(true);doReturn(new RuleCheckOriginalEvidenceService.Result(RuleCheckOriginalEvidenceService.Code.UNAVAILABLE,null)).when(originals).locate(any());
        mvc.perform(get(P+"/originals/"+token)).andExpect(status().isConflict());
    }
    @Test void originalRechecksAfterIoAndRejectsLocatorOrIdentityChange()throws Exception {
        String token=originalToken();
        var quote=answerService().decision().confirmedFacts().getFirst().evidence().getFirst();
        var found=new RuleCheckOriginalEvidenceService.Result(RuleCheckOriginalEvidenceService.Code.FOUND,
            new RuleCheckOriginalEvidenceService.View(quote.citationId(),quote.sourceId(),quote.chunkId(),quote.revision(),"合成标题","合成条款",1,
                "RECORDED_SOURCE_PAGE",null,null,quote.text(),3,3,5,List.of()));
        doReturn(found,new RuleCheckOriginalEvidenceService.Result(RuleCheckOriginalEvidenceService.Code.UNAVAILABLE,null)).when(originals).locate(any());
        mvc.perform(get(P+"/originals/"+token)).andExpect(status().isConflict()).andExpect(jsonPath("$.contextText").doesNotExist());
        verify(originals,times(2)).locate(any());
        doReturn(found).when(originals).locate(any());
        doThrow(new PreviewIdentityRepository.Failure(PreviewIdentityRepository.Code.AUTH_REVOKED)).when(identities).requireCurrent(actor);
        mvc.perform(get(P+"/originals/"+token)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.contextText").doesNotExist());
    }
    void switchContext(TrustedActor current,boolean saveInSession) {
        var context=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(new PreviewPrincipal(current),null,List.of()));
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
        if(saveInSession)session.setAttribute(org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,context);
    }
    @Test void delayedOldActorCannotOverwriteAlreadyPublishedNewActorOriginalGeneration()throws Exception {
        var other=new TrustedActor(UUID.randomUUID(),TrustedActor.Role.USER,0);switchContext(other,true);
        String current=originalToken();clearInvocations(service);
        switchContext(actor,false); // 模拟入口通过后迟到的A请求，Session已经属于B。
        mvc.perform(post(P).contentType("application/json").content(payload("q",Map.of()))).andExpect(status().isConflict());
        switchContext(other,false);mvc.perform(get(P+"/originals/"+current)).andExpect(status().isOk());
        verify(service,never()).checkWithContext(anyString(),anyMap());
    }
    @Test void delayedOldActorCannotConsumeNewActorClarificationSlot()throws Exception {
        clarificationService();var other=new TrustedActor(UUID.randomUUID(),TrustedActor.Role.USER,0);switchContext(other,true);
        String current=token("q");switchContext(actor,false);
        mvc.perform(post(P).contentType("application/json").content(continuation("q",current,Map.of("type","ordinary")))).andExpect(status().isConflict());
        switchContext(other,false);
        mvc.perform(post(P).contentType("application/json").content(continuation("q",current,Map.of("type","ordinary")))).andExpect(status().isOk());
        verify(service,times(1)).resume(any(),anyMap());
    }
}
