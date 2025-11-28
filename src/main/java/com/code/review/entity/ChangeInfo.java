package com.code.review.entity;

import lombok.Data;

@Data
public class ChangeInfo {
    /**
     * 差异信息
     */
    private String diff;
    /**
     * 新文件路径
     */
    private String newPath;
    /**
     * 新增行数
     */
    private Integer additions;
    /**
     * 删除行数
     */
    private Integer deletions;
}
