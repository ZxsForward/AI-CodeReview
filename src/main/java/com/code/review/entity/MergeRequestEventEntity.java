package com.code.review.entity;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Data
public class MergeRequestEventEntity {

    private String projectName;
    private String author;
    private String sourceBranch;
    private String targetBranch;
    private LocalDateTime updatedAt;
    private JsonNode commits;
    private int score;
    private String url;
    private String reviewResult;
    private String urlSlug;
    private JsonNode rootNode;
    private int additions;
    private int deletions;
    private String lastCommitId;
    private String commitMessages;

    public MergeRequestEventEntity() {
    }

    public MergeRequestEventEntity(
            String projectName,
            String author,
            String sourceBranch,
            String targetBranch,
            LocalDateTime updatedAt,
            JsonNode commits,
            int score,
            String url,
            String reviewResult,
            String urlSlug,
            JsonNode rootNode,
            int additions,
            int deletions,
            String lastCommitId) {
        this.projectName = projectName;
        this.author = author;
        this.sourceBranch = sourceBranch;
        this.targetBranch = targetBranch;
        this.updatedAt = updatedAt;
        this.commits = commits;
        this.score = score;
        this.url = url;
        this.reviewResult = reviewResult;
        this.urlSlug = urlSlug;
        this.rootNode = rootNode;
        this.additions = additions;
        this.deletions = deletions;
        this.lastCommitId = lastCommitId;
        this.commitMessages = generalCommitMessages();
    }

    /**
     * 获取所有提交信息的拼接字符串
     *
     * @return 返回以分号分隔的所有提交消息字符串，如果无提交记录则返回空字符串
     */
    private String generalCommitMessages() {
        // 将提交记录集合转换为流，提取每条记录的message字段并用分号连接
        return StreamSupport.stream(this.commits.spliterator(), false)
                .map(commit -> commit.path("message").asText(""))
                .collect(Collectors.joining(";"));
    }
}
