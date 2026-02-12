# AI CodeReview - 开发规范指南

## 项目概述
基于 Spring Boot 3.2.12（Java 17）开发的 AI 代码审查自动化系统，支持 GitHub/GitLab Webhook 集成。

## 构建命令

```bash
# 编译项目
mvn compile

# 打包应用
mvn package

# 清理构建产物
mvn clean

# 完整构建（包含测试）
mvn clean package

# 跳过测试进行构建
mvn clean package -DskipTests
```

## 测试命令

```bash
# 运行所有测试
mvn test

# 运行单个测试类
mvn test -Dtest=类名

# 运行特定测试方法
mvn test -Dtest=类名#方法名

# 详细模式运行测试
mvn test -X
```

## 代码风格规范

### 项目结构
- **基础包名：** `com.code.review`
- **主类：** `AICodeReviewApplication`
- **资源目录：** `src/main/resources/`
- **服务端口：** 5000（在 application.yml 中配置）

### 包组织结构
```
com.code.review/
├── client/      # LLM API 客户端（DeepSeek、OpenAI、Coze 等）
├── config/      # 配置类（OpenAPI、线程池等）
├── controller/  # REST 控制器
├── entity/      # 实体/DTO 类
├── event/       # 事件处理系统
├── factory/     # 工厂模式类
├── mapper/      # MyBatis 映射器
├── notify/      # 通知服务
├── service/     # 业务服务层
└── utils/       # 工具类
```

### 导入顺序
1. `java.*` 包
2. 第三方库（`org.*`、`com.*` 等）
3. 项目内部导入（`com.code.review.*`）

### 命名规范
- **类名：** PascalCase（例如：`AICodeReviewController`）
- **方法名：** camelCase（例如：`handleWebhook`）
- **变量名：** camelCase（例如：`eventHandler`）
- **常量名：** UPPER_SNAKE_CASE（例如：`LLM_PROVIDER`）
- **包名：** 全小写（例如：`com.code.review.entity.github`）

### 注解使用
- 日志记录使用 `@Slf4j`（Lombok）
- 实体类使用 `@Data`（Lombok）
- 依赖注入使用 `@Resource`（不使用 @Autowired）
- REST 端点使用 `@RestController`
- OpenAPI 文档使用 `@Schema`（SpringDoc）

### Jakarta EE 迁移（重要）
Spring Boot 3.x 使用 Jakarta EE 命名空间：
- `javax.annotation.Resource` → `jakarta.annotation.Resource`
- `javax.servlet.http.HttpServletRequest` → `jakarta.servlet.http.HttpServletRequest`

### 错误处理
- 业务错误使用 `RuntimeException`
- 控制器统一返回 `AjaxResult` 保持响应格式一致
- 控制器方法中使用 `try-catch`，通过 `AjaxResult.error()` 返回错误响应

### 注释规范
- 公共 API 的方法级 Javadoc 使用英文编写
- 行内注释可使用中文（现有约定）
- 单行注释使用 `//`
- 多行注释使用 `/* */`

### 配置规范
- 环境变量使用 UPPER_SNAKE_CASE 格式
- YAML 配置位于 `application.yml`
- 数据库：MySQL + MyBatis
- 连接池：Druid

### 核心依赖
- Spring Boot Starter Web 3.2.12
- Spring Boot Starter AOP
- Lombok（仅编译时使用）
- MyBatis Spring Boot Starter 3.0.4
- SpringDoc OpenAPI 2.3.0
- Apache HttpClient 5.x
- JTokkit 1.1.0（Token 计算）

### 日志规范
- 通过 Lombok 的 `@Slf4j` 使用 SLF4J
- 日志级别：`log.info()`、`log.error()`、`log.warn()`
- 日志输出到 `code-review.log`

### 测试说明
- 当前代码库中没有测试文件
- 添加测试时使用 JUnit 5（JUnit Jupiter）
- 测试文件放在 `src/test/java/com/code/review/`
- 遵循与主代码相同的包结构

### API 文档
- OpenAPI JSON: http://localhost:5000/v3/api-docs
- Swagger UI: http://localhost:5000/swagger-ui/index.html
