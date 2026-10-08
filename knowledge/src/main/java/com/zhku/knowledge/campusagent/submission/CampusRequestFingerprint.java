package com.zhku.knowledge.campusagent.submission;

import com.zhku.knowledge.campusagent.CampusCreditAgentService.Request;
import com.zhku.knowledge.campusagent.CampusCreditAgentService.CourseInput;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/** v1固定字段规范化；版本和课程顺序属于协议，不能随展示或模型配置变化。 */
public final class CampusRequestFingerprint {
    private static final ObjectMapper JSON = new ObjectMapper();
    private CampusRequestFingerprint() { }
    public static Request normalize(Request request) {
        Objects.requireNonNull(request);
        if (request.courses().size() > 20) throw new IllegalArgumentException("课程数量超限");
        var courses = request.courses().stream().map(course -> {
            var id = text(course.courseId(), 64);
            var category = text(course.category(), 32);
            var credits = course.credits() == null ? null : course.credits().stripTrailingZeros();
            // 先限制数值表示，不能展开巨大指数；负值/null仍交由原领域规则澄清。
            if (credits != null && (credits.precision() > 16 || credits.scale() < -6 || credits.scale() > 6))
                throw new IllegalArgumentException("学分数值表示超限");
            return new CourseInput(id, category, credits);
        }).toList();
        return new Request(text(request.question(), 500), courses);
    }
    private static String text(String value, int max) {
        if (value == null) return null;
        var normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException("字段长度超限");
        return normalized;
    }
    public static CampusRunSubmission.Fingerprint of(Request request) {
        var normalized = normalize(request);
        var node = JSON.createObjectNode(); node.put("question", normalized.question());
        var courses = node.putArray("courses");
        for (var course : normalized.courses()) {
            var item = courses.addObject(); item.put("courseId", course.courseId()); item.put("category", course.category());
            if (course.credits() == null) item.putNull("credits");
            else item.put("credits", course.credits().toString());
        }
        try {
            var hash = MessageDigest.getInstance("SHA-256").digest(node.toString().getBytes(StandardCharsets.UTF_8));
            return new CampusRunSubmission.Fingerprint(1, HexFormat.of().formatHex(hash));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256不可用"); }
    }
}
