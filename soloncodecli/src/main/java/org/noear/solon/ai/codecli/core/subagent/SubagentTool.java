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
package org.noear.solon.ai.codecli.core.subagent;

import org.noear.solon.ai.agent.AgentResponse;
import org.noear.solon.ai.chat.prompt.Prompt;
import org.noear.solon.ai.chat.tool.AbsTool;
import org.noear.solon.core.util.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 子代理技能
 *
 * 将子代理能力暴露为可调用的工具（Claude Code Subagent 类似实现）
 *
 * @author bai
 * @since 3.9.5
 */
public class SubagentTool extends AbsTool {
    private static final Logger LOG = LoggerFactory.getLogger(SubagentTool.class);

    private final SubagentManager manager;

    public SubagentTool(SubagentManager manager) {
        this.manager = manager;

        addParam("subagent_type", String.class, "用于此任务的子代理类型");
        addParam("prompt", String.class, "分派给子代理的具体任务要求（应包含详细背景）");
        addParam("description", String.class, false, "简短的任务描述（3-5个词）");
        addParam("task_id", String.class, false, "可选。若要继续之前的任务会话，请传入对应的 task_id");
    }

    @Override
    public String name() {
        return "subagent";
    }

    @Override
    public String description() {
        StringBuilder sb = new StringBuilder();
        sb.append("启动专门的子代理来处理复杂任务。子代理会返回任务结论及一个 task_id。\n\n");

        sb.append("### 可用的 subagent_type 列表:\n");
        sb.append("<available_agents>\n");
        for (Subagent agent : manager.getAgents()) {
            sb.append(String.format("  <agent subagent_type=\"%s\">%s</agent>\n",
                    agent.getType(), agent.getDescription()));
        }
        sb.append("</available_agents>\n");

        sb.append("\n### 使用准则:\n");
        sb.append("1. **何时使用**: 需要执行复杂分析、编写大量代码、调用 Bash 或进行多阶段规划时。\n");
        sb.append("2. **何时不使用**: 简单的文件读取（使用 read 工具）、搜索单个定义（使用 glob 工具）。\n");
        sb.append("3. **并发建议**: 可以在单次回复中调用多个子代理以提高效率。\n");
        sb.append("4. **会话持续性**: 返回结果中包含 task_id。若需要子代理根据之前的进度继续，请在下次调用时传入 task_id。\n\n");

        sb.append("\n### 使用说明:\n");
        sb.append("- 如果要继续之前的任务，请务必传入对应的 task_id。\n");
        sb.append("- 子代理无法感知主会话的所有上下文，请在 prompt 中提供充足的细节。\n");

        sb.append("### 示例:\n");
        sb.append("- Task(subagent_type='plan', description='设计重构方案', prompt='分析现有的 Auth 逻辑并提出微服务化方案')\n");
        sb.append("- Task(subagent_type='bash', description='检查日志错误', prompt='/bin/grep -r \"Error\" ./logs')\n");

        return sb.toString();
    }

    @Override
    public Object handle(Map<String, Object> args) throws Throwable {
        String subagentType = (String) args.get("subagent_type");
        String prompt = (String) args.get("prompt");
        String description = (String) args.get("description");
        String taskId = (String) args.get("task_id");

        try {
            Subagent agent = manager.getAgent(subagentType);

            if (agent == null) {
                return "ERROR: 未知的子代理类型 '" + subagentType + "'。请从 <available_agents> 列表中选择。";
            }

            // 1. 会话标识处理：如果有 task_id 则沿用，否则生成基于类型的标识
            String finalSessionId = Assert.isEmpty(taskId)
                    ? "subagent_" + subagentType + "_" + System.currentTimeMillis()
                    : taskId;

            LOG.info("分派任务 -> 类型: {}, 会话: {}, 描述: {}", subagentType, finalSessionId, description);

            // 2. 这里的 AgentSession 逻辑应与 AgentKernel 深度绑定
            // 注意：execute 内部应能识别 sessionId 并从 sessionManager 恢复上下文
            AgentResponse response = agent.execute(finalSessionId, Prompt.of(prompt));

            String result = response.getContent();
            LOG.info("子代理任务完成: {}", finalSessionId);

            // 3. 结构化输出，方便主代理识别结果并知道如何继续
            return String.format(
                    "task_id: %s\n" +
                            "subagent_type: %s\n" +
                            "\n" +
                            "<task_result>\n" +
                            "%s\n" +
                            "</task_result>",
                    finalSessionId, subagentType, result
            );

        } catch (Throwable e) {
            LOG.error("子代理执行崩溃: type={}, error={}", subagentType, e.getMessage(), e);
            return "ERROR: 子代理执行失败: " + e.getMessage();
        }
    }
}