package com.code.review.client;

import com.code.review.utils.SpringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

@Slf4j
public class OpenAiClient implements LLMClient {
    @Override
    public String completions(List<Map<String, String>> messages) {
        // 获取模型名称
        String openaiApiModel = SpringUtils.getPropertyOrDefault("OPENAI_API_MODEL", "gpt-4o-mini");
        return completions(messages, openaiApiModel);
    }

    @Override
    public String completions(List<Map<String, String>> messages, String model) {
        // 获取API密钥
        String openaiApiKey = SpringUtils.getPropertyOrDefault("OPENAI_API_KEY", "");
        if (StringUtils.isBlank(openaiApiKey)) {
            throw new RuntimeException("OPENAI API key is required. Please provide it or set it in the environment variables.");
        }
        // 请求地址
        String url = SpringUtils.getPropertyOrDefault("OPENAI_API_URL", "https://api.openai.com/v1/chat/completions");

        // 构建请求体参数，指定模型和相关配置
        Map<String, Object> requestBody = generalBaseRequestBody(messages, model);

        // 调用AI模型接口获取结果
        String response = callAIModel(url, requestBody, openaiApiKey);

        if (StringUtils.isBlank(response)) {
            return "";
        }

        try {
            ObjectMapper objectMapper = SpringUtils.getBean(ObjectMapper.class);
            return objectMapper.readTree(response).path("choices").path(0).path("message").path("content").asText("");
        } catch (Exception e) {
            log.error("openai response parse error: {}", e.getMessage());
            return "";
        }
    }
}
