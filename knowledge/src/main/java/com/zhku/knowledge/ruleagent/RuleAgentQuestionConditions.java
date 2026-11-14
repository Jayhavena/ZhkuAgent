package com.zhku.knowledge.ruleagent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 从用户首问原文中提取“用户已经明确声明的规则条件”，当前只支持 courseType。
 *
 * <p>这里只做纯文本提取，不接触 TaskStore、Runner、规则证据或发布协议。提取结果只是用户问题里的
 * 声明，不能当成规则事实，也不能用来伪造 Evidence、checksum 或发布资格。设计上宁可返回未知，
 * 也不把背景课程、另一问、比较、否定、身份、不确定或转述误当成条件：一旦误填，后续 when 分支
 * 会直接走错规则，比多问一次澄清的代价高得多。</p>
 *
 * <p>本版不是通用中文语义解析器。多问、条件从句、口语省略都靠分句结构加少量词表保守判断；
 * 无法确定作用域时一律返回空，由上游继续澄清。</p>
 */
public final class RuleAgentQuestionConditions {

    /**
     * 用户首问中明确声明的单个条件值。
     *
     * <p>start/end 是原始 question 的 UTF-16 坐标，且 {@code question.substring(start,end)}
     * 必须逐字等于命中词元。这里不能先做 NFKC 再算偏移，否则全角标点或补充平面字符会让跨度错位。</p>
     */
    public record Declared(String conditionId, String value, String subquestionId, int start, int end) {
        public Declared {
            Objects.requireNonNull(conditionId, "conditionId");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(subquestionId, "subquestionId");
            if (start < 0 || end <= start) throw new IllegalArgumentException("条件跨度无效");
        }
    }

    private static final String COURSE_TYPE = "courseType";
    private static final String PUBLIC = "PUBLIC";
    private static final String PROFESSIONAL = "PROFESSIONAL";

    private record Lexeme(String text, String value) {
    }

    /**
     * 长词在前，避免“公选课”又被拆成“公选”重复计数；命中后按词长前进，不产生重叠候选。
     */
    private static final List<Lexeme> LEXEMES = List.of(
        new Lexeme("公共选修课", PUBLIC),
        new Lexeme("公共选修", PUBLIC),
        new Lexeme("公选课", PUBLIC),
        new Lexeme("公选", PUBLIC),
        new Lexeme("专业选修课", PROFESSIONAL),
        new Lexeme("专业选修", PROFESSIONAL),
        new Lexeme("专选课", PROFESSIONAL),
        new Lexeme("专选", PROFESSIONAL)
    );

    /**
     * 只检查紧邻类别词左侧的否定，不扫描整句否定。
     * “公共选修课不办理缓考吗”里的“不办理”是在问规则，不是在否定“公共选修课”这个类别。
     */
    private static final Pattern NEGATED_PREFIX = Pattern.compile(
        "(?:不是|并非|不属于|不算作|不算|而不是|非|除了|除|排除|不含|不包括|没有|没|无|"
            + "(?:没|没有|未|未曾|不曾|不)(?:有)?(?:选|选过|选修|修|修过|读|报|报名|选择|考虑))$");

    /** “公选之外/公选课以外/公选课除外”是在排除类别，不能当作正选声明。 */
    private static final Pattern EXCLUSION_SUFFIX = Pattern.compile("(?U)^\\s*(?:的)?\\s*(?:之外|以外|除外|外的|外)");

    /**
     * 类别词修饰“学生/老师/资格”等身份或资格时，问的不是规则类别；这类句子保持未知。
     * “公选课的缓考规定”仍会通过，因为“的”后面不是身份名词。
     */
    private static final Pattern IDENTITY_SUFFIX = Pattern.compile(
        "(?U)^\\s*(?:的)?\\s*(?:学生|老师|教师|课代表|班长|辅导员|助教|负责人|资格|名额|身份)");

    /** 显式比较词：出现即说明用户在比较两个对象，不是在声明所问规则的类别。 */
    private static final Pattern COMPARISON = Pattern.compile(
        "区别|差别|差异|不同|比较|相比|哪个|哪一类|哪一种|有何不同|有什么不同|二选一");

