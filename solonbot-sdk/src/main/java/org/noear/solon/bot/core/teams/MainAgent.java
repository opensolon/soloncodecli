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
package org.noear.solon.bot.core.teams;

import org.noear.solon.ai.agent.AgentResponse;
import org.noear.solon.ai.agent.AgentSession;
import org.noear.solon.ai.agent.AgentSessionProvider;
import org.noear.solon.ai.agent.react.ReActAgent;
import org.noear.solon.ai.chat.ChatModel;
import org.noear.solon.ai.chat.prompt.Prompt;
import org.noear.solon.bot.core.CliSkillProvider;
import org.noear.solon.bot.core.PoolManager;
import org.noear.solon.bot.core.SystemPrompt;
import org.noear.solon.bot.core.event.AgentEvent;
import org.noear.solon.bot.core.event.AgentEventType;
import org.noear.solon.bot.core.event.EventBus;
import org.noear.solon.bot.core.event.EventHandler;
import org.noear.solon.bot.core.event.EventMetadata;
import org.noear.solon.bot.core.memory.SharedMemoryManager;
import org.noear.solon.bot.core.memory.ShortTermMemory;
import org.noear.solon.bot.core.message.AgentMessage;
import org.noear.solon.bot.core.message.MessageAck;
import org.noear.solon.bot.core.message.MessageChannel;
import org.noear.solon.bot.core.subagent.SubAgentMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 主代理（Team Lead）- Agent Teams 模式协调器
 *
 * 负责任务：
 * - 创建和管理共享任务列表
 * - 分配初始任务到共享任务池
 * - 协调多个子代理协作
 * - 监控任务执行状态
 * - 汇总最终结果
 *
 * @author bai
 * @since 3.9.5
 */
public class MainAgent {
    private static final Logger LOG = LoggerFactory.getLogger(MainAgent.class);

    private final SubAgentMetadata config;
    private final AgentSessionProvider sessionProvider;
    private final SharedMemoryManager sharedMemoryManager;
    private final EventBus eventBus;
    private final MessageChannel messageChannel;
    private final SharedTaskList taskList;
    private final String workDir;
    private final PoolManager poolManager;

    private ReActAgent agent;
    private AgentSession session;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // 任务事件监听器
    private String taskEventSubscriptionId;
    private String taskFailedSubscriptionId;  //  新增：保存失败事件订阅ID
    private String messageHandlerId;

    public MainAgent(SubAgentMetadata config,
                     AgentSessionProvider sessionProvider,
                     SharedMemoryManager sharedMemoryManager,
                     EventBus eventBus,
                     MessageChannel messageChannel,
                     SharedTaskList taskList,
                     String workDir,
                     PoolManager poolManager) {
        this.config = config;
        this.sessionProvider = sessionProvider;
        this.sharedMemoryManager = sharedMemoryManager;
        this.eventBus = eventBus;
        this.messageChannel = messageChannel;
        this.taskList = taskList;
        this.workDir = workDir;
        this.poolManager = poolManager;

        // 注册任务事件监听器
        registerTaskEventListeners();

        // 注册消息处理器
        registerMessageHandler();
    }

    /**
     * 初始化主代理
     */
    public synchronized void initialize(ChatModel chatModel) {
        if (agent == null) {
            ReActAgent.Builder builder = ReActAgent.of(chatModel);

            // 设置系统提示词
            builder.systemPrompt(SystemPrompt.builder()
                    .instruction(getSystemPrompt())
                    .build());

            // 添加技能
            CliSkillProvider skillProvider = new CliSkillProvider(workDir);
            if (poolManager != null) {
                poolManager.getPoolMap().forEach((alias, path) -> {
                    skillProvider.skillPool(alias, path);
                });
            }

            builder.defaultSkillAdd(skillProvider.getTerminalSkill());
            builder.defaultSkillAdd(skillProvider.getExpertSkill());

            // 设置较大的步数（主代理需要协调多个任务）
            builder.maxSteps(50);
            builder.sessionWindowSize(10);

            this.agent = builder.build();
            this.session = sessionProvider.getSession("main_agent");

            LOG.info("MainAgent '{}' 初始化完成", config.getCode());
        }
    }

