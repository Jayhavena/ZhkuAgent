package com.zhku.knowledge.ruleagent.web;
import com.zhku.knowledge.ruleagent.*;
import com.zhku.knowledge.retrieval.*;
import com.zhku.knowledge.rulecheck.*;
import javax.sql.DataSource;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.*;
/** 只装配已审核资料工具，资料安装与身份仓储由独立受控启动入口提供；不隐式启用模型。 */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name={"zhku.rule-agent-http.enabled","zhku.rule-agent-http.reviewed-tools-enabled"},havingValue="true")
public class RuleAgentReviewedToolsConfiguration {
    public record Assets(Path root){
        public Assets {
            Objects.requireNonNull(root);
            if(!root.isAbsolute()||!Files.isDirectory(root.resolve("knowledge/src")))throw new IllegalArgumentException("须显式指定受控资料绝对根目录");
            root=root.normalize();
        }
        public void qualify(){
            // 外部路径仅供读取已审核字节；不加载其代码，不改审核指纹或拷贝PDF到worktree。
            try(var ignored=new Reviewed2025ControlledCatalogue(root)){}catch(Exception invalid){throw new IllegalArgumentException("受控资料资格不可用",invalid);}
        }
    }
    @Bean RuleAgentTaskToolsRegistry.ToolFactory ruleAgentReviewedTools(Assets assets,Reviewed2025ControlledCatalogue catalogue,DataSource source,Clock clock){
        assets.qualify();requireIsolated(source);
        return (owner,task)->{
            assets.qualify();var settings=catalogue.settings();
            // 审核许可与原文资格不是启动时永久许可；每次工具取目录时重新核对。
            return new RuleAgentEvidenceTools(owner,task,settings,()->{
                assets.qualify();try{return RuleArticle4Overlay.apply(assets.root(),catalogue.reviewedCatalogue(assets.root()));}
                catch(Exception revoked){throw new IllegalStateException("受控目录当前不可用",revoked);}
            },JdbcLexicalCandidateRetriever.forChineseBigramEvaluation(source,new RetrievalEligibilityFilter(clock),settings.profileId(),settings.requiredScope(),settings.chunkingVersion()),
                new JdbcRuleCheckEvidenceReader(source,settings.profileId(),settings.chunkingVersion(),settings.requiredScope()),
                new JdbcRuleCheckCitationAuditRepository(source,settings.profileId(),settings.chunkingVersion(),settings.requiredScope()),clock);
        };
    }
    @Bean @ConditionalOnMissingBean(RuleCheckOriginalEvidenceService.class)
    RuleCheckOriginalEvidenceService ruleAgentReviewedOriginals(Reviewed2025ControlledCatalogue catalogue,DataSource source){
        requireIsolated(source);var settings=catalogue.settings();
        var reader=new JdbcRuleCheckEvidenceReader(source,settings.profileId(),settings.chunkingVersion(),settings.requiredScope());
        return new RuleCheckOriginalEvidenceService(reader::readLocated,settings.referenceRevision(),Set.of());
    }
    private static void requireIsolated(DataSource source){
        if(!(source instanceof org.springframework.jdbc.datasource.DriverManagerDataSource db))throw new IllegalArgumentException("须使用专用直接数据源");
        var match=java.util.regex.Pattern.compile("^jdbc:postgresql://127\\.0\\.0\\.1:([0-9]{4,5})/zhkuagent_rulecheck_e2e$").matcher(Objects.toString(db.getUrl(),""));
        if(!match.matches())throw new IllegalArgumentException("须使用专用回环临时库");
        int port=Integer.parseInt(match.group(1));if(port<1024||port>65535||port==54329)throw new IllegalArgumentException("拒绝原演示库或非法端口");
    }
}