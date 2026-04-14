# AI Development — Solon AI 开发

> 适用场景：LLM 调用、Tool Calling、RAG、MCP 协议、智能体 Agent、AI UI、Harness 框架。
>
> 目标版本：3.10.0+（当前官方最新 v3.10.3）

## ChatModel — LLM 调用

Dependency: `solon-ai`（包含 `solon-ai-core` 及所有方言包）或单独使用 `solon-ai-core`。

### 配置器构建

app.yml:
```yaml
solon.ai.chat:
  demo:
    apiUrl: "http://127.0.0.1:11434/api/chat" # 使用完整地址（而不是 api_base）
    provider: "ollama" # 使用 ollama 服务时，需要配置 provider
    model: "llama3.2"
```

```java
@Configuration
public class AiConfig {
    @Bean
    public ChatModel chatModel(@Inject("${solon.ai.chat.demo}") ChatConfig config) {
        return ChatModel.of(config).build();
    }
}
```

### Builder 原始构建

```java
ChatModel chatModel = ChatModel.of("http://127.0.0.1:11434/api/chat")
        .headerSet("x-demo", "demo1")
        .provider("ollama")
        .model("llama3.2")
        .build();
```

### 调用示例

```java
// 同步调用
ChatResponse resp = chatModel.prompt("你好").call();
String content = resp.getMessage().getContent();

// 流式调用（需 solon-web-rx）
Flux<ChatResponse> stream = chatModel.prompt("你好").stream();
```

## Tool Calling

Tool Call（或 Function Call）能够让大语言模型在生成时，“按需”调用外部的工具，进而连接外部的数据和系统。通过定义一组函数作为模型可访问的工具，并根据对话历史在适当的时候使用它们。

### @ToolMapping 注解开发

```java
public class WeatherTools {
    @ToolMapping(description = "查询天气")
    public String getWeather(@Param(description = "城市") String location) {
        return location + "：晴，14度";
    }
}
```

### 工具注册

```java
// 方式1：通过对象注册（推荐，自动扫描 @ToolMapping）
@Bean
public ChatModel chatModel(ChatConfig config) {
    return ChatModel.of(config).defaultToolAdd(new WeatherTools()).build();
}

// 方式2：通过 MethodToolProvider 注册
@Bean
public ChatModel chatModel(ChatConfig config) {
    return ChatModel.of(config).defaultToolAdd(new MethodToolProvider(new WeatherTools())).build();
}

// 方式3：通过 Lambda 注册
@Bean
public ChatModel chatModel(ChatConfig config) {
    return ChatModel.of(config)
            .defaultToolAdd("getWeather", tool -> {
                tool.description("查询天气")
                    .paramAdd("location", "string", "城市");
            })
            .build();
}
```

### 工具属性

| 属性 | 描述 |
|---|---|
| `name` | 工具名称 |
| `title` | 标题（对接 MCP tool 的 title 属性） |
| `description` | 描述（LLM 识别用） |
| `returnDirect` | 是否直接返回给调用者（跳过 LLM 再加工） |
| `inputSchema` | 输入架构 |
| `outputSchema` | 输出架构 |
| `meta` | 元信息（可对描述语进行染色） |

## RAG — 检索增强生成

Dependency: `solon-ai-rag`

### EmbeddingModel 嵌入模型

Builder 原始构建：
```java
EmbeddingModel embeddingModel = EmbeddingModel.of("http://127.0.0.1:11434/api/embed")
    .provider("ollama").model("bge-m3:latest").build();
```

配置器构建：
```yaml
solon.ai.embed:
  demo:
    apiUrl: "http://127.0.0.1:11434/api/embed"
    provider: "ollama"
    model: "bge-m3:latest"
```

```java
@Bean
public EmbeddingModel embeddingModel(@Inject("${solon.ai.embed.demo}") EmbeddingConfig config) {
    return EmbeddingModel.of(config).build();
}
```

调用：
```java
// 标准调用
EmbeddingResponse resp = embeddingModel
        .input("比较原始的风格", "能表达内在的大概过程", "太阳升起来了")
        .call();

// 快捷调用
float[] data = embeddingModel.embed("比较原始的风格");
List<Document> documents = ...;
embeddingModel.embed(documents);
```

### RerankingModel 重排模型

为文档进行相似度排序。和聊天模型一样，也会有方言及适配。

Builder 原始构建：
```java
RerankingModel rerankingModel = RerankingModel.of("https://api.moark.com/v1/rerank")
    .apiKey("***").model("bge-reranker-v2-m3").build();
```

