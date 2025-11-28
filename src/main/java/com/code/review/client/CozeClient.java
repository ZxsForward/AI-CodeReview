package com.code.review.client;

import com.code.review.utils.SpringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class CozeClient implements LLMClient {

    private static final ObjectMapper objectMapper = SpringUtils.getBean(ObjectMapper.class);
    private static final Pattern EVENT_PATTERN = Pattern.compile("id: (.*?)\\nevent: (.*?)\\ndata: (.*?)(?=\\n\\nid:|\\Z)", Pattern.DOTALL);

    @Override
    public String completions(List<Map<String, String>> messages) {
        return completions(messages, "");
    }

    @Override
    public String completions(List<Map<String, String>> messages, String model) {
        // 获取API密钥
        String cozeApiKey = SpringUtils.getPropertyOrDefault("COZE_API_KEY", "");
        if (StringUtils.isBlank(cozeApiKey)) {
            throw new RuntimeException("COZE API key is required. Please provide it or set it in the environment variables.");
        }

        // 工作流Workflow ID
        String workflowId = SpringUtils.getPropertyOrDefault("COZE_WORKFLOW_ID", "");
        if (StringUtils.isBlank(workflowId)) {
            throw new RuntimeException("COZE workflow ID is required. Please provide it or set it in the environment variables.");
        }

        // 请求地址
        String url = SpringUtils.getPropertyOrDefault("COZE_API_URL", "https://api.coze.cn/v1/workflow/stream_run");

        // 构建请求体参数，指定模型和相关配置
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("workflow_id", workflowId);
        requestBody.put("parameters", messages.get(0));

        // 调用AI模型接口获取结果
        String response = callAIModel(url, requestBody, cozeApiKey);

        if (StringUtils.isBlank(response)) {
            return "";
        }

        return parseCozeResponse(response);
    }

    /**
     * 解析Coze工作流的响应数据，提取其中的消息内容
     *
     * @param response Coze工作流返回的原始响应字符串
     * @return 解析后拼接的消息内容字符串
     */
    private String parseCozeResponse(String response) {
        StringBuilder result = new StringBuilder();
        Matcher matcher = EVENT_PATTERN.matcher(response);

        // 遍历匹配到的所有事件，提取消息类型事件的内容
        while (matcher.find()) {
            String id = matcher.group(1).trim();
            String eventType = matcher.group(2).trim();
            Object data = parseData(matcher.group(3).trim());

            // 只处理消息类型的事件，且数据为JSON节点时提取内容
            if ("Message".equals(eventType) && data instanceof JsonNode) {
                JsonNode dataNode = (JsonNode) data;
                String content = dataNode.path("content").asText("");
                result.append(content);
            }
        }

        return result.toString();
    }

    /**
     * 解析数据字符串为JSON节点对象
     *
     * @param dataStr 待解析的数据字符串，可能为空或JSON格式字符串
     * @return 如果输入为空字符串或"{}"则返回null；
     * 如果能成功解析为JSON则返回JsonNode对象；
     * 如果解析失败则返回原始字符串
     */
    private Object parseData(String dataStr) {
        // 处理空数据情况：空字符串或空JSON对象字符串返回null
        if (dataStr.isEmpty() || "{}".equals(dataStr)) {
            return null;
        }

        try {
            // 尝试将字符串解析为JSON节点树
            return objectMapper.readTree(dataStr);
        } catch (Exception e) {
            // 解析失败时返回原始字符串作为fallback
            return dataStr; // 返回原始字符串
        }
    }
}
