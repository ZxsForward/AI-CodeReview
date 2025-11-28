package com.code.review.utils;

import com.code.review.client.LLMClient;
import com.code.review.factory.LLMClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class AICodeReviewUtils {

    private static final Map<String, Map<String, String>> prompts;
    private static final LLMClient llmClient;
    private static final Pattern SCORE_PATTERN = Pattern.compile("总分[:：]\\s*(\\d+)分?");

    public AICodeReviewUtils() {
    }

    static {
        prompts = loadPrompts(SpringUtils.getPropertyOrDefault("REVIEW_STYLE", "professional"));
        llmClient = LLMClientFactory.getClient();
    }

    /**
     * 加载指定样式的提示模板
     *
     * @param style 样式标识，用于指定要加载的提示模板类型
     * @return 返回包含提示模板内容的Map对象，键为模板名称，值为模板内容
     */
    private static Map<String, Map<String, String>> loadPrompts(String style) {
        // 读取resources文件夹下的prompt_templates.yml文件内容
        return new PromptLoader().loadPrompts("code_review_prompt", style);
    }

    /**
     * 审查并清理代码文本
     * <p>
     * 该方法首先检查输入的代码文本是否为空，如果为空则直接返回提示信息。
     * 然后根据配置的最大token数限制，对过长的代码文本进行截断处理。
     * 最后调用代码审查方法对处理后的代码进行审查，并对审查结果进行格式化处理。
     *
     * @param changeText  待审查的代码变更文本
     * @param commitText  提交信息文本
     * @param projectName 项目名称
     * @return 代码审查结果字符串，如果输入代码为空则返回"代码为空"，否则返回格式化后的审查结果
     */
    public static String reviewAndStripCode(String changeText, String commitText, String projectName) {
        if (StringUtils.isBlank(changeText)) {
            log.info("changeText is blank");
            return "代码为空";
        }

        // 获取最长token数
        int reviewMaxTokens = Integer.parseInt(SpringUtils.getPropertyOrDefault("REVIEW_MAX_TOKENS", "10000"));

        // 计算tokens数量，如果超过REVIEW_MAX_TOKENS，截断changes_text
        int countTokens = TokenUtils.countTokens(changeText);
        if (countTokens > reviewMaxTokens) {
            changeText = TokenUtils.truncateTextByTokens(changeText, reviewMaxTokens);
        }

        // 调用代码审查方法，获取审查结果
        String reviewResult = reviewCode(changeText, commitText, projectName).trim();
        if (reviewResult.startsWith("```markdown") && reviewResult.endsWith("```")) {
            reviewResult = reviewResult.substring(10, reviewResult.length() - 3);
        }

        return reviewResult.trim();
    }

    /**
     * 对代码变更进行审查并生成审查意见
     *
     * @param changeText  变更的代码内容文本
     * @param commitText  提交信息文本
     * @param projectName 项目名称
     * @return LLM模型返回的代码审查结果
     */
    private static String reviewCode(String changeText, String commitText, String projectName) {
        // 构建消息数组，包含系统提示和用户问题
        List<Map<String, String>> messages = new ArrayList<>();

        String provider = SpringUtils.getPropertyOrDefault("LLM_PROVIDER", "deepseek");
        if ("coze".equals(provider)) {
            Map<String, String> message = new HashMap<>();
            message.put("commits_text", commitText);
            message.put("diffs_text", changeText);
            message.put("project_name", projectName);

            messages.add(message);
        } else {
            Map<String, String> systemMessage = prompts.get("system_message");
            Map<String, String> userMessage = new HashMap<>(prompts.get("user_message"));
            String content = String.format(userMessage.get("content"), changeText, commitText);
            userMessage.put("content", content);

            messages.add(systemMessage);
            messages.add(userMessage);
        }
        return callLLM(messages);
    }

    /**
     * 调用大语言模型进行代码审查
     *
     * @param messages 包含对话历史的消息列表，每个Map包含角色和内容信息
     * @return 大语言模型的响应结果字符串
     */
    private static String callLLM(List<Map<String, String>> messages) {
        // 记录发送AI代码审查请求的日志
        log.info("向 AI 发送代码 Review 请求");
        // 调用大语言模型客户端获取代码审查结果
        String reviewResult = llmClient.completions(messages);
        // 记录AI代码审查响应的日志
        log.debug("AI 响应代码 Review 结果: \n{}", reviewResult);

        return StringUtils.isBlank(reviewResult) ? "" : reviewResult;
    }

    /**
     * 解析评审结果字符串，提取其中的评分分数
     *
     * @param reviewResult 评审结果字符串，可能包含评分信息
     * @return 解析出的评分分数，如果无法解析或输入为空则返回0
     */
    public static int parseReviewScore(String reviewResult) {
        // 检查输入字符串是否为空或空白
        if (StringUtils.isBlank(reviewResult)) {
            return 0;
        }

        // 使用正则表达式匹配评分模式
        Matcher matcher = SCORE_PATTERN.matcher(reviewResult);

        // 查找匹配的评分信息并解析为整数
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        // 未找到匹配的评分信息，返回默认值0
        return 0;
    }
}
