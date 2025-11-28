package com.code.review.entity.github;

import com.code.review.utils.HttpClientUtils;
import com.code.review.utils.SpringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Data
public class PullRequestHandlerEntity {
    private JsonNode rootNode;
    private String gitHubToken;
    private String gitHubUrl;
    private Integer pullRequestNumber;
    private String eventType = "pull_request";
    private String repoFullName;
    private String action;
    private String sourceBranch;
    private String targetBranch;
    private String projectName;
    private ObjectMapper objectMapper = SpringUtils.getBean(ObjectMapper.class);

    public PullRequestHandlerEntity() {

    }

    public PullRequestHandlerEntity(JsonNode rootNode, String gitHubToken, String gitHubUrl) {
        this.rootNode = rootNode;
        this.gitHubToken = gitHubToken;
        this.gitHubUrl = gitHubUrl;
        this.pullRequestNumber = rootNode.path("pull_request").path("number").asInt(-1);
        this.repoFullName = rootNode.path("repository").path("full_name").asText("");
        this.action = rootNode.path("action").asText("");
        this.sourceBranch = rootNode.path("pull_request").path("head").path("ref").asText("");
        this.targetBranch = rootNode.path("pull_request").path("base").path("ref").asText("");
        this.projectName = rootNode.path("repository").path("name").asText("");
    }

    /**
     * 检查目标分支是否为受保护分支
     *
     * @return boolean 返回目标分支是否受保护，如果检查过程中发生异常则返回false
     */
    public boolean targetBranchProtected() {
        try {
            // 构造获取受保护分支的API URL
            String url = "https://api.github.com/repos/" + this.repoFullName + "/branches?protected=true";

            // 设置请求头信息，包含GitHub访问令牌
            HashMap<String, String> headers = new HashMap<>();
            headers.put("Authorization", "token " + this.gitHubToken);
            headers.put("Accept", "application/vnd.github.v3+json");

            // 发送HTTP GET请求获取受保护分支列表
            String response = HttpClientUtils.get(url, null, headers);
            if (StringUtils.isBlank(response)) {
                log.error("targetBranchProtected http error，url：{}", url);
                return false;
            }

            // 解析响应数据，检查目标分支是否在受保护分支列表中
            JsonNode responseNode = this.objectMapper.readTree(response);
            if (responseNode.isArray()) {
                for (JsonNode node : responseNode) {
                    if (this.targetBranch.equals(node.path("name").asText(""))) {
                        return true;
                    }
                }
            }

            return false;
        } catch (Exception e) {
            log.error("targetBranchProtected exception：", e);
            return false;
        }
    }

