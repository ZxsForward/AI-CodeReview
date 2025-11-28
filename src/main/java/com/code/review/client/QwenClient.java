package com.code.review.client;

import com.code.review.utils.SpringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class QwenClient implements LLMClient {
    @Override
    public String completions(List<Map<String, String>> messages) {
        // 获取模型名称
        String qwenApiModel = SpringUtils.getPropertyOrDefault("QWEN_API_MODEL", "qwen-plus");
        return completions(messages, qwenApiModel);
    }

    @Override
    public String completions(List<Map<String, String>> messages, String model) {
        // 获取API密钥
        String qwenApiKey = SpringUtils.getPropertyOrDefault("QWEN_API_KEY", "");
        if (StringUtils.isBlank(qwenApiKey)) {
            throw new RuntimeException("QWEN API key is required. Please provide it or set it in the environment variables.");
        }
        // 请求地址
        String url = SpringUtils.getPropertyOrDefault("QWEN_API_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions");

        // 构建请求体参数，指定模型和相关配置
        Map<String, Object> requestBody = generalBaseRequestBody(messages, model);
        Map<String, Boolean> extraBody = new HashMap<>();
        extraBody.put("enable_thinking", false);
        requestBody.put("extra_body", extraBody);

        // 调用AI模型接口获取结果
        String response = callAIModel(url, requestBody, qwenApiKey);

        if (StringUtils.isBlank(response)) {
            return "";
        }

        try {
            ObjectMapper objectMapper = SpringUtils.getBean(ObjectMapper.class);
            return objectMapper.readTree(response).path("choices").path(0).path("message").path("content").asText("");
        } catch (Exception e) {
            log.error("qwen response parse error: {}", e.getMessage());
            return "";
        }
    }
}
