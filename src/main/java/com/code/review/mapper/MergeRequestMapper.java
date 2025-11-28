package com.code.review.mapper;

import com.code.review.entity.MergeRequestEventEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MergeRequestMapper {

    int selectMRLastCommitIdCount(
            @Param("projectName") String projectName,
            @Param("sourceBranch") String sourceBranch,
            @Param("targetBranch") String targetBranch,
            @Param("lastCommitId") String lastCommitId);

    void insertMergeRequest(MergeRequestEventEntity mergeRequestEntity);
}
