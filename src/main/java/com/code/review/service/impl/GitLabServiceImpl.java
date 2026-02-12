package com.code.review.service.impl;

import com.code.review.entity.*;
import com.code.review.entity.gitlab.MergeRequestHandlerEntity;
import com.code.review.entity.gitlab.PushHandlerEntity;
import com.code.review.event.EventManager;
import com.code.review.mapper.MergeRequestMapper;
import com.code.review.service.GitLabService;
import com.code.review.utils.AICodeReviewUtils;
import com.code.review.utils.GitUtils;
import com.code.review.utils.NotifierUtils;
import com.code.review.utils.SpringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@Service
public class GitLabServiceImpl implements GitLabService {
    @Resource
    private MergeRequestMapper mergeRequestMapper;

    private static final EventManager eventManager = EventManager.getInstance();

    /**
     * 处理GitLab Webhook请求
     *
     * @param request  HTTP请求对象，包含webhook请求的相关信息
     * @param rootNode JSON根节点，包含webhook传递的事件数据
     * @return AjaxResult 异步处理结果，成功时返回对应事件类型的处理状态，失败时返回错误信息
     */
    @Override
    public AjaxResult handleWebhook(HttpServletRequest request, JsonNode rootNode) {
        // 获取GitLab实例的URL
        String gitlabUrl = resolveUrl(request, rootNode);
        if (StringUtils.isBlank(gitlabUrl)) {
            return AjaxResult.warn("GitLab URL not found");
        }

        // 获取GitLab实例的TOKEN
        String gitlabToken = resolveToken(request);
        if (StringUtils.isBlank(gitlabToken)) {
            return AjaxResult.warn("GitLab token not found");
        }

        // 获取GitLab URL的slug格式
        String gitlabUrlSlug = GitUtils.slugifyUrl(gitlabUrl);

        // 获取提交类型
        String eventType = rootNode.path("object_kind").asText("");
        ThreadPoolTaskExecutor threadPoolTaskExecutor = SpringUtils.getBean("threadPoolTaskExecutor");

        // 多线程处理不同事件类型
        if ("merge_request".equals(eventType)) {
            log.info("Handle GitLab Merge Request Event");
            threadPoolTaskExecutor.execute(() -> {
                // 处理merge_request事件
                handleMergeRequestEvent(rootNode, gitlabToken, gitlabUrl, gitlabUrlSlug);
            });
            return AjaxResult.success("merge_request will process asynchronously.");
        } else if ("push".equals(eventType)) {
            log.info("Handle GitLab Push Event");
            threadPoolTaskExecutor.execute(() -> {
                // 处理push事件
                handlePushEvent(rootNode, gitlabToken, gitlabUrl, gitlabUrlSlug);
            });
            return AjaxResult.success("push will process asynchronously.");
        } else {
            return AjaxResult.warn("Unsupported event type，received：" + eventType);
        }
    }

