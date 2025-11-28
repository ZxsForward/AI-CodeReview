package com.code.review.entity;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Data
public class PushEventEntity {

    private String projectName;
    private String author;
    private String branch;
    private LocalDateTime updatedAt;
    private List<CommitInfo> commits;
    private int score;
    private String reviewResult;
    private String urlSlug;
    private JsonNode webhookData;
    private int additions;
    private int deletions;
    private String commitMessages;

    public PushEventEntity() {
    }

    public PushEventEntity(
            String projectName,
            String author,
            String branch,
            LocalDateTime updatedAt,
            List<CommitInfo> commits,
            int score,
            String reviewResult,
            String urlSlug,
            JsonNode webhookData,
            int additions,
            int deletions
    ) {
        this.projectName = projectName;
        this.author = author;
        this.branch = branch;
        this.updatedAt = updatedAt;
        this.commits = commits;
        this.score = score;
        this.reviewResult = reviewResult;
        this.urlSlug = urlSlug;
        this.webhookData = webhookData;
        this.additions = additions;
        this.deletions = deletions;
        this.commitMessages = generalCommitMessages();
    }

    /**
     * 获取所有提交信息的拼接字符串
     *
     * @return 返回以分号分隔的所有提交消息字符串，如果无提交记录则返回空字符串
     */
    private String generalCommitMessages() {
        // 将提交记录集合转换为流，提取每条记录的message字段并用分号连接
        return this.commits.stream()
                .map(CommitInfo::getMessage)
                .collect(Collectors.joining(";"));
    }
}
