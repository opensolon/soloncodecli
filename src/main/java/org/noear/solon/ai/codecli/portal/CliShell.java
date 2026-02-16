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
package org.noear.solon.ai.codecli.portal;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.completer.FileNameCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp;
import org.noear.solon.ai.agent.AgentSession;
import org.noear.solon.ai.agent.react.ReActChunk;
import org.noear.solon.ai.agent.react.intercept.HITL;
import org.noear.solon.ai.agent.react.intercept.HITLTask;
import org.noear.solon.ai.agent.react.task.ActionChunk;
import org.noear.solon.ai.agent.react.task.ReasonChunk;
import org.noear.solon.ai.chat.prompt.Prompt;
import org.noear.solon.ai.codecli.core.AgentNexus;
import org.noear.solon.ai.codecli.core.skills.CodeSkill;
import org.noear.solon.core.util.Assert;
import org.noear.solon.lang.Preview;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Code CLI 终端 (Claude Code 风格对齐版)
 */
@Preview("3.9.4")
public class CliShell implements Runnable {
    private final static Logger LOG = LoggerFactory.getLogger(CliShell.class);

    private Terminal terminal;
    private LineReader reader;
    private final AgentNexus codeAgent;

    // ANSI 颜色常量 - 严格对齐 Claude 极简风
    private final static String
            BOLD = "\033[1m",
            DIM = "\033[2m",
            GREEN = "\033[32m",
            YELLOW = "\033[33m",
            RED = "\033[31m",
            CYAN = "\033[36m",
            RESET = "\033[0m";

    public CliShell(AgentNexus codeAgent) {
        this.codeAgent = codeAgent;
        try {
            this.terminal = TerminalBuilder.builder()
                    .jna(true).jansi(true).system(true).dumb(true).build();

            this.reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(new FileNameCompleter())
                    .build();
        } catch (Exception e) {
            LOG.error("JLine initialization failed", e);
        }
    }

    @Override
    public void run() {
        codeAgent.prepare();
        printWelcome();
        AgentSession session = codeAgent.getSession("cli");

        // 1. 初始化对齐
        codeAgent.init(session);

        // 2. 主循环
        while (true) {
            try {
                String promptStr = "\n" + BOLD + CYAN + "User" + RESET + "\n" + BOLD + CYAN + "> " + RESET;
                String input;

                try {
                    input = reader.readLine(promptStr);
                } catch (UserInterruptException e) {
                    continue;
                } catch (EndOfFileException e) {
                    break;
                }

                if (Assert.isEmpty(input)) continue;

                if (!isSystemCommand(session, input)) {
                    terminal.writer().println("\n" + BOLD + codeAgent.getName() + RESET);
                    performAgentTask(session, input);
                }
            } catch (Throwable e) {
                terminal.writer().println("\n" + RED + "! Error: " + RESET + e.getMessage());
            }
        }
    }

