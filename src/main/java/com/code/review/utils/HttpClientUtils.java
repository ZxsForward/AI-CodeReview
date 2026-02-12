package com.code.review.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.springframework.util.CollectionUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * HTTP客户端工具类，封装了常用的HTTP GET/POST请求方法
 */
@Slf4j
public class HttpClientUtils {

    /**
     * 发送GET请求
     *
     * @param url    请求地址
     * @param params 请求参数
     * @return 响应结果字符串
     * @throws Exception 请求异常
     */
    public static String get(String url, Map<String, String> params) throws Exception {
        return get(url, params, new HashMap<>());
    }

    /**
     * 发送带请求头的GET请求
     *
     * @param url     请求地址
     * @param params  请求参数
     * @param headers 请求头
     * @return 响应结果字符串
     * @throws Exception 请求异常
     */
    public static String get(String url, Map<String, String> params, Map<String, String> headers) throws Exception {
        log.debug("调用get, url=【{}】", url);

        // 构建带参数的URL
        String fullUrl = buildUrlWithParams(url, params);
        HttpGet httpGet = new HttpGet(fullUrl);

        return executeHttpRequest(httpGet, headers);
    }

    /**
     * 发送POST请求
     *
     * @param url          请求地址
     * @param entityString 实体字符串
     * @return 响应结果字符串
     */
    public static String post(String url, String entityString) {
        return post(url, entityString, new HashMap<>());
    }

    /**
     * 发送POST请求（表单数据）
     *
     * @param url    请求地址
     * @param params 表单参数
     * @return 响应结果字符串
     */
    public static String post(String url, Map<String, String> params) {
        return post(url, null, params);
    }

    /**
     * 发送POST请求（带实体字符串）
     *
     * @param url          请求地址
     * @param entityString 实体字符串
     * @param params       表单参数
     * @return 响应结果字符串
     */
    public static String post(String url, String entityString, Map<String, String> params) {
        return post(url, entityString, params, new HashMap<>());
    }

    /**
     * 发送带请求头的POST请求
     *
     * @param url          请求地址
     * @param entityString 实体字符串
     * @param params       表单参数
     * @param headers      请求头
     * @return 响应结果字符串
     */
    public static String post(String url, String entityString, Map<String, String> params, Map<String, String> headers) {
        return post(url, entityString, params, headers, false);
    }

    /**
     * 发送POST请求，支持指定是否为JSON格式
     *
     * @param url          请求地址
     * @param entityString 实体字符串
     * @param params       表单参数
     * @param jsonPost     是否为JSON格式
     * @return 响应结果字符串
     */
    public static String post(String url, String entityString, Map<String, String> params, boolean jsonPost) {
        return post(url, entityString, params, new HashMap<>(), jsonPost);
    }

    /**
     * 统一的POST请求方法
     *
     * @param url          请求地址
     * @param entityString 实体字符串
     * @param params       表单参数
     * @param headers      请求头
     * @param jsonPost     是否为JSON格式
     * @return 响应结果字符串
     */
    public static String post(String url, String entityString, Map<String, String> params,
                              Map<String, String> headers, boolean jsonPost) {
        log.debug("调用post, url=【{}】", url);

        HttpPost httpPost = new HttpPost(url);

        // 构建请求实体
        HttpEntity httpEntity = buildHttpEntity(entityString, params, jsonPost);
        if (httpEntity != null) {
            httpPost.setEntity(httpEntity);
        }

        try {
            return executeHttpRequest(httpPost, headers);
        } catch (Exception e) {
            log.error("调用post Exception, url=【{}】,param=【{}】", url, params, e);
            return null;
        }
    }

    /**
     * 构建带参数的URL
     *
     * @param url    基础URL
     * @param params 参数映射
     * @return 拼接参数后的完整URL
     */
    private static String buildUrlWithParams(String url, Map<String, String> params) {
        if (CollectionUtils.isEmpty(params)) {
            return url;
        }

        StringBuilder stringBuilder = new StringBuilder(url);
        if (!url.contains("?")) {
            stringBuilder.append("?");
        } else if (!url.endsWith("?") && !url.endsWith("&")) {
            stringBuilder.append("&");
        }

        boolean firstParam = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!firstParam) {
                stringBuilder.append("&");
            }
            stringBuilder.append(entry.getKey()).append("=").append(entry.getValue());
            firstParam = false;
        }

        return stringBuilder.toString();
    }

    /**
     * 为HTTP请求添加请求头
     *
     * @param request HTTP请求对象
     * @param headers 请求头映射
     */
    private static void addHeaders(ClassicHttpRequest request, Map<String, String> headers) {
        if (!CollectionUtils.isEmpty(headers)) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                request.addHeader(header.getKey(), header.getValue());
            }
        }
    }

    /**
     * 构建HTTP请求实体
     *
     * @param entityString 实体字符串
     * @param params       表单参数
     * @param jsonPost     是否为JSON格式
     * @return HTTP实体对象
     */
    private static HttpEntity buildHttpEntity(String entityString, Map<String, String> params, boolean jsonPost) {
        ContentType contentType;
        if (jsonPost) {
            contentType = ContentType.APPLICATION_JSON;
        } else {
            contentType = ContentType.APPLICATION_FORM_URLENCODED;
        }

        // 如果有表单参数，创建表单实体
        if (!CollectionUtils.isEmpty(params)) {
            List<NameValuePair> nameValuePairList = new ArrayList<>();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                nameValuePairList.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
            }
            return new UrlEncodedFormEntity(nameValuePairList, StandardCharsets.UTF_8);
        }

        // 添加实体字符串
        if (StringUtils.isNotBlank(entityString)) {
            return new StringEntity(entityString, contentType);
        }

        return null;
    }

    /**
     * 执行HTTP请求的统一方法
     *
     * @param request HTTP请求对象
     * @param headers 请求头
     * @return 响应结果字符串
     * @throws Exception 请求异常
     */
    private static String executeHttpRequest(ClassicHttpRequest request, Map<String, String> headers) throws Exception {
        try (CloseableHttpClient httpClient = SSLUtils.createInsecureHttpClient()) {
            // 设置请求配置（需要类型转换）
            if (request instanceof HttpGet) {
                ((HttpGet) request).setConfig(getConfig());
            } else if (request instanceof HttpPost) {
                ((HttpPost) request).setConfig(getConfig());
            }

            addHeaders(request, headers);

            return httpClient.execute(request, response -> {
                try {
                    return handleHttpResponse(response);
                } catch (Exception e) {
                    log.error("处理HTTP响应时发生异常", e);
                    throw new RuntimeException(e);
                }
            });
        }
    }

    /**
     * 处理HTTP响应的统一方法
     *
     * @param response HTTP响应对象
     * @return 响应内容字符串
     * @throws Exception 处理异常
     */
    private static String handleHttpResponse(org.apache.hc.core5.http.ClassicHttpResponse response) throws Exception {
        int statusCode = response.getCode();
        if (statusCode == 200) {
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                String body = EntityUtils.toString(entity, StandardCharsets.UTF_8);
                EntityUtils.consume(entity);
                return body;
            }
        } else {
            log.error("状态码不为200，statusCode=【{}】", statusCode);
        }
        return null;
    }

    /**
     * 获取请求配置
     *
     * @return 请求配置对象
     */
    private static RequestConfig getConfig() {
        int defaultTimeOut = 60000;
        return RequestConfig.custom()
                .setResponseTimeout(defaultTimeOut, TimeUnit.MILLISECONDS)
                .setConnectionRequestTimeout(defaultTimeOut, TimeUnit.MILLISECONDS)
                .build();
    }
}
