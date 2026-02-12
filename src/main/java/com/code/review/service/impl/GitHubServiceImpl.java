package com.code.review.service.impl;

import com.code.review.entity.*;
import com.code.review.entity.github.PullRequestHandlerEntity;
import com.code.review.entity.github.PushHandlerEntity;
import com.code.review.event.EventManager;
import com.code.review.mapper.MergeRequestMapper;
import com.code.review.service.GitHubService;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@Service
public class GitHubServiceImpl implements GitHubService {
    @Resource
    private MergeRequestMapper mergeRequestMapper;

    private static final Pattern PATTERN = Pattern.compile("@@ -\\d+,\\d+ \\+0,0 @@");
    private static final EventManager eventManager = EventManager.getInstance();

    /**
     * 处理GitHub Webhook事件
     *
     * @param request HTTP请求对象，包含webhook请求的相关信息
     * @param rootNode JSON节点对象，包含webhook事件的完整数据
     * @param eventType 事件类型字符串，标识具体的GitHub事件类型
     * @return AjaxResult 异步处理结果，包含处理状态和消息
     */
    @Override
    public AjaxResult handleWebhook(HttpServletRequest request, JsonNode rootNode, String eventType) {
        // 获取GitHub实例的TOKEN
        String gitHubToken = resolveGitHubToken(request);
        if (StringUtils.isBlank(gitHubToken)) {
            return AjaxResult.warn("GitHub token not found");
        }

        // GitHub实例的URL
        String gitHubUrl = "https://github.com";

        // 获取GitHub URL的slug格式
        String gitHubUrlSlug = GitUtils.slugifyUrl(gitHubUrl);

        ThreadPoolTaskExecutor threadPoolTaskExecutor = SpringUtils.getBean("threadPoolTaskExecutor");

        // 多线程处理不同事件类型
        if ("pull_request".equals(eventType)) {
            log.info("Handle GitHub Pull Request Event");
            threadPoolTaskExecutor.execute(() -> {
                // 处理pull_request事件
                handlePullRequestEvent(rootNode, gitHubToken, gitHubUrl, gitHubUrlSlug);
            });
            return AjaxResult.success("pull_request will process asynchronously.");
        } else if ("push".equals(eventType)) {
            log.info("Handle GitHub Push Event");
            threadPoolTaskExecutor.execute(() -> {
                // 处理push事件
                handlePushEvent(rootNode, gitHubToken, gitHubUrl, gitHubUrlSlug);
            });
            return AjaxResult.success("push will process asynchronously.");
        } else {
            return AjaxResult.warn("Unsupported eventType type，received：" + eventType);
        }
    }

    /**
     * 处理 GitHub 的 Push 事件，解析推送的提交信息，并根据配置决定是否进行代码评审。
     * 若启用了 Push Review 功能，则会提取变更内容、调用 AI 接口进行代码评审，并将结果发布到 GitHub Notes。
     * 最后触发 push_reviewed 事件通知。
     *
     * @param rootNode       包含 Push 事件完整数据的 JSON 节点
     * @param gitHubToken    用于访问 GitHub API 的认证令牌
     * @param gitHubUrl      GitHub 仓库地址（API 地址）
     * @param gitHubUrlSlug  GitHub 仓库的 URL 标识符（如 owner/repo）
     */
    private void handlePushEvent(JsonNode rootNode, String gitHubToken, String gitHubUrl, String gitHubUrlSlug) {
        try {
            // 检测是否开启Push Review功能
            boolean pushReviewEnabled = "1".equals(SpringUtils.getPropertyOrDefault("PUSH_REVIEW_ENABLED", "1"));

            PushHandlerEntity handler = new PushHandlerEntity(rootNode, gitHubToken, gitHubUrl);
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
                    // 将review结果提交到GitHub的 notes
                    handler.addPushNote("Auto Review Result：\n" + reviewResult);
                }
            }

