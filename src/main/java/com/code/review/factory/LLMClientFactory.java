package com.code.review.factory;

import com.code.review.client.*;
import com.code.review.utils.SpringUtils;
import org.springframework.stereotype.Component;

@Component
public class LLMClientFactory {

    public static LLMClient getClient() {
        String provider = SpringUtils.getPropertyOrDefault("LLM_PROVIDER", "deepseek");
        switch (provider.toLowerCase()) {
            case "openai":
                return new OpenAiClient();
            case "zhipuai":
                return new ZhiPuAiClient();
            case "qwen":
                return new QwenClient();
            case "deepseek":
                return new DeepSeekClient();
            case "coze":
                return new CozeClient();
            default:
                throw new IllegalArgumentException("Unknown LLM provider: " + provider);
        }
    }
}
