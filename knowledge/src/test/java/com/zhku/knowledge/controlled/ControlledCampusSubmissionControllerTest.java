package com.zhku.knowledge.controlled;

import com.fasterxml.jackson.databind.*;
import com.zhku.knowledge.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.Clock;
import java.util.*;

class ControlledCampusSubmissionControllerTest {
    static final String P="/zhku/controlled-preview/v1/runs";
    @Test void missingOrMalformedKeysNeverReachAccessAndBodyLimitFollowsAuthorization() {
        new WebApplicationContextRunner().withUserConfiguration(PreviewSecurityConfiguration.class,Fixture.class,
                ControlledAuthController.class,ControlledCampusAgentController.class,ControlledPreviewExceptionHandler.class)
                .withInitializer(c->c.getEnvironment().setActiveProfiles("zhku-controlled-preview")).run(context->{
            var mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            var access=context.getBean(CampusRunAccessService.class);
            String huge=" ".repeat(16385),body="{\"question\":\"合成\",\"courses\":[]}";
            mvc.perform(post(P).with(csrf()).contentType("application/json").content(huge)).andExpect(status().isUnauthorized());
            mvc.perform(post(P).contentType("application/json").content(huge)).andExpect(status().isForbidden());
            var login=mvc.perform(post("/zhku/controlled-preview/v1/auth/login").with(csrf()).param("username","user-a").param("password","synthetic-test-password"))
                    .andExpect(status().isOk()).andReturn();
            var session=(org.springframework.mock.web.MockHttpSession)login.getRequest().getSession(false);
            mvc.perform(post(P).session(session).with(csrf()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
            for(String key:List.of("wrong","AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA","00000000-0000-1000-8000-000000000001"))
                mvc.perform(post(P).session(session).with(csrf()).header("Idempotency-Key",key).contentType("application/json").content(body)).andExpect(status().isBadRequest());
            mvc.perform(post(P).session(session).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(huge))
                    .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("REQUEST_TOO_LARGE"));
            verifyNoInteractions(access);
        });
    }
    @Configuration(proxyBeanMethods=false) @EnableWebMvc static class Fixture {
        @Bean ObjectMapper mapper(){return new ObjectMapper().findAndRegisterModules();}
        @Bean Clock clock(){return Clock.systemUTC();}
        @Bean CampusRunAccessService access(){return mock(CampusRunAccessService.class);}
        @Bean PreviewIdentityRepository repository(){var repo=mock(PreviewIdentityRepository.class);
            when(repo.authenticate(anyString(),any())).thenReturn(Optional.of(new PreviewPrincipal(new TrustedActor(UUID.randomUUID(),TrustedActor.Role.USER,0))));return repo;}
    }
}
