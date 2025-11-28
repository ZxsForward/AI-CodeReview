package com.code.review.controller;

import com.code.review.entity.AjaxResult;
import com.code.review.service.GitHubService;
import com.code.review.service.GitLabService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@Slf4j
@RestController
public class AICodeReviewController {

    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private GitLabService gitLabService;
    @Resource
    private GitHubService gitHubService;

    @PostMapping("/review/webhook")
    public AjaxResult handleWebhook(
            HttpServletRequest request,
            @RequestBody String requestData) {
        try {
            // 检查是否为JSON格式
            String contentType = request.getHeader("Content-Type");
            if (StringUtils.isBlank(contentType) || !contentType.contains("application/json")) {
                return AjaxResult.warn("Invalid request format，not json");
            }

            // 解析JSON
            JsonNode rootNode = this.objectMapper.readTree(requestData);

            // 判断是否为GitHub请求
            String eventType = request.getHeader("X-GitHub-Event");
            if (StringUtils.isNotBlank(eventType)) {
                log.info("Handle GitHub Event");
                // 处理GitHub的webhook
                return this.gitHubService.handleWebhook(request, rootNode, eventType);
            } else {
                log.info("Handle GitLab Event");
                // 处理GitLab的webhook
                return this.gitLabService.handleWebhook(request, rootNode);
            }
        } catch (Exception e) {
            return AjaxResult.error(e.getMessage());
        }
    }
}
