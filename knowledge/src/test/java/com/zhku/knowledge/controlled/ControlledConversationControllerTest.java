package com.zhku.knowledge.controlled;

import com.fasterxml.jackson.databind.*;
import com.zhku.knowledge.conversation.*;
import com.zhku.knowledge.security.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 实际4A过滤链+业务门面替身；不冒充真实HTTP/JDBC组合。 */
class ControlledConversationControllerTest {
    static final String P="/zhku/controlled-preview/v1/conversations";
    static final UUID ID=UUID.randomUUID();
    static final TrustedActor USER=new TrustedActor(UUID.randomUUID(),TrustedActor.Role.USER,0);
    static final TrustedActor M=new TrustedActor(UUID.randomUUID(),TrustedActor.Role.MAINTAINER,0);
    @Test void anonymousCsrfAndAuthenticationRejectBeforeFacade() {
        run(true,(mvc,facade)->{
            mvc.perform(post(P).contentType("application/json").content("{}")).andExpect(status().isForbidden());
            mvc.perform(get(P+"/"+ID)).andExpect(status().isUnauthorized());
            verifyNoInteractions(facade);
        });
    }
    @Test void fourJsonMethodsUseOnlyFrameworkPrincipalAndSixStateFields() {
        run(true,(mvc,facade)->{
            var session=login(mvc,"user-a");
            mvc.perform(post(P).session(session).with(csrf()).contentType("application/json").content("{}"))
                    .andExpect(status().isCreated()).andExpect(header().string("Cache-Control","no-store"))
                    .andExpect(jsonPath("$.sessionId").value(ID.toString())).andExpect(jsonPath("$.owner_user_id").doesNotExist());
            mvc.perform(get(P+"/"+ID).session(session).header("userId",M.userId().toString()).header("role","MAINTAINER"))
                    .andExpect(status().isOk());
            mvc.perform(post(P+"/"+ID+"/messages").session(session).with(csrf()).contentType("application/json")
                    .content("{\"message\":\"专业选修课\",\"expectedRevision\":0}")).andExpect(status().isOk());
            mvc.perform(post(P+"/"+ID+"/cancel").session(session).with(csrf()).contentType("application/json")
                    .content("{\"expectedRevision\":0}")).andExpect(status().isOk());
            verify(facade).createConversation(USER); verify(facade).findConversation(USER,ID);
            verify(facade).sendMessage(USER,ID,0,"专业选修课"); verify(facade).cancelConversation(USER,ID,0);
        });
    }
    @Test void unknownIdentityTtlAndHistoryFieldsAndMissingRevisionAreBadRequests() {
        run(true,(mvc,facade)->{
            var session=login(mvc,"user-a");
            for(String body:List.of("{}","{\"message\":\"专业选修课\"}","{\"message\":\"专业选修课\",\"expectedRevision\":-1}",
                    "{\"message\":\"专业选修课\",\"expectedRevision\":0,\"userId\":\"forged\"}",
                    "{\"message\":\"专业选修课\",\"expectedRevision\":0,\"role\":\"USER\"}",
                    "{\"message\":\"专业选修课\",\"expectedRevision\":0,\"authRevision\":0}",
                    "{\"message\":\"专业选修课\",\"expectedRevision\":0,\"expiresAt\":\"later\"}",
                    "{\"message\":\"专业选修课\",\"expectedRevision\":0,\"history\":[]}"))
                mvc.perform(post(P+"/"+ID+"/messages").session(session).with(csrf()).contentType("application/json").content(body))
                        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.answer").doesNotExist());
            mvc.perform(post(P).session(session).with(csrf()).contentType("application/json").content("{\"userId\":\"forged\"}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(post(P+"/"+ID+"/cancel").session(session).with(csrf()).contentType("application/json").content("{}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(get(P+"/bad-uuid").session(session)).andExpect(status().isBadRequest());
            verifyNoInteractions(facade);
        });
    }
    @Test void maintainerNeverInheritsConversationPermission() {
        run(true,(mvc,facade)->{
            var session=login(mvc,"maintainer");
            mvc.perform(get(P+"/"+ID).session(session)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ROLE_DENIED"));
            verifyNoInteractions(facade);
        });
    }
    @Test void localStateErrorsAreFiniteAndNeverContainStateOrAnswer() {
        run(true,(mvc,facade)->{
            var session=login(mvc,"user-a");
            for(var code:ControlledConversationAccessStore.Code.values()) {
                doThrow(new ControlledConversationAccessStore.Failure(code)).when(facade).findConversation(USER,ID);
                int expected=code==ControlledConversationAccessStore.Code.SESSION_NOT_FOUND?404:code==ControlledConversationAccessStore.Code.SESSION_EXPIRED?410:409;
                mvc.perform(get(P+"/"+ID).session(session)).andExpect(status().is(expected)).andExpect(jsonPath("$.code").value(code.name()))
                        .andExpect(jsonPath("$.state").doesNotExist()).andExpect(jsonPath("$.answer").doesNotExist());
            }
        });
    }
    @Test void unavailableAndUnknownCommitAre503WithoutGuessingFailedWrite() {
        run(true,(mvc,facade)->{
            var session=login(mvc,"user-a");
            for(var code:List.of(ConversationStoreFailure.Code.STORE_UNAVAILABLE,ConversationStoreFailure.Code.CREATION_UNCONFIRMED,
                    ConversationStoreFailure.Code.WRITE_UNCONFIRMED)) {
                doThrow(new ConversationStoreFailure(code)).when(facade).createConversation(USER);
                String expected=code==ConversationStoreFailure.Code.STORE_UNAVAILABLE?"SESSION_STORE_UNAVAILABLE":"SESSION_"+code.name();
                mvc.perform(post(P).session(session).with(csrf()).contentType("application/json").content("{}"))
                        .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value(expected))
                        .andExpect(jsonPath("$.sessionId").doesNotExist()).andExpect(jsonPath("$.state").doesNotExist());
            }
        });
    }
    @Test void listSseAndWrongMethodsStayDeniedAndTextEventStreamCannotProduceReply() {
        run(true,(mvc,facade)->{
            var session=login(mvc,"user-a");
            mvc.perform(get(P).session(session)).andExpect(status().isForbidden());
            mvc.perform(get(P+"/"+ID+"/stream").session(session)).andExpect(status().isForbidden());
            mvc.perform(delete(P+"/"+ID).session(session).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(get(P+"/"+ID).session(session).accept("text/event-stream")).andExpect(status().isNotAcceptable());
            verifyNoInteractions(facade);
        });
    }
    @Test void defaultFalseHasNoConversationControllerOrRoutes() {
        run(false,(mvc,facade)->{
            var session=login(mvc,"user-a");
            mvc.perform(get(P+"/"+ID).session(session)).andExpect(status().isForbidden());
            mvc.perform(post(P).session(session).with(csrf()).contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden()); verifyNoInteractions(facade);
        });
    }
    @Test void explicit3bGuardRetains4aIndependentRejectionAndRejectsUnsafeTargets() {
        var environment=new org.springframework.mock.env.MockEnvironment().withProperty("zhku.controlled-preview.enabled","true")
                .withProperty("zhku.controlled-preview.conversations.enabled","true").withProperty("server.address","127.0.0.1")
                .withProperty("zhku.campus-agent.enabled","true").withProperty("zhku.campus-agent.archive.enabled","true")
                .withProperty("zhku.db.url","jdbc:postgresql://127.0.0.1:55436/zhkuagent_run_archive_e2e").withProperty("zhku.db.username","zhkuagent");
        environment.setActiveProfiles("zhku-controlled-preview");
        assertThatThrownBy(()->ControlledPreviewConfiguration.validateEnvironment(environment)).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(()->ControlledConversationConfiguration.validateEnvironment(environment)).doesNotThrowAnyException();
        environment.withProperty("zhku.db.url","jdbc:postgresql://127.0.0.1:54329/zhkuagent");
        assertThatThrownBy(()->ControlledConversationConfiguration.validateEnvironment(environment)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void ownedSqlRevocationDuringRequestClearsCurrentLoginImmediately() {
        run(true,(mvc,facade)->{
            var session=login(mvc,"user-a");
            doThrow(new ConversationStoreFailure(ConversationStoreFailure.Code.AUTH_REVOKED)).when(facade).findConversation(USER,ID);
            mvc.perform(get(P+"/"+ID).session(session)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REVOKED"))
                    .andExpect(header().string("Set-Cookie",org.hamcrest.Matchers.containsString("Path=/api/zhku/controlled-preview/v1")))
                    .andExpect(header().string("Set-Cookie",org.hamcrest.Matchers.containsString("Max-Age=0")));
            assertThat(session.isInvalid()).isTrue();
        });
    }
    @Test void facadeIdentityRevocationDuringRequestAlsoClearsCurrentLogin() {
        run(true,(mvc,facade)->{
            var session=login(mvc,"user-a");
            doThrow(new PreviewIdentityRepository.Failure(PreviewIdentityRepository.Code.AUTH_REVOKED)).when(facade).findConversation(USER,ID);
            mvc.perform(get(P+"/"+ID).session(session)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_REVOKED"));
            assertThat(session.isInvalid()).isTrue();
        });
    }
    @Test void direct3bConfigurationFailsBeforeResourcesWithoutSingleExplicitOwner() {
        var environment=new org.springframework.mock.env.MockEnvironment().withProperty("zhku.controlled-preview.enabled","true")
                .withProperty("zhku.controlled-preview.conversations.enabled","true").withProperty("server.address","127.0.0.1")
                .withProperty("zhku.campus-agent.enabled","true").withProperty("zhku.campus-agent.archive.enabled","true")
                .withProperty("zhku.db.url","jdbc:postgresql://127.0.0.1:55436/zhkuagent_run_archive_e2e").withProperty("zhku.db.username","zhkuagent");
        environment.setActiveProfiles("zhku-controlled-preview");
        var guard=ControlledPreviewConfiguration.controlledPreviewEarlyGuard(environment);
        assertThatThrownBy(()->guard.postProcessBeanFactory(new org.springframework.beans.factory.support.DefaultListableBeanFactory()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("3B开关要求显式受控会话装配");
    }
    @FunctionalInterface interface Check { void run(MockMvc mvc,ControlledConversationFacade facade) throws Exception; }
    static void run(boolean enabled,Check check) {
        new WebApplicationContextRunner().withUserConfiguration(PreviewSecurityConfiguration.class,Fixture.class,ControlledAuthController.class,
                ControlledConversationController.class).withInitializer(context->context.getEnvironment().setActiveProfiles("zhku-controlled-preview"))
                .withPropertyValues("zhku.controlled-preview.conversations.enabled="+enabled).run(context->{
                    assertThat(context).hasNotFailed();
                    if(!enabled) assertThat(context).doesNotHaveBean(ControlledConversationController.class);
                    check.run(MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(),context.getBean(ControlledConversationFacade.class));
                });
    }
    static MockHttpSession login(MockMvc mvc,String name) throws Exception {
        return (MockHttpSession)mvc.perform(post("/zhku/controlled-preview/v1/auth/login").param("username",name)
                .param("password","synthetic-test-password").with(csrf())).andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    @Configuration(proxyBeanMethods=false) @EnableWebMvc static class Fixture implements org.springframework.web.servlet.config.annotation.WebMvcConfigurer {
        @Bean ObjectMapper mapper() { return new ObjectMapper().findAndRegisterModules().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES); }
        @Override public void configureMessageConverters(java.util.List<org.springframework.http.converter.HttpMessageConverter<?>> converters) {
            // 独立EnableWebMvc夹具不会自动采用Boot的严格Mapper，必须绑定实际转换器才能测未知字段。
            converters.add(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(mapper()));
        }
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean PreviewIdentityRepository identities() {
            var repository=mock(PreviewIdentityRepository.class);
            when(repository.authenticate(anyString(),any())).thenAnswer(invocation->{
                Arrays.fill((char[])invocation.getArgument(1),'\0');
                return Optional.of(new PreviewPrincipal("maintainer".equals(invocation.getArgument(0))?M:USER));
            }); return repository;
        }
        @Bean ControlledConversationFacade facade() {
            var facade=mock(ControlledConversationFacade.class); var state=ConversationState.active(ID,Instant.now().plusSeconds(600));
            when(facade.createConversation(USER)).thenReturn(state); when(facade.findConversation(USER,ID)).thenReturn(state);
            when(facade.cancelConversation(USER,ID,0)).thenReturn(state); return facade;
        }
    }
}