    private void performAgentTask(AgentSession session, String input) throws Exception {
        String currentInput = input;
        final AtomicBoolean isTaskCompleted = new AtomicBoolean(false);

        while (true) {
            CountDownLatch latch = new CountDownLatch(1);
            final AtomicBoolean isInterrupted = new AtomicBoolean(false);
            final AtomicBoolean isFirstReasonChunk = new AtomicBoolean(true);

            reactor.core.Disposable disposable = codeAgent.stream(session.getSessionId(), Prompt.of(currentInput))
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnNext(chunk -> {
                        if (chunk instanceof ReasonChunk) {
                            ReasonChunk reason = (ReasonChunk) chunk;
                            if (!reason.isToolCalls()) {
                                String delta = clearThink(reason.getContent());

                                if (Assert.isNotEmpty(delta)) {
                                    // 对所有换行进行缩进处理
                                    delta = delta.replace("\n", "\n  ");

                                    if (isFirstReasonChunk.get()) {
                                        // 彻底裁剪掉首块前的所有前导换行（因为 ActionChunk 已经给过换行了）
                                        String trimmed = delta.replaceAll("^[\\s\\n]+", "");
                                        if (Assert.isNotEmpty(trimmed)) {
                                            // 打印两个空格作为首行缩进，接上内容
                                            terminal.writer().print("  " + trimmed);
                                            isFirstReasonChunk.set(false);
                                        }
                                        // 如果 trimmed 是空的，说明这块全是空白，等下一块内容
                                    } else {
                                        terminal.writer().print(delta);
                                    }
                                    terminal.flush();
                                }
                            }
                        } else if (chunk instanceof ActionChunk) {
                            ActionChunk action = (ActionChunk) chunk;
                            if (Assert.isNotEmpty(action.getToolName())) {
                                // 1. 打印指令行（面包屑）
                                terminal.writer().println();
                                terminal.writer().println(YELLOW + "❯ " + RESET + BOLD + action.getToolName() + RESET);

                                // 2. 处理工具返回的结果内容 (getContent)
                                if (Assert.isNotEmpty(action.getContent())) {
                                    // 给结果内容的每一行开头都加上 2 个空格的缩进
                                    String indentedContent = "  " + action.getContent().trim().replace("\n", "\n  ");

                                    // 使用 DIM 颜色（灰色）让工具输出看起来更像“系统日志”，不干扰主视线
                                    terminal.writer().println(DIM + indentedContent + RESET);
                                }

                                terminal.flush();

                                // 3. 接下来 AI 可能会针对这个结果进行分析 (Reasoning)，设置首行缩进标记
                                isFirstReasonChunk.set(true);
                            }
                        } else if (chunk instanceof ReActChunk) {
                            isTaskCompleted.set(true);
                            ReActChunk reAct = (ReActChunk) chunk;
                            if (reAct.getTrace().getMetrics() != null) {
                                terminal.writer().println(DIM + " (" + reAct.getTrace().getMetrics().getTotalTokens() + " tokens)" + RESET);
                            }
                        }
                    })
                    .doOnError(e -> {
                        terminal.writer().println("\n" + RED + "── Error ────────────────" + RESET);
                        terminal.writer().println(e.getMessage());
                        isTaskCompleted.set(true);
                    })
                    .doFinally(signal -> latch.countDown())
                    .subscribe();

            // 监听回车中断
            while (latch.getCount() > 0) {
                if (terminal.reader().peek(10) != -2) {
                    int c = terminal.reader().read();
                    if (c == '\r' || c == '\n') {
                        disposable.dispose();
                        isInterrupted.set(true);
                        latch.countDown();
                        break;
                    }
                }
                if (HITL.isHitl(session)) {
                    latch.countDown();
                    break;
                }
                Thread.sleep(30);
            }
            latch.await();

            if (isInterrupted.get()) {
                terminal.writer().println(DIM + "[Task interrupted]" + RESET);
                session.addMessage(org.noear.solon.ai.chat.message.ChatMessage.ofAssistant("Task interrupted by user."));
                return;
            }

            // HITL 处理 (授权交互)
            if (HITL.isHitl(session)) {
                HITLTask task = HITL.getPendingTask(session);
                terminal.writer().println("\n" + BOLD + YELLOW + "Permission Required" + RESET);
                if ("bash".equals(task.getToolName())) {
                    terminal.writer().println(DIM + "Command: " + RESET + task.getArgs().get("command"));
                }

                String choice = reader.readLine(BOLD + GREEN + "Approve? (y/n) " + RESET).trim().toLowerCase();
                if ("y".equals(choice) || "yes".equals(choice)) {
                    HITL.approve(session, task.getToolName());
                    currentInput = null; // 触发 Agent 继续执行
                    continue;
                } else {
                    HITL.reject(session, task.getToolName());
                    terminal.writer().println(DIM + "Action rejected." + RESET);
                    return;
                }
            }

            if (isTaskCompleted.get()) return;
            break;
        }
    }

    private String clearThink(String chunk) {
        return chunk.replaceAll("(?s)<\\s*/?think\\s*>", "");
    }

    private boolean isSystemCommand(AgentSession session, String input) {
        String cmd = input.trim().toLowerCase();
        if ("exit".equals(cmd) || "quit".equals(cmd)) {
            terminal.writer().println(DIM + "Exiting..." + RESET);
            System.exit(0);
            return true;
        }
        if ("init".equals(cmd)) {
            terminal.writer().println(DIM + "Re-initializing workspace..." + RESET);
            String result = codeAgent.init(session);
            terminal.writer().println(DIM + result + RESET);
            return true;
        }
        if ("clear".equals(cmd)) {
            terminal.puts(InfoCmp.Capability.clear_screen);
            return true;
        }
        return false;
    }

    protected void printWelcome() {
        String path = new File(codeAgent.getWorkDir()).getAbsolutePath();
        // 连带版本号，紧凑排列
        terminal.writer().println(BOLD + codeAgent.getName() + RESET + DIM + " v3.9.4" + RESET);
        terminal.writer().println(DIM + path + RESET);
        // 仅保留一个空行
        terminal.writer().println();
        terminal.flush();
    }
}