package com.zhku.knowledge.ruleagent;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.assertj.core.api.Assertions.*;

/**
 * 首问条件提取只回答“用户这句话里明确问的是哪一类规则”。
 * 测试同时锁定保守边界：背景课程、另一问、身份、比较、否定、不确定、转述和引号都不能被当成用户已声明条件。
 */
@Timeout(15)
class RuleAgentQuestionConditionsTest {

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"公选课挂了，能重修体育课吗","公选课没过，可以重修必修课吗","公选课挂了，能申请重修高数吗","公选课不过，能补考英语吗","专选课挂了，能重修高数吗","公选课挂了，能补考法语吗"})
    void aDifferentCourseAfterThePredicateCannotInheritBackgroundCategory(String text){
        assertThat(extract(text)).isEmpty();
    }

    private static List<RuleAgentQuestionConditions.Declared> extract(String text) {
        return RuleAgentQuestionConditions.extract(text, List.of(new RuleAgentRunner.Question("q1", text)));
    }

    private static RuleAgentQuestionConditions.Declared only(String text) {
        var result = extract(text);
        assertThat(result).as("应提取出唯一明确条件: %s", text).hasSize(1);
        return result.getFirst();
    }

    private static void exactToken(String question, RuleAgentQuestionConditions.Declared declared, String token) {
        assertThat(question.substring(declared.start(), declared.end())).isEqualTo(token);
    }

    @Test
    void unrelatedCourseAndUnscopedQuestionsNeverSeedGlobalCategory() {
        for (String text : List.of(
            "我公选课没过，必修课能重修吗", "公选课学分不够，通识课能算吗",
            "公选课能缓考吗？必修课能退吗？", "公选课能缓考吗\n必修课能退吗",
            "公选课能缓考吗 必修课能退吗", "公选课能缓考吗、必修课能退吗",
            "公选课能缓考吗。培养方案怎么查", "公选课能缓考吗？可以退课吗",
            "公选课能缓考吗\n可以退课吗", "公选课能缓考吗 可以退课吗",
            "公选课能缓考吗、可以退课吗")) {
            assertThat(extract(text)).as(text).isEmpty();
        }
    }

    /**
     * 类别词只出现在背景分句、承载问句的分句另起了一个课程主体时必须未知。
     * 这里不能靠穷举课程名兜底：分句里“能不能/可以”之前出现了新主体就不再继承前置条件。
     */
    @Test
    void backgroundCourseClauseIsNeverTheAskedCategory() {
        for (String text : List.of(
            "我公选课没过，体育课能重修吗",
            "我公选课没过，高数能缓考吗",
            "我公选课没过，实践课能补选吗",
            "公选课没选上，实验课能重修吗",
            "公选课没过，其他课程能重修吗",
            "我专选课挂了，体育课能补考吗",
            "公选课没过，体育课呢")) {
            assertThat(extract(text)).as(text).isEmpty();
        }
    }

    /** 只有“不含新主体的规则谓词”分句可以继承前文明确类别，如“公选课没过，能重修吗”。 */
    @Test
    void bareRulePredicateMayInheritEarlierExplicitCategory() {
        assertThat(only("公选课没过，能重修吗").value()).isEqualTo("PUBLIC");
        assertThat(only("公选课没过，请问还能重修吗").value()).isEqualTo("PUBLIC");
        assertThat(only("如果挂科的是专业选修课，可以重修吗").value()).isEqualTo("PROFESSIONAL");
    }

    /** 问号、句号、换行、空格、顿号后的另一问即使没有疑问词，也不能共享前一句的类别。 */
    @Test
    void anotherQuestionWithoutMarkerNeverSharesCategory() {
        for (String text : List.of(
            "公选课能退？培养方案查询。",
            "公选课能退？培养方案查询",
            "公选课能退。培养方案查询。",
            "公选课能退\n培养方案查询",
            "公选课能退 培养方案查询",
            "公选课能退、培养方案查询")) {
            assertThat(extract(text)).as(text).isEmpty();
        }
    }

    /** 子问题只是原文切片；切片外的否定或引号上下文仍必须阻止提取。 */
    @Test
    void trimmedSubquestionCannotHideOuterNegationOrQuotation() {
        assertThat(RuleAgentQuestionConditions.extract("不是公选课能缓考吗",
            List.of(new RuleAgentRunner.Question("q1", "公选课能缓考吗")))).isEmpty();
        assertThat(RuleAgentQuestionConditions.extract("他说“公选课能缓考吗”",
            List.of(new RuleAgentRunner.Question("q1", "公选课能缓考吗")))).isEmpty();
        assertThat(RuleAgentQuestionConditions.extract("公告栏写着「公选课能缓考吗」",
            List.of(new RuleAgentRunner.Question("q1", "公选课能缓考吗")))).isEmpty();
        assertThat(RuleAgentQuestionConditions.extract("比如公选课能缓考吗",
            List.of(new RuleAgentRunner.Question("q1", "公选课能缓考吗")))).isEmpty();
    }

    @Test
    void spacesCannotEraseNegationOrAttribution() {
        for (String text : List.of("我选的不是 公选课", "我选的不是　公选课", "我同学说 公选课很简单", "公选课　学生资格")) {
            assertThat(RuleAgentQuestionConditions.extract(text, List.of(new RuleAgentRunner.Question("q1", text))))
                .as(text).isEmpty();
        }
    }

    @Test
    void acceptsExplicitCategoryWithoutFirstPersonOrIdentityWording() {
        var cases = List.of(
            new String[]{"公共选修课能缓考吗", "PUBLIC", "公共选修课"},
            new String[]{"公共选修能退课吗", "PUBLIC", "公共选修"},
            new String[]{"公选课能退吗", "PUBLIC", "公选课"},
            new String[]{"公选能退吗", "PUBLIC", "公选"},
            new String[]{"专业选修课挂科可以重修吗", "PROFESSIONAL", "专业选修课"},
            new String[]{"专业选修可以重修吗", "PROFESSIONAL", "专业选修"},
            new String[]{"专选课可以重修吗", "PROFESSIONAL", "专选课"},
            new String[]{"专选可以重修吗", "PROFESSIONAL", "专选"}
        );
        for (var item : cases) {
            var declared = only(item[0]);
            assertThat(declared.conditionId()).isEqualTo("courseType");
            assertThat(declared.value()).isEqualTo(item[1]);
            assertThat(declared.subquestionId()).isEqualTo("q1");
            exactToken(item[0], declared, item[2]);
        }
    }

    @Test
    void genericCategoryTopicIsAcceptedBecauseItIsTheRuleCategoryAsked() {
        assertThat(only("公共选修课").value()).isEqualTo("PUBLIC");
        assertThat(only("专业选修课").value()).isEqualTo("PROFESSIONAL");
        assertThat(only("公选课有哪些规定？").value()).isEqualTo("PUBLIC");
    }

    /** 常见单问正例必须保留，不能因为保守化把整句变成未知。 */
    @Test
    void commonSingleQuestionPositivesStillExtract() {
        assertThat(only("公共选修课没过能重修吗？").value()).isEqualTo("PUBLIC");
        // “能不能”和“吗”同属一问，不能按两个疑问句拒绝。
        assertThat(only("公选课能不能缓考吗").value()).isEqualTo("PUBLIC");
        assertThat(only("公共选修课不办理缓考吗").value()).isEqualTo("PUBLIC");

        assertThat(only("公选课能退？公选课能延期吗").value()).isEqualTo("PUBLIC");
        assertThat(only("公选课能退？公共选修课能延期吗").value()).isEqualTo("PUBLIC");
    }

    @Test
    void predicateNegationDoesNotNegateTheCategoryItself() {
        var publicDefer = only("公共选修课不办理缓考吗");
        assertThat(publicDefer.value()).isEqualTo("PUBLIC");
        exactToken("公共选修课不办理缓考吗", publicDefer, "公共选修课");

        assertThat(only("公选课不能缓考吗").value()).isEqualTo("PUBLIC");
        assertThat(only("公选课不办理缓考，这是真的吗").value()).isEqualTo("PUBLIC");
    }

    @Test
    void negatedUnrelatedClauseDoesNotKillAnExplicitPositiveCategory() {
        assertThat(only("我不是大一新生，公选课能缓考吗").value()).isEqualTo("PUBLIC");
        assertThat(only("上学期没选课，公选课能缓考吗").value()).isEqualTo("PUBLIC");
        assertThat(only("我不喜欢必修课，公选课能退吗").value()).isEqualTo("PUBLIC");
    }

    @Test
    void conditionalClauseStillDeclaresTheRuleCategory() {
        var text = "如果挂科的是专业选修课，可以重修吗";
        var professional = only(text);
        assertThat(professional.value()).isEqualTo("PROFESSIONAL");
        exactToken(text, professional, "专业选修课");

        assertThat(only("如果挂科的是公共选修课，可以补考吗").value()).isEqualTo("PUBLIC");
    }

    @Test
    void rejectsExplicitCategoryNegationOrExclusion() {
        assertThat(extract("我选的不是公选课")).isEmpty();
        assertThat(extract("非公选课能缓考吗")).isEmpty();
        assertThat(extract("公选之外能缓考吗")).isEmpty();
        assertThat(extract("公选课之外能缓考吗")).isEmpty();
        assertThat(extract("公选课以外能缓考吗")).isEmpty();
        assertThat(extract("公选课除外")).isEmpty();
        assertThat(extract("除了公选课，其他课能缓考吗")).isEmpty();
        assertThat(extract("我不选公选课，能毕业吗")).isEmpty();
        assertThat(extract("没有选公选课能毕业吗")).isEmpty();
        assertThat(extract("我选的不是公选课，是必修课")).isEmpty();
    }

    @Test
    void rejectsChoiceComparisonAndExclusionBetweenCategories() {
        assertThat(extract("公选课和专业选修有什么区别")).isEmpty();
        assertThat(extract("公选课是专业选修吗")).isEmpty();
        assertThat(extract("公选课或专选课能退吗")).isEmpty();
        assertThat(extract("公选课还是专业选修课")).isEmpty();
        assertThat(extract("公选课和必修课有什么区别")).isEmpty();
        assertThat(extract("公选课与必修课有何不同")).isEmpty();
        assertThat(extract("公选课还是必修课")).isEmpty();
        assertThat(extract("公选课或者必修课能替代吗")).isEmpty();
        assertThat(extract("公选课、必修课哪个更容易过")).isEmpty();
        assertThat(extract("公选课跟必修课一样吗")).isEmpty();
        assertThat(extract("公选课和必修课相同吗")).isEmpty();
    }

    @Test
    void rejectsCategoryUncertaintyButNotAnOrdinaryRuleQuestion() {
        assertThat(extract("好像是公选课")).isEmpty();
        assertThat(extract("可能是公选课")).isEmpty();
        assertThat(extract("不确定是不是公选课")).isEmpty();
        assertThat(extract("不清楚是不是公选课")).isEmpty();
        assertThat(extract("不知道是不是公选课")).isEmpty();
        assertThat(extract("公选课好像不能缓考")).isEmpty();
        assertThat(extract("公选课可能不能缓考")).isEmpty();
        assertThat(extract("公选课不确定能不能缓考")).isEmpty();

        // “是不是”在这里是规则问句，不是在怀疑课程类别。
        assertThat(only("公选课是不是不能缓考").value()).isEqualTo("PUBLIC");
        assertThat(only("公选课是否能缓考").value()).isEqualTo("PUBLIC");
        // “还是”表示“仍然”而不是选择；排除已命中类别词后没有第二个课程名词。
        assertThat(only("公共选修课还是不能缓考吗").value()).isEqualTo("PUBLIC");
        assertThat(only("公选课还是不能缓考吗").value()).isEqualTo("PUBLIC");
    }

    @Test
    void rejectsThirdPartyReportQuotationAndExample() {
        assertThat(extract("听说公选课不能缓考")).isEmpty();
        assertThat(extract("据说公选课可以退")).isEmpty();
        assertThat(extract("我同学说公选课能缓考")).isEmpty();
        assertThat(extract("朋友说公选课能缓考")).isEmpty();
        assertThat(extract("室友说公选课能缓考")).isEmpty();
        assertThat(extract("有人说公选课能缓考")).isEmpty();
        assertThat(extract("通知里说公选课能缓考")).isEmpty();

        assertThat(extract("“公选课”能缓考吗")).isEmpty();
        assertThat(extract("「公选课」能缓考吗")).isEmpty();
        assertThat(extract("『公选课』能缓考吗")).isEmpty();
        assertThat(extract("比如公选课能缓考吗")).isEmpty();
        assertThat(extract("以公选课为例")).isEmpty();
        assertThat(extract("所谓公选课是什么")).isEmpty();
    }

    @Test
    void rejectsIdentityOrEligibilityInsteadOfRuleCategory() {
        assertThat(extract("我是公选课学生，能缓考吗")).isEmpty();
        assertThat(extract("公选课学生能缓考吗")).isEmpty();
        assertThat(extract("公选课资格怎么获得")).isEmpty();
    }

    @Test
    void multipleSubquestionsNeverShareATaskGlobalCondition() {
        var bothCategories = List.of(
            new RuleAgentRunner.Question("q1", "公选课能缓考吗"),
            new RuleAgentRunner.Question("q2", "专选课能重修吗")
        );
        assertThat(RuleAgentQuestionConditions.extract("公选课能缓考吗；专选课能重修吗", bothCategories)).isEmpty();
        assertThat(extract("公选课能缓考吗；专选课能重修吗")).isEmpty();

        // 只有一个类别也必须为空：不能把第一问的条件共享给第二问。
        var oneCategory = List.of(
            new RuleAgentRunner.Question("q1", "公选课能缓考吗"),
            new RuleAgentRunner.Question("q2", "可以退课吗")
        );
        assertThat(RuleAgentQuestionConditions.extract("公选课能缓考吗；可以退课吗", oneCategory)).isEmpty();
    }

    @Test
    void sameCategoryRepeatedKeepsOnlyFirstAcceptedExactSpan() {
        var repeated = only("公选课能缓考吗？公选课能退吗？");
        assertThat(repeated.start()).isZero();
        assertThat(repeated.end()).isEqualTo(3);
        exactToken("公选课能缓考吗？公选课能退吗？", repeated, "公选课");

        var mixedLexeme = only("公选课能缓考吗？公共选修课能退吗？");
        assertThat(mixedLexeme.start()).isZero();
        exactToken("公选课能缓考吗？公共选修课能退吗？", mixedLexeme, "公选课");
    }

    @Test
    void earlierNegatedOccurrenceDoesNotMaskLaterPositiveClause() {
        var text = "上学期没选公选课，这学期公选课能退吗";
        var declared = only(text);
        assertThat(declared.start()).isEqualTo(12);
        assertThat(declared.end()).isEqualTo(15);
        exactToken(text, declared, "公选课");
    }

    @Test
    void offsetsAreRawUtf16PositionsInTheOriginalQuestion() {
        var fullWidth = "问题：公选课　能缓考吗？";
        var declared = only(fullWidth);
        assertThat(declared.start()).isEqualTo(3);
        assertThat(declared.end()).isEqualTo(6);
        exactToken(fullWidth, declared, "公选课");

        var ideographicSpace = "　公共选修课？";
        var spaced = only(ideographicSpace);
        assertThat(spaced.start()).isEqualTo(1);
        assertThat(spaced.end()).isEqualTo(6);
        exactToken(ideographicSpace, spaced, "公共选修课");

        // 不做 NFKC：补充平面字符按两个 UTF-16 单元计数，跨度仍可直接切原文。
        var emoji = "😀公选课能缓考吗";
        var prefixed = only(emoji);
        assertThat(prefixed.start()).isEqualTo(2);
        assertThat(prefixed.end()).isEqualTo(5);
        exactToken(emoji, prefixed, "公选课");

        var conditional = only("如果挂科的是专业选修课，可以重修吗");
        assertThat(conditional.start()).isEqualTo(6);
        assertThat(conditional.end()).isEqualTo(11);
        exactToken("如果挂科的是专业选修课，可以重修吗", conditional, "专业选修课");
    }

    @Test
    void blankPunctuationNullAndNoCategoryReturnEmpty() {
        assertThat(extract(null)).isEmpty();
        assertThat(extract("")).isEmpty();
        assertThat(extract("   ")).isEmpty();
        assertThat(extract("　")).isEmpty();
        assertThat(extract("？？！。，")).isEmpty();
        assertThat(extract("公开选拔课程能缓考吗")).isEmpty();
        assertThat(RuleAgentQuestionConditions.extract("公选课能缓考吗", null)).isEmpty();
        assertThat(RuleAgentQuestionConditions.extract("公选课能缓考吗", List.of())).isEmpty();
    }

    @Test
    void resultIsAnImmutableCollection() {
        var result = extract("公选课能缓考吗");
        assertThat(result).hasSize(1);
        assertThatThrownBy(() -> result.add(result.getFirst())).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(result::clear).isInstanceOf(UnsupportedOperationException.class);
    }
}