    /**
     * 处理 GitLab 的 Merge Request 事件回调。
     * <p>
     * 根据配置及 MR 状态决定是否触发 AI Code Review，并将评审结果以评论形式发布到 GitLab，
     * 同时记录相关事件信息用于后续处理或通知。
     *
     * @param rootNode      包含 Merge Request 事件数据的 JSON 节点
     * @param gitlabToken   用于访问 GitLab API 的认证 Token
     * @param gitlabUrl     GitLab 实例的基础 URL
     * @param gitlabUrlSlug 用于标识项目的 URL Slug（路径）
     */
    private void handleMergeRequestEvent(JsonNode rootNode, String gitlabToken, String gitlabUrl, String gitlabUrlSlug) {
        try {
            // 检测是否开启Merge请求过滤功能
            boolean mergeReviewOnlyProtectedBranchesEnabled = "1".equals(SpringUtils.getPropertyOrDefault("MERGE_REVIEW_ONLY_PROTECTED_BRANCHES_ENABLED", "0"));

            MergeRequestHandlerEntity handler = new MergeRequestHandlerEntity(rootNode, gitlabToken, gitlabUrl);
            JsonNode objectAttributesNode = rootNode.path("object_attributes");

            // 判断是否为draft（草稿）MR
            boolean isDraft = objectAttributesNode.path("draft").asBoolean(false) || objectAttributesNode.path("work_in_progress").asBoolean(false);
            if (isDraft) {
                String message = "[通知] MR为草稿（draft），未触发AI审查。\n" +
                        "项目：" + handler.getProjectName() + "\n" +
                        "作者：" + rootNode.path("user").path("username").asText("") + "\n" +
                        "源分支：" + handler.getSourceBranch() + "\n" +
                        "目标分支：" + handler.getTargetBranch() + "\n" +
                        "链接：" + objectAttributesNode.path("url").asText("");
                log.info(message);
                NotifierUtils.sendNotification(message);
                return;
            }

            // 如果开启了仅review projected branches的，判断当前目标分支是否为projected branches
            if (mergeReviewOnlyProtectedBranchesEnabled && !handler.targetBranchProtected()) {
                log.info("Merge Request target branch not match protected branches, ignored.");
                return;
            }

            if (!Arrays.asList("open", "update").contains(handler.getAction())) {
                log.info("Merge Request Hook event, action={}, ignored.", handler.getAction());
                return;
            }

            // 检查last_commit_id是否已经存在，如果存在则跳过处理
            String lastCommitId = objectAttributesNode.path("last_commit").path("id").asText("");
            if (StringUtils.isNotBlank(lastCommitId)) {
                // 查询数据库
                int count = this.mergeRequestMapper.selectMRLastCommitIdCount(handler.getProjectName(), handler.getSourceBranch(), handler.getTargetBranch(), lastCommitId);
                if (count > 0) {
                    log.info("Merge Request with last_commit_id {} already exists，skipping review for {}", lastCommitId, handler.getProjectName());
                    return;
                }
            }

            // 仅仅在MR创建或更新时进行Code Review
            // 获取Merge Request的changes
            JsonNode mergeRequestChanges = handler.getMergeRequestChanges();
            List<ChangeInfo> changes = filterChanges(mergeRequestChanges);
            if (CollectionUtils.isEmpty(changes)) {
                log.info("未检测到Merge Request有关代码的修改,修改文件可能不满足SUPPORTED_EXTENSIONS。");
                return;
            }

            // 统计本次新增、删除的代码总数
            int additions = changes.stream().mapToInt(ChangeInfo::getAdditions).sum();
            int deletions = changes.stream().mapToInt(ChangeInfo::getDeletions).sum();

            // 获取Merge Request的commits
            JsonNode mergeRequestCommits = handler.getMergeRequestCommits();
            if (mergeRequestCommits.isEmpty()) {
                log.info("Failed to get commits");
                return;
            }

            String commitsText = StreamSupport.stream(mergeRequestCommits.spliterator(), false)
                    .map(commit -> commit.path("title").asText(""))
                    .collect(Collectors.joining(";"));

            String reviewResult = AICodeReviewUtils.reviewAndStripCode(changes.toString(), commitsText, handler.getProjectName());

            // 如果评审结果为空，则设置默认提示信息；否则将其作为 note 发布至 Git
            if (StringUtils.isBlank(reviewResult)) {
                reviewResult = "AI审核结果为空，请检查是否存在错误";
            } else {
                // 将review结果提交到Gitlab的 notes
                handler.addMergeRequestNote("Auto Review Result：\n" + reviewResult);
            }

            // 构造 MergeRequestEventEntity 并触发事件通知
            eventManager.emit("merge_request_review", new MergeRequestEventEntity(
                    handler.getProjectName(),
                    rootNode.path("user").path("username").asText(""),
                    handler.getSourceBranch(),
                    handler.getTargetBranch(),
                    LocalDateTime.now(),
                    mergeRequestCommits,
                    AICodeReviewUtils.parseReviewScore(reviewResult),
                    objectAttributesNode.path("url").asText(""),
                    reviewResult,
                    gitlabUrlSlug,
                    rootNode,
                    additions,
                    deletions,
                    lastCommitId
            ));
        } catch (Exception e) {
            // 记录异常日志并发送通知
            String message = "Merge request event error: " + e.getMessage();
            log.error(message);
            NotifierUtils.sendNotification(message);
        }
    }

