package com.code.review.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.ssl.TrustStrategy;

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

        // 创建连接池管理器
        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(csf)
                .build();

        // 创建HttpClient
        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .build();
    }
}
