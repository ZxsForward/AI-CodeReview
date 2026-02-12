package com.code.review.event;

import com.code.review.entity.CommitInfo;
import com.code.review.entity.MergeRequestEventEntity;
import com.code.review.entity.PushEventEntity;
import com.code.review.mapper.MergeRequestMapper;
import com.code.review.mapper.PushMapper;
import com.code.review.utils.NotifierUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

@Slf4j
@Component
public class EventHandler {

    @Resource
    private MergeRequestMapper mergeRequestMapper;
    @Resource
    private PushMapper pushMapper;

    /**
     * 处理推送事件，构建并发送IM消息通知
     *
     * @param entity 推送事件实体，包含项目信息、提交记录和AI审查结果
     */
    public void onPushEvent(PushEventEntity entity) {
        // 发送IM消息通知
        StringBuilder imMsgBuilder = new StringBuilder();
        imMsgBuilder.append("### 🚀 ").append(entity.getProjectName()).append(": Push\n\n");
        imMsgBuilder.append("#### 提交记录:\n");

        // 遍历所有提交记录，构建提交信息内容
        for (CommitInfo commit : entity.getCommits()) {
            imMsgBuilder.append("- **提交信息**: ").append(commit.getMessage()).append("\n")
                    .append("- **提交者**: ").append(commit.getAuthor()).append("\n")
                    .append("- **时间**: ").append(commit.getTimestamp()).append("\n")
                    .append("- **链接**: ").append(commit.getUrl()).append("\n\n");
        }

        // 添加AI审查结果到消息中
        imMsgBuilder.append("#### AI Review 结果: \n ").append(entity.getReviewResult());

        // 发送消息
        NotifierUtils.sendNotification(imMsgBuilder.toString(), "markdown", entity.getProjectName() + " Push Event", entity.getProjectName(), entity.getUrlSlug());

        // 入库
        log.info("PushEventEntity 入库");
        this.pushMapper.insertPush(entity);
    }

    /**
     * 处理合并请求事件的方法
     * <p>
     * 该方法接收一个MergeRequestEventEntity对象，构建包含合并请求详细信息的Markdown格式消息，
     * 并通过通知工具发送给相关项目成员。
     *
     * @param entity 合并请求事件实体对象，包含合并请求的所有相关信息
     */
    public void onMergeRequestEvent(MergeRequestEventEntity entity) {
        // 构建Markdown格式的IM消息内容，包含项目名称、合并请求信息和AI审查结果
        String imMsg = "### 🔀 " + entity.getProjectName() + ": Merge Request\n" +
                "#### 合并请求信息:\n" +
                "- **提交者**: " + entity.getAuthor() + "\n" +
                "- **源分支**: " + entity.getSourceBranch() + "\n" +
                "- **目标分支**: " + entity.getTargetBranch() + "\n" +
                "- **更新时间**: " + entity.getUpdatedAt() + "\n" +
                "- **提交信息**: " + entity.getCommitMessages() + "\n" +
                "- **链接**: " + entity.getUrl() + "\n\n" +
                "#### AI Review 结果: \n " + entity.getReviewResult();

        // 发送通知消息到IM系统
        NotifierUtils.sendNotification(imMsg, "markdown", entity.getProjectName() + " Merge Request Event", entity.getProjectName(), entity.getUrlSlug());

        // 入库
        log.info("MergeRequestEventEntity 入库");
        this.mergeRequestMapper.insertMergeRequest(entity);
    }
}
