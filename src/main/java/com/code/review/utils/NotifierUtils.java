package com.code.review.utils;

import com.code.review.notify.WXWorkNotifier;
import org.apache.commons.lang3.StringUtils;

public class NotifierUtils {

    /**
     * 发送通知消息
     *
     * @param notification 消息内容
     * @param msgType      消息类型，默认为"text"
     * @param title        消息标题，默认为"通知"
     * @param projectName  项目名称
     * @param urlSlug      URL标识符
     */
    public static void sendNotification(String notification, String msgType, String title, String projectName, String urlSlug) {
        if (StringUtils.isBlank(msgType)) {
            msgType = "text";
        }
        if (StringUtils.isBlank(title)) {
            title = "通知";
        }
        // 发送企微消息
        WXWorkNotifier wxWorkNotifier = new WXWorkNotifier();
        wxWorkNotifier.sendWXWorkMessage(notification, msgType, title, projectName, urlSlug);
    }

    /**
     * 发送通知消息
     *
     * @param notification 消息内容
     */
    public static void sendNotification(String notification) {
        sendNotification(notification, null, null, null, null);
    }
}
