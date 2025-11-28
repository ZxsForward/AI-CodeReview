package com.code.review.entity.gitlab;

import com.code.review.utils.HttpClientUtils;
import com.code.review.utils.SpringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Data
public class MergeRequestHandlerEntity {

    private JsonNode rootNode;
    private String gitlabToken;
    private String gitlabUrl;
    private String eventType;
    private Integer mergeRequestIid;
    private Integer projectId;
    private String projectName;
    private String action;
    private String sourceBranch;
    private String targetBranch;
    private ObjectMapper objectMapper = SpringUtils.getBean(ObjectMapper.class);

    public MergeRequestHandlerEntity() {
    }

    public MergeRequestHandlerEntity(JsonNode rootNode, String gitlabToken, String gitlabUrl) {
        this.rootNode = rootNode;
        this.gitlabToken = gitlabToken;
        this.gitlabUrl = gitlabUrl;
        this.eventType = rootNode.path("object_kind").asText("");
        this.projectName = rootNode.path("project").path("name").asText("");

        JsonNode objectAttributesNode = rootNode.path("object_attributes");
        this.mergeRequestIid = objectAttributesNode.path("iid").asInt(-1);
        this.projectId = objectAttributesNode.path("target_project_id").asInt(-1);
        this.action = objectAttributesNode.path("action").asText("");
        this.sourceBranch = objectAttributesNode.path("source_branch").asText("");
        this.targetBranch = objectAttributesNode.path("target_branch").asText("");
    }

    /**
     * 检查目标分支是否为受保护分支
     *
     * @return boolean 返回true表示目标分支是受保护分支，返回false表示不是受保护分支或检查失败
     */
    public boolean targetBranchProtected() {
        try {
            // 构造获取受保护分支的API URL
            String url = this.gitlabUrl + "/api/v4/projects/" + this.projectId + "/protected_branches";

            // 设置请求头信息，包含GitLab访问令牌
            HashMap<String, String> headers = new HashMap<>();
            headers.put("Private-Token", this.gitlabToken);
            headers.put("Content-Type", "application/json");

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
     * 获取 GitLab Merge Request 的变更内容（changes）。
     * <p>
     * 该方法会检查当前事件类型是否为 "merge_request"，如果不是则记录警告日志并返回空节点。
     * 若是合法事件，则通过 GitLab API 请求指定 Merge Request 的 changes 数据。
     * 由于 GitLab API 可能存在延迟导致获取不到数据，因此采用多次重试机制以提高成功率。
     *
     * @return 返回包含 changes 内容的 JsonNode；如果失败或无数据，则返回 MissingNode 实例
     */
    public JsonNode getMergeRequestChanges() {
        // 检查是否为 Merge Request Hook 事件
        if (!"merge_request".equals(this.eventType)) {
            log.warn("Invalid event type: {}. Only 'merge_request' event is supported now.", this.eventType);
            return MissingNode.getInstance();
        }

        // Gitlab merge request changes API可能存在延迟，多次尝试
        int maxRetries = 3; // 最大重试次数
        int retryDelay = 10000; // 重试间隔（毫秒）

        for (int i = 0; i < maxRetries; i++) {
            try {
                // 调用 GitLab API 获取 Merge Request 的 changes
                String url = this.gitlabUrl + "/api/v4/projects/" + this.projectId + "/merge_requests/" + this.mergeRequestIid + "/changes?access_raw_diffs=true";

                // 设置请求头信息，包含GitLab访问令牌
                HashMap<String, String> headers = new HashMap<>();
                headers.put("Private-Token", this.gitlabToken);

                String response = HttpClientUtils.get(url, null, headers);

                if (StringUtils.isBlank(response)) {
                    log.error("Failed to get changes from GitLab，url：{}", url);
                    // 如果是最后一次尝试，则返回空节点
                    if (i == maxRetries - 1) {
                        return MissingNode.getInstance();
                    }
                } else {
                    JsonNode jsonNode = this.objectMapper.readTree(response);
                    // 成功获取到有效数据则直接返回
                    if (!jsonNode.path("changes").isEmpty()) {
                        return jsonNode;
                    } else {
                        log.info("Changes is empty, retrying in {} seconds...(attempt {} / {})，url：{}", retryDelay / 1000, i + 1, maxRetries, url);
                        // 如果不是最后一次尝试，则等待后继续重试
                        if (i < maxRetries - 1) {
                            Thread.sleep(retryDelay);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("getMergeRequestChanges error：{}", e.getMessage());
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
     * 获取Merge Request的提交记录
     *
     * @return JsonNode 包含commit信息的JSON节点，如果获取失败则返回MissingNode实例
     */
    public JsonNode getMergeRequestCommits() {
        try {
            // 检查是否为 Merge Request Hook 事件
            if (!"merge_request".equals(this.eventType)) {
                log.warn("Invalid event type: {}. Only 'merge_request' event is supported now.", this.eventType);
                return MissingNode.getInstance();
            }

            // 调用 GitLab API 获取 Merge Request 的 commits
            String url = this.gitlabUrl + "/api/v4/projects/" + this.projectId + "/merge_requests/" + this.mergeRequestIid + "/commits";

            // 获取请求头信息，包含GitLab访问令牌
            HashMap<String, String> headers = new HashMap<>();
            headers.put("Private-Token", this.gitlabToken);

            String response = HttpClientUtils.get(url, null, headers);

            // 检查HTTP响应是否为空
            if (StringUtils.isBlank(response)) {
                log.error("getMergeRequestCommits http error，url：{}", url);
                return MissingNode.getInstance();
            }

            // 解析JSON响应数据
            return this.objectMapper.readTree(response);
        } catch (Exception e) {
            log.error("getMergeRequestCommits error：{}", e.getMessage());
            return MissingNode.getInstance();
        }
    }

    /**
     * 添加合并请求备注
     *
     * @param message 备注消息内容
     */
    public void addMergeRequestNote(String message) {
        // 检查是否为 Merge Request Hook 事件
        if (!"merge_request".equals(this.eventType)) {
            log.warn("Invalid event type: {}. Only 'merge_request' event is supported now.", this.eventType);
            return;
        }

        // 构建 GitLab API URL
        String url = this.gitlabUrl + "/api/v4/projects/" + this.projectId + "/merge_requests/" + this.mergeRequestIid + "/notes";

        // 设置请求头信息
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Private-Token", this.gitlabToken);
        headers.put("Content-Type", "application/json");

        // 设置请求参数
        Map<String, String> params = new HashMap<>();
        params.put("body", message);

        // 发送 POST 请求添加备注
        HttpClientUtils.post(url, null, params, headers);
    }
}
