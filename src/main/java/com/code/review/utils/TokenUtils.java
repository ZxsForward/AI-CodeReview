package com.code.review.utils;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

@Slf4j
public class TokenUtils {

    /**
     * 计算文本的 token 数量。
     *
     * @param text 输入文本
     * @return token 数量
     */
    public static int countTokens(String text) {
        if (StringUtils.isBlank(text)) {
            return 0;
        }

        // 获取编码器 (适用于 OpenAI GPT 系列)
        Encoding encoding = Encodings.newDefaultEncodingRegistry()
                .getEncoding(EncodingType.CL100K_BASE);

        // 编码文本为 tokens 并返回数量
        return encoding.encode(text).size();
    }

    /**
     * 根据token数量截断文本
     *
     * @param changeText 需要截断的文本内容
     * @param reviewMaxTokens 最大允许的token数量
     * @return 截断后的文本，如果原文本为空或未超出限制则返回原文本
     */
    public static String truncateTextByTokens(String changeText, int reviewMaxTokens) {
        if (StringUtils.isBlank(changeText)) {
            return changeText;
        }

        // 获取编码器
        Encoding encoding = Encodings.newDefaultEncodingRegistry()
                .getEncoding(EncodingType.CL100K_BASE);

        // 将文本编码为 tokens
        List<Integer> tokens = encoding.encode(changeText).boxed();

        // 如果 tokens 数量超过最大限制，则截断
        if (tokens.size() > reviewMaxTokens) {
            IntArrayList truncatedTokens = new IntArrayList();
            for (int i = 0; i < reviewMaxTokens; i++) {
                truncatedTokens.add(tokens.get(i));
            }
            return encoding.decode(truncatedTokens);
        }

        return changeText;
    }
}