            // 构造 PushEventEntity 并触发事件通知
            eventManager.emit("push_reviewed", new PushEventEntity(
                    handler.getProjectName(),
                    rootNode.path("sender").path("login").asText(""),
                    handler.getBranchName(),
                    LocalDateTime.now(),
                    pushCommits,
                    score,
                    reviewResult,
                    gitHubUrlSlug,
                    rootNode,
                    additions,
                    deletions
            ));
        } catch (Exception e) {
            // 记录异常日志并发送通知
            String message = "Push request event error: " + e.getMessage();
            log.error(message);
            NotifierUtils.sendNotification(message);
        }
    }

    /**
     * 处理 GitHub 的 Pull Request 事件，执行代码审查逻辑。
     * <p>
     * 主要流程包括：
     * - 判断是否启用受保护分支过滤；
     * - 根据事件类型决定是否继续处理（只处理 opened 和 synchronize）；
     * - 防止重复处理相同 commit 的 PR；
     * - 获取变更文件并过滤出支持的文件类型；
     * - 调用 AI 接口进行代码审查；
     * - 将审查结果发布为评论，并触发相关事件通知。
     *
     * @param rootNode      包含 Pull Request 事件数据的 JSON 节点
     * @param gitHubToken   GitHub 访问令牌
     * @param gitHubUrl     GitHub API 地址
     * @param gitHubUrlSlug GitHub URL 中用于标识项目的部分
     */
    private void handlePullRequestEvent(JsonNode rootNode, String gitHubToken, String gitHubUrl, String gitHubUrlSlug) {
        try {
            // 检测是否开启Merge请求过滤功能
            boolean mergeReviewOnlyProtectedBranchesEnabled = "1".equals(SpringUtils.getPropertyOrDefault("MERGE_REVIEW_ONLY_PROTECTED_BRANCHES_ENABLED", "0"));

            PullRequestHandlerEntity handler = new PullRequestHandlerEntity(rootNode, gitHubToken, gitHubUrl);

            // 如果开启了仅review projected branches的，判断当前目标分支是否为projected branches
            if (mergeReviewOnlyProtectedBranchesEnabled && !handler.targetBranchProtected()) {
                log.info("Pull Request target branch not match protected branches, ignored.");
                return;
            }

            if (!Arrays.asList("opened", "synchronize").contains(handler.getAction())) {
                log.info("Pull Request Hook event, action={}, ignored.", handler.getAction());
                return;
            }

            // 检查last_commit_id是否已经存在，如果存在则跳过处理
            String lastCommitId = rootNode.path("pull_request").path("head").path("sha").asText("");
            if (StringUtils.isNotBlank(lastCommitId)) {
                // 查询数据库
                int count = this.mergeRequestMapper.selectMRLastCommitIdCount(handler.getProjectName(), handler.getSourceBranch(), handler.getTargetBranch(), lastCommitId);
                if (count > 0) {
                    log.info("Pull Request with last_commit_id {} already exists，skipping review for {}", lastCommitId, handler.getProjectName());
                    return;
                }
            }

            // 仅仅在PR创建或更新时进行Code Review
            // 获取Pull Request的changes
            JsonNode pullRequestChanges = handler.getPullRequestChanges();
            List<ChangeInfo> changes = filterChanges(pullRequestChanges);
            if (CollectionUtils.isEmpty(changes)) {
                log.info("未检测到Pull Request有关代码的修改,修改文件可能不满足SUPPORTED_EXTENSIONS。");
                return;
            }

            // 统计本次新增、删除的代码总数
            int additions = changes.stream().mapToInt(ChangeInfo::getAdditions).sum();
            int deletions = changes.stream().mapToInt(ChangeInfo::getDeletions).sum();

            // 获取Pull Request的commits
            JsonNode pullRequestCommits = handler.getPullRequestCommits();
            if (pullRequestCommits.isEmpty()) {
                log.info("Failed to get commits");
                return;
            }

            String commitsText = StreamSupport.stream(pullRequestCommits.spliterator(), false)
                    .map(commit -> commit.path("title").asText(""))
                    .collect(Collectors.joining(";"));

            String reviewResult = AICodeReviewUtils.reviewAndStripCode(changes.toString(), commitsText, handler.getProjectName());

            // 如果评审结果为空，则设置默认提示信息；否则将其作为 note 发布至 Git
            if (StringUtils.isBlank(reviewResult)) {
                reviewResult = "AI审核结果为空，请检查是否存在错误";
            } else {
                // 将review结果提交到GitHub的 notes
                handler.addPullRequestNote("Auto Review Result：\n" + reviewResult);
            }

            // 构造 MergeRequestEventEntity 并触发事件通知
            eventManager.emit("merge_request_review_result", new MergeRequestEventEntity(
                    handler.getProjectName(),
                    rootNode.path("pull_request").path("user").path("login").asText(""),
                    handler.getSourceBranch(),
                    handler.getTargetBranch(),
                    LocalDateTime.now(),
                    pullRequestCommits,
                    AICodeReviewUtils.parseReviewScore(reviewResult),
                    rootNode.path("pull_request").path("html_url").asText(""),
                    reviewResult,
                    gitHubUrlSlug,
                    rootNode,
                    additions,
                    deletions,
                    lastCommitId
            ));
        } catch (Exception e) {
            // 记录异常日志并发送通知
            String message = "Pull request event error: " + e.getMessage();
            log.error(message);
            NotifierUtils.sendNotification(message);
        }
    }

    /**
     * 过滤变更节点，提取符合要求的变更信息。
     * <p>
     * 本方法会根据配置的支持文件扩展名过滤变更记录，并排除已被删除的文件（通过 status 字段或 diff 内容判断）。
     * 最终只保留具有有效扩展名且未被完全删除的文件变更信息。
     *
     * @param changeNodes 包含多个变更记录的 JsonNode 对象
     * @return 符合条件的变更信息列表
     */
    private List<ChangeInfo> filterChanges(JsonNode changeNodes) {
        List<ChangeInfo> changes = new ArrayList<>();

        // 从环境变量中获取支持的文件扩展名，默认为.java,.py,.php
        String supportedExtensions = SpringUtils.getPropertyOrDefault("SUPPORTED_EXTENSIONS", ".java,.py,.php");

        // 根据英文逗号分割字符串，得到支持的文件扩展名数组
        String[] extensions = supportedExtensions.split(",");

        // 遍历所有变更节点，过滤出符合条件的变更信息
        for (JsonNode changeNode : changeNodes) {
            String newPath = changeNode.path("new_path").asText("");

            // 优先检查status字段是否为"removed"
            if ("removed".equals(changeNode.path("status").asText(""))) {
                log.info("Detected file deletion via status field：{}", newPath);
                continue;
            }

            // 如果没有status字段或status不为"removed"，继续检查diff模式
            String diff = changeNode.path("diff").asText("");
            if (StringUtils.isNotBlank(diff) && PATTERN.matcher(diff).find()) {
                // 检查除了diff头部外的所有行是否都以减号开头
                String[] diffLines = diff.split("\n");  // 分割所有行
                boolean allStartWithMinus = true;

                // 跳过第一行(索引0)，检查其余行
                for (int i = 1; i < diffLines.length; i++) {
                    String line = diffLines[i];
                    if (!line.startsWith("-") && !line.isEmpty()) {
                        allStartWithMinus = false;
                        break;
                    }
                }

                // 如果所有行都以减号开头，则认为该文件被删除，则跳过处理
                if (allStartWithMinus) {
                    log.info("Detected file deletion via diff pattern: {}", newPath);
                    continue;
                }
            }

            // 过滤 `new_path` 以支持的扩展名结尾的元素，仅保留diff和new_path字段
            if (!StringUtils.endsWithAny(newPath, extensions)) {
                continue;
            }

            // 构造变更信息对象并添加到结果列表
            ChangeInfo changeInfo = new ChangeInfo();
            changeInfo.setDiff(diff);
            changeInfo.setNewPath(newPath);
            changeInfo.setAdditions(changeNode.path("additions").asInt(0));
            changeInfo.setDeletions(changeNode.path("deletions").asInt(0));

            changes.add(changeInfo);
        }
        return changes;
    }

    /**
     * 解析GitHub访问令牌
     *
     * @param request HTTP请求对象，用于从请求头中获取令牌
     * @return GitHub访问令牌字符串，如果未找到则返回null或空字符串
     */
    private String resolveGitHubToken(HttpServletRequest request) {
        // 优先从环境变量获取，如果没有，则从请求头获取
        String githubToken = SpringUtils.getProperty("GITHUB_ACCESS_TOKEN");
        if (StringUtils.isBlank(githubToken)) {
            githubToken = request.getHeader("X-GitHub-Token");
        }
        return githubToken;
    }
}
