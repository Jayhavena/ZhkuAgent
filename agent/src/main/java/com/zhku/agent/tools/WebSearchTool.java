package com.zhku.agent.tools;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import com.zhku.agent.agent.model.AgentExecutionFailure;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 网页搜索工具
 */
public class WebSearchTool {

    // SearchAPI 的搜索接口地址
    private static final String SEARCH_API_URL = "https://www.searchapi.io/api/v1/search";

    private final String apiKey;
    private final java.util.function.BiFunction<String, Map<String, Object>, String> transport;

    public WebSearchTool(String apiKey) {
        this(apiKey, HttpUtil::get);
    }

    WebSearchTool(String apiKey, java.util.function.BiFunction<String, Map<String, Object>, String> transport) {
        this.apiKey = apiKey;
        this.transport = java.util.Objects.requireNonNull(transport);
    }

    @Tool(description = "Search for information from Baidu Search Engine")
    public String searchWeb(
            @ToolParam(description = "Search query keyword") String query) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("q", query);
        paramMap.put("api_key", apiKey);
        paramMap.put("engine", "baidu");
        try {
            String response = transport.apply(SEARCH_API_URL, paramMap);
            // 取出返回结果的前 5 条
            JSONObject jsonObject = JSONUtil.parseObj(response);
            // 提取 organic_results 部分
            Object rawResults = jsonObject.get("organic_results");
            if (!(rawResults instanceof JSONArray organicResults)) throw new IllegalStateException("搜索结果格式无效");
            // 空数组是合法的零结果；缺字段或异常必须作为失败，不能伪装成可供模型引用的搜索文本。
            List<Object> objects = organicResults.subList(0, Math.min(5, organicResults.size()));
            // 拼接搜索结果为字符串
            String result = objects.stream().map(obj -> {
                if (!(obj instanceof JSONObject tmpJSONObject)) throw new IllegalStateException("搜索条目格式无效");
                return tmpJSONObject.toString();
            }).collect(Collectors.joining(","));
            return result;
        } catch (Exception e) {
            throw new AgentExecutionFailure(AgentExecutionFailure.Category.TOOL_FAILURE);
        }
    }
}