配置器构建：
```yaml
solon.ai.rerank:
  demo:
    apiUrl: "https://api.moark.com/v1/rerank"
    apiKey: "......"
    provider: "giteeai"
    model: "bge-reranker-v2-m3"
```

```java
@Bean
public RerankingModel rerankingModel(@Inject("${solon.ai.rerank.demo}") RerankingConfig config) {
    return RerankingModel.of(config).build();
}
```

调用：
```java
// 标准调用
RerankingResponse resp = rerankingModel
        .input("比较原始的风格", "能表达内在的大概过程", "太阳升起来了")
        .call();

// 快捷调用：为文档重新排序
documents = rerankingModel.rerank(query, documents);
```

### 文档加载与分割

```java
EmbeddingModel embeddingModel = EmbeddingModel.of("http://127.0.0.1:11434/api/embed")
    .provider("ollama").model("nomic-embed-text").build();

InMemoryRepository repository = new InMemoryRepository(embeddingModel);

// 文档加载与切分
List<Document> docs = new SplitterPipeline()
    .next(new RegexTextSplitter("\n\n"))
    .next(new TokenSizeTextSplitter(500))
    .split(new PdfLoader(new File("data.pdf")).load());
repository.save(docs);

// 检索并构造增强 Prompt
List<Document> context = repository.search("查询问题");
ChatMessage msg = ChatMessage.ofUserAugment("查询问题", context);
```

### RAG Document Loaders

| Artifact | Format | Loader 类 |
|---|---|---|
| `solon-ai-load-pdf` | PDF | `PdfLoader` |
| `solon-ai-load-word` | Word (.doc/.docx) | `WordLoader` |
| `solon-ai-load-excel` | Excel (.xls/.xlsx) | `ExcelLoader` |
| `solon-ai-load-html` | HTML | `HtmlSimpleLoader` |
| `solon-ai-load-markdown` | Markdown | `MarkdownLoader` |
| `solon-ai-load-ppt` | PowerPoint (.ppt/.pptx) | `PptLoader` |

```java
// 加载示例（各 Loader 用法一致）
PdfLoader loader = new PdfLoader(new File("data.pdf"));
List<Document> docs = loader.load();
repository.insert(docs);
```

### RAG Vector Repositories

| Artifact | Backend |
|---|---|
| `solon-ai-repo-milvus` | Milvus |
| `solon-ai-repo-pgvector` | PgVector |
| `solon-ai-repo-elasticsearch` | Elasticsearch |
| `solon-ai-repo-redis` | Redis |
| `solon-ai-repo-qdrant` | Qdrant |
| `solon-ai-repo-chroma` | Chroma |
| `solon-ai-repo-weaviate` | Weaviate |
| `solon-ai-repo-dashvector` | DashVector |

### RAG WebSearch — 联网搜索

| Artifact | 搜索引擎 | Repository 类 |
|---|---|---|
| `solon-ai-search-baidu` | 百度搜索 | `BaiduWebSearchRepository` |
| `solon-ai-search-bocha` | Bocha 搜索 | `BochaWebSearchRepository` |
| `solon-ai-search-tavily` | Tavily 搜索 | `TavilyWebSearchRepository` |

## MCP — Model Context Protocol

Dependency: `solon-ai-mcp`

支持 MCP_2025-03-26 版本协议。支持 Java 8, 11, 17, 21, 25。也可嵌入到第三方框架生态（Solon、SpringBoot、jFinal、Vert.X、Quarkus、Micronaut）。

### 四种传输方式

| 传输方式 | 说明 | 备注 |
|---|---|---|
| `STDIO` | 本地进程内通讯（通过子进程启动） | 有状态 |
| `SSE` | 远程 HTTP SSE 通讯 | 有状态（MCP 官方已标为弃用） |
| `STREAMABLE` | 远程 HTTP Streamable 通讯 | 有状态（v3.5.0 后支持） |
| `STREAMABLE_STATELESS` | 远程 HTTP Streamable 无状态通讯 | 仅 server 端（v3.8.0 后支持），集群友好 |

传输方式对应表（服务端与客户端，须使用对应的传输方式才可通讯）：

