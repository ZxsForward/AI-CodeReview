package com.code.review.service;

import com.code.review.entity.AjaxResult;
import com.fasterxml.jackson.databind.JsonNode;

import javax.servlet.http.HttpServletRequest;

public interface GitLabService {

    AjaxResult handleWebhook(HttpServletRequest request, JsonNode rootNode);
}
