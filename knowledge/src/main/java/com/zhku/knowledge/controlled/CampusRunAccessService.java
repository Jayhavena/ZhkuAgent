package com.zhku.knowledge.controlled;

import com.zhku.knowledge.campusagent.CampusCreditAgentManager;
import com.zhku.knowledge.campusagent.CampusCreditAgentService;
import com.zhku.knowledge.campusagent.archive.CampusRunArchiveStore;
import com.zhku.knowledge.security.PreviewIdentityRepository;
import com.zhku.knowledge.security.TrustedActor;
import java.util.UUID;
import java.util.Objects;
import java.util.function.Supplier;
import com.zhku.knowledge.campusagent.archive.CampusRunArchive;
import com.zhku.knowledge.campusagent.archive.CampusRunHistoryService;
import com.zhku.knowledge.campusagent.submission.CampusRequestFingerprint;
import com.zhku.knowledge.campusagent.submission.CampusRunSubmission;
import java.time.Instant;

/** 受控结果投影；Run归属不会授予政策资料许可。 */
public final class CampusRunAccessService {
    public enum ResultVisibility { RUNNING, NOT_APPLICABLE, VISIBLE, HIDDEN, UNCONFIRMED }
    public record ControlledRunReply(CampusCreditAgentManager.Snapshot run, ResultVisibility resultVisibility) { }
    public record SubmissionReply(UUID submissionKey,UUID runId,boolean replayed,Instant acceptedAt,Instant expiresAt,
            String location,ControlledRunReply live,CampusRunHistoryService.Detail archived) {
        public SubmissionReply {
            if (!switch(location) {
                case "LIVE" -> live!=null&&archived==null;
                case "ARCHIVED" -> live==null&&archived!=null;
                case "UNCONFIRMED" -> live==null&&archived==null;
                default -> false;
            })throw new IllegalArgumentException("提交结果位置不一致");
        }
    }
    public static final class AcceptedResultUnavailable extends RuntimeException {
        private final UUID runId;
        AcceptedResultUnavailable(UUID runId) { super("RESULT_READ_UNAVAILABLE", null, false, false); this.runId=runId; }
        public UUID runId() { return runId; }
    }
    private final CampusCreditAgentManager manager;
    private final CampusRunArchiveStore archive;
    private final PreviewIdentityRepository identities;
    public CampusRunAccessService(CampusCreditAgentManager manager, CampusRunArchiveStore archive,
            PreviewIdentityRepository identities) {
        this.manager=Objects.requireNonNull(manager);
        this.archive=Objects.requireNonNull(archive);
        this.identities=Objects.requireNonNull(identities);
    }
    public ControlledRunReply start(TrustedActor actor, CampusCreditAgentService.Request request) {
        requireUser(actor);
        var run=manager.startOwned(request,actor);
        try { return project(actor,run); }
        catch(RuntimeException unavailable) {
            // 已确认准入不能因响应读取失败假称未受理，也不能自动再次调度。
            throw new AcceptedResultUnavailable(run.runId());
        }
    }
    public SubmissionReply submit(TrustedActor actor,UUID key,CampusCreditAgentService.Request request) {
        requireUser(actor);
        var normalized=CampusRequestFingerprint.normalize(request);
        var submitted=manager.submitOwned(actor,key,normalized,CampusRequestFingerprint.of(normalized));
        try {return submissionReply(actor,submitted.claim().record(),submitted.live(),
                submitted.claim().decision()==CampusRunSubmission.Decision.REPLAYED);}
        catch(PreviewIdentityRepository.Failure denied){throw denied;}
        catch(RuntimeException unavailable){
            throw new CampusRunSubmission.Failure(CampusRunSubmission.Code.RESULT_READ_UNAVAILABLE,key,submitted.claim().record().runId());
        }
    }
    public SubmissionReply findSubmission(TrustedActor actor,UUID key) {
        requireUser(actor);
        var row=archive.findSubmissionOwned(actor,key).orElseThrow(()->new CampusRunSubmission.Failure(CampusRunSubmission.Code.SUBMISSION_NOT_FOUND,key));
        CampusCreditAgentManager.Snapshot live;
        try {live=manager.findOwned(actor,row.runId());}
        catch(IllegalArgumentException noLocalContext){live=null;}
        return submissionReply(actor,row,live,false);
    }
    private SubmissionReply submissionReply(TrustedActor actor,CampusRunSubmission.Record row,
            CampusCreditAgentManager.Snapshot live,boolean replayed) {
        if(live!=null) {
            var read=archive.findOwned(actor,row.runId()).orElseThrow(()->new CampusRunArchive.Failure(CampusRunArchive.Code.ARCHIVE_READ_UNAVAILABLE));
            var projected=project(live,read);
            return new SubmissionReply(row.submissionKey(),row.runId(),replayed,row.acceptedAt(),read.archive().retainedUntil(),"LIVE",projected,null);
        }
        CampusRunHistoryService.Detail detail;
        try {detail=new CampusRunHistoryService(archive).findOwned(actor,row.runId());}
        catch(CampusRunArchive.Failure missing) {
            if(missing.code()!=CampusRunArchive.Code.ARCHIVE_NOT_FOUND)throw missing;
            return new SubmissionReply(row.submissionKey(),row.runId(),replayed,row.acceptedAt(),null,"UNCONFIRMED",null,null);
        }
        if(detail.archiveStatus()==CampusRunArchive.ArchiveStatus.ADMITTED)
            return new SubmissionReply(row.submissionKey(),row.runId(),replayed,row.acceptedAt(),null,"UNCONFIRMED",null,null);
        return new SubmissionReply(row.submissionKey(),row.runId(),replayed,row.acceptedAt(),detail.retainedUntil(),"ARCHIVED",null,detail);
    }
    public ControlledRunReply find(TrustedActor actor, UUID runId) {
        requireUser(actor);
        return project(actor,ownedSnapshot(()->manager.findOwned(actor,runId)));
    }
    public ControlledRunReply cancel(TrustedActor actor, UUID runId) {
        requireUser(actor);
        return project(actor,ownedSnapshot(()->manager.cancelOwned(actor,runId)));
    }
    private static CampusCreditAgentManager.Snapshot ownedSnapshot(Supplier<CampusCreditAgentManager.Snapshot> query) {
        try { return query.get(); }
        catch(IllegalArgumentException missing) {
            // 原匿名Manager的九字段/异常契约保留，新受控路径把他人/不存在/已回收统一为404。
            throw new CampusRunArchive.Failure(CampusRunArchive.Code.ARCHIVE_NOT_FOUND);
        }
    }
    private void requireUser(TrustedActor actor) {
        if(actor==null) throw new PreviewIdentityRepository.Failure(PreviewIdentityRepository.Code.AUTH_REVOKED);
        if(actor.role()!=TrustedActor.Role.USER)
            throw new PreviewIdentityRepository.Failure(PreviewIdentityRepository.Code.ROLE_DENIED);
        identities.requireCurrent(actor);
    }
    private ControlledRunReply project(TrustedActor actor,CampusCreditAgentManager.Snapshot run) {
        var read=archive.findOwned(actor,run.runId()).orElseThrow(()->new CampusRunArchive.Failure(
                CampusRunArchive.Code.ARCHIVE_NOT_FOUND));
        return project(run,read);
    }
    private static ControlledRunReply project(CampusCreditAgentManager.Snapshot run,CampusRunArchive.Read read) {
        ResultVisibility visibility;
        CampusRunArchive.PayloadCandidate confirmed=null;
        if(!run.state().isTerminal()) visibility=ResultVisibility.RUNNING;
        else if(read.archive().archiveStatus()!=CampusRunArchive.ArchiveStatus.FINALIZED)
            visibility=ResultVisibility.UNCONFIRMED;
        else if(run.state()!=com.zhku.agent.agent.runtime.AgentRunState.SUCCEEDED
                ||run.status()!=CampusCreditAgentService.Status.ANSWERED) visibility=ResultVisibility.NOT_APPLICABLE;
        else if(read.visibility().status()==CampusRunArchive.VisibilityStatus.VISIBLE&&read.result()!=null) {
            visibility=ResultVisibility.VISIBLE;confirmed=read.result();
        } else visibility=ResultVisibility.HIDDEN;
        // 内存结果只贡献执行事实；正文只能来自本人的确认归档及当前来源许可，自由步骤一律去掉。
        var projected=new CampusCreditAgentManager.Snapshot(run.runId(),run.state(),run.stopReason(),run.terminated(),
                run.status(),confirmed==null?null:confirmed.answer(),run.failureCategory(),run.steps().stream()
                        .map(step->new CampusCreditAgentService.Step(step.number(),step.name(),step.status(),null)).toList(),
                confirmed==null?null:confirmed.source());
        return new ControlledRunReply(projected,visibility);
    }
}