    /**
     * 执行主任务
     *
     * @param prompt 用户提示
     * @return 响应结果
     */
    public AgentResponse execute(Prompt prompt) throws Throwable {
        if (agent == null) {
            throw new IllegalStateException("MainAgent 尚未初始化");
        }

        running.set(true);

        try {
            // 1. 发布主代理任务开始事件
            publishEvent(AgentEventType.MAIN_TASK_STARTED, prompt.getUserContent(), null);

            // 2. 分析任务并创建子任务
            List<TeamTask> subTasks = analyzeAndCreateTasks(prompt);

            // 3. 将任务添加到共享任务列表（带错误处理和验证）
            if (!subTasks.isEmpty()) {
                try {
                    // 打印依赖图
                    LOG.debug("任务依赖关系图:\n{}", taskList.getDependencyGraph());

                    // 检测循环依赖
                    List<TeamTask> cyclicTasks = taskList.detectCyclicDependencies();
                    if (!cyclicTasks.isEmpty()) {
                        LOG.error("检测到循环依赖，任务将被拒绝:");
                        for (TeamTask task : cyclicTasks) {
                            LOG.error("  - {}", task.getTitle());
                        }
                        throw new IllegalStateException("存在循环依赖，无法添加任务");
                    }

                    List<TeamTask> added = taskList.addTasks(subTasks).join();
                    LOG.info("主代理已添加 {} 个子任务到共享任务列表", added.size());

                    // 4. 广播任务可用通知
                    broadcastTaskNotification(added);

                    // 5. 记录可认领任务数
                    List<TeamTask> claimableTasks = taskList.getClaimableTasks();
                    LOG.info("当前可认领任务数: {} / {}", claimableTasks.size(), added.size());

                } catch (IllegalStateException e) {
                    LOG.error("添加任务失败: {}", e.getMessage());
                    // 继续执行主代理自身任务
                } catch (Exception e) {
                    LOG.error("添加任务时发生异常: {}", e.getMessage(), e);
                    // 继续执行主代理自身任务
                }
            }

            // 6. 执行主代理自身的协调逻辑
            AgentResponse response = agent.prompt(prompt)
                    .session(session)
                    .call();

            // 7. 等待所有子任务完成
            waitForAllTasksCompleted();

            // 8. 汇总结果
            String summary = summarizeResults();

            // 9. 发布主代理任务完成事件
            publishEvent(AgentEventType.MAIN_TASK_COMPLETED, summary, null);

            return response;

        } finally {
            running.set(false);
        }
    }

