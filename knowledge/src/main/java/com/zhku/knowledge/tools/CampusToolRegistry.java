package com.zhku.knowledge.tools;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * 校园工具的唯一注册入口。
 *
 * <p>使用稳定名称而非调用方持有工具实例，避免模型定义与实际执行对象在不同调用链中漂移。</p>
 */
public final class CampusToolRegistry {

    private final Map<String, CampusTool> toolsByName;

    public CampusToolRegistry(Collection<? extends CampusTool> tools) {
        Objects.requireNonNull(tools, "工具集合不能为空");
        Map<String, CampusTool> registered = new TreeMap<>();
        for (CampusTool tool : tools) {
            CampusTool candidate = Objects.requireNonNull(tool, "校园工具不能为空");
            String name = candidate.descriptor().name();
            if (registered.putIfAbsent(name, candidate) != null) {
                // 同名覆盖会使模型看到的参数说明对应到另一个实现，必须在装配时失败。
                throw new IllegalArgumentException("重复注册校园工具：" + name);
            }
        }
        // Map.copyOf 不承诺保留 TreeMap 的迭代顺序，会让模型工具清单在不同运行中漂移。
        this.toolsByName = Collections.unmodifiableMap(new TreeMap<>(registered));
    }

    /**
     * 返回当前获准暴露给调用适配层的全部低风险工具。
     * 新工具必须显式加入此处，避免仅有实现却被模型侧悄然调用或遗漏审查。
     */
    public static CampusToolRegistry withDefaultCampusTools() {
        return new CampusToolRegistry(List.of(
                new GraduationCreditProgressTool(),
                new ScheduleConflictTool(),
                new SampleAcademicScheduleTool(),
                new CourseCreditSummaryTool()
        ));
    }

    public List<CampusToolDescriptor> descriptors() {
        return toolsByName.values().stream()
                .map(CampusTool::descriptor)
                .toList();
    }

    public Optional<CampusToolDescriptor> findDescriptor(String toolName) {
        Objects.requireNonNull(toolName, "工具名称不能为空");
        return Optional.ofNullable(toolsByName.get(toolName))
                .map(CampusTool::descriptor);
    }

    public CampusToolResult execute(CampusToolCall call) {
        Objects.requireNonNull(call, "工具调用不能为空");
        CampusTool tool = toolsByName.get(call.toolName());
        if (tool == null) {
            return CampusToolResult.failure(
                    call.toolName(),
                    CampusToolStatus.UNKNOWN_TOOL,
                    "{\"error\":\"UNKNOWN_TOOL\"}",
                    "工具未注册，不能执行或猜测结果。"
            );
        }
        return tool.execute(call.argumentsJson());
    }
}