    /** “和/与/跟……一样/相同”这类比较没有固定比较词，单独兜住，避免与“公选课一样要考试吗”混淆。 */
    private static final Pattern COMPARISON_CONNECTOR = Pattern.compile(
        "(?:和|与|跟)[^？?。！!；;，,]{0,20}(?:一样|相同|不同|区别|差别|差异)");

    /** “或者/或是/或X”是选择结构；“或许”是猜测，不在此列，交给不确定规则处理。 */
    private static final Pattern CHOICE = Pattern.compile("或者|或是|或(?!许|者)|二选一");

    /** “还是”既可以表示选择，也可以表示“仍然”，只有后面还跟着另一个课程名词时才按选择处理。 */
    private static final Pattern CHOICE_NOUN = Pattern.compile("必修|选修|课程|专业课|通识课|其他课|网课|慕课");

    private static final List<String> UNCERTAINTY_PREFIX = List.of(
        "好像", "可能", "也许", "或许", "大概", "似乎", "不确定", "不清楚", "不知道", "不了解",
        "说不准", "估计", "是不是", "是否", "算不算", "能不能算", "是否算");

    /**
     * 类别词之后的不确定词同样会让条件失效，但刻意不包含“是不是/是否”：
     * “公选课是不是不能缓考”是在问规则，不是在怀疑类别。
     */
    private static final List<String> UNCERTAINTY_SUFFIX = List.of(
        "好像", "可能", "也许", "或许", "大概", "似乎", "不确定", "不清楚", "不知道", "不了解",
        "说不准", "估计");

    /** 转述来源不是用户自己的声明；只有出现在类别词所在分句时才拒绝，避免误杀无关分句。 */
    private static final List<String> THIRD_PARTY = List.of(
        "听说", "据说", "传言", "我同学", "同学说", "我朋友", "朋友说", "室友", "有人说",
        "别人说", "通知里", "通知中", "通知上说", "网上说", "网上看到", "辅导员说", "老师说", "导员说");

    /** 引号、示例和“所谓”都在提示这是举例或引述，不是用户对规则类别的直接声明。 */
    private static final List<String> EXAMPLE = List.of(
        "例如", "比如", "举例", "譬如", "诸如", "为例", "所谓", "打个比方", "比方说");

    private static final Pattern QUESTION_MARKER = Pattern.compile(
        "为什么|能不能|可不可以|如何|怎么|怎样|能否|是否|什么|哪|多少|多久|吗|呢");

    /**
     * 把一句话判断成“在问规则”的补充信号。用于“公选课能退？培养方案查询。”这类
     * 后句没有疑问词、却明显另起一问的情况；不包含“选课”等普通动作，避免误伤背景分句。
     */
    private static final Pattern RULE_REQUEST = Pattern.compile(
        "查询|办理|流程|规定|政策|要求|申请|说明|介绍|解释");

    /** “……，这是真的吗”只是对前一句的确认，不应被当成另起一问而丢掉前面的类别。 */
    private static final Pattern CONFIRMATION_TAIL = Pattern.compile(
        "^(?:这|那)?(?:是)?(?:真的|这样|如此)?(?:吗|嘛|么|吧)$|^(?:对不对|是不是|对吧|好吗|行吗|可以吗)$");

    /**
     * 已知的非本类别课程主体，仅作为第二道防线。
     *
     * <p>真正的保护来自“承载问句的分句必须含类别词，或是不带新主体的规则谓词”这一结构判断；
     * 这里不追求穷举任意课程名，新增词只用于兜住常见误判。</p>
     */
    private static final Pattern OTHER_COURSE_NOUN = Pattern.compile(
        "必修|通识|专业课|其他课程|其他课|体育课|体育|实践课|实践|实验课|实验|网课|慕课|高数|数学|英语|物理|化学|计算机");

    /**
     * 同一问句里常见口语前缀；剥掉后才能判断后面是不是“能/可以/怎么”这类无主语的规则谓词。
     * 长词在前，避免“那么”只剥掉“那”。
     */
    private static final List<String> LEADING_FILLERS = List.of(
        "请问", "那么", "要是", "如果", "到底", "究竟", "另外", "然后", "现在", "目前",
        "那", "这", "我", "该", "还", "也", "都", "就", "再");

