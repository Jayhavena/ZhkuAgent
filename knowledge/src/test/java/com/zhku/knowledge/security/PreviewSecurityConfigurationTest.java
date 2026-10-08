package com.zhku.knowledge.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhku.knowledge.controlled.ControlledAuthController;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 真实框架过滤链+身份存储替身；不冒充实际监听或真库认证组合。 */
class PreviewSecurityConfigurationTest {
    private static final String P = "/zhku/controlled-preview/v1";
    private static final TrustedActor USER = new TrustedActor(UUID.randomUUID(), TrustedActor.Role.USER, 0);
    private static final TrustedActor MAINTAINER = new TrustedActor(UUID.randomUUID(), TrustedActor.Role.MAINTAINER, 0);

    @Test void csrfPrecedesAnonymousAuthenticationAndMeNeedsLogin() {
        run((mvc, repository) -> {
            mvc.perform(post(P + "/runs")).andExpect(status().isForbidden());
            mvc.perform(get(P + "/auth/me")).andExpect(status().isUnauthorized());
            mvc.perform(get(P + "/auth/csrf")).andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"));
            verifyNoInteractions(repository);
        });
    }
    @Test void loginKeepsOnlyTrustedPrincipalAndMaintainerDoesNotInheritUser() {
        run((mvc, repository) -> {
            var session = login(mvc, "user-a");
            mvc.perform(get(P + "/auth/me").session(session)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(USER.userId().toString()))
                    .andExpect(jsonPath("$.role").value("USER"));
            mvc.perform(post(P + "/runs").session(session).with(csrf())).andExpect(status().isOk());
            var maintenance = login(mvc, "maintainer");
            mvc.perform(post(P + "/runs").session(maintenance).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(get("/login").session(session)).andExpect(status().isForbidden());
            mvc.perform(get("/zhku/local-preview/campus-agent/v1/runs").session(session)).andExpect(status().isForbidden());
        });
    }
    @Test void wrongMethodIsDeniedBeforeControllerCanProduceSentinel() {
        run((mvc, repository) -> {
            var session = login(mvc, "user-a");
            mvc.perform(get(P + "/runs").session(session)).andExpect(status().isForbidden());
        });
    }
    @Test void frameworkPrincipalFromOtherAuthenticationSourceCannotBecomeTrustedActor() {
        run((mvc, repository) -> {
            mvc.perform(post(P + "/runs").with(user("foreign").roles("USER")).with(csrf()))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(repository);
        });
    }
    @Test void revokedIdentityClearsSessionAndDatabaseFailureDoesNotReachController() {
        run((mvc, repository) -> {
            var session = login(mvc, "user-a");
            doThrow(new PreviewIdentityRepository.Failure(PreviewIdentityRepository.Code.IDENTITY_UNAVAILABLE))
                    .when(repository).requireCurrent(USER);
            mvc.perform(get(P + "/auth/me").session(session)).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("IDENTITY_UNAVAILABLE"));
            doThrow(new PreviewIdentityRepository.Failure(PreviewIdentityRepository.Code.AUTH_REVOKED))
                    .when(repository).requireCurrent(USER);
            mvc.perform(post(P + "/runs").session(session)).andExpect(status().isForbidden());
            mvc.perform(get(P + "/auth/me").session(session)).andExpect(status().isUnauthorized());
            assertThat(session.isInvalid()).isTrue();
        });
    }
    @Test void loginRateLimitIncludesSuccessfulAttemptAndDoesNotReadDatabaseAfterTwenty() {
        var repository = mock(PreviewIdentityRepository.class);
        when(repository.authenticate(eq("user-a"), any())).thenReturn(Optional.of(new PreviewPrincipal(USER)));
        when(repository.authenticate(eq("unknown"), any())).thenReturn(Optional.empty());
        var instant = new AtomicReference<>(Instant.parse("2026-10-05T00:00:00Z"));
        var clock = new Clock() {
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId ignored) { return this; }
            public Instant instant() { return instant.get(); }
        };
        var provider = new PreviewSecurityConfiguration.Provider(repository, clock);
        provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("user-a", "synthetic-password-value"));
        for (int i=0;i<19;i++) {
            assertThatThrownBy(() -> provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("unknown", "synthetic-password-value")))
                    .isInstanceOf(BadCredentialsException.class);
        }
        assertThatThrownBy(() -> provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("user-a", "synthetic-password-value")))
                .isInstanceOf(InternalAuthenticationServiceException.class).hasMessage("LOGIN_RATE_LIMIT");
        verify(repository, times(1)).authenticate(eq("user-a"), any());
        verify(repository, times(19)).authenticate(eq("unknown"), any());
        instant.set(instant.get().plusSeconds(60));
        provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("user-a", "synthetic-password-value"));
        verify(repository, times(2)).authenticate(eq("user-a"), any());
    }
    @Test void oneHttpLoginConsumesExactlyOneAttempt() {
        run((mvc, repository) -> {
            for (int i=0;i<20;i++) {
                mvc.perform(post(P + "/auth/login").param("username", "unknown")
                        .param("password", "synthetic-password-value").with(csrf()))
                        .andExpect(status().isUnauthorized());
            }
            mvc.perform(post(P + "/auth/login").param("username", "unknown")
                    .param("password", "synthetic-password-value").with(csrf()))
                    .andExpect(status().isTooManyRequests());
            verify(repository, times(20)).authenticate(eq("unknown"), any());
        });
    }
    @Test void logoutInvalidatesSessionAndUsesRestrictedCookiePath() {
        run((mvc, repository) -> {
            var session = login(mvc, "user-a");
            mvc.perform(post(P + "/auth/logout").session(session).with(csrf()))
                    .andExpect(status().isNoContent())
                    .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Path=/api" + P)))
                    .andExpect(header().doesNotExist("Location"));
            assertThat(session.isInvalid()).isTrue();
        });
    }
    @Test void identityHeaderDoesNotAuthenticateAnonymousRequest() {
        run((mvc, repository) -> {
            mvc.perform(get(P + "/auth/me").header("userId", USER.userId().toString())
                    .header("role", "USER")).andExpect(status().isUnauthorized());
            verifyNoInteractions(repository);
        });
    }

    private static MockHttpSession login(MockMvc mvc, String name) throws Exception {
        return (MockHttpSession)mvc.perform(post(P + "/auth/login").param("username", name)
                .param("password", "synthetic-password-value").with(csrf())).andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location")).andReturn().getRequest().getSession(false);
    }
    @FunctionalInterface private interface Check { void run(MockMvc mvc, PreviewIdentityRepository repository) throws Exception; }
    private static void run(Check check) {
        new WebApplicationContextRunner().withUserConfiguration(PreviewSecurityConfiguration.class, Fixture.class,
                ControlledAuthController.class, SentinelController.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("zhku-controlled-preview"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
                    check.run(mvc, context.getBean(PreviewIdentityRepository.class));
                });
    }
    @Configuration(proxyBeanMethods=false) @EnableWebMvc
    static class Fixture {
        @Bean ObjectMapper mapper() { return new ObjectMapper(); }
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean PreviewIdentityRepository repository() {
            var repository = mock(PreviewIdentityRepository.class);
            when(repository.authenticate(anyString(), any())).thenAnswer(invocation -> {
                Arrays.fill((char[])invocation.getArgument(1), '\0');
                String name = invocation.getArgument(0);
                return name.equals("user-a") ? Optional.of(new PreviewPrincipal(USER))
                        : name.equals("maintainer") ? Optional.of(new PreviewPrincipal(MAINTAINER)) : Optional.empty();
            });
            return repository;
        }
    }
    @RestController
    static class SentinelController {
        @RequestMapping(value=P+"/runs", method={RequestMethod.GET, RequestMethod.POST})
        String run() { return "METHOD_GUARD_SENTINEL"; }
    }
}
