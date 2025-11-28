package com.code.review.entity.gitlab;

import com.code.review.entity.CommitInfo;
import com.code.review.utils.HttpClientUtils;
import com.code.review.utils.SpringUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
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
    private String gitlabToken;
    private String gitlabUrl;
    private String eventType;
    private Integer projectId;
    private String projectName;
    private String branchName;
    private JsonNode commitList;
    private ObjectMapper objectMapper = SpringUtils.getBean(ObjectMapper.class);

    public PushHandlerEntity() {
    }


    public PushHandlerEntity(JsonNode rootNode, String gitlabToken, String gitlabUrl) {
        this.rootNode = rootNode;
        this.gitlabToken = gitlabToken;
        this.gitlabUrl = gitlabUrl;
        this.eventType = rootNode.path("object_kind").asText("");
        int projectId = rootNode.path("project_id").asInt(-1);
        if (projectId == -1) {
            this.projectId = rootNode.path("project").path("id").asInt();
        } else {
            this.projectId = projectId;
        }
        this.projectName = rootNode.path("project").path("name").asText("");
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
     * 获取推送变更信息
     *
     * @return JsonNode 包含推送变更信息的JSON节点，如果无法获取有效变更信息则返回MissingNode实例
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

        // 获取before和after的commit_id
        String before = this.rootNode.path("before").asText("");
        String after = this.rootNode.path("after").asText("");
        if (StringUtils.isBlank(before) || StringUtils.isBlank(after)) {
            return MissingNode.getInstance();
        }

        // 处理删除分支的情况
        if (after.startsWith("0000000")) {
            // 删除分支处理
            return MissingNode.getInstance();
        }

        // 处理新增分支的情况
        if (before.startsWith("0000000")) {
            // 新增分支处理
            String firstCommitId = this.commitList.path(0).path("id").asText("");
            String parentCommitId = getParentCommitId(firstCommitId);
            if (StringUtils.isNotBlank(parentCommitId)) {
                before = parentCommitId;
            }
        }

        // 比较仓库差异并返回结果
        return repositoryCompare(before, after);
    }

    /**
     * 比较GitLab仓库中两个提交之间的差异
     *
     * @param before 起始提交的SHA值
     * @param after  结束提交的SHA值
     * @return 返回比较结果的JsonNode对象，如果出现异常或错误则返回MissingNode实例
     */
    private JsonNode repositoryCompare(String before, String after) {
        try {
            // 构造GitLab API比较接口URL
            String url = this.gitlabUrl + "/api/v4/projects/" + this.projectId + "/repository/compare?from=" + before + "&to=" + after;

            // 设置请求头信息，包含GitLab访问令牌
            HashMap<String, String> headers = new HashMap<>();
            headers.put("Private-Token", this.gitlabToken);

            // 发送HTTP GET请求获取比较结果
            String response = HttpClientUtils.get(url, null, headers);
            if (StringUtils.isBlank(response)) {
                log.error("repositoryCompare http error，url：{}", url);
                return MissingNode.getInstance();
            }

            // 解析响应结果为JsonNode并返回
            return this.objectMapper.readTree(response).path("diffs");
        } catch (Exception e) {
            log.error("repositoryCompare exception：", e);
            return MissingNode.getInstance();
        }
    }

    /**
     * 获取指定提交的父提交ID
     *
     * @param commitId 提交ID
     * @return 父提交ID，如果获取失败则返回空字符串
     */
    private String getParentCommitId(String commitId) {
        try {
            if (StringUtils.isBlank(commitId)) {
                return "";
            }

            // 构造获取提交信息的API URL
            String url = this.gitlabUrl + "/api/v4/projects/" + this.projectId + "/repository/commits?ref_name=" + commitId + "&per_page=1&page=1";

            // 设置请求头，包含GitLab访问令牌
            HashMap<String, String> headers = new HashMap<>();
            headers.put("Private-Token", this.gitlabToken);

            // 发送HTTP GET请求获取提交信息
            String response = HttpClientUtils.get(url, null, headers);
            if (StringUtils.isBlank(response)) {
                log.error("getParentCommitId http error，url：{}", url);
                return "";
            }

            // 解析响应数据，提取第一个父提交ID
            return this.objectMapper.readTree(response)
                    .path(0)
                    .path("parent_ids")
                    .path(0)
                    .asText("");
        } catch (Exception e) {
            log.error("getParentCommitId exception：{}", e.getMessage());
            return "";
        }
    }

    /**
     * 添加推送通知评论到GitLab最后一次提交
     *
     * @param message 要添加的评论消息内容
     */
    public void addPushNote(String message) throws JsonProcessingException {
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

        // 构建GitLab API评论提交的URL
        String url = this.gitlabUrl + "/api/v4/projects/" + this.projectId + "/repository/commits/" + lastCommitId + "/comments";

        // 设置请求头信息，包含认证令牌和内容类型
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Private-Token", this.gitlabToken);
        headers.put("Content-Type", "application/json");

        // 设置请求参数，包含评论内容
        Map<String, String> params = new HashMap<>();
        params.put("note", message);

        // 发送POST请求添加评论
        HttpClientUtils.post(url, objectMapper.writeValueAsString(params), null, headers);
    }
}
