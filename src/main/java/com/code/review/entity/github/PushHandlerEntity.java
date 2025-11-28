package com.code.review.entity.github;

import com.code.review.entity.CommitInfo;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Data
public class PushHandlerEntity {
    private JsonNode rootNode;
    private String gitHubToken;
    private String gitHubUrl;
    private String eventType = "push";
    private String repoFullName;
    private String projectName;
    private String branchName;
    private JsonNode commitList;
    private ObjectMapper objectMapper = SpringUtils.getBean(ObjectMapper.class);

    public PushHandlerEntity() {
    }

    public PushHandlerEntity(JsonNode rootNode, String gitHubToken, String gitHubUrl) {
        this.rootNode = rootNode;
        this.gitHubToken = gitHubToken;
        this.gitHubUrl = gitHubUrl;
        this.repoFullName = rootNode.path("repository").path("full_name").asText("");
        this.projectName = rootNode.path("repository").path("name").asText("");
        this.branchName = rootNode.path("ref").asText("").replace("refs/heads/", "");
        this.commitList = rootNode.path("commits");
    }

    /**
     * 获取推送事件的提交信息列表
     */
    public List<CommitInfo> getPushCommits() {
        List<CommitInfo> commitList = new ArrayList<>();
        // 检查是否是push事件
        if (!"push".equals(this.eventType)) {
            log.warn("Invalid event type: {}. Only 'push' event is supported now.", this.eventType);
            return commitList;
        }
        // 读取提交信息
        for (JsonNode commitNode : this.commitList) {
            String message = commitNode.path("message").asText("");
            String author = commitNode.path("author").path("name").asText("");
            String timestamp = commitNode.path("timestamp").asText("");
            String url = commitNode.path("url").asText("");

            CommitInfo commitInfo = new CommitInfo();
            commitInfo.setMessage(message);
            commitInfo.setAuthor(author);
            commitInfo.setTimestamp(timestamp);
            commitInfo.setUrl(url);

            commitList.add(commitInfo);
        }
        return commitList;
    }

    /**
     * 获取 Push 事件中的代码变更信息。
     * <p>
     * 该方法首先判断当前事件类型是否为 "push"，并且提交列表不为空。
     * 然后根据是否存在 `before` 和 `after` 提交 ID 来决定如何获取变更：
     * - 若存在，则调用 {@link #repositoryCompare(String, String)} 进行比较；
     * - 若不存在，则尝试从每个 commit 中逐个获取其父提交，并进行比较；
     * - 对于创建或删除分支的情况也有特殊处理。
     *
     * @return 包含文件变更信息的 JsonNode，若无法获取则返回 MissingNode 实例
     */
    public JsonNode getPushChanges() {
        // 检测是否为push事件
        if (!"push".equals(this.eventType)) {
            log.warn("Invalid event type: {}. Only 'push' event is supported now.", this.eventType);
            return MissingNode.getInstance();
        }
        if (this.commitList.isEmpty()) {
            log.warn("No commits found in push event.");
            return MissingNode.getInstance();
        }

        // 优先尝试compare API获取变更
        String before = this.rootNode.path("before").asText("");
        String after = this.rootNode.path("after").asText("");

        if (StringUtils.isBlank(before) && StringUtils.isBlank(after)) {
            // GitHub没有0000000的写法，但可以检查是否是创建或删除分支事件
            if (this.rootNode.path("created").asBoolean(false)) {
                // 创建分支处理
                String firstCommitId = this.commitList.path(0).path("id").asText("");
                String parentCommitId = getParentCommitId(firstCommitId);
                if (StringUtils.isNotBlank(parentCommitId)) {
                    before = parentCommitId;
                }
            } else if (this.rootNode.path("deleted").asBoolean(false)) {
                // 删除分支处理
                return MissingNode.getInstance();
            }

            // 比较仓库差异并返回结果
            return repositoryCompare(before, after);
        } else {
            // 如果before和after不存在，尝试通过commits获取
            log.info("before or after not found in webhook data, trying to get changes from commits.");

            ArrayNode arrayNode = this.objectMapper.createArrayNode();

            // 遍历所有提交，逐个获取每个提交与其父提交之间的差异
            for (JsonNode node : this.commitList) {
                String commitId = node.path("id").asText("");
                String parentCommitId = getParentCommitId(commitId);

                // 只有当父提交ID存在时才进行比较
                if (StringUtils.isNotBlank(parentCommitId)) {
                    JsonNode jsonNode = repositoryCompare(parentCommitId, commitId);

                    // 将比较结果中的每个文件变更信息添加到总的结果数组中
                    if (jsonNode.isArray()) {
                        for (JsonNode tempNode : jsonNode) {
                            arrayNode.add(tempNode);
                        }
                    }
                }
            }
            return arrayNode;
        }
    }

