package com.code.review.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;

import javax.net.ssl.SSLContext;
import java.security.cert.X509Certificate;

@Slf4j
public class SSLUtils {
    /**
     * 创建一个不验证SSL的HttpClient
     */
    public static CloseableHttpClient createInsecureHttpClient() throws Exception {
        // 创建信任所有证书的策略
        TrustStrategy acceptingTrustStrategy = (X509Certificate[] chain, String authType) -> true;

        // 创建SSL上下文
        SSLContext sslContext = SSLContexts.custom()
                .loadTrustMaterial(null, acceptingTrustStrategy)
                .build();

        // 创建SSL连接工厂
        SSLConnectionSocketFactory csf = new SSLConnectionSocketFactory(
                sslContext,
                NoopHostnameVerifier.INSTANCE);

        // 创建HttpClient
        return HttpClients.custom()
                .setSSLSocketFactory(csf)
                .build();
    }
}