package com.code.review.utils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GitUtils {

    /**
     * 将原始URL转换为slug格式的字符串
     * 该函数会移除URL的协议部分（http://或https://），并将所有非字母数字字符替换为下划线
     * 最后会移除末尾的下划线
     *
     * @param originalUrl 原始URL字符串
     * @return 转换后的slug格式字符串
     */
    public static String slugifyUrl(String originalUrl) {
        // 移除URL协议部分（http://或https://）
        originalUrl = originalUrl.replaceFirst("^https?://", "");
        // 将所有非字母数字字符替换为下划线
        String target = originalUrl.replaceAll("[^a-zA-Z0-9]", "_");
        // 移除末尾的下划线
        target = target.replaceAll("_$", "");

        return target;
    }
}
