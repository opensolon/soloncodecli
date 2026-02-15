/*
 * Copyright 2017-2026 noear.org and authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.noear.solon.ai.codecli.impl;

import org.noear.snack4.ONode;
import org.noear.solon.ai.agent.AgentChunk;
import org.noear.solon.ai.agent.AgentResponse;
import org.noear.solon.ai.agent.AgentSession;
import org.noear.solon.ai.agent.AgentSessionProvider;
import org.noear.solon.ai.agent.react.ReActAgent;
import org.noear.solon.ai.agent.react.ReActChunk;
import org.noear.solon.ai.agent.react.ReActRequest;
import org.noear.solon.ai.agent.react.intercept.HITL;
import org.noear.solon.ai.agent.react.intercept.HITLInterceptor;
import org.noear.solon.ai.agent.react.intercept.HITLTask;
import org.noear.solon.ai.agent.react.task.ActionChunk;
import org.noear.solon.ai.agent.react.task.ReasonChunk;
import org.noear.solon.ai.agent.session.InMemoryAgentSession;
import org.noear.solon.ai.chat.ChatModel;
import org.noear.solon.ai.chat.message.ChatMessage;
import org.noear.solon.ai.chat.prompt.Prompt;
import org.noear.solon.ai.skills.cli.CliSkill;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.Handler;
import org.noear.solon.core.util.Assert;
import org.noear.solon.core.util.MimeType;
import org.noear.solon.lang.Preview;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Code CLI 终端 (Pool-Box 模型)
 * <p>基于 ReAct 模式的代码协作终端，提供多池挂载与任务盒隔离体验</p>
 *
 * @author noear
 * @since 3.9.1
 */
@Preview("3.9.1")
public class CodeCLI implements Handler, Runnable {
    private final static Logger LOG = LoggerFactory.getLogger(CodeCLI.class);
    private final static String SESSION_DEFAULT = "cli";

    private final ChatModel chatModel;
    private AgentSessionProvider sessionProvider;
    private String name = "CodeCLI"; // 默认名称
    private String workDir = ".";
    private final Map<String, String> extraPools = new LinkedHashMap<>();
    private Consumer<ReActAgent.Builder> configurator;
    private boolean enableWeb = true;      // 默认启用 Web
    private boolean enableConsole = true;  // 默认启用控制台
    private boolean enableHitl = false;

