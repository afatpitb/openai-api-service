# OpenAI API Service (Mock 实现)

## 📌 项目介绍

本项目基于 Spring Boot 实现了一个简化版的 OpenAI API 服务，模拟官方接口行为，支持 Chat Completions、Models API、流式响应（SSE）、鉴权、限流等功能。

该项目用于学习后端开发及接口设计，同时满足相关考核要求。

---

## 🛠 使用语言与技术特性

### 编程语言

* **Java 17+**

### 使用的核心语法与特性

* **面向对象编程（OOP）**

    * 使用类（Controller / Service / Repository）进行分层设计
* **集合框架**

    * 使用 `Map`、`List` 构建 JSON 结构
* **泛型（Generics）**

    * 如 `Optional<T>`、`List<Map<String, Object>>`
* **Lambda 表达式**

    * 用于集合处理等场景
* **注解（Annotations）**

    * `@RestController`
    * `@Service`
    * `@Repository`
    * `@RequestMapping`
* **异常处理机制**

    * 使用 Spring Boot 默认异常处理 + 自定义返回结构
* **时间 API**

    * `Instant.now()` 生成时间戳

---

### 使用框架与组件

* **Spring Boot**

    * 快速构建 Web 服务
* **Spring Web MVC**

    * 处理 HTTP 请求
* **Spring Data JPA**

    * 数据持久化
* **H2 数据库**

    * 内存数据库，用于存储请求与响应
* **Guava RateLimiter**

    * 实现接口限流
* **SSE（Server-Sent Events）**

    * 实现流式响应

---

## ⚙️ 完成的功能

### ✅ 1. Chat Completions API

接口：

```
POST /v1/chat/completions
```

功能：

* 支持标准对话请求
* 返回符合 OpenAI 格式的 JSON
* 支持 temperature 参数

---

### ✅ 2. 流式响应（Streaming）

功能：

* 支持 `stream=true`
* 使用 SSE 实现逐段返回数据
* 模拟真实 OpenAI 流式输出

---

### ✅ 3. Models API

接口：

```
GET /v1/models
```

功能：

* 返回模型列表
* 数据结构符合 OpenAI 标准

---

### ✅ 4. Token 鉴权

功能：

* 所有接口必须携带 Header：

```
Authorization: Bearer sk-test-123
```

* 未携带或错误返回 401

---

### ✅ 5. 接口限流

功能：

* 使用 Guava RateLimiter
* 每秒最多 5 次请求
* 超出返回 429 错误

---

### ✅ 6. 数据存储

功能：

* 使用 H2 内存数据库
* 存储请求与响应数据
* 支持查询、删除、取消任务

---

### ✅ 7. Completion 管理接口

包括：

* 查询任务状态
* 删除任务
* 取消任务

---

### ✅ 8. Python SDK 调用

支持使用 Python 官方 SDK 调用本地接口：

```python
from openai import OpenAI

client = OpenAI(
    api_key="sk-test-123",
    base_url="http://localhost:8080/v1"
)

response = client.chat.completions.create(
    model="gpt-4o-mini",
    messages=[
        {"role": "user", "content": "你好"}
    ]
)

print(response.choices[0].message.content)
```

---

## ▶️ 项目启动方式

```bash
mvn spring-boot:run
```

启动后访问：

```
http://localhost:8080
```



## 📌 总结

本项目实现了一个简化版 OpenAI API 服务，涵盖接口设计、鉴权、限流、流式响应等核心后端能力，具备良好的扩展性和实践意义。

---

