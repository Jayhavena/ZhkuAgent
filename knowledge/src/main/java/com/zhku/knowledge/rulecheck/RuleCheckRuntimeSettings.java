package com.zhku.knowledge.rulecheck;

/** 服务端统一配置；不能从学生请求覆盖检索、核验或审计的资料边界。 */
public record RuleCheckRuntimeSettings(String profileId, String chunkingVersion, String requiredScope,
                                       String referenceRevision, int candidateLimit, int resultLimit) {
    public RuleCheckRuntimeSettings {
        for (String boundary : new String[]{profileId,chunkingVersion,requiredScope,referenceRevision})
            if (boundary == null || boundary.isBlank()) throw new IllegalArgumentException("运行边界必须显式配置");
        // 这是工程预算保护，不是已验证的最优检索参数；候选深度仍须业务eval。
        if (resultLimit < 1 || resultLimit > 50 || candidateLimit < resultLimit || candidateLimit > 200)
            throw new IllegalArgumentException("检索预算超出受控范围");
    }
}