    /**
     * 无新主体的规则谓词开头。只用于“类别词在前一分句、后一分句省略主语”的情形，
     * 例如“公选课没过，能重修吗”；“体育课能重修吗”不会被当作省略。
     */
    private static final Pattern BARE_PREDICATE = Pattern.compile(
        "^(?:能否|能不能|可不可以|是不是|会不会|要不要|需不需要|该不该|有没有|算不算|"
            + "能|可以|是否|会|要|需要|应该|应当|该|怎么|怎样|如何|为什么|为啥|什么|哪|多少|多久|"
            + "重修|补考|缓考|退课|退选|选课|考试|成绩|学分|申请|办理|查询|通过|计入|认定|算)");

    private RuleAgentQuestionConditions() {
    }

    /**
     * 提取用户首问中唯一明确的条件声明。
     *
     * <p>多子问题时无法判断条件属于哪一问，任何“共享任务全局条件”都会把未说过的条件安到
     * 其他问题上，因此直接返回空。相同类别重复出现时只保留第一个通过检查的精确跨度，
     * 不复制多份声明。</p>
     */
    public static List<Declared> extract(String question, List<RuleAgentRunner.Question> questions) {
        if (question == null || question.isBlank() || questions == null || questions.size() != 1) return List.of();
        var subquestion = questions.get(0);
        if (subquestion == null || subquestion.id() == null || subquestion.id().isBlank()
            || subquestion.text() == null || subquestion.text().isBlank()) return List.of();

        // 子问题必须是原句的一部分；找不到原文就无法给出可信跨度，按未知处理。
        int regionStart = question.indexOf(subquestion.text());
        if (regionStart < 0) return List.of();
        int regionEnd = regionStart + subquestion.text().length();

        var occurrences = occurrences(question, regionStart, regionEnd);
        if (occurrences.isEmpty()) return List.of();

        // 同一题出现两个类别时，用户是在选择、比较或排除，不是声明单一类别。
        var values = occurrences.stream().map(item -> item.lexeme().value()).distinct().toList();
        if (values.size() > 1) return List.of();
        String value = values.get(0);

        String region = question.substring(regionStart, regionEnd);
        if (COMPARISON.matcher(region).find() || COMPARISON_CONNECTOR.matcher(region).find()) return List.of();
        if (CHOICE.matcher(region).find() || stillChoice(region, regionStart, occurrences)) return List.of();

        var units = questionUnits(question);
        if (!scopeAllowsInheritance(question, units, occurrences, value)) return List.of();

        // 同一类别重复出现时只保留首个通过检查的跨度，避免下游把同一条件当成多次声明。
        boolean unitHasCategory = units.size() == 1
            && occurrences.stream().anyMatch(item -> contains(units.get(0), item));
        for (var occurrence : occurrences) {
            if (unitHasCategory && !contains(units.get(0), occurrence)) continue;
            if (!acceptable(question, regionEnd, occurrence)) continue;
            return List.of(new Declared(
                COURSE_TYPE, occurrence.lexeme().value(), subquestion.id(), occurrence.start(), occurrence.end()));
        }
        return List.of();
    }

    /**
     * 判断类别词能否作为整题条件。
     *
     * <p>多问时每一问都必须自己带同一类别，不能把第一问的类别共享给后一问；单问时允许
     * “如果挂科的是专业选修课，可以重修吗”这种条件从句，但承载问句的分句必须是无新主体的
     * 规则谓词，否则“我公选课没过，体育课能重修吗”会把背景类别误当问题类别。</p>
     */
    private static boolean scopeAllowsInheritance(String question, List<Clause> units, List<Occurrence> occurrences,
                                                  String value) {
        if (units.size() > 1) {
            for (var unit : units) {
                boolean declares = occurrences.stream()
                    .anyMatch(item -> contains(unit, item) && item.lexeme().value().equals(value)
                        && acceptable(question, question.length(), item));
                if (!declares) return false;
            }
            return true;
        }
        if (units.size() == 1) {
            var unit = units.get(0);
            boolean hasCategory = occurrences.stream().anyMatch(item -> contains(unit, item));
            // 单问句允许类别只在条件从句；但若问句自带新主体（体育课/高数……），不能继承前置条件。
            return hasCategory || bareRulePredicate(unit.text());
        }
        // 没有任何疑问信号时，只有显式类别本身才可能是整句主题，例如“公共选修课”。
        return true;
    }

