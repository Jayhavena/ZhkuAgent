package com.zhku.knowledge.retrieval;
import com.zhku.knowledge.rulecheck.RuleCheckApplicationService;
import java.nio.file.Path;
import java.util.List;
/** 测试沿用正式受控目录校验，不复制规则装配逻辑。 */
final class RuleCheckTopicCatalogueDraft {
    static List<RuleCheckApplicationService.CatalogEntry> loadWithReviewedLimits(Path root,Reviewed2025Trial trial)throws Exception {
        return Reviewed2025TopicCatalogue.loadWithReviewedLimits(root,trial);
    }
    static List<RuleCheckApplicationService.CatalogEntry> load(Path root,Reviewed2025Trial trial)throws Exception {
        return Reviewed2025TopicCatalogue.load(root,trial);
    }
}