package com.zhku.knowledge.controlled;

import com.zhku.agent.agent.runtime.AgentRunState;
import com.zhku.agent.agent.runtime.AgentStopReason;
import com.zhku.agent.agent.runtime.contract.AgentRunSnapshot.EndReason;
import com.zhku.knowledge.campusagent.CampusCreditAgentManager;
import com.zhku.knowledge.campusagent.CampusCreditAgentService;
import com.zhku.knowledge.campusagent.archive.CampusRunArchive;
import com.zhku.knowledge.campusagent.archive.CampusRunArchiveStore;
import com.zhku.knowledge.security.PreviewIdentityRepository;
import com.zhku.knowledge.security.TrustedActor;
import com.zhku.knowledge.web.LocalPreviewConversationFacade.ConversationAnswer;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.zhku.knowledge.campusagent.archive.CampusRunArchive.*;
import static com.zhku.knowledge.controlled.CampusRunAccessService.ResultVisibility.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 存储/身份替身投影测试；HTTP和当前资料许可真库另验收。 */
class CampusRunAccessServiceTest {
    private final TrustedActor actor=new TrustedActor(UUID.randomUUID(),TrustedActor.Role.USER,0);
    private final UUID id=UUID.randomUUID();
    private final CampusCreditAgentManager manager=mock(CampusCreditAgentManager.class);
    private final CampusRunArchiveStore archive=mock(CampusRunArchiveStore.class);
    private final PreviewIdentityRepository identities=mock(PreviewIdentityRepository.class);
    private final CampusRunAccessService access=new CampusRunAccessService(manager,archive,identities);
    private static final String CACHE="PRIVATE_MEMORY_SENTINEL";
    private CampusCreditAgentManager.Snapshot completed(){
        return new CampusCreditAgentManager.Snapshot(id,AgentRunState.SUCCEEDED,AgentStopReason.NONE,true,
                CampusCreditAgentService.Status.ANSWERED,CACHE,"NONE",
                List.of(new CampusCreditAgentService.Step(1,"LOOKUP","SUCCESS",CACHE)),source());
    }
    private ConversationAnswer source(){return new ConversationAnswer("合成规则","合成依据",UUID.randomUUID(),
            "合成政策","synthetic-v1","合成第1条","本科生");}
    private Read finalized(boolean visible){
        Instant at=Instant.now();
        var facts=new FinalFacts(id,AgentRunState.SUCCEEDED,AgentStopReason.NONE,EndReason.COMPLETED,2,null,null,
                "ANSWERED","NONE",CleanupOutcome.SUCCESS,at);
        var record=new CampusRunArchive(id,1,WRITER_VERSION,ArchiveStatus.FINALIZED,
                new OwnerToken(UUID.randomUUID(),1),1,at,at,at,at.plusSeconds(86400),facts,
                PayloadDisposition.STORED,Reason.NONE,null);
        return new Read(record,new Visibility(visible?VisibilityStatus.VISIBLE:VisibilityStatus.HIDDEN,
                visible?Reason.NONE:Reason.SOURCE_NOT_VISIBLE),visible?new PayloadCandidate("CONFIRMED_ARCHIVE_SENTINEL",source()):null);
    }
    private Read admitted(){
        return new Read(new CampusRunArchive(id,1,WRITER_VERSION,ArchiveStatus.ADMITTED,new OwnerToken(UUID.randomUUID(),1),0,
                Instant.now(),null,null,null,null,null,null,null),
                new Visibility(VisibilityStatus.NOT_STORED,Reason.NO_RESULT_FOR_OUTCOME),null);
    }
    @Test void visibleAnswerOnlyUsesConfirmedArchiveAndFreeFormStepsAreRemoved(){
        when(manager.findOwned(actor,id)).thenReturn(completed());
        var confirmed=finalized(true);when(archive.findOwned(actor,id)).thenReturn(Optional.of(confirmed));
        var reply=access.find(actor,id);
        assertThat(reply.resultVisibility()).isEqualTo(VISIBLE);
        assertThat(reply.run().answer()).isEqualTo(confirmed.result().answer()).doesNotContain(CACHE);
        assertThat(reply.run().source()).isEqualTo(confirmed.result().source());
        assertThat(reply.run().steps()).containsExactly(new CampusCreditAgentService.Step(1,"LOOKUP","SUCCESS",null));
        assertThat(reply.run().state()).isEqualTo(AgentRunState.SUCCEEDED);assertThat(reply.run().terminated()).isTrue();
    }
    @Test void hiddenMaterialKeepsSuccessfulExecutionButNeverUsesCachedText(){
        when(manager.findOwned(actor,id)).thenReturn(completed());when(archive.findOwned(actor,id)).thenReturn(Optional.of(finalized(false)));
        var reply=access.find(actor,id);assertThat(reply.resultVisibility()).isEqualTo(HIDDEN);
        assertThat(reply.run().answer()).isNull();assertThat(reply.run().source()).isNull();
        assertThat(reply.run().steps().getFirst().detail()).isNull();assertThat(reply.run().state()).isEqualTo(AgentRunState.SUCCEEDED);
        assertThat(reply.run().terminated()).isTrue();
    }
    @Test void publishedMemoryResultWithAdmittedArchiveIsUnconfirmed(){
        when(manager.findOwned(actor,id)).thenReturn(completed());when(archive.findOwned(actor,id)).thenReturn(Optional.of(admitted()));
        var reply=access.find(actor,id);assertThat(reply.resultVisibility()).isEqualTo(UNCONFIRMED);
        assertThat(reply.run().answer()).isNull();assertThat(reply.run().source()).isNull();assertThat(reply.run().terminated()).isTrue();
    }
    @Test void archiveReadFailureNeverFallsBackToSuccessfulMemory(){
        when(manager.findOwned(actor,id)).thenReturn(completed());when(archive.findOwned(actor,id)).thenThrow(new Failure(Code.ARCHIVE_READ_UNAVAILABLE));
        assertThatThrownBy(()->access.find(actor,id)).isInstanceOf(Failure.class);
        verify(manager,times(1)).findOwned(actor,id);
    }
    @Test void confirmedStartWithFailedProjectionReportsAcceptedIdWithoutDispatchingAgain(){
        var request=new CampusCreditAgentService.Request("公选课学分",List.of());
        when(manager.startOwned(request,actor)).thenReturn(completed());when(archive.findOwned(actor,id)).thenThrow(new Failure(Code.ARCHIVE_READ_UNAVAILABLE));
        assertThatThrownBy(()->access.start(actor,request)).isInstanceOf(CampusRunAccessService.AcceptedResultUnavailable.class)
                .extracting(e->((CampusRunAccessService.AcceptedResultUnavailable)e).runId()).isEqualTo(id);
        verify(manager,times(1)).startOwned(request,actor);verify(manager,never()).start(any());
    }
    @Test void unknownAdmissionDoesNotClaimAcceptedResult(){
        var request=new CampusCreditAgentService.Request("公选课学分",List.of());
        when(manager.startOwned(request,actor)).thenThrow(new Failure(Code.ARCHIVE_ADMISSION_UNCONFIRMED));
        assertThatThrownBy(()->access.start(actor,request)).isInstanceOf(Failure.class)
                .extracting(e->((Failure)e).code()).isEqualTo(Code.ARCHIVE_ADMISSION_UNCONFIRMED);
        verifyNoInteractions(archive);
    }
    @Test void invalidCurrentIdentityAndMaintainerRejectBeforeManagerOrArchive(){
        doThrow(new PreviewIdentityRepository.Failure(PreviewIdentityRepository.Code.AUTH_REVOKED)).when(identities).requireCurrent(actor);
        assertThatThrownBy(()->access.find(actor,id)).isInstanceOf(PreviewIdentityRepository.Failure.class);
        var maintainer=new TrustedActor(actor.userId(),TrustedActor.Role.MAINTAINER,0);
        assertThatThrownBy(()->access.cancel(maintainer,id)).isInstanceOf(PreviewIdentityRepository.Failure.class)
                .extracting(e->((PreviewIdentityRepository.Failure)e).code()).isEqualTo(PreviewIdentityRepository.Code.ROLE_DENIED);
        verifyNoInteractions(manager,archive);verify(identities,never()).requireCurrent(maintainer);
    }
    @Test void runningAndNonAnswerResultsDoNotExposeAnyUnarchivedText(){
        var running=new CampusCreditAgentManager.Snapshot(id,AgentRunState.RUNNING,AgentStopReason.NONE,false,null,CACHE,null,List.of(),null);
        when(manager.findOwned(actor,id)).thenReturn(running);when(archive.findOwned(actor,id)).thenReturn(Optional.of(admitted()));
        assertThat(access.find(actor,id).resultVisibility()).isEqualTo(RUNNING);assertThat(access.find(actor,id).run().answer()).isNull();
        var clarifying=new CampusCreditAgentManager.Snapshot(id,AgentRunState.INCOMPLETE,AgentStopReason.NONE,true,
                CampusCreditAgentService.Status.NEED_CLARIFICATION,CACHE,"NONE",List.of(),null);
        when(manager.findOwned(actor,id)).thenReturn(clarifying);
        Instant at=Instant.now();
        var facts=new FinalFacts(id,AgentRunState.INCOMPLETE,AgentStopReason.NONE,EndReason.DOMAIN_DECISION,1,null,null,
                "NEED_CLARIFICATION","NONE",CleanupOutcome.SUCCESS,at);
        var confirmed=new CampusRunArchive(id,1,WRITER_VERSION,ArchiveStatus.FINALIZED,new OwnerToken(UUID.randomUUID(),1),1,
                at,at,at,at.plusSeconds(86400),facts,PayloadDisposition.NOT_APPLICABLE,Reason.NO_RESULT_FOR_OUTCOME,null);
        when(archive.findOwned(actor,id)).thenReturn(Optional.of(new Read(confirmed,
                new Visibility(VisibilityStatus.NOT_STORED,Reason.NO_RESULT_FOR_OUTCOME),null)));
        assertThat(access.find(actor,id).resultVisibility()).isEqualTo(NOT_APPLICABLE);assertThat(access.find(actor,id).run().answer()).isNull();
    }
    @Test void cancellationRetainsActualStoppingAndTerminationFacts(){
        var stopping=new CampusCreditAgentManager.Snapshot(id,AgentRunState.STOP_REQUESTED,AgentStopReason.USER_REQUEST,false,null,CACHE,null,List.of(),null);
        when(manager.cancelOwned(actor,id)).thenReturn(stopping);when(archive.findOwned(actor,id)).thenReturn(Optional.of(admitted()));
        var reply=access.cancel(actor,id);assertThat(reply.run().state()).isEqualTo(AgentRunState.STOP_REQUESTED);
        assertThat(reply.run().terminated()).isFalse();assertThat(reply.run().stopReason()).isEqualTo(AgentStopReason.USER_REQUEST);
        assertThat(reply.run().answer()).isNull();verify(manager,never()).cancel(any());
    }
    @Test void unavailableMemoryIdsAreUniformNotFoundForReadAndCancel(){
        when(manager.findOwned(actor,id)).thenThrow(new IllegalArgumentException("运行不存在"));
        when(manager.cancelOwned(actor,id)).thenThrow(new IllegalArgumentException("运行不存在"));
        for(Runnable action:List.<Runnable>of(()->access.find(actor,id),()->access.cancel(actor,id)))
            assertThatThrownBy(action::run).isInstanceOf(Failure.class)
                    .extracting(e->((Failure)e).code()).isEqualTo(Code.ARCHIVE_NOT_FOUND);
        verifyNoInteractions(archive);
    }
}