    /**
     * 处理 GitLab 的 Push 事件，解析推送的提交信息，并根据配置决定是否进行 AI 代码评审。
     * 若开启 Push Review 功能，则会提取变更文件、调用 AI 接口进行代码评审，并将评审结果以评论形式发布到 GitLab。
     * 最后通过事件管理器发出处理完成的事件。
     *
     * @param rootNode      包含 Push 事件完整数据的 JSON 节点
     * @param gitlabToken   用于访问 GitLab API 的认证 Token
     * @param gitlabUrl     GitLab 实例的基础 URL
     * @param gitlabUrlSlug 当前项目的 URL Slug（路径标识）
     */
    private void handlePushEvent(JsonNode rootNode, String gitlabToken, String gitlabUrl, String gitlabUrlSlug) {
        try {
            // 检测是否开启Push Review功能
            boolean pushReviewEnabled = "1".equals(SpringUtils.getPropertyOrDefault("PUSH_REVIEW_ENABLED", "1"));

            PushHandlerEntity handler = new PushHandlerEntity(rootNode, gitlabToken, gitlabUrl);
            List<CommitInfo> pushCommits = handler.getPushCommits();

            // 如果没有获取到提交信息则记录错误并返回
            if (CollectionUtils.isEmpty(pushCommits)) {
                log.error("No commits found in push event");
                return;
            }

            String reviewResult = "Push Review未开启";
            int score = 0;
            int additions = 0;
            int deletions = 0;

            if (pushReviewEnabled) {
                // 获取PUSH的changes
                JsonNode pushChanges = handler.getPushChanges();
                List<ChangeInfo> changes = filterChanges(pushChanges);

                // 如果没有符合要求的变更文件，则记录提示信息
                if (CollectionUtils.isEmpty(changes)) {
                    log.info("未检测到PUSH代码的修改,修改文件可能不满足SUPPORTED_EXTENSIONS。");
                    reviewResult = "关注的文件没有修改";
                } else {
                    // 拼接所有提交的消息文本
                    String commitText = pushCommits.stream()
                            .map(commitInfo -> commitInfo.getMessage().trim())
                            .collect(Collectors.joining(";"));

                    // 调用AI接口，获取代码评审结果
                    reviewResult = AICodeReviewUtils.reviewAndStripCode(changes.toString(), commitText, handler.getProjectName());

                    // 解析评审得分
                    score = AICodeReviewUtils.parseReviewScore(reviewResult);

                    // 使用stream计算总的新增行数和删除行数
                    additions = changes.stream()
                            .mapToInt(ChangeInfo::getAdditions)
                            .sum();
                    deletions = changes.stream()
                            .mapToInt(ChangeInfo::getDeletions)
                            .sum();
                }

                // 如果评审结果为空，则设置默认提示信息；否则将其作为 note 发布至 Git
                if (StringUtils.isBlank(reviewResult)) {
                    reviewResult = "AI审核结果为空，请检查是否存在错误";
                } else {
                    // 将review结果提交到Gitlab的 notes
                    handler.addPushNote("Auto Review Result：\n" + reviewResult);
                }
            }

            // 构造 PushEventEntity 并触发事件通知
            eventManager.emit("push_reviewed", new PushEventEntity(
                    handler.getProjectName(),
                    rootNode.path("user_username").asText(""),
                    handler.getBranchName(),
                    LocalDateTime.now(),
                    pushCommits,
                    score,
                    reviewResult,
                    gitlabUrlSlug,
                    rootNode,
                    additions,
                    deletions
            ));
        } catch (Exception e) {
            // 记录异常日志并发送通知
            String message = "Handle push event error: " + e.getMessage();
            log.error(message);
            NotifierUtils.sendNotification(message);
        }
    }

