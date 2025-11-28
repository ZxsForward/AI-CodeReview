package com.code.review.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP客户端工具类，封装了常用的HTTP GET/POST请求方法
 */
@Slf4j
public class HttpClientUtils {
    private static final CloseableHttpClient httpClient;

    static {
        try {
            httpClient = SSLUtils.createInsecureHttpClient();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

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
        String body;
        CloseableHttpResponse httpResponse = null;

        log.debug("调用get, url=【{}】", url);

        try {
            // 构建带参数的URL
            String fullUrl = buildUrlWithParams(url, params);

            HttpGet httpGet = new HttpGet(fullUrl);
            httpGet.setConfig(getConfig());

            // 添加请求头
            addHeaders(httpGet, headers);

            httpResponse = httpClient.execute(httpGet);
            body = handleResponse(httpResponse);
        } finally {
            closeResponse(httpResponse);
        }
        return body;
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
        String body = null;
        HttpPost httpPost = new HttpPost(url);
        httpPost.setConfig(getConfig());
        CloseableHttpResponse httpResponse = null;

        log.debug("调用post, url=【{}】", url);

        try {
            // 构建请求实体
            HttpEntity httpEntity = buildHttpEntity(entityString, params, jsonPost);
            httpPost.setEntity(httpEntity);

            // 添加请求头
            addHeaders(httpPost, headers);

            httpResponse = httpClient.execute(httpPost);
            body = handleResponse(httpResponse);
        } catch (IOException e) {
            log.error("调用post Exception, url=【{}】,param=【{}】", url, params, e);
        } finally {
            closeResponse(httpResponse);
        }
        return body;
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
    private static void addHeaders(HttpUriRequest request, Map<String, String> headers) {
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
        EntityBuilder entityBuilder = EntityBuilder.create();

        // 设置内容类型
        if (jsonPost) {
            entityBuilder.setContentType(ContentType.APPLICATION_JSON.withCharset("utf-8"));
        } else {
            entityBuilder.setContentType(ContentType.APPLICATION_FORM_URLENCODED.withCharset("utf-8"));
        }

        // 添加表单参数
        if (!CollectionUtils.isEmpty(params)) {
            List<NameValuePair> nameValuePairList = new ArrayList<>();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                nameValuePairList.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
            }
            entityBuilder.setParameters(nameValuePairList);
        }

        // 添加实体字符串
        if (StringUtils.isNotBlank(entityString)) {
            entityBuilder.setText(entityString);
        }

        return entityBuilder.build();
    }

    /**
     * 处理HTTP响应
     *
     * @param httpResponse HTTP响应对象
     * @return 响应体字符串
     * @throws IOException IO异常
     */
    private static String handleResponse(CloseableHttpResponse httpResponse) throws IOException {
        String body = null;
        int statusCode = httpResponse.getStatusLine().getStatusCode();

        if (statusCode == 200) {
            HttpEntity entity = httpResponse.getEntity();
            if (entity != null) {
                body = EntityUtils.toString(entity, "utf-8");
                EntityUtils.consume(entity);
            }
        } else {
            log.error("状态码不为200，statusCode=【{}】", statusCode);
        }
        return body;
    }

    /**
     * 关闭HTTP响应连接
     *
     * @param httpResponse HTTP响应对象
     */
    private static void closeResponse(CloseableHttpResponse httpResponse) {
        if (httpResponse != null) {
            try {
                httpResponse.close();
            } catch (IOException e) {
                log.error("关闭HTTP响应连接异常", e);
            }
        }
    }

    /**
     * 获取请求配置
     *
     * @return 请求配置对象
     */
    private static RequestConfig getConfig() {
        int defaultTimeOut = 60000;
        return RequestConfig.custom()
                .setSocketTimeout(defaultTimeOut)
                .setConnectTimeout(defaultTimeOut)
                .build();
    }
}