    /**
     * 分析任务并创建子任务
     */
    private List<TeamTask> analyzeAndCreateTasks(Prompt prompt) {
        List<TeamTask> tasks = new ArrayList<>();
        String userPrompt = prompt.getUserContent().toLowerCase();

        // 从共享内存中读取相关信息（如果有历史任务结果）
        StringBuilder contextBuilder = new StringBuilder();
        if (sharedMemoryManager != null) {
            try {
                // 查询相关的历史任务结果
                List<org.noear.solon.bot.core.memory.Memory> recentMemories =
                        sharedMemoryManager.search("task-result", 10);

                if (!recentMemories.isEmpty()) {
                    contextBuilder.append("\n\n# 历史任务上下文\n\n");
                    for (org.noear.solon.bot.core.memory.Memory memory : recentMemories) {
                        if (memory instanceof ShortTermMemory) {
                            ShortTermMemory stm = (ShortTermMemory) memory;
                            String taskTitle = (String) stm.getMetadata("taskTitle");
                            contextBuilder.append(String.format("- **%s**: %s\n",
                                    taskTitle != null ? taskTitle : "Unknown",
                                    stm.getContext()));
                        }
                    }
                    LOG.debug("从共享内存加载了 {} 条历史任务记录", recentMemories.size());
                }
            } catch (Exception e) {
                LOG.warn("从共享内存读取历史任务失败", e);
            }
        }

        // 根据提示内容智能创建任务
        // 这里是简化版本，实际可以使用 LLM 来分析任务
        // 可以将 contextBuilder 中的内容添加到任务的描述中

        if (userPrompt.contains("探索") || userPrompt.contains("explore") || userPrompt.contains("分析")) {
            // 创建探索任务链：探索 -> 搜索
            String exploreId = "task-explore-" + System.currentTimeMillis();
            String searchId = "task-search-" + System.currentTimeMillis();

            TeamTask exploreTask = TeamTask.builder()
                    .id(exploreId)
                    .title("探索代码库")
                    .description("探索和分析代码库结构")
                    .type(TeamTask.TaskType.EXPLORATION)
                    .priority(8)
                    .build();

            TeamTask searchTask = TeamTask.builder()
                    .id(searchId)
                    .title("搜索关键文件")
                    .description("搜索与任务相关的关键文件")
                    .type(TeamTask.TaskType.ANALYSIS)
                    .priority(7)
                    .dependencies(Collections.singletonList(exploreId))
                    .build();

            tasks.add(exploreTask);
            tasks.add(searchTask);
        }

        if (userPrompt.contains("实现") || userPrompt.contains("开发") || userPrompt.contains("implement")) {
            // 创建开发任务链：计划 -> 实现 -> 测试
            String planId = "task-plan-" + System.currentTimeMillis();
            String implId = "task-impl-" + System.currentTimeMillis();
            String testId = "task-test-" + System.currentTimeMillis();

            TeamTask planTask = TeamTask.builder()
                    .id(planId)
                    .title("制定实现计划")
                    .description("制定详细的实现计划")
                    .type(TeamTask.TaskType.DEVELOPMENT)
                    .priority(9)
                    .build();

            TeamTask implTask = TeamTask.builder()
                    .id(implId)
                    .title("实现功能")
                    .description("根据计划实现功能")
                    .type(TeamTask.TaskType.DEVELOPMENT)
                    .priority(8)
                    .dependencies(Collections.singletonList(planId))
                    .build();

            TeamTask testTask = TeamTask.builder()
                    .id(testId)
                    .title("编写测试")
                    .description("为实现的代码编写测试")
                    .type(TeamTask.TaskType.TESTING)
                    .priority(6)
                    .dependencies(Collections.singletonList(implId))
                    .build();

            tasks.add(planTask);
            tasks.add(implTask);
            tasks.add(testTask);
        }

        if (userPrompt.contains("文档") || userPrompt.contains("document")) {
            // 创建文档任务
            tasks.add(TeamTask.builder()
                    .id("task-doc-" + System.currentTimeMillis())
                    .title("生成文档")
                    .description("生成项目文档")
                    .type(TeamTask.TaskType.DOCUMENTATION)
                    .priority(5)
                    .build());
        }

        // 默认任务（如果没有匹配到特定类型）
        if (tasks.isEmpty()) {
            tasks.add(TeamTask.builder()
                    .id("task-default-" + System.currentTimeMillis())
                    .title("处理用户请求")
                    .description(prompt.getUserContent())
                    .type(TeamTask.TaskType.DEVELOPMENT)
                    .priority(7)
                    .build());
        }

        // 验证任务依赖关系
        logTaskDependencies(tasks);

        return tasks;
    }