| 服务端 | 客户端 | 备注 |
|---|---|---|
| `STDIO` | `STDIO` | 有状态，支持反向通讯 |
| `SSE` | `SSE` | 有状态，支持反向通讯 |
| `STREAMABLE` | `STREAMABLE` | 有状态，支持反向通讯 |
| `STREAMABLE_STATELESS` | `STREAMABLE` | 无状态，不支持反向通讯，对 server 集群很友好 |

### Server

```java
@McpServerEndpoint(channel = McpChannel.STREAMABLE, mcpEndpoint = "/mcp")
public class McpServerTool {
    @ToolMapping(description = "查询天气")
    public String getWeather(@Param(description = "城市") String location) {
        return location + "：晴，14度";
    }
}
```

### Client

```java
McpClientProvider client = McpClientProvider.builder()
    .channel(McpChannel.STREAMABLE)
    .url("http://localhost:8080/mcp").build();

// 直接调用
String result = client.callTool("getWeather", Map.of("location", "杭州")).getContent();

// 与 ChatModel 集成
ChatModel chatModel = ChatModel.of(chatConfig)
        .defaultToolAdd(client) //绑定 MCP 工具
        .build();
```

### Client 配置器构建

```yaml
solon.ai:
  mcp:
    client:
      demo:
        channel: "streamable"
        url: "http://localhost:8080/mcp"
```

```java
@Bean
public McpClientProvider mcpClient(
        @Inject("${solon.ai.mcp.client.demo}") McpClientProvider clientProvider) {
    return clientProvider;
}

@Bean
public ChatModel chatModel(@Inject("${solon.ai.chat.demo}") ChatConfig chatConfig,
                           McpClientProvider clientProvider) {
    return ChatModel.of(chatConfig)
            .defaultToolAdd(clientProvider) //添加默认工具
            .build();
}
```

## Agent — 智能体

Dependency: `solon-ai-agent`

v3.8.1 后支持。框架内置三种模式的智能体：

| 智能体 | 模式描述 |
|---|---|
| `SimpleAgent` | 简单模式，适用于简单的指令响应 |
| `ReActAgent` | 自省模式，“思考-行动-观察”循环的自反思智能体，支持工具调用 |
| `TeamAgent` | 协作模式，指挥成员按协议（如 A2A、Swarm、Sequential、Hierarchical）进行协作 |

### SimpleAgent Hello World

```java
ChatModel chatModel = ChatModel.of("https://api.moark.com/v1/chat/completions")
        .apiKey("***")
        .model("Qwen3-32B")
        .build();

SimpleAgent robot = SimpleAgent.of(chatModel)
        .defaultToolAdd(new TimeTool())
        .build();

String answer = robot.prompt("现在几点了？")
        .call()
        .getContent();

public static class TimeTool {
    @ToolMapping(description = "获取当前系统时间")
    public String getTime() {
        return LocalDateTime.now().toString();
    }
}
```

### ReActAgent（自主推理 + 工具调用）

```java
ReActAgent agent = ReActAgent.of(chatModel)
    .name("assistant")
    .defaultToolAdd(new SearchTools())
    .maxSteps(5)
    .build();
String answer = agent.prompt("搜索并总结...").call().getContent();
```

### TeamAgent（多 Agent 协作）

```java
TeamAgent team = TeamAgent.of(chatModel)
    .name("DevTeam")
    .protocol(TeamProtocols.SEQUENTIAL) // 支持 SEQUENTIAL, A2A, SWARM, HIERARCHICAL, NONE
    .agentAdd(coder, reviewer)
    .build();
String result = team.prompt("写一个单例模式").call().getContent();
```

### Agent 接口核心属性

| 维度 | 属性 | 描述 |
|---|---|---|
| 身份 | `name` | 唯一标识：智能体在团队中的名字 |
| 角色 | `role` | 智能体角色职责（用于 Prompt 提示与协作分发参考） |
| 画像 | `profile` | 交互契约：定义能力画像、输入限制等约束条件 |
| 执行 | `call` | 核心逻辑：具体的推理与工具执行过程 |

## AI UI — 对接 Vercel AI SDK

Dependency: `solon-ai-ui-aisdk`

