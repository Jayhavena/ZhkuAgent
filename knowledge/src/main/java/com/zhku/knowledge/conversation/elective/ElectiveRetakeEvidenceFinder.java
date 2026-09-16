package com.zhku.knowledge.conversation.elective;

import java.util.Optional;

/**
 * 为指定课程类别查找可用于学生回答的当前证据。
 *
 * <p>调用方只能在返回证据时作答；空结果必须降级为资料不足，不能用相邻条款补全结论。</p>
 */
@FunctionalInterface
public interface ElectiveRetakeEvidenceFinder {

    Optional<ElectiveRetakeEvidence> find(CourseType courseType);
}
