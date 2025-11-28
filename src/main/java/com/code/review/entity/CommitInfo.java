package com.code.review.entity;

import lombok.Data;

@Data
public class CommitInfo {
    /**
     * 提交信息
     */
    private String message;
    /**
     * 提交作者
     */
    private String author;
    /**
     * 提交时间
     */
    private String timestamp;
    /**
     * 提交URL
     */
    private String url;
}