    private static List<Occurrence> occurrences(String question, int regionStart, int regionEnd) {
        var result = new ArrayList<Occurrence>();
        for (int index = regionStart; index < regionEnd; ) {
            Lexeme matched = null;
            for (var lexeme : LEXEMES) {
                if (index + lexeme.text().length() <= regionEnd && question.startsWith(lexeme.text(), index)) {
                    matched = lexeme;
                    break;
                }
            }
            if (matched == null) {
                index++;
                continue;
            }
            result.add(new Occurrence(matched, index, index + matched.text().length()));
            index += matched.text().length();
        }
        return result;
    }

    /**
     * 按标点和空白切分句。空白参与“是否是另一问”的判断，因此“公选课能缓考吗 体育课呢”
     * 不会被当成一个分句；但否定检查另用只认标点的切分，避免空格截断“不是 公选课”。
     */
    private static List<Clause> questionUnits(String question) {
        var clauses = new ArrayList<Clause>();
        int start = 0;
        boolean startsNew = false;
        for (int index = 0; index <= question.length(); index++) {
            boolean atEnd = index == question.length();
            char current = atEnd ? ' ' : question.charAt(index);
            if (!atEnd && !isScopeSeparator(current)) continue;
            if (index > start) {
                clauses.add(new Clause(question.substring(start, index), start, index,
                    !atEnd && isQuestionMark(current), startsNew));
                startsNew = !atEnd && startsAnotherQuestion(current);
            } else if (!atEnd && startsAnotherQuestion(current)) {
                startsNew = true;
            }
            start = index + 1;
        }
        return clauses.stream()
            .filter(clause -> !CONFIRMATION_TAIL.matcher(clause.text().strip()).matches())
            .filter(RuleAgentQuestionConditions::isQuestionUnit).toList();
    }

    private static boolean isQuestionUnit(Clause clause) {
        return QUESTION_MARKER.matcher(clause.text()).find()
            || clause.endsWithQuestion()
            || (clause.startsNew() && RULE_REQUEST.matcher(clause.text()).find());
    }

    /** 只有句末标点、空白和顿号会把后一分句当成“另起一问”的候选；逗号/分号仍属同一问。 */
    private static boolean startsAnotherQuestion(char value) {
        return isQuestionMark(value) || "。.！!、".indexOf(value) >= 0
            || Character.isWhitespace(value) || Character.isSpaceChar(value);
    }

    /** 剥掉口语前缀后，分句仍以规则谓词开头，说明它没有引入新的课程主体。 */
    private static boolean bareRulePredicate(String text) {
        String probe = text.strip();
        boolean stripped = true;
        while (stripped) {
            stripped = false;
            for (var filler : LEADING_FILLERS) {
                if (probe.startsWith(filler)) {
                    probe = probe.substring(filler.length()).stripLeading();
                    stripped = true;
                    break;
                }
            }
        }
        return BARE_PREDICATE.matcher(probe).find();
    }

    /**
     * “还是”只有在排除已命中的类别词后仍能读到另一个课程名词时，才按选择结构拒绝。
     * 这样“公选课还是不能缓考吗”不会被误判成“公选课还是必修课”式的选择。
     */
    private static boolean stillChoice(String region, int regionStart, List<Occurrence> occurrences) {
        if (!region.contains("还是")) return false;
        var rest = new StringBuilder(region);
        for (var occurrence : occurrences) {
            for (int index = occurrence.start() - regionStart; index < occurrence.end() - regionStart; index++) {
                rest.setCharAt(index, ' ');
            }
        }
        return CHOICE_NOUN.matcher(rest).find();
    }