    /**
     * 比较仓库中两个提交之间的差异
     *
     * @param before 比较的起始提交SHA或分支名
     * @param after  比较的结束提交SHA或分支名
     * @return 包含文件差异信息的JSON节点，如果发生错误则返回缺失节点
     */
    private JsonNode repositoryCompare(String before, String after) {
        try {
            // 构造获取仓库差异的API URL
            String url = "https://api.github.com/repos/" + this.repoFullName + "/compare/" + before + "..." + after;

            // 设置请求头信息，包含GitHub访问令牌
            HashMap<String, String> headers = new HashMap<>();
            headers.put("Authorization", "token " + this.gitHubToken);
            headers.put("Accept", "application/vnd.github.v3+json");

            // 发送HTTP GET请求获取比较结果
            String response = HttpClientUtils.get(url, null, headers);
            if (StringUtils.isBlank(response)) {
                log.error("repositoryCompare http error，url：{}", url);
                return MissingNode.getInstance();
            }

            // 解析JSON响应数据并提取所需字段
            JsonNode diffNodes = this.objectMapper.readTree(response).path("files");
            ArrayNode diffArrayNode = this.objectMapper.createArrayNode();

            for (JsonNode node : diffNodes) {
                ObjectNode diff = this.objectMapper.createObjectNode();

                diff.put("diff", node.path("patch").asText(""));
                diff.put("new_path", node.path("filename").asText(""));
                diff.put("status", node.path("status").asText(""));
                diff.put("additions", node.path("additions").asInt(0));
                diff.put("deletions", node.path("deletions").asInt(0));

                diffArrayNode.add(diff);
            }

            return diffArrayNode;
        } catch (Exception e) {
            log.error("repositoryCompare exception：", e);
            return MissingNode.getInstance();
        }
    }

    /**
     * 获取指定提交的父提交ID
     *
     * @param commitId 提交ID
     * @return 父提交ID，如果获取失败或无父提交则返回空字符串
     */
    private String getParentCommitId(String commitId) {
        try {
            if (StringUtils.isBlank(commitId)) {
                return "";
            }

            // 构造获取提交信息的API URL
            String url = "https://api.github.com/repos/" + this.repoFullName + "/commits/" + commitId;

            // 设置请求头信息，包含GitHub访问令牌
            HashMap<String, String> headers = new HashMap<>();
            headers.put("Authorization", "token " + this.gitHubToken);
            headers.put("Accept", "application/vnd.github.v3+json");

            // 发送HTTP GET请求获取提交信息
            String response = HttpClientUtils.get(url, null, headers);
            if (StringUtils.isBlank(response)) {
                log.error("getParentCommitId http error，url：{}", url);
                return "";
            }

            // 解析响应数据，提取第一个父提交ID
            return this.objectMapper.readTree(response)
                    .path("parents")
                    .path(0)
                    .path("sha")
                    .asText("");
        } catch (Exception e) {
            log.error("getParentCommitId exception: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 添加推送通知评论到GitHub最后一次提交
     *
     * @param message 要添加的评论消息内容
     */
    public void addPushNote(String message) {
        // 检测是否为push事件
        if (!"push".equals(this.eventType)) {
            log.warn("Invalid event type: {}. Only 'push' event is supported now.", this.eventType);
            return;
        }
        if (this.commitList.isEmpty()) {
            log.warn("No commits found to add notes to.");
            return;
        }

        // 获取最后一个提交的id
        String lastCommitId = this.commitList.path(this.commitList.size() - 1).path("id").asText("");
        if (StringUtils.isBlank(lastCommitId)) {
            log.error("Last commit ID not found.");
            return;
        }

        // 构建GitHub API评论提交的URL
        String url = "https://api.github.com/repos/" + this.repoFullName + "/commits/" + lastCommitId + "/comments";

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
