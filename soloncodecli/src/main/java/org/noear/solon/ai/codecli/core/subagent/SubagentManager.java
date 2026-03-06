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

import org.noear.solon.ai.codecli.core.AgentKernel;
import org.noear.solon.core.util.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 子代理管理器
 *
 * @author bai
 * @since 3.9.5
 */
public class SubagentManager {
    private static final Logger LOG = LoggerFactory.getLogger(SubagentManager.class);

    private final Map<String, Subagent> subagentMap = new ConcurrentHashMap<>();
    private final AgentKernel mainAgent;

    public SubagentManager(AgentKernel mainAgent) {
        this.mainAgent = mainAgent;

        // 添加预置的智能体类型
        addSubagent(new ExploreSubagent(mainAgent));
        addSubagent(new DevPlanSubagent(mainAgent));
        addSubagent(new BashSubagent(mainAgent));
        addSubagent(new SolonGuideSubagent(mainAgent));
        addSubagent(new GeneralPurposeSubagent(mainAgent));
    }


    public void addSubagent(Subagent subagent) {
        subagentMap.putIfAbsent(subagent.getType(), subagent);
    }

    /**
     * 获取指定名称的子代理（支持自定义代理）
     */
    public Subagent getAgent(String agentName) {
        // 1. 首先尝试作为预定义类型
        Subagent subagent = subagentMap.get(agentName);
        if (subagent == null) {
            throw new IllegalArgumentException("未找到代理: " + agentName);
        } else {
            return subagent;
        }
    }


    /**
     * 检查子代理是否已注册
     */
    public boolean hasAgent(String agentName) {
        return subagentMap.containsKey(agentName);
    }

    /**
     * 获取所有已注册的子代理
     */
    public Collection<Subagent> getAgents() {
        return subagentMap.values();
    }

    /**
     * 清除所有子代理
     */
    public void clear() {
        subagentMap.clear();
    }

    /**
     * 注册自定义 agents 池
     *
     * @param dir agents 目录路径，可以是绝对路径或相对路径
     */
    public void agentPool(String dir) {
        if (Assert.isEmpty(dir)) {
            return;
        }

        Path path = Paths.get(dir).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            LOG.warn("代理池目录不存在: {}", dir);
            return;
        }

        try (Stream<Path> stream = Files.list(path)) {
            List<Path> files = stream.filter(p -> p.toString().endsWith(".md"))
                    .collect(Collectors.toList());

            for (Path file : files) {
                try {
                    String fileName = file.getFileName().toString();
                    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);

                    String description = extractDescription(lines);
                    String systemPrompt = String.join("\n", lines);
                    String subagentType = fileName.substring(0, fileName.length() - 3); // .md 是3位

                    AbsSubagent subagent = (AbsSubagent) subagentMap.computeIfAbsent(subagentType,
                            k -> new DynamicSubagent(mainAgent, k));

                    subagent.setDescription(description);
                    subagent.setSystemPrompt(systemPrompt);
                    subagent.refresh();
                } catch (IOException e) {
                    LOG.error("读取代理文件失败: {}", file, e);
                }
            }
        } catch (IOException e) {
            LOG.error("扫描代理池目录失败: {}", dir, e);
        }
    }

    /**
     * 从 MD 文件提取描述
     * 优先读取第一行作为描述，否则使用默认描述
     */
    private String extractDescription(List<String> lines) {
        if (!lines.isEmpty()) {
            String firstLine = lines.get(0).trim();
            // 跳过 Markdown 标题标记 (# )
            if (firstLine.startsWith("#")) {
                firstLine = firstLine.substring(1).trim();
            }
            // 限制描述长度
            if (firstLine.length() > 50) {
                firstLine = firstLine.substring(0, 47) + "...";
            }

            return firstLine;
        }

        return "自定义代理";
    }
}