    /**
     * 获取 GitHub Pull Request 的变更文件列表。
     * <p>
     * 该方法会检查当前事件类型是否为 "pull_request"，如果不是则记录警告并返回空节点。
     * 若是有效的 Pull Request 事件，则通过 GitHub API 请求对应的变更文件信息，并进行重试机制处理可能的延迟问题。
     * 返回的数据包括每个文件的 diff 内容、新路径、新增行数和删除行数等关键字段。
     *
     * @return JsonNode 包含 Pull Request 变更信息的 JSON 节点。如果失败或无数据，返回 MissingNode 实例。
     */
    public JsonNode getPullRequestChanges() {
        // 检查是否为 Pull Request Hook 事件
        if (!"pull_request".equals(this.eventType)) {
            log.warn("Invalid event type: {}. Only 'pull_request' event is supported now.", this.eventType);
            return MissingNode.getInstance();
        }

        // GitHub pull request changes API可能存在延迟，多次尝试
        int maxRetries = 3; // 最大重试次数
        int retryDelay = 10000; // 重试间隔（毫秒）

        for (int i = 0; i < maxRetries; i++) {
            try {
                // 调用 GitHub API 获取 Pull Request 的 files（变更）
                String url = "https://api.github.com/repos/" + this.repoFullName + "/pulls/" + this.pullRequestNumber + "/files";

                // 设置请求头信息，包含GitHub访问令牌
                HashMap<String, String> headers = new HashMap<>();
                headers.put("Authorization", "token " + this.gitHubToken);
                headers.put("Accept", "application/vnd.github.v3+json");

                String response = HttpClientUtils.get(url, null, headers);

                if (StringUtils.isBlank(response)) {
                    log.error("Failed to get changes from GitHub，url：{}", url);
                    // 如果是最后一次尝试，则返回空节点
                    if (i == maxRetries - 1) {
                        return MissingNode.getInstance();
                    }
                } else {
                    JsonNode jsonNode = this.objectMapper.readTree(response);
                    // 成功获取到有效数据则直接返回
                    if (!jsonNode.isEmpty()) {
                        ArrayNode changesArrayNode = this.objectMapper.createArrayNode();

                        for (JsonNode node : jsonNode) {
                            ObjectNode changeNode = this.objectMapper.createObjectNode();

                            changeNode.put("diff", node.path("patch").asText(""));
                            changeNode.put("new_path", node.path("filename").asText(""));
                            changeNode.put("additions", node.path("additions").asInt(0));
                            changeNode.put("deletions", node.path("deletions").asInt(0));

                            changesArrayNode.add(changeNode);
                        }

                        return changesArrayNode;
                    } else {
                        log.info("Changes is empty, retrying in {} seconds...(attempt {} / {})，url：{}", retryDelay / 1000, i + 1, maxRetries, url);
                        // 如果不是最后一次尝试，则等待后继续重试
                        if (i < maxRetries - 1) {
                            Thread.sleep(retryDelay);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("getPullRequestChanges error：{}", e.getMessage());
                // 如果是最后一次尝试，则返回空节点
                if (i == maxRetries - 1) {
                    return MissingNode.getInstance();
                }
                try {
                    Thread.sleep(retryDelay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return MissingNode.getInstance();
                }
            }
        }

        // 尝试完所有次数仍未获得有效数据，返回空节点
        return MissingNode.getInstance();
    }

    /**
     * 获取 Pull Request 的提交记录（commits）信息。
     * <p>
     * 该方法仅在当前事件类型为 "pull_request" 时有效。它会调用 GitHub API，
     * 请求指定仓库和 Pull Request 编号对应的提交列表，并解析返回的 JSON 数据，
     * 提取关键字段封装成新的 JSON 结构返回。
     * </p>
     *
     * @return 返回一个包含所有提交信息的 JsonNode 对象；如果请求失败或事件类型不匹配，则返回 MissingNode 实例。
     */
    public JsonNode getPullRequestCommits() {
        try {
            // 检查是否为 Pull Request Hook 事件
            if (!"pull_request".equals(this.eventType)) {
                log.warn("Invalid event type: {}. Only 'pull_request' event is supported now.", this.eventType);
                return MissingNode.getInstance();
            }

            // 调用 GitHub API 获取 Pull Request 的 commits
            String url = "https://api.github.com/repos/" + this.repoFullName + "/pulls/" + this.pullRequestNumber + "/commits";

            // 设置请求头信息，包含GitHub访问令牌
            HashMap<String, String> headers = new HashMap<>();
            headers.put("Authorization", "token " + this.gitHubToken);
            headers.put("Accept", "application/vnd.github.v3+json");

            String response = HttpClientUtils.get(url, null, headers);

            if (StringUtils.isBlank(response)) {
                log.error("getMergeRequestCommits http error，url：{}", url);
                return MissingNode.getInstance();
            }

            // 解析JSON响应数据并提取所需字段
            JsonNode jsonNode = this.objectMapper.readTree(response);
            ArrayNode changesArrayNode = this.objectMapper.createArrayNode();

            for (JsonNode node : jsonNode) {
                ObjectNode changeNode = this.objectMapper.createObjectNode();
                JsonNode commitNode = node.path("commit");

                changeNode.put("id", node.path("sha").asText(""));
                changeNode.put("title", commitNode.path("message").asText("").split("\n")[0]);
                changeNode.put("message", commitNode.path("message").asText(""));
                changeNode.put("author_name", commitNode.path("author").path("name").asText(""));
                changeNode.put("author_email", commitNode.path("author").path("email").asText(""));
                changeNode.put("created_at", commitNode.path("author").path("date").asText(""));
                changeNode.put("web_url", node.path("html_url").asText(""));

                changesArrayNode.add(changeNode);
            }

            return changesArrayNode;
        } catch (Exception e) {
            log.error("getPullRequestCommits error：{}", e.getMessage());
            return MissingNode.getInstance();
        }
    }

    /**
     * 为Pull Request添加备注信息
     *
     * @param message 要添加的备注消息内容
     */
    public void addPullRequestNote(String message) {
        // 检查是否为 Pull Request Hook 事件
        if (!"pull_request".equals(this.eventType)) {
            log.warn("Invalid event type: {}. Only 'pull_request' event is supported now.", this.eventType);
            return;
        }

        // 构建 GitHub API URL
        String url = "https://api.github.com/repos/" + this.repoFullName + "/issues/" + this.pullRequestNumber + "/comments";

        // 设置请求头信息，包含GitHub访问令牌
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Authorization", "token " + this.gitHubToken);
        headers.put("Accept", "application/vnd.github.v3+json");

        // 设置请求参数
        Map<String, String> params = new HashMap<>();
        params.put("body", message);

        // 发送 POST 请求添加备注
        HttpClientUtils.post(url, null, params, headers);
    }
}
