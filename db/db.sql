SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for mr_review_log
-- ----------------------------
DROP TABLE IF EXISTS `mr_review_log`;
CREATE TABLE `mr_review_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_name` varchar(255) CHARACTER SET utf8 COLLATE utf8_unicode_ci NULL DEFAULT NULL,
  `author` varchar(255) CHARACTER SET utf8 COLLATE utf8_unicode_ci NULL DEFAULT NULL,
  `source_branch` varchar(255) CHARACTER SET utf8 COLLATE utf8_unicode_ci NULL DEFAULT NULL,
  `target_branch` varchar(255) CHARACTER SET utf8 COLLATE utf8_unicode_ci NULL DEFAULT NULL,
  `updated_at` datetime NULL DEFAULT NULL,
  `commit_messages` text CHARACTER SET utf8 COLLATE utf8_unicode_ci NULL,
  `score` int NULL DEFAULT NULL,
  `url` varchar(255) CHARACTER SET utf8 COLLATE utf8_unicode_ci NULL DEFAULT NULL,
  `review_result` text CHARACTER SET utf8 COLLATE utf8_unicode_ci NULL,
  `additions` int NULL DEFAULT 0,
  `deletions` int NULL DEFAULT 0,
  `last_commit_id` varchar(255) CHARACTER SET utf8 COLLATE utf8_unicode_ci NULL DEFAULT '',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for push_review_log
-- ----------------------------
DROP TABLE IF EXISTS `push_review_log`;
CREATE TABLE `push_review_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_name` varchar(255) CHARACTER SET utf8 COLLATE utf8_unicode_ci NULL DEFAULT NULL,
  `author` varchar(255) CHARACTER SET utf8 COLLATE utf8_unicode_ci NULL DEFAULT NULL,
  `branch` varchar(255) CHARACTER SET utf8 COLLATE utf8_unicode_ci NULL DEFAULT NULL,
  `updated_at` datetime NULL DEFAULT NULL,
  `commit_messages` text CHARACTER SET utf8 COLLATE utf8_unicode_ci NULL,
  `score` int NULL DEFAULT NULL,
  `review_result` text CHARACTER SET utf8 COLLATE utf8_unicode_ci NULL,
  `additions` int NULL DEFAULT 0,
  `deletions` int NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_unicode_ci ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
