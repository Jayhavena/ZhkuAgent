package com.zhku.knowledge.web;

import com.zhku.knowledge.campusagent.CampusCreditAgentManager;
import com.zhku.knowledge.campusagent.archive.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.Instant;
import java.util.*;
import static com.zhku.knowledge.campusagent.archive.CampusRunArchive.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CampusAgentHistoryPreviewControllerTest {
    private static final String BASE = "/zhku/local-preview/campus-agent/v1/history";
    private final CampusRunArchiveStore store = mock(CampusRunArchiveStore.class);
    private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new CampusAgentHistoryPreviewController(new CampusRunHistoryService(store))).build();

    @Test
    void withdrawalHidesResultWithoutChangingPastSuccess() throws Exception {
        var id = UUID.randomUUID();
        var now = Instant.EPOCH;
        var facts = new FinalFacts(id, com.zhku.agent.agent.runtime.AgentRunState.SUCCEEDED,
                com.zhku.agent.agent.runtime.AgentStopReason.NONE,
                com.zhku.agent.agent.runtime.contract.AgentRunSnapshot.EndReason.COMPLETED, 2, null, null, "ANSWERED",
                "NONE", CleanupOutcome.SUCCESS, now);
        var archive = new CampusRunArchive(id, 1, WRITER_VERSION, ArchiveStatus.FINALIZED,
                new OwnerToken(UUID.randomUUID(), 1), 1,
                now, now, now, now.plusSeconds(604800), facts, PayloadDisposition.STORED, Reason.NONE, null);
        when(store.find(id)).thenReturn(Optional
                .of(new Read(archive, new Visibility(VisibilityStatus.HIDDEN, Reason.SOURCE_NOT_VISIBLE), null)));
        mvc.perform(get(BASE + "/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.execution.state").value("SUCCEEDED"))
                .andExpect(jsonPath("$.result").doesNotExist())
                .andExpect(jsonPath("$.resultVisibility.status").value("HIDDEN"))
                .andExpect(jsonPath("$.terminated").doesNotExist()).andExpect(jsonPath("$.owner").doesNotExist())
                .andExpect(jsonPath("$.revision").doesNotExist())
                .andExpect(jsonPath("$.provenance").value("REFERENCE_ONLY"));
    }

    @Test
    void missingAndReadFailureHaveDistinctFixedHttpStatus() throws Exception {
        var id = UUID.randomUUID();
        when(store.find(id)).thenReturn(Optional.empty());
        mvc.perform(get(BASE + "/" + id)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ARCHIVE_NOT_FOUND"));
        when(store.find(id)).thenThrow(new Failure(Code.ARCHIVE_READ_UNAVAILABLE));
        mvc.perform(get(BASE + "/" + id)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ARCHIVE_READ_UNAVAILABLE"));
    }

    @Test
    void invalidInputsAre400AndNoHistoryMutationRoutesExist() throws Exception {
        mvc.perform(get(BASE + "/not-a-uuid")).andExpect(status().isBadRequest());
        for (var limit : List.of("0", "101", "invalid"))
            mvc.perform(get(BASE).param("limit", limit)).andExpect(status().isBadRequest());
        mvc.perform(get(BASE).param("cursor", "!invalid!")).andExpect(status().isBadRequest());
        mvc.perform(post(BASE)).andExpect(status().isMethodNotAllowed());
        mvc.perform(post(BASE + "/" + UUID.randomUUID() + "/cancel")).andExpect(status().isNotFound());
    }

    @Test
    void onlineAdmissionFailuresUseExistingErrorReplyAndOldCache404StillStays404() throws Exception {
        var manager = mock(CampusCreditAgentManager.class);
        when(manager.start(any())).thenThrow(new Failure(Code.ARCHIVE_ADMISSION_UNCONFIRMED));
        when(manager.find(any())).thenThrow(new IllegalArgumentException("missing"));
        when(manager.cancel(any())).thenThrow(new IllegalArgumentException("missing"));
        var online = MockMvcBuilders.standaloneSetup(new CampusAgentPreviewController(manager)).build();
        var base = "/zhku/local-preview/campus-agent/v1/runs";
        online.perform(post(base).contentType("application/json").content("{\"question\":\"合成追问\",\"courses\":[]}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ARCHIVE_ADMISSION_UNCONFIRMED"));
        online.perform(get(base + "/" + UUID.randomUUID())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RUN_NOT_FOUND"));
        online.perform(post(base + "/" + UUID.randomUUID() + "/cancel")).andExpect(status().isNotFound());
    }
}
