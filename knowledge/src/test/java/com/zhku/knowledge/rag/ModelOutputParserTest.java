package com.zhku.knowledge.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 合成协议反例，不代表真实校园规则或模型回答质量。 */
class ModelOutputParserTest {

    @Test
    void preservesValidFieldsWithoutRewritingCitationIds() {
        ModelResponse response = ModelOutputParser.parse(json("\"合成回答\"", "0.9", "[\" cit-1 \"]", "false"));
        assertThat(response.answer()).isEqualTo("合成回答");
        assertThat(response.uncertainty()).isEqualTo(0.9);
        assertThat(response.citationIds()).containsExactly(" cit-1 ");
        assertThat(response.handoffRequired()).isFalse();
    }

    @Test
    void acceptsNumericRangeEndpointsWithoutChangingScoreSemantics() {
        for (String score : new String[]{"0", "1"}) {
            assertThat(ModelOutputParser.parse(json("\"回答\"", score, "[\"cit-1\"]", "false"))
                    .uncertainty()).isEqualTo(Double.parseDouble(score));
        }
    }

    @Test
    void canRepresentHandoffWithNoCitations() {
        // 无依据的转人工响应可被解析，但能否展示为答案仍由守卫决定。
        ModelResponse response = ModelOutputParser.parse(json("\"无法确认\"", "0", "[]", "true"));
        assertThat(response.citationIds()).isEmpty();
        assertThat(response.handoffRequired()).isTrue();
    }

    @Test
    void rejectsJavaNullWithControlledException() { reject(null); }

    @Test
    void rejectsEmptyInputWithControlledException() {
        for (String input : new String[]{"", " \n\t"}) { reject(input); }
    }

    @Test
    void rejectsNonObjectRoots() {
        for (String input : new String[]{"null", "[]", "42", "\"answer\"", "true"}) { reject(input); }
    }

    @Test
    void rejectsMalformedJson() { reject("not-json"); }

    @Test
    void requiresEveryProtocolField() {
        reject("{\"uncertainty\":0.9,\"citationIds\":[\"cit-1\"],\"handoffRequired\":false}");
        reject("{\"answer\":\"回答\",\"citationIds\":[\"cit-1\"],\"handoffRequired\":false}");
        reject("{\"answer\":\"回答\",\"uncertainty\":0.9,\"handoffRequired\":false}");
        reject("{\"answer\":\"回答\",\"uncertainty\":0.9,\"citationIds\":[\"cit-1\"]}");
    }

    @Test
    void rejectsNonTextAnswersIndependently() {
        for (String value : new String[]{"42", "false", "null", "{}", "[]"}) {
            reject(json(value, "0.9", "[\"cit-1\"]", "false"));
        }
    }

    @Test
    void rejectsBlankAnswers() {
        for (String value : new String[]{"\"\"", "\" \\n\\t\""}) {
            reject(json(value, "0.9", "[\"cit-1\"]", "false"));
        }
    }

    @Test
    void rejectsNonNumericScoresIndependently() {
        for (String value : new String[]{"\"0.9\"", "true", "null", "{}", "[]"}) {
            reject(json("\"回答\"", value, "[\"cit-1\"]", "false"));
        }
    }

    @Test
    void rejectsOutOfRangeOrOverflowScores() {
        for (String value : new String[]{"-0.01", "1.01", "1e400"}) {
            reject(json("\"回答\"", value, "[\"cit-1\"]", "false"));
        }
    }

    @Test
    void requiresCitationArray() {
        for (String value : new String[]{"\"cit-1\"", "null", "{}", "7"}) {
            reject(json("\"回答\"", "0.9", value, "false"));
        }
    }

    @Test
    void rejectsInvalidCitationMembersIncludingMixedArray() {
        for (String value : new String[]{"7", "false", "null", "{}", "[]", "\"\"", "\" \\t\""}) {
            reject(json("\"回答\"", "0.9", "[\"cit-1\"," + value + "]", "false"));
        }
    }

    @Test
    void requiresActualBooleanHandoffIndependently() {
        for (String value : new String[]{"\"false\"", "0", "null", "{}", "[]"}) {
            reject(json("\"回答\"", "0.9", "[\"cit-1\"]", value));
        }
    }

    private static void reject(String input) {
        assertThatThrownBy(() -> ModelOutputParser.parse(input))
                .as("必须受控拒绝输入：%s", input).isInstanceOf(ModelOutputFormatException.class);
    }

    private static String json(String answer, String score, String citations, String handoff) {
        return "{\"answer\":" + answer + ",\"uncertainty\":" + score
                + ",\"citationIds\":" + citations + ",\"handoffRequired\":" + handoff + "}";
    }
}
