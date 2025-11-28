package com.code.review.notify;

import com.code.review.utils.HttpClientUtils;
import com.code.review.utils.SpringUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class WXWorkNotifier {

    private final String defaultWebhookUrl;
    private final boolean enabled;

    public WXWorkNotifier() {
        this.defaultWebhookUrl = SpringUtils.getPropertyOrDefault("WECOM_WEBHOOK_URL", "");
        this.enabled = "1".equals(SpringUtils.getPropertyOrDefault("WECOM_ENABLED", "0"));
    }

    /**
     * 发送企业微信消息
     *
     * @param content     消息内容
     * @param msgType     消息类型，支持"text"和"markdown"类型
     * @param title       消息标题
     * @param projectName 项目名称，用于获取对应的webhook配置
     * @param urlSlug     URL标识符，用于获取对应的webhook配置
     */
    public void sendWXWorkMessage(String content, String msgType, String title, String projectName, String urlSlug) {
        if (!this.enabled) {
            log.info("WXWorkNotifier is disabled.");
            return;
        }
        try {
            // 获取群机器人webhook url
            String webhookUrl = getWebhookUrl(projectName, urlSlug);

            // 企业微信消息内容最大长度限制
            // text类型最大2048字节，markdown类型最大4096字节
            int maxLength = "text".equals(msgType) ? 2000 : 4000; // 留出安全边际

            // 检查内容长度
            int contentLength = content.getBytes(StandardCharsets.UTF_8).length;

            // 内容长度在限制范围内，直接发送
            if (contentLength <= maxLength) {
                // 构建消息数据
                String data = buildMessage(content, msgType, title);
                // 发送消息
                sendMessage(webhookUrl, data);
            } else {
                // 内容超过限制，需要分割发送
                log.warn("消息内容超过{}字节，将进行分片发送。总长度：{}字节", maxLength, contentLength);
                // 分块发送
                sendMessageInChunks(content, title, webhookUrl, msgType, maxLength);
            }
        } catch (Exception e) {
            log.error("Failed to send WXWork message: {}", e.getMessage());
        }
    }

    /**
     * 将消息内容分块发送到指定的webhook地址
     *
     * @param content    消息内容
     * @param title      消息标题，可为空
     * @param webhookUrl webhook地址
     * @param msgType    消息类型
     * @param maxLength  每个消息块的最大长度
     * @throws JsonProcessingException 当JSON处理出现异常时抛出
     */
    private void sendMessageInChunks(String content, String title, String webhookUrl, String msgType, int maxLength) throws JsonProcessingException {
        // 将内容按照最大长度分割成多个块
        List<String> chunks = splitContent(content, maxLength);

        // 遍历所有块并逐个发送
        for (int i = 0; i < chunks.size(); i++) {
            // 构造带有序号的标题
            String chunkTitle = (title != null) ?
                    String.format("%s (第%d/%d部分)", title, i + 1, chunks.size()) :
                    String.format("消息 (第%d/%d部分)", i + 1, chunks.size());

            // 构建消息数据
            String data = buildMessage(chunks.get(i), msgType, chunkTitle);
            // 发送消息
            sendMessage(webhookUrl, data, i + 1, chunks.size());
        }
    }

    /**
     * 将给定的字符串内容按照指定的最大字节数进行分割
     *
     * @param content  需要分割的字符串内容
     * @param maxBytes 每个分块的最大字节数
     * @return 分割后的字符串列表，每个字符串的字节长度不超过maxBytes
     */
    private List<String> splitContent(String content, int maxBytes) {
        List<String> chunks = new ArrayList<>();
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        int contentLength = contentBytes.length;
        int startPos = 0;

        while (startPos < contentLength) {
            int endPos = Math.min(startPos + maxBytes, contentLength);

            // 如果不是最后一块，尝试在换行符处分割
            if (endPos < contentLength) {
                // 寻找最近的换行符，但不超过起始位置
                int originalEndPos = endPos;
                while (endPos > startPos && contentBytes[endPos - 1] != '\n') {
                    endPos--;
                }

                // 如果找不到换行符或者调整后块太小，使用原始分割点
                if (endPos == startPos || (originalEndPos - endPos) > maxBytes * 0.3) {
                    endPos = originalEndPos;
                }
            }

            // 提取当前块的内容并添加到结果列表中
            String chunk = new String(contentBytes, startPos, endPos - startPos, StandardCharsets.UTF_8);
            chunks.add(chunk);
            startPos = endPos;
        }

        return chunks;
    }

    /**
     * 发送消息到企业微信 webhook 接口。
     * 支持将消息内容分片发送，并记录相应的发送日志和结果。
     *
     * @param webhookUrl  企业微信机器人的 webhook 地址，用于发送消息的目标地址
     * @param data        要发送的消息内容，格式应为 JSON 字符串
     * @param chunkNum    当前消息分片的编号（从1开始），如果为 null 表示不分片发送
     * @param totalChunks 消息总共的分片数量，仅在分片发送时有效
     */
    private void sendMessage(String webhookUrl, String data, Integer chunkNum, Integer totalChunks) {
        // 构建日志前缀
        String logPrefix = buildLogPrefix(chunkNum, totalChunks);
        try {
            // 记录发送日志
            log.debug("{}发送企业微信消息: url={}, data={}", logPrefix, webhookUrl, data);

            // 设置请求头并发送POST请求
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            String response = HttpClientUtils.post(webhookUrl, data, null, headers);

            // 解析响应内容，判断发送是否成功
            if (StringUtils.isNotBlank(response)) {
                JsonNode rootNode = SpringUtils.getBean(ObjectMapper.class).readTree(response);
                int errCode = rootNode.path("errcode").asInt(-1);
                if (errCode != 0) {
                    log.error("{}企业微信消息发送失败! webhook_url：{}，errCode：{}，errMsg：{}", logPrefix, webhookUrl, errCode, rootNode.path("errmsg").asText(""));
                } else {
                    log.info("{}企业微信消息发送成功! webhook_url：{}", logPrefix, webhookUrl);
                }
            }
        } catch (Exception e) {
            // 记录发送失败日志
            log.error("{}发送企业微信消息异常: {}", logPrefix, e.getMessage());
        }
    }

    /**
     * 构建日志前缀，用于区分分片消息和普通消息
     *
     * @param chunkNum    分片编号
     * @param totalChunks 总分片数
     * @return 日志前缀字符串
     */
    private String buildLogPrefix(Integer chunkNum, Integer totalChunks) {
        if (chunkNum != null && totalChunks != null) {
            return String.format("分块[%d/%d] ", chunkNum, totalChunks);
        }
        return "";
    }

    /**
     * 发送消息
     *
     * @param webhookUrl webhook url
     * @param data       消息内容
     */
    private void sendMessage(String webhookUrl, String data) {
        sendMessage(webhookUrl, data, null, null);
    }

    /**
     * 构建消息内容
     *
     * @param content 通知内容
     * @param msgType 消息类型，支持"text"和"markdown"两种类型
     * @param title   消息标题，用于markdown类型消息
     * @return 根据消息类型构建的完整消息内容
     * @throws RuntimeException 当消息类型不是"text"或"markdown"时抛出异常
     */
    private String buildMessage(String content, String msgType, String title) throws JsonProcessingException {
        // 根据消息类型构建不同的消息格式
        Map<String, Object> requestBody;
        if ("text".equals(msgType)) {
            requestBody = buildTextMessage(content);
        } else if ("markdown".equals(msgType)) {
            requestBody = buildMarkdownMessage(content, title);
        } else {
            throw new RuntimeException("Invalid message type: " + msgType);
        }
        // 将消息内容转换为JSON字符串
        return SpringUtils.getBean(ObjectMapper.class).writeValueAsString(requestBody);
    }

    /**
     * 构建Markdown格式的消息体
     *
     * @param content 通知内容
     * @param title   消息标题
     * @return 包含Markdown消息格式的Map对象
     */
    private Map<String, Object> buildMarkdownMessage(String content, String title) {
        // 格式化Markdown内容
        String formattedContent = formatMarkdownContent(content, title);

        // 构建请求体Map
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("msgtype", "markdown");
        requestBody.put("markdown", new HashMap<String, Object>() {{
            put("content", formattedContent);
        }});
        return requestBody;
    }

    /**
     * 格式化markdown内容以适配企业微信
     *
     * @param content 内容
     * @param title   标题
     * @return 格式化后的内容
     */
    private String formatMarkdownContent(String content, String title) {
        // 处理标题
        StringBuilder formattedContent = new StringBuilder();
        if (title != null && !title.isEmpty()) {
            formattedContent.append("## ").append(title).append("\n\n");
        }

        // 将内容中的5级以上标题转为4级
        content = content.replaceAll("#{5,}\\s", "#### ");

        // 处理链接格式
        content = content.replaceAll("\\[(.*?)]\\((.*?)\\)", "[链接]$2");

        // 移除HTML标签
        content = content.replaceAll("<[^>]+>", "");

        formattedContent.append(content);
        return formattedContent.toString();
    }

    /**
     * 构建文本消息的请求体Map结构
     *
     * @param content 消息通知内容
     * @return 包含消息类型和文本内容的Map结构
     */
    private Map<String, Object> buildTextMessage(String content) {
        // 构造消息Map结构，包含消息类型和文本内容
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("msgtype", "text");
        requestBody.put("text", new HashMap<String, Object>() {{
            put("content", content);
            put("mentioned_list", new String[]{});
        }});
        return requestBody;
    }

    /**
     * 获取企业微信机器人Webhook URL
     *
     * @param projectName 项目名称，用于查找对应的Webhook配置
     * @param urlSlug     GitLab URL标识，作为备选方案查找Webhook配置
     * @return 返回找到的Webhook URL
     * @throws RuntimeException 当无法找到合适的Webhook URL时抛出异常
     */
    private String getWebhookUrl(String projectName, String urlSlug) {
        // 如果未提供 project_name，返回默认的 Webhook URL
        if (StringUtils.isBlank(projectName)) {
            if (StringUtils.isBlank(this.defaultWebhookUrl)) {
                throw new RuntimeException("No project name provided and no WXWork default webhook URL configured.");
            } else {
                return this.defaultWebhookUrl;
            }
        }

        // 构建目标键
        String targetKeyProject = "WECOM_WEBHOOK_URL_" + projectName.toUpperCase();
        String targetKeyUrlSlug = "WECOM_WEBHOOK_URL_" + urlSlug.toUpperCase();

        // 项目名称对应的Webhook URL
        String projectWebhookUrl = SpringUtils.getPropertyOrDefault(targetKeyProject, "");
        if (StringUtils.isNotBlank(projectWebhookUrl)) {
            return projectWebhookUrl;
        }

        // GitLab URL 对应的 Webhook URL
        String urlSlugWebhookUrl = SpringUtils.getPropertyOrDefault(targetKeyUrlSlug, "");
        if (StringUtils.isNotBlank(urlSlugWebhookUrl)) {
            return urlSlugWebhookUrl;
        }

        // 如果未找到匹配的环境变量，降级使用全局的 Webhook URL
        if (StringUtils.isNotBlank(this.defaultWebhookUrl)) {
            return this.defaultWebhookUrl;
        }

        // 如果既未找到匹配项，也没有默认值，抛出异常
        throw new RuntimeException("未找到项目 " + projectName + " 对应的微信群机器人 Webhook URL，且未设置默认的 Webhook URL。");
    }
}