    /**
     * 过滤数据，只保留支持的文件类型以及必要的字段信息
     *
     * @param changeNodes 变更的JSON节点
     * @return 过滤后的变更信息列表，仅包含支持的文件类型及必要字段
     */
    private List<ChangeInfo> filterChanges(JsonNode changeNodes) {
        List<ChangeInfo> changes = new ArrayList<>();

        // 从环境变量中获取支持的文件扩展名，默认为.java,.py,.php
        String supportedExtensions = SpringUtils.getPropertyOrDefault("SUPPORTED_EXTENSIONS", ".java,.py,.php");

        // 根据英文逗号分割字符串，得到支持的文件扩展名数组
        String[] extensions = supportedExtensions.split(",");

        // 遍历所有变更节点，过滤出符合条件的变更信息
        for (JsonNode changeNode : changeNodes) {
            // 跳过已删除的文件
            if (changeNode.path("deleted_file").asBoolean(false)) {
                continue;
            }

            // 过滤 `new_path` 以支持的扩展名结尾的元素，仅保留diff和new_path字段
            if (!StringUtils.endsWithAny(changeNode.path("new_path").asText(""), extensions)) {
                continue;
            }

            // 获取diff内容和新路径
            String diff = changeNode.path("diff").asText("");
            String newPath = changeNode.path("new_path").asText("");

            // 统计新增行数和删除行数
            int additions = 0;
            int deletions = 0;

            // 如果diff内容不为空，则逐行分析统计增删行数
            if (StringUtils.isNotBlank(diff)) {
                String[] lines = diff.split("\n");
                for (String line : lines) {
                    if (line.startsWith("+") && !line.startsWith("++")) {
                        additions++;
                    } else if (line.startsWith("-") && !line.startsWith("--")) {
                        deletions++;
                    }
                }
            }

            // 构造变更信息对象并添加到结果列表
            ChangeInfo changeInfo = new ChangeInfo();
            changeInfo.setDiff(diff);
            changeInfo.setNewPath(newPath);
            changeInfo.setAdditions(additions);
            changeInfo.setDeletions(deletions);

            changes.add(changeInfo);
        }

        return changes;
    }

    /**
     * 解析GitLab访问令牌
     *
     * @param request HTTP请求对象，用于从请求头中获取令牌
     * @return 返回解析到的GitLab访问令牌，如果未找到则返回null
     */
    private String resolveToken(HttpServletRequest request) {
        // 优先从环境变量获取，如果没有，则从请求头获取
        String gitlabToken = SpringUtils.getProperty("GITLAB_ACCESS_TOKEN");
        if (StringUtils.isBlank(gitlabToken)) {
            gitlabToken = request.getHeader("X-Gitlab-Token");
        }
        return gitlabToken;
    }

    /**
     * 解析GitLab URL地址
     *
     * @param request  HTTP请求对象，用于获取请求头信息
     * @param rootNode JSON节点对象，用于解析推送事件中的仓库信息
     * @return 返回解析得到的GitLab URL地址，如果解析失败则返回null
     */
    private String resolveUrl(HttpServletRequest request, JsonNode rootNode) {
        try {
            // 优先从环境变量获取，如果没有，则从请求头获取，如果没有，则从推送事件中获取
            String gitlabUrl = SpringUtils.getProperty("GITLAB_URL");
            if (StringUtils.isBlank(gitlabUrl)) {
                gitlabUrl = request.getHeader("X-Gitlab-URL");
            }
            if (StringUtils.isBlank(gitlabUrl)) {
                String homepage = rootNode.path("repository").path("homepage").asText("");
                if (StringUtils.isNotBlank(homepage)) {
                    URI parsedUri = new URI(homepage);
                    gitlabUrl = parsedUri.getScheme() + "://" + parsedUri.getHost() + "/";
                }
            }
            return gitlabUrl;
        } catch (Exception e) {
            return null;
        }
    }
}