    /**
     * 记录任务依赖关系（用于调试）
     */
    private void logTaskDependencies(List<TeamTask> tasks) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("创建任务依赖关系:");
            for (TeamTask task : tasks) {
                if (task.getDependencies() != null && !task.getDependencies().isEmpty()) {
                    LOG.debug("  {} 依赖: {}", task.getTitle(), task.getDependencies());
                }
            }
        }
    }

    /**
     * 广播任务可用通知
     */
    private void broadcastTaskNotification(List<TeamTask> tasks) {
        if (messageChannel != null) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("action", "tasks_available");
            payload.put("count", tasks.size());
            payload.put("tasks", tasks);

            AgentMessage<Map<String, Object>> message = AgentMessage.<Map<String, Object>>of(payload)
                    .from(config.getCode())
                    .to("*")
                    .type("task_notification")
                    .build();

            try {
                List<MessageAck> acks = messageChannel.broadcast(message).join();
                LOG.info("任务通知已广播，收到 {} 个确认", acks.size());
            } catch (Exception e) {
                LOG.warn("广播任务通知失败", e);
            }
        }
    }

    /**
     * 等待所有任务完成（使用事件驱动机制）
     */
    private void waitForAllTasksCompleted() {
        int maxWaitSeconds = 300; // 最多等待5分钟
        int waitedSeconds = 0;
        int checkInterval = 2; // 每2秒检查一次（降低轮询频率）

        while (waitedSeconds < maxWaitSeconds) {
            SharedTaskList.TaskStatistics stats = taskList.getStatistics();

            // 检查是否所有任务都已完成或失败
            if (stats.inProgressTasks == 0 && stats.pendingTasks == 0) {
                LOG.info("所有子任务已完成（总计: {}, 完成: {}, 失败: {}）",
                        stats.totalTasks, stats.completedTasks, stats.failedTasks);
                break;
            }

            LOG.debug("等待任务完成... (进行中: {}, 待认领: {})",
                    stats.inProgressTasks, stats.pendingTasks);

            try {
                Thread.sleep(checkInterval * 1000L);
                waitedSeconds += checkInterval;

                // 每30秒打印一次统计信息
                if (waitedSeconds % 30 == 0) {
                    LOG.info("任务进度: {}", stats);
                    // 打印阻塞信息
                    if (stats.pendingTasks > 0) {
                        String blockingInfo = taskList.getBlockingInfo();
                        if (blockingInfo.length() > 50) {
                            LOG.debug("阻塞任务:\n{}", blockingInfo);
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warn("等待任务完成被中断");
                break;
            }
        }

        if (waitedSeconds >= maxWaitSeconds) {
            LOG.warn("等待任务完成超时（{}秒），当前状态: {}", maxWaitSeconds, taskList.getStatistics());
        }
    }

    /**
     * 汇总结果
     */
    private String summarizeResults() {
        SharedTaskList.TaskStatistics stats = taskList.getStatistics();
        List<TeamTask> allTasks = taskList.getAllTasks();

        StringBuilder summary = new StringBuilder();
        summary.append("## 任务执行汇总\n\n");
        summary.append(stats.toString()).append("\n\n");

        // 列出完成的任务
        summary.append("### 已完成任务\n\n");
        for (TeamTask task : allTasks) {
            if (task.isCompleted()) {
                summary.append(String.format("- **%s**: %s\n",
                        task.getTitle(),
                        task.getResult() != null ? task.getResult().toString() : "完成"));
            }
        }

        // 列出失败的任务
        summary.append("\n### 失败任务\n\n");
        for (TeamTask task : allTasks) {
            if (task.isFailed()) {
                summary.append(String.format("- **%s**: %s\n",
                        task.getTitle(),
                        task.getErrorMessage()));
            }
        }

        return summary.toString();
    }

    /**
     * 注册任务事件监听器
     */
    private void registerTaskEventListeners() {
        if (eventBus != null) {
            // 监听子任务完成事件
            taskEventSubscriptionId = eventBus.subscribe(AgentEventType.TASK_COMPLETED, event -> {
                String taskId = event.getMetadata().getTaskId();
                Map<String, Object> payload = (Map<String, Object>) event.getPayload();

                String taskTitle = payload != null ? (String) payload.get("taskTitle") : "Unknown";
                String agentId = payload != null ? (String) payload.get("agentId") : "Unknown";

                LOG.info("主代理收到子任务完成事件: {} by {}", taskTitle, agentId);

                // 将任务结果存储到共享内存（供后续任务使用）
                if (sharedMemoryManager != null && payload != null) {
                    try {
                        Object result = payload.get("result");
                        if (result != null) {
                            // 创建短期记忆存储任务结果
                            ShortTermMemory memory = new ShortTermMemory(
                                    agentId,
                                    result.toString(),
                                    taskId
                            );
                            memory.setId("task-result-" + taskId);

                            // 添加元数据
                            memory.putMetadata("taskTitle", taskTitle);
                            memory.putMetadata("taskType", "task-result");
                            memory.putMetadata("source", agentId);

                            sharedMemoryManager.store(memory);
                            LOG.debug("任务结果已存储到共享内存: taskId={}, source={}", taskId, agentId);
                        }
                    } catch (Exception e) {
                        LOG.warn("存储任务结果到共享内存失败", e);
                    }
                }

                // 检查是否有依赖此任务的其他任务可以开始执行
                checkAndNotifyDependentTasks(taskId);

                return CompletableFuture.completedFuture(EventHandler.Result.success());
            });

            // 监听子任务失败事件
            taskFailedSubscriptionId = eventBus.subscribe(AgentEventType.TASK_FAILED, event -> {
                String taskId = event.getMetadata().getTaskId();
                Map<String, Object> payload = (Map<String, Object>) event.getPayload();

                String taskTitle = payload != null ? (String) payload.get("taskTitle") : "Unknown";
                String agentId = payload != null ? (String) payload.get("agentId") : "Unknown";

                // 获取错误信息（从任务对象或payload）
                String errorMessage = null;
                if (payload != null && payload.containsKey("errorMessage")) {
                    errorMessage = (String) payload.get("errorMessage");
                } else {
                    // 从任务列表中获取错误信息
                    TeamTask failedTask = taskList.getTask(taskId);
                    if (failedTask != null) {
                        errorMessage = failedTask.getErrorMessage();
                    }
                }

                if (errorMessage == null) {
                    errorMessage = "Unknown error";
                }

                LOG.warn("主代理收到子任务失败事件: {} by {} - {}", taskTitle, agentId, errorMessage);

                // 处理任务失败：决定是否需要重试或标记依赖任务失败
                handleTaskFailure(taskId, errorMessage);

                return CompletableFuture.completedFuture(EventHandler.Result.success());
            });

            LOG.info("MainAgent 任务事件监听器已注册（TASK_COMPLETED, TASK_FAILED）");
        }
    }

    /**
     * 检查并通知依赖此任务的其他任务
     */
    private void checkAndNotifyDependentTasks(String completedTaskId) {
        try {
            // 查找依赖此任务的所有待认领任务
            List<TeamTask> claimableTasks = taskList.getClaimableTasks();

            if (!claimableTasks.isEmpty()) {
                LOG.info("发现 {} 个可认领的任务（由于任务 {} 完成）", claimableTasks.size(), completedTaskId);

                // 可以在这里触发通知，让子代理认领这些任务
                // 例如：通过 MessageChannel 广播
            }
        } catch (Exception e) {
            LOG.error("检查依赖任务失败", e);
        }
    }

    /**
     * 处理任务失败
     */
    private void handleTaskFailure(String taskId, String errorMessage) {
        try {
            // 查找依赖此失败任务的其他任务
            List<TeamTask> allTasks = taskList.getAllTasks();
            List<TeamTask> dependentTasks = new ArrayList<>();

            for (TeamTask task : allTasks) {
                if (task.getStatus() == TeamTask.Status.PENDING &&
                    task.getDependencies().contains(taskId)) {
                    dependentTasks.add(task);
                }
            }

            if (!dependentTasks.isEmpty()) {
                LOG.warn("发现 {} 个任务受影响（依赖失败任务 {}）", dependentTasks.size(), taskId);

                // 策略1：标记依赖任务为失败
                // 策略2：等待人工干预
                // 当前：记录日志，保持待认领状态（由人工决策）
            }
        } catch (Exception e) {
            LOG.error("处理任务失败时发生异常", e);
        }
    }

    /**
     * 注册消息处理器
     */
    private void registerMessageHandler() {
        if (messageChannel != null) {
            messageHandlerId = messageChannel.registerHandler(
                    config.getCode(),
                    this::handleMessage
            );
            LOG.info("MainAgent 消息处理器已注册");
        }
    }

    /**
     * 处理子代理发送的消息
     */
    private <T> CompletableFuture<Object> handleMessage(AgentMessage<T> message) {
        LOG.debug("MainAgent 收到消息: from={}, type={}",
                message.getFrom(), message.getType());

        // 处理任务相关消息
        if ("task_query".equals(message.getType())) {
            // 子代理查询任务列表
            return CompletableFuture.completedFuture(getTaskListInfo());
        } else if ("task_help".equals(message.getType())) {
            // 子代理请求帮助
            return CompletableFuture.completedFuture(handleHelpRequest(message));
        }

        return CompletableFuture.completedFuture("ACK");
    }

    /**
     * 获取任务列表信息
     */
    private Map<String, Object> getTaskListInfo() {
        Map<String, Object> info = new HashMap<>();
        SharedTaskList.TaskStatistics stats = taskList.getStatistics();

        info.put("statistics", stats);
        info.put("pendingTasks", taskList.getPendingTasks().size());
        info.put("claimableTasks", taskList.getClaimableTasks().size());

        return info;
    }

    /**
     * 处理帮助请求
     */
    private <T> Object handleHelpRequest(AgentMessage<T> message) {
        LOG.info("收到来自 {} 的帮助请求", message.getFrom());

        // 这里可以实现协调逻辑，例如：
        // 1. 重新分配任务
        // 2. 提供额外的资源
        // 3. 协调多个代理协作

        return "Help request received";
    }

    /**
     * 发布事件
     */
    private void publishEvent(AgentEventType eventType, Object payload, String taskId) {
        if (eventBus != null) {
            EventMetadata metadata = EventMetadata.builder()
                    .sourceAgent(config.getCode())
                    .taskId(taskId)
                    .priority(5)
                    .build();

            AgentEvent event = new AgentEvent(eventType, payload, metadata);
            eventBus.publishAsync(event);
        }
    }

    /**
     * 获取系统提示词
     */
    private String getSystemPrompt() {
        return "## 主代理（Team Lead）\n\n" +
                "你是 Agent Teams 的团队领导，负责协调多个子代理协作完成任务。\n" +
                "\n" +
                "### 核心职责\n" +
                "- 分析用户请求，创建合理的子任务\n" +
                "- 将任务分配到共享任务列表\n" +
                "- 协调多个子代理协作\n" +
                "- 监控任务执行状态\n" +
                "- 汇总并呈现最终结果\n" +
                "\n" +
                "### 工作流程\n" +
                "1. **任务分析**：理解用户请求，识别需要哪些类型的任务\n" +
                "2. **任务创建**：创建合适的子任务，设置优先级和依赖关系\n" +
                "3. **任务分配**：将任务添加到共享任务列表\n" +
                "4. **协调执行**：监控任务进度，处理异常情况\n" +
                "5. **结果汇总**：收集所有子任务的结果，生成最终报告\n" +
                "\n" +
                "### 协作策略\n" +
                "- 鼓励子代理主动认领任务\n" +
                "- 当子代理遇到困难时，提供支持和指导\n" +
                "- 平衡各代理的工作负载\n" +
                "- 确保任务按正确顺序执行（依赖关系）\n" +
                "\n" +
                "### 输出要求\n" +
                "- 提供清晰的任务分解\n" +
                "- 说明每个子任务的目标\n" +
                "- 汇总所有子任务的结果\n" +
                "- 标注重要的发现和决策\n";
    }

    /**
     * 检查是否正在运行
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 获取配置
     */
    public SubAgentMetadata getConfig() {
        return config;
    }

    /**
     * 获取共享任务列表
     */
    public SharedTaskList getTaskList() {
        return taskList;
    }

    /**
     * 清理资源
     */
    public void destroy() {
        // 注销事件监听器
        if (eventBus != null) {
            if (taskEventSubscriptionId != null) {
                eventBus.unsubscribe(taskEventSubscriptionId);
                LOG.info("MainAgent TASK_COMPLETED 事件监听器已注销");
            }
            if (taskFailedSubscriptionId != null) {
                eventBus.unsubscribe(taskFailedSubscriptionId);
                LOG.info("MainAgent TASK_FAILED 事件监听器已注销");
            }
        }

        // 注销消息处理器
        if (messageChannel != null && messageHandlerId != null) {
            messageChannel.unregisterHandler(config.getCode(), messageHandlerId);
            LOG.info("MainAgent 消息处理器已注销");
        }
    }
}
