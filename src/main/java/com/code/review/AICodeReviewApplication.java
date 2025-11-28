package com.code.review;

import com.code.review.entity.MergeRequestEventEntity;
import com.code.review.entity.PushEventEntity;
import com.code.review.event.EventHandler;
import com.code.review.event.EventManager;
import com.code.review.utils.SpringUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AICodeReviewApplication {
    public static void main(String[] args) {
        SpringApplication.run(AICodeReviewApplication.class, args);

        // 获取事件管理器实例
        EventManager eventManager = EventManager.getInstance();
        // 获取事件处理器
        EventHandler eventHandler = SpringUtils.getBean(EventHandler.class);
        // 注册事件监听器
        eventManager.addListener("push_reviewed", event -> {
            if (event instanceof PushEventEntity) {
                eventHandler.onPushEvent((PushEventEntity) event);
            }
        });
        eventManager.addListener("merge_request_reviewed", event -> {
            if (event instanceof MergeRequestEventEntity) {
                eventHandler.onMergeRequestEvent((MergeRequestEventEntity) event);
            }
        });
    }
}
