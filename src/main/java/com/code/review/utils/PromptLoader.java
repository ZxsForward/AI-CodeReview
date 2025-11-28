package com.code.review.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class PromptLoader {

    private static final Map<String, String> STYLE_DESCRIPTION_MAP = new HashMap<>();

    static {
        STYLE_DESCRIPTION_MAP.put("professional", "评论时请使用标准的工程术语，保持专业严谨");
        STYLE_DESCRIPTION_MAP.put("sarcastic", "评论时请大胆使用讽刺性语言，但要确保技术指正准确");
        STYLE_DESCRIPTION_MAP.put("gentle", "评论时请多用\"建议\"、\"可以考虑\"等温和措辞");
        STYLE_DESCRIPTION_MAP.put("humorous", "评论时请：\n" +
                "    1. 在技术点评中加入适当幽默元素\n" +
                "    2. 合理使用相关Emoji（但不要过度）：\n" +
                "       - \uD83D\uDC1B 表示bug\n" +
                "       - \uD83D\uDCA5 表示严重问题\n" +
                "       - \uD83C\uDFAF 表示改进建议\n" +
                "       - \uD83D\uDD0D 表示需要仔细检查");
    }

    /**
     * 加载指定提示词配置并根据样式渲染系统和用户提示词
     *
     * @param promptKey 提示词配置的键名，用于在配置文件中查找对应的提示词模板
     * @param style     样式参数，用于渲染提示词模板
     * @return 包含系统消息和用户消息的Map，键为"system_message"和"user_message"
     * @throws RuntimeException 当配置文件未找到、提示词配置不存在或加载失败时抛出
     */
    public Map<String, Map<String, String>> loadPrompts(String promptKey, String style) {
        String promptTemplatesFile = "prompt_templates.yml";

        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(promptTemplatesFile)) {
            // 检查配置文件是否存在
            if (inputStream == null) {
                throw new RuntimeException("提示词配置文件未找到: " + promptTemplatesFile);
            }

            // 解析YAML配置文件
            Yaml yaml = new Yaml();
            Map<String, Object> allPrompts = yaml.load(inputStream);
            Map<String, String> prompts = (Map<String, String>) allPrompts.get(promptKey);

            // 检查指定的提示词配置是否存在
            if (CollectionUtils.isEmpty(prompts)) {
                throw new RuntimeException("未找到对应的提示词配置: " + promptKey);
            }

            // 渲染系统提示词和用户提示词模板
            String systemPrompt = renderTemplate(prompts.get("system_prompt"), style);
            String userPrompt = renderTemplate(prompts.get("user_prompt"), style);

            // 构造返回结果
            Map<String, Map<String, String>> result = new HashMap<>();
            result.put("system_message", createMessage("system", systemPrompt));
            result.put("user_message", createMessage("user", userPrompt));

            return result;
        } catch (Exception e) {
            throw new RuntimeException("提示词配置加载失败: " + e.getMessage(), e);
        }
    }

    /**
     * 渲染模板字符串，将样式描述替换到模板中
     *
     * @param template 模板字符串，应包含一个%s占位符用于插入样式描述
     * @param style    样式标识，用于从映射表中获取对应的样式描述
     * @return 渲染后的字符串，如果模板为空则返回空字符串
     */
    private String renderTemplate(String template, String style) {
        if (StringUtils.isBlank(template)) {
            return "";
        }

        // 获取样式描述，如果找不到对应样式则使用默认描述
        String styleDescription = STYLE_DESCRIPTION_MAP.getOrDefault(style, "评论时请使用标准的工程术语，保持专业严谨");

        return template.replaceFirst("\\{style}", styleDescription);
    }

    /**
     * 创建一个消息对象，包含角色和内容信息
     *
     * @param role    消息发送者的角色
     * @param content 消息的内容
     * @return 包含role和content键值对的Map对象
     */
    private Map<String, String> createMessage(String role, String content) {
        // 创建消息Map并填充角色和内容信息
        Map<String, String> message = new HashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }
}