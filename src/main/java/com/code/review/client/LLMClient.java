package com.code.review.client;

import com.code.review.utils.HttpClientUtils;
import com.code.review.utils.SpringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface LLMClient {
    String completions(List<Map<String, String>> messages);

    String completions(List<Map<String, String>> messages, String model);

    /**
     * 构建通用的基础请求体
     *
     * @param messages 消息列表，每个消息包含角色和内容信息
     * @param model    模型名称
     * @return 包含模型参数的请求体Map
     */
    default Map<String, Object> generalBaseRequestBody(List<Map<String, String>> messages, String model) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.8);
        requestBody.put("stream", false);
        requestBody.put("max_tokens", 6000);

        return requestBody;
    }

    /**
     * 调用AI模型接口获取结果
     *
     * @param url         AI服务接口地址
     * @param requestBody 请求体参数映射
     * @param apiKey      认证密钥
     * @return AI模型返回的响应结果
     */
    default String callAIModel(String url, Map<String, Object> requestBody, String apiKey) {
        try {
            // 构建HTTP请求头，包含认证信息和内容类型
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("Authorization", "Bearer " + apiKey);

            // 获取objectMapper
            ObjectMapper objectMapper = SpringUtils.getBean(ObjectMapper.class);

            // 将请求体转换为JSON字符串格式
            String jsonBody = objectMapper.writeValueAsString(requestBody);

            // 发送POST请求到AI服务接口
            return HttpClientUtils.post(url, jsonBody, null, headers);
        } catch (Exception e) {
            throw new RuntimeException("获取AI内容出现异常，异常原因：" + e.getMessage());
        }
    }
}
