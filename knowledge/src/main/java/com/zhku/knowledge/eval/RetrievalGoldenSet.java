package com.zhku.knowledge.eval;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 版本化评测黄金集（v1）。
 *
 * <p>语料是公共选修规则手册的四条条款 + 一条内部资料诱饵；每个评测问题给出
 * "相关片段集合"作为分母。**评测集一旦发布即冻结**：改动必须换版本号
 * （VERSION），否则历史基线与新结果不可比。</p>
 *
 * <p>语料片段 UUID 与评测装置共用（评测装置按这些 UUID 造数据并索引），
 * 保证"分母—候选—引用"指向同一批片段。</p>
 */
public final class RetrievalGoldenSet {

    /** 数据集版本：改动语料或分母时必须换版本号，并在证据账本登记。 */
    public static final String VERSION = "retrieval-golden-v1";

    /** 语料来源：公共选修规则手册（已发布、学生可见、分享权利确认）。 */
    public static final UUID SOURCE_ID = UUID.fromString("f0111111-1111-4111-8111-111111111111");
    /** 内部资料来源：用于验证"内部资料永不召回"。 */
    public static final UUID INTERNAL_SOURCE_ID = UUID.fromString("f0222222-2222-4222-8222-222222222222");

    public static final UUID CHUNK_CREDIT_LIMIT = UUID.fromString("f0c11111-1111-4111-8111-111111111111");
    public static final UUID CHUNK_NO_DEFERRAL = UUID.fromString("f0c22222-2222-4222-8222-222222222222");
    public static final UUID CHUNK_WITHDRAWAL_WINDOW = UUID.fromString("f0c33333-3333-4333-8333-333333333333");
    public static final UUID CHUNK_NO_SELECTION = UUID.fromString("f0c44444-4444-4444-8444-444444444444");
    public static final UUID CHUNK_INTERNAL_DECOY = UUID.fromString("f0c55555-5555-4555-8555-555555555555");

    public static final String CONTENT_CREDIT_LIMIT = "第一条 每学期公共选修课最高修读 4 学分。";
    public static final String CONTENT_NO_DEFERRAL = "第二条 公共选修课不办理缓考，不及格者应重修。";
    public static final String CONTENT_WITHDRAWAL_WINDOW =
            "第三条 退改选办理时间为 2026-09-10 至 2026-09-15，入口为教务系统。";
    public static final String CONTENT_NO_SELECTION = "第四条 未选课学生不得参加课程考核。";
    public static final String CONTENT_INTERNAL_DECOY = "内部条款 公共选修课缓考特殊处理办法。";

    private RetrievalGoldenSet() {
    }

    /**
     * 一个评测问题及其相关片段集合。
     *
     * <p>问法刻意使用与条款同形的词面：首期词法路是受控词面匹配（非分词、非 BM25），
     * 评测集如实反映这一能力边界，不靠"人工润色问法"制造好看的数字。</p>
     */
    public record Case(String query, Set<UUID> relevantChunkIds) {

        public Case {
            if (query == null || query.isBlank()) {
                throw new IllegalArgumentException("评测问题不能为空");
            }
            if (relevantChunkIds == null || relevantChunkIds.isEmpty()) {
                // 分母为空的问题不计入评测，构造时就要暴露。
                throw new IllegalArgumentException("评测问题必须声明相关片段集合");
            }
            relevantChunkIds = Set.copyOf(relevantChunkIds);
        }
    }

    public static List<Case> cases() {
        return List.of(
                new Case("公共选修课 最高修读 学分", Set.of(CHUNK_CREDIT_LIMIT)),
                new Case("不办理缓考 重修", Set.of(CHUNK_NO_DEFERRAL)),
                new Case("退改选办理时间 教务系统", Set.of(CHUNK_WITHDRAWAL_WINDOW)),
                new Case("未选课 参加考核", Set.of(CHUNK_NO_SELECTION))
        );
    }
}