    public CodeCLI(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 设置 Agent 名称 (同时也作为控制台输出前缀)
     */
    public CodeCLI name(String name) {
        if (name != null && !name.isEmpty()) {
            this.name = name;
        }
        return this;
    }

    public CodeCLI workDir(String workDir) {
        this.workDir = workDir;
        return this;
    }

    public CodeCLI mountPool(String alias, String dir) {
        if (dir != null) {
            this.extraPools.put(alias, dir);
        }
        return this;
    }

    public CodeCLI session(AgentSessionProvider sessionProvider) {
        this.sessionProvider = sessionProvider;
        return this;
    }

    public CodeCLI config(Consumer<ReActAgent.Builder> configurator) {
        this.configurator = configurator;
        return this;
    }

    /**
     * 是否启用 Web 交互
     */
    public CodeCLI enableWeb(boolean enableWeb) {
        this.enableWeb = enableWeb;
        return this;
    }

    /**
     * 是否启用控制台交互
     */
    public CodeCLI enableConsole(boolean enableConsole) {
        this.enableConsole = enableConsole;
        return this;
    }

    /**
     * 是否启用 HITL 交互
     */
    public CodeCLI enableHitl(boolean enableHitl) {
        this.enableHitl = enableHitl;
        return this;
    }

    private ReActAgent agent;

    protected CliSkill getSkill(AgentSession session) {
        String boxId = session.getSessionId();

        return (CliSkill) session.attrs().computeIfAbsent("CliSkill", x -> {
            CliSkill skill = new CliSkill(boxId, workDir + "/boxes/" + boxId);
            extraPools.forEach(skill::mountPool);
            return skill;
        });
    }

    protected void prepare() {
        if (agent == null) {
            if (sessionProvider == null) {
                Map<String, AgentSession> store = new ConcurrentHashMap<>();
                sessionProvider = (k) -> store.computeIfAbsent(k, InMemoryAgentSession::new);
            }

            ReActAgent.Builder agentBuilder = ReActAgent.of(chatModel)
                    .role("你的名字叫 " + name + "。")
                    .instruction("你是一个超级智能助手，有记忆能力。" +
                            "首要原则是解决任务。执行任务时参考挂载技能的【规范】与【准则】；" +
                            "若现有技能不足以完成任务，请尝试组合现有技能，或通过 bash 自主创建脚本/工具来解决问题。" +
                            "遇到 @pool 路径请阅读其 SKILL.md。");

            if (enableHitl) {
                agentBuilder.defaultInterceptorAdd(new HITLInterceptor()
                        .onTool("bash", new CodeHITLStrategy()));
            }

            if (configurator != null) {
                configurator.accept(agentBuilder);
            }

            agent = agentBuilder.build();
        }
    }

    private ReActRequest buildRequest(String sessonId, Prompt prompt) {
        if (sessonId == null) {
            sessonId = SESSION_DEFAULT;
        }

        AgentSession session = sessionProvider.getSession(sessonId);

        return agent.prompt(prompt)
                .session(session)
                .options(o -> {
                    o.skillAdd(getSkill(session));
                });
    }

    public Flux<AgentChunk> stream(String sessionId, Prompt prompt) {
        return buildRequest(sessionId, prompt)
                .stream();
    }

    public AgentResponse call(String sessionId, Prompt prompt) throws Throwable {
        return buildRequest(sessionId, prompt).call();
    }

    @Override
    public void handle(Context ctx) throws Throwable {
        if (!enableWeb) {
            ctx.status(404); // 如果未启用，直接返回 404
            return;
        }

        prepare();

        String input = ctx.param("input");
        String mode = ctx.param("m");
        String sessionId = ctx.headerOrDefault("X-Session-Id", SESSION_DEFAULT);

        if (sessionId.contains("..") || sessionId.contains("/") || sessionId.contains("\\")) {
            ctx.status(400);
            ctx.output("Invalid Session ID");
            return;
        }

        if (Assert.isNotEmpty(input)) {
            if ("call".equals(mode)) {
                ctx.contentType(MimeType.TEXT_PLAIN_UTF8_VALUE);
                String result = call(sessionId, Prompt.of(input))
                        .getContent();

                ctx.output(result);
            } else {
                ctx.contentType(MimeType.TEXT_EVENT_STREAM_UTF8_VALUE);


                Flux<String> stringFlux = stream(sessionId, Prompt.of(input))
                        .map(chunk -> {
                            if (chunk.hasContent()) {
                                if (chunk instanceof ReasonChunk) {
                                    return ONode.serialize(new Chunk("reason", chunk.getContent()));
                                } else if (chunk instanceof ActionChunk) {
                                    return ONode.serialize(new Chunk("action", chunk.getContent()));
                                } else if (chunk instanceof ReActChunk) {
                                    return ONode.serialize(new Chunk("agent", chunk.getContent()));
                                }
                            }

                            return "";
                        })
                        .filter(Assert::isNotEmpty)
                        .onErrorResume(e -> Flux.just(ONode.serialize(new Chunk("error", e.getMessage()))))
                        .concatWithValues("[DONE]");

                ctx.returnValue(stringFlux);
            }
        }
    }

    @Override
    public void run() {
        if (!enableConsole) {
            LOG.warn("SolonCodeCLI 控制台交互已禁用");
            return;
        }

        prepare();
        Scanner scanner = new Scanner(System.in);
        printWelcome();
        AgentSession session = sessionProvider.getSession("cli");

        while (true) {
            try {
                cleanInputBuffer();

                // --- 优化输出：仅在就绪时打印提示符 ---
                System.out.print("\r\033[K" + CYAN + "\uD83D\uDCBB > " + RESET);
                System.out.flush();

                if (!scanner.hasNextLine()) break;
                String input = scanner.nextLine();

                if (input == null || input.trim().isEmpty()) continue;
                if (isSystemCommand(input)) break;

                // 打印 Agent 响应前缀并清除当前行提示符余墨
                System.out.print("\r\033[K" + name + ": ");
                System.out.flush();

                performAgentTask(session, input, scanner);

            } catch (Throwable e) {
                System.err.println("\n" + RED + "[错误] " + RESET + (e.getMessage() == null ? "执行中断" : e.getMessage()));
            }
        }
    }

    final static String GRAY = "\033[90m", YELLOW = "\033[33m", GREEN = "\033[32m",
            RED = "\033[31m", CYAN = "\033[36m", RESET = "\033[0m";

    /**
     * 执行 Agent 任务（优化版：修复状态泄露与异步同步问题）
     */
    private void performAgentTask(AgentSession session, String input, Scanner scanner) throws Exception {
        String currentInput = input;
        boolean isSubmittingDecision = false;

        while (true) {
            CountDownLatch latch = new CountDownLatch(1);
            final AtomicBoolean isInterrupted = new AtomicBoolean(false);

            reactor.core.Disposable disposable = stream(session.getSessionId(), Prompt.of(currentInput))
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnNext(chunk -> {
                        // --- 优化输出：分段式渲染 ---
                        if (chunk instanceof ReasonChunk) {
                            ReasonChunk reasonChunk = (ReasonChunk) chunk;
                            if (chunk.hasContent() && !reasonChunk.isToolCalls()) {
                                // 思考过程：使用淡灰色
                                System.out.print(GRAY + clearThink(chunk.getContent()) + RESET);
                                if (reasonChunk.isFinished()) System.out.println();
                                System.out.flush();
                            }
                        } else if (chunk instanceof ActionChunk) {
                            ActionChunk actionChunk = (ActionChunk) chunk;
                            String toolName = actionChunk.getToolName();
                            String content = chunk.getContent();

                            if (Assert.isNotEmpty(toolName)) {
                                // 工具调用：块状高亮，借鉴 Claude 的清晰边界
                                System.out.println("\n" + YELLOW + " ❯ " + toolName + RESET);
                                if (Assert.isNotEmpty(content)) {
                                    System.out.println(GRAY + "   " + content.replace("\n", "\n   ") + RESET);
                                }
                            }
                            System.out.flush();
                        } else if (chunk instanceof ReActChunk) {
                            // 最终回复：与上方内容空一行，确保易读性
                            System.out.println("\n" + GREEN + "━━ " + name + " 回复 ━━━━━━━━━━━━━━━━━━━━" + RESET);
                            System.out.println(chunk.getContent());
                        }
                    })
                    .doFinally(signal -> latch.countDown())
                    .subscribe();

            if (isSubmittingDecision) {
                Thread.sleep(100);
                isSubmittingDecision = false;
            }

            // 阻塞监控循环：监听键盘中断和 HITL
            while (latch.getCount() > 0) {
                if (System.in.available() > 0) {
                    disposable.dispose();
                    isInterrupted.set(true);
                    latch.countDown();
                    break;
                }
                if (HITL.isHitl(session)) {
                    latch.countDown();
                    break;
                }
                Thread.sleep(40);
            }
            latch.await();

            if (isInterrupted.get()) {
                cleanInputBuffer();
                System.out.println(YELLOW + "\n[已中断]" + RESET);
                session.addMessage(ChatMessage.ofAssistant("【执行摘要】：该任务已被用户手动中断。"));
                return;
            }

            // --- 优化：HITL 交互区渲染 ---
            if (HITL.isHitl(session)) {
                HITLTask task = HITL.getPendingTask(session);

                System.out.println("\n" + RED + " ⚠ 需要授权 " + RESET);
                if (Assert.isNotEmpty(task.getComment())) {
                    System.out.println(GRAY + "   原因: " + task.getComment() + RESET);
                }
                if ("bash".equals(task.getToolName())) {
                    System.out.println(CYAN + "   执行: " + RESET + task.getArgs().get("command"));
                }

                System.out.print(GREEN + "   确认执行？(y/n) " + RESET);
                String choice = scanner.nextLine().trim().toLowerCase();

                if (choice.equals("y") || choice.equals("yes")) {
                    HITL.approve(session, task.getToolName());
                } else {
                    System.out.println(RED + "   已拒绝操作。" + RESET);
                    HITL.reject(session, task.getToolName());
                }

                currentInput = null;
                isSubmittingDecision = true;
                continue;
            }
            break;
        }
    }

    private String clearThink(String chunk) {
        return chunk.replaceAll("(?s)<\\s*/?think\\s*>", "");
    }

    /**
     * 清理输入缓冲区，防止中断触发的回车符污染下一个指令
     */
    private void cleanInputBuffer() throws Exception {
        Thread.sleep(50); // 给系统 IO 一点反应时间
        while (System.in.available() > 0) {
            System.in.read();
        }
    }

    /**
     * 系统指令判定
     */
    private boolean isSystemCommand(String input) {
        String cmd = input.trim().toLowerCase();
        if ("exit".equals(cmd) || "quit".equals(cmd)) {
            System.out.println("再见！");
            System.exit(0); // 强制退出 JVM
            return true;
        }

        if ("clear".equals(cmd)) {
            System.out.print("\033[H\033[2J");
            System.out.flush();
            return false;
        }
        return false;
    }

    protected void printWelcome() {
        // 获取绝对且规范化的路径，去掉多余的 "."
        String absolutePath;
        try {
            absolutePath = new File(workDir).getCanonicalPath();
        } catch (Exception e) {
            absolutePath = new File(workDir).getAbsolutePath();
        }

        System.out.println("==================================================");
        System.out.println("🚀 " + name + " 已就绪");
        System.out.println("--------------------------------------------------");
        System.out.println("📂 工作空间: " + absolutePath);

        if (!extraPools.isEmpty()) {
            System.out.println("📦 挂载技能池:");
            extraPools.forEach((k, v) -> {
                // 对池路径也做一下规范化显示
                String p = new File(v).getAbsolutePath();
                System.out.println("  - " + k + " -> " + p);
            });
        }

        System.out.println("--------------------------------------------------");
        System.out.println("💡 输入 'exit' 退出, 'clear' 清屏");
        System.out.println("🛑 在输出时按 '回车(Enter)' 可中断回复"); // 新增提示
        System.out.println("==================================================");
    }

    public static class Chunk implements Serializable {
        public final String type;
        public final String text;

        public Chunk(String type, String text) {
            this.type = type;
            this.text = text;
        }
    }
}