    private static boolean acceptable(String question, int regionEnd, Occurrence occurrence) {
        // 子问题只是原文的一段；否定可能落在它前面（“不是公选课能缓考吗”），必须回看整句。
        int clauseStart = clauseStart(question, occurrence.start());
        int clauseEnd = clauseEnd(question, regionEnd, occurrence.end());
        String prefix = question.substring(clauseStart, occurrence.start()).stripTrailing();
        String clause = question.substring(clauseStart, clauseEnd);
        String suffix = question.substring(occurrence.end(), clauseEnd);

        if (NEGATED_PREFIX.matcher(prefix).find()) return false;
        if (EXCLUSION_SUFFIX.matcher(suffix).find()) return false;
        if (IDENTITY_SUFFIX.matcher(suffix).find()) return false;
        if (UNCERTAINTY_PREFIX.stream().anyMatch(prefix::contains)) return false;
        if (UNCERTAINTY_SUFFIX.stream().anyMatch(suffix::contains)) return false;
        if (THIRD_PARTY.stream().anyMatch(clause::contains)) return false;
        if (EXAMPLE.stream().anyMatch(clause::contains)) return false;
        if (otherCourseNounInside(clause)) return false;
        return !quoted(question, occurrence.start(), occurrence.end());
    }

    /**
     * 同一分句里另出现其它课程主体时，当前类别词多半是并列、比较或替代关系。
     * 先挖掉已命中的类别词，避免“公共选修课”中的“选修”被当成其它课程。
     */
    private static boolean otherCourseNounInside(String clause) {
        var rest = new StringBuilder(clause);
        for (var lexeme : LEXEMES) {
            int from = 0;
            while (true) {
                int index = rest.indexOf(lexeme.text(), from);
                if (index < 0) break;
                for (int cursor = index; cursor < index + lexeme.text().length(); cursor++) rest.setCharAt(cursor, ' ');
                from = index + lexeme.text().length();
            }
        }
        return OTHER_COURSE_NOUN.matcher(rest).find();
    }

    private static int clauseStart(String question, int index) {
        int cursor = index - 1;
        while (cursor >= 0 && !isSeparator(question.charAt(cursor))) cursor--;
        return cursor + 1;
    }

    private static int clauseEnd(String question, int regionEnd, int index) {
        int cursor = index;
        while (cursor < regionEnd && !isSeparator(question.charAt(cursor))) cursor++;
        return cursor;
    }

    private static boolean isSeparator(char value) {
        // 空格只是排版，不能截断否定或转述的保护上下文。
        return "，,。.；;！!？?、：:…".indexOf(value) >= 0;
    }

    private static boolean isScopeSeparator(char value) {
        return isSeparator(value) || Character.isWhitespace(value) || Character.isSpaceChar(value);
    }

    private static boolean isQuestionMark(char value) {
        return value == '？' || value == '?';
    }

    private static boolean contains(Clause clause, Occurrence occurrence) {
        return occurrence.start() >= clause.start() && occurrence.end() <= clause.end();
    }

    /**
     * 引号内的类别通常是被讨论、被引述或作为示例，不能当成用户自己的声明。
     * 这里按整句配对：子问题切片可能刚好丢掉外面的引号，不能因此把引述当成用户声明。
     */
    private static boolean quoted(String question, int start, int end) {
        if (start > 0 && isQuote(question.charAt(start - 1)) && end < question.length() && isQuote(question.charAt(end))) {
            return true;
        }
        return insidePair(question, start, end, '“', '”')
            || insidePair(question, start, end, '‘', '’')
            || insidePair(question, start, end, '「', '」')
            || insidePair(question, start, end, '『', '』')
            || insidePair(question, start, end, '《', '》')
            || insideSymmetric(question, start, end, '"')
            || insideSymmetric(question, start, end, '\'');
    }

    private static boolean insidePair(String question, int start, int end, char open, char close) {
        int opening = question.lastIndexOf(open, start - 1);
        if (opening < 0) return false;
        int closing = question.indexOf(close, end);
        if (closing < 0) return false;
        // 开引号之前已经出现过闭引号，说明这是上一段引用，当前词并不在引号内。
        return question.lastIndexOf(close, start - 1) < opening;
    }

    private static boolean insideSymmetric(String question, int start, int end, char quote) {
        int before = 0;
        for (int index = 0; index < start; index++) if (question.charAt(index) == quote) before++;
        if (before % 2 == 0) return false;
        for (int index = end; index < question.length(); index++) if (question.charAt(index) == quote) return true;
        return false;
    }

    private static boolean isQuote(char value) {
        return "“”‘’「」『』《》\"'".indexOf(value) >= 0;
    }

    private record Occurrence(Lexeme lexeme, int start, int end) {
    }

    private record Clause(String text, int start, int end, boolean endsWithQuestion, boolean startsNew) {
    }
}
