package com.code.review.client;

import com.code.review.utils.SpringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

@Slf4j
public class DeepSeekClient implements LLMClient {
    @Override
    public String completions(List<Map<String, String>> messages) {
        // 获取模型名称
        String deepseekApiModel = SpringUtils.getPropertyOrDefault("DEEPSEEK_API_MODEL", "deepseek-chat");
        return completions(messages, deepseekApiModel);
    }

    @Override
    public String completions(List<Map<String, String>> messages, String model) {
        // 获取API密钥
        String deepseekApiKey = SpringUtils.getPropertyOrDefault("DEEPSEEK_API_KEY", "");
        if (StringUtils.isBlank(deepseekApiKey)) {
            throw new RuntimeException("DEEPSEEK API key is required. Please provide it or set it in the environment variables.");
        }
        // 请求地址
        String url = SpringUtils.getPropertyOrDefault("DEEPSEEK_API_URL", "https://api.deepseek.com/chat/completions");

        // 构建请求体参数，指定模型和相关配置
        Map<String, Object> requestBody = generalBaseRequestBody(messages, model);

        // 调用AI模型接口获取结果
        String response = callAIModel(url, requestBody, deepseekApiKey);

        if (StringUtils.isBlank(response)) {
            return "";
        }

        try {
            ObjectMapper objectMapper = SpringUtils.getBean(ObjectMapper.class);
            return objectMapper.readTree(response).path("choices").path(0).path("message").path("content").asText("");
        } catch (Exception e) {
            log.error("deepseek response parse error: {}", e.getMessage());
            return "";
        }
    }
}