将 `ChatModel.prompt().stream()` 的 `Flux<ChatResponse>` 自动转换为 [UI Message Stream Protocol v1](https://ai-sdk.dev/docs/ai-sdk-ui/stream-protocol) 格式的 SSE 事件流，前端可直接使用 `@ai-sdk/vue` 或 `@ai-sdk/react` 的 `useChat`。

支持：文本流、深度思考(reasoning)、工具调用(tool-calls)、搜索结果引用(source-url)、文档引用(source-document)、文件(file)、自定义数据(data-*)、元数据(metadata)。

### 后端示例

```java
@Controller
public class AiChatController {
    @Inject ChatModel chatModel;
    private final AiSdkStreamWrapper wrapper = AiSdkStreamWrapper.of();

    @Produces(MimeType.TEXT_EVENT_STREAM_UTF8_VALUE)
    @Mapping("/ai/chat/stream")
    public Flux<SseEvent> stream(String prompt, Context ctx) {
        ctx.headerSet("x-vercel-ai-ui-message-stream", "v1");
        return wrapper.toAiSdkStream(chatModel.prompt(prompt).stream());
    }
}
```

### 带会话记忆 + 元数据

```java
@Controller
public class AiChatController {
    @Inject ChatModel chatModel;
    private final AiSdkStreamWrapper wrapper = AiSdkStreamWrapper.of();
    private final Map<String, ChatSession> sessionMap = new ConcurrentHashMap<>();

    @Produces(MimeType.TEXT_EVENT_STREAM_UTF8_VALUE)
    @Mapping("/ai/chat/stream")
    public Flux<SseEvent> stream(@Header("sessionId") String sessionId,
                                 String prompt, Context ctx) {
        ctx.headerSet("x-vercel-ai-ui-message-stream", "v1");
        ChatSession session = sessionMap.computeIfAbsent(sessionId,
                k -> InMemoryChatSession.builder().sessionId(k).build());
        Map<String, Object> metadata = Map.of("sessionId", sessionId);
        return wrapper.toAiSdkStream(
                chatModel.prompt(prompt).session(session).stream(), metadata);
    }
}
```

### 前端对接（Vue 3 + @ai-sdk/vue）

```vue
<script setup lang="ts">
import { useChat } from '@ai-sdk/vue'
const { messages, input, handleSubmit, status } = useChat({
  api: '/ai/chat/stream'
})
</script>
```

### 核心 Part 类

| Part 类 | type 值 | 说明 |
|---|---|---|
| `StartPart` | `start` | 流开始（含 messageId） |
| `TextStartPart` / `TextDeltaPart` / `TextEndPart` | `text-start` / `text-delta` / `text-end` | 正文流 |
| `ReasoningStartPart` / `ReasoningDeltaPart` / `ReasoningEndPart` | `reasoning-*` | 深度思考流 |
| `ToolInputStartPart` / `ToolInputDeltaPart` / `ToolInputAvailablePart` / `ToolOutputAvailablePart` | `tool-*` | 工具调用流 |
| `SourceUrlPart` / `SourceDocumentPart` | `source-url` / `source-document` | 引用来源 |
| `FilePart` | `file` | 文件附件 |
| `DataPart` | `data-*` | 自定义数据 |
| `FinishPart` | `finish` | 流结束（含 usage） |
| `ErrorPart` | `error` | 错误 |

### 自定义 Data Part

```java
DataPart weatherPart = DataPart.of("weather", Map.of("location", "SF", "temperature", 100));
// → {"type":"data-weather","data":{"location":"SF","temperature":100}}
```

## ACP — Agent Client Protocol

Dependency: `solon-ai-acp`

提供 ACP 协议支持（stdio、websocket），支持完整的 ACP 能力开发。

```xml
<dependency>
    <groupId>org.noear</groupId>
    <artifactId>solon-ai-acp</artifactId>
</dependency>
```

## A2A — Agent to Agent

Dependency: `solon-ai-a2a`

提供智能体间通信协议支持。

```xml
<dependency>
    <groupId>org.noear</groupId>
    <artifactId>solon-ai-a2a</artifactId>
</dependency>
```

## GenerateModel — 生成模型（图/音/视）

也支持图像生成或修改模型（由 GenerateModel 体系替代原 ImageModel 体系）。

```java
// 图像生成示例
GenerateModel generateModel = GenerateModel.of(apiUrl)
        .provider(...)
        .model(...)
        .build();

GenerateResponse resp = generateModel.prompt("一只猫的插画").call();
```

## AI Skills — 技能体系

Dependency: 各 `solon-ai-skill-*` 插件

v3.9.0 后支持。Solon AI Skills 是一种可插拔的技能扩展机制，可以动态加载到 ChatModel 或 Agent 中使用。

| Artifact | 描述 |
|---|---|
| `solon-ai-skill-cli` | CLI 命令行技能（支持 bash, read, edit, grep, glob 等） |
| `solon-ai-skill-restapi` | REST API 技能 |
| `solon-ai-skill-toolgateway` | 工具网关技能 |
| `solon-ai-skill-memory` | 记忆技能（支持会话隔离与共享） |
| `solon-ai-skill-lucene` | Lucene 搜索技能 |
| `solon-ai-skill-diff` | 文本差异对比技能 |

```java
// 技能使用示例
ChatModel chatModel = ChatModel.of(config)
        .defaultSkillAdd(new CliSkill())
        .build();
```

## Harness — 智能体马具框架

Dependency: `solon-ai-harness`

v3.10.1 后支持。通过 `solon-ai-skill-*` 插件组合并定制而成的高性能智能体执行框架。

### 核心职责

- **工具使用 (Tool Steering)**: 动态挂载、MCP 协议、安全拦截
- **记忆与会话 (Memory & Session)**: 持久化、短期/长期记忆、快照恢复
- **上下文工程 (Context Engineering)**: 窗口滑窗、语义压缩、意图聚焦
- **环境隔离 (Sandbox)**: 影子空间、自愈循环

### Helloworld

```java
public class DemoApp {
    public static void main(String[] arg) throws Throwable {
        //--- 1. 初始化
        HarnessProperties harnessProps = new HarnessProperties(".tmp/");
        harnessProps.addTools(ToolPermission.TOOL_PI); //设定工具权限
        harnessProps.addModel(null); //设定大模型配置

        ChatModel chatModel = ChatModel.of(harnessProps.getChatModel()).build();
        AgentSessionProvider sessionProvider = new AgentSessionProvider() {
            private Map<String, AgentSession> sessionMap = new ConcurrentHashMap<>();

            @Override
            public AgentSession getSession(String instanceId) {
                return sessionMap.computeIfAbsent(instanceId,
                        k -> InMemoryAgentSession.of(k));
            }
        };

        HarnessEngine engine = HarnessEngine.builder()
                .properties(harnessProps)
                .chatModel(chatModel)
                .sessionProvider(sessionProvider)
                .build();

        //--- 用主代理执行
        case1(engine, "hello");

        //--- 动态创建子代理执行（可以动态创建不同的工具权限）
        case2(engine, "hello");
    }

    private static void case1(HarnessEngine engine, String prompt) throws Throwable {
        AgentSession session = engine.getSession(HarnessEngine.SESSION_DEFAULT);

        engine.getMainAgent().prompt(prompt)
                .session(session) //没有，则为临时会话
                .options(o -> {
                    //按需，动态指定工作区（没有，则为默认工作区）
                    o.toolContextPut(HarnessEngine.ATTR_CWD, "xxx");
                })
                .call();
    }

    private static void case2(HarnessEngine engine, String prompt) throws Throwable {
        AgentSession session = engine.getSession(HarnessEngine.SESSION_DEFAULT);

        //动态定义智能体
        AgentDefinition definition = new AgentDefinition();
        definition.setSystemPrompt("xxx"); //系统提示词
        definition.getMetadata().addTools(ToolPermission.TOOL_BASH); //工具权限

        ReActAgent subagent = engine.createSubagent(definition).build();
        subagent.prompt(prompt)
                .session(session)
                .options(o -> {
                    o.toolContextPut(HarnessEngine.ATTR_CWD, "xxx");
                })
                .call();
    }
}
```

### 工具权限配置

| 工具名 | 类型 | 描述 |
|---|---|---|
| `**` | - | 所有公域 + 私域工具 |
| `*` | - | 仅所有公域工具 |
| `pi` | - | piagent 权限（read, write, edit, bash） |
| `hitl` | 私域 | 人工介入审核 |
| `generate` | 私域 | 动态生成子代理 |
| `mcp` | 私域 | MCP 服务接入 |
| `bash` / `read` / `edit` / `ls` / `grep` / `glob` | 公域 | 文件与命令操作 |
| `websearch` / `webfetch` / `codesearch` | 公域 | 网络搜索 |
| `todo` / `skill` / `task` / `code` | 公域 | 任务管理 |

### 内置拦截器

- `summarizationInterceptor` — 上下文摘要处理
- `hitlInterceptor` — 人工介入处理

```java
HarnessEngine engine = HarnessEngine.builder()
        .properties(harnessProps)
        .sessionProvider(sessionProvider)
        .summarizationInterceptor(new SummarizationInterceptor())
        .hitlInterceptor(new HITLInterceptor())
        .build();
```

---

## AI 注解参考

| Annotation | Target | Description |
|---|---|---|
| `@ToolMapping` | Method | 声明 AI 工具方法（必填 description） |
| `@ToolMapping(name="...")` | Method | 指定工具名称 |
| `@ToolMapping(returnDirect=true)` | Method | 工具结果直接返回给调用者（跳过 LLM 加工） |
| `@McpServerEndpoint` | Class | 声明 MCP 服务端点（必填 channel, mcpEndpoint） |
| `@Param(description="...")` | Parameter | 工具参数描述 |

## AI 核心 API 参考

| Class/Interface | Description |
|---|---|
| `ChatModel` | LLM 调用核心接口，支持 call/stream |
| `ChatConfig` | ChatModel 配置类，可从 yml 注入 |
| `ChatResponse` | 聊天响应 |
| `ChatMessage` | 消息构建（User/System/Assistant/Tool 四种类型），支持 ofUserAugment |
| `ChatSession` | 会话管理，支持多轮对话 |
| `InMemoryChatSession` | 内存会话实现 |
| `ChatDialect` | 聊天方言（用于适配不同的 LLM 接口规范） |
| `FunctionTool` | 函数工具接口 |
| `ToolProvider` | 工具提供者接口 |
| `MethodToolProvider` | 方法工具提供者（分析 @ToolMapping 注解） |
| `EmbeddingModel` | 嵌入模型接口 |
| `EmbeddingConfig` | 嵌入模型配置类 |
| `RerankingModel` | 重排模型接口 |
| `RerankingConfig` | 重排模型配置类 |
| `InMemoryRepository` | 内存向量知识库 |
| `SplitterPipeline` | 文档分割管道 |
| `SimpleAgent` | 简单智能体 |
| `ReActAgent` | 推理行动 Agent（自反思模式） |
| `TeamAgent` | 多 Agent 协作 |
| `AgentSession` | 智能体会话 |
| `McpClientProvider` | MCP 客户端（同时提供 Tool/Prompt/Resource） |
| `McpChannel` | MCP 通道类型（STDIO/SSE/STREAMABLE/STREAMABLE_STATELESS） |
| `AiSdkStreamWrapper` | AI SDK 协议流包装器 |
| `HarnessEngine` | 智能体马具引擎 |
| `GenerateModel` | 生成模型（图/音/视） |

## AI 核心依赖

| Artifact | Description |
|---|---|
| `solon-ai-core` | AI 核心模块（ChatModel/ToolCall/ChatSession） |
| `solon-ai` | 核心 AI 模块（包含 solon-ai-core 及所有方言包） |
| `solon-ai-rag` | RAG 检索增强生成 |
| `solon-ai-mcp` | MCP 协议支持 |
| `solon-ai-agent` | Agent 框架（Simple/ReAct/Team） |
| `solon-ai-flow` | AI + Flow 集成 |
| `solon-ai-ui-aisdk` | AI UI — 对接 Vercel AI SDK 协议 |
| `solon-ai-acp` | ACP 协议支持（stdio/websocket） |
| `solon-ai-a2a` | A2A 智能体间通信 |
| `solon-ai-harness` | 智能体马具框架 |
| `solon-ai-search-baidu` | 百度联网搜索 |
| `solon-ai-search-bocha` | Bocha 联网搜索 |
| `solon-ai-search-tavily` | Tavily 联网搜索 |

## LLM Dialects（聊天方言）

solon-ai 通过方言适配机制兼容各种不同的大模型接口。ChatConfig 通过 provider 或 apiUrl 自动识别模型服务并选择对应的方言。

| Artifact | Provider 配置值 | 描述 |
|---|---|---|
| `solon-ai-dialect-openai` | `openai`（默认） | 兼容 openai 的接口规范。DeepSeek、QWen、GLM、Kimi、GPT 等兼容 |
| `solon-ai-dialect-ollama` | `ollama` | 兼容 ollama 的接口规范 |
| `solon-ai-dialect-gemini` | `gemini` | 兼容 google gemini 的接口规范（v3.8.1 后可试用） |
| `solon-ai-dialect-anthropic` | `anthropic` | 兼容 anthropic claude 的接口规范（v3.9.1 后可试用） |
| `solon-ai-dialect-dashscope` | `dashscope` | 兼容阿里云百炼的接口规范 |

> 提醒：一般匹配不到方言时？要么是 provider 配置有问题，要么是 pom 缺少相关的依赖包。
