package org.noear.solon.ai.codecli.core;

import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.ai.chat.prompt.Prompt;
import org.noear.solon.ai.chat.skill.AbsSkill;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.annotation.Param;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 规约自治网关
 *
 * 支持三阶段模式自动切换：
 * 1. FULL: 数量 <= dynamicThreshold，全量平铺。
 * 2. DYNAMIC: 数量 <= searchThreshold，指令内展示清单。
 * 3. SEARCH: 数量 > searchThreshold，强制搜索。
 */
public class SkillDiscoverySkill extends AbsSkill {
    private static final Logger LOG = LoggerFactory.getLogger(SkillDiscoverySkill.class);

    private final Map<String, Path> skillPoolMap = new ConcurrentHashMap<>();
    private final Map<String, SkillDir> skillMap = new ConcurrentHashMap<>();

    private int dynamicThreshold = 8; // 超过此值，不再平铺注入所有 SKILL.md
    private int searchThreshold = 80;  // 超过此值，不再展示摘要清单，进入强制搜索

    public Map<String, SkillDir> getSkillMap() {
        return skillMap;
    }

    @Override
    public String description() {
        return "专家技能管理器。支持从本地或资源池发现并加载专家技能 (SKILL.md)。";
    }

    @Override
    public String getInstruction(Prompt prompt) {
        if (skillPoolMap.isEmpty()) return null;

        int total = skillMap.size();
        StringBuilder sb = new StringBuilder("\n#### 专家技能库执行规约 (当前可用技能: " + total + ")\n");

        if (total <= dynamicThreshold && total > 0) {
            sb.append("已加载以下专家技能，其内容即为执行标准，请严格遵循：\n");
            for (SkillDir skill : skillMap.values()) {
                sb.append(renderSkillXml(skill, false));
            }
        } else if (total <= searchThreshold) {
            sb.append("检测到多个专家技能。在执行相关领域动作前，必须先调用 `explain_skill` 加载具体技能规约：\n");
            sb.append("<available_skills>\n");
            for (SkillDir skill : skillMap.values()) {
                sb.append("  <skill name=\"").append(skill.aliasPath).append("\">")
                        .append(skill.description).append("</skill>\n");
            }
            sb.append("</available_skills>");
        } else {
            sb.append("专家技能库规模较大。为了确保工程质量，请执行以下检索流程：\n");
            sb.append("1. **技能检索**：处理特定技术栈前，必须通过 `search_skills` 检索对应的专家技能。\n");
            sb.append("2. **规约读取**：通过 `explain_skill` 获取技能对应的 SKILL.md 完整规约。\n");
            sb.append("3. **禁止盲目操作**：严禁在未确认是否存在专家技能指引的情况下，直接使用通用命令。");
        }

        return sb.toString();
    }

    @Override
    public Collection<FunctionTool> getTools(Prompt prompt) {
        if (skillPoolMap.isEmpty()) return null;

        int total = skillMap.size();
        if (total <= dynamicThreshold) {
            return tools.stream().filter(t -> t.name().equals("explain_skill")).collect(Collectors.toList());
        } else if (total <= searchThreshold) {
            return tools.stream().filter(t -> t.name().equals("explain_skill") || t.name().equals("list_skills")).collect(Collectors.toList());
        } else {
            return this.tools;
        }
    }

    @ToolMapping(name = "list_skills", description = "列出所有已挂载专家技能池中的可用清单。")
    public String listSkills() {
        if (skillMap.isEmpty()) return "当前没有可用的专家技能。";

        StringBuilder sb = new StringBuilder("可用专家技能列表：\n");
        for (SkillDir s : skillMap.values()) {
            sb.append("- ").append(s.aliasPath).append(": ").append(s.description).append("\n");
        }
        return sb.toString();
    }

    @ToolMapping(name = "search_skills", description = "在所有专家技能池中搜索关键字。支持空格分隔多个词。")
    public String searchSkills(@Param("query") String query) {
        String[] keys = query.toLowerCase().split("\\s+");

        List<SkillDir> matches = skillMap.values().stream()
                .filter(s -> Arrays.stream(keys).anyMatch(k ->
                        s.aliasPath.toLowerCase().contains(k) ||
                                s.description.toLowerCase().contains(k)))
                .limit(15)
                .collect(Collectors.toList());

        if (matches.isEmpty()) return "未找到匹配专家技能。";

        StringBuilder sb = new StringBuilder("<search_results>\n");
        for (SkillDir s : matches) {
            sb.append("  <skill path=\"").append(s.aliasPath).append("\">")
                    .append(s.description).append("</skill>\n");
        }
        sb.append("</search_results>");
        return sb.toString();
    }

    @ToolMapping(name = "explain_skill", description = "加载特定专家技能的完整 SKILL.md 规约及其文件参考。")
    public String explainSkill(@Param("path") String path, String __workDir) throws IOException {
        // 1. 优先从内存 Map 查找逻辑路径
        SkillDir cachedSkill = skillMap.get(path);
        if (cachedSkill != null) {
            return renderSkillXml(cachedSkill, true);
        }

        // 2. 回退到物理路径解析
        Path target = resolvePathExtended(__workDir, path);
        if (!isSkillDir(target)) return "Error: 路径 " + path + " 不是有效的专家技能目录 (缺少 SKILL.md)";

        return renderSkillXml(new SkillDir(path, target, null), true);
    }

    @ToolMapping(name = "refresh_skills", description = "重新扫描所有专家技能池，更新专家技能列表。")
    public String refreshSkills() {
        skillMap.clear();
        skillPoolMap.forEach(this::scanAndCache);
        return "专家技能库已刷新，当前可用专家技能数：" + skillMap.size();
    }

    // --- 核心渲染与辅助逻辑 ---

    private String renderSkillXml(SkillDir skill, boolean includeFiles) {
        Path md = skill.realPath.resolve("SKILL.md");
        if (!Files.exists(md)) md = skill.realPath.resolve("skill.md");

        try {
            String content = Files.exists(md) ? new String(Files.readAllBytes(md), StandardCharsets.UTF_8) : "";
            StringBuilder sb = new StringBuilder("\n<skill_content name=\"" + skill.aliasPath + "\">\n");
            sb.append(content.trim()).append("\n\n");
            sb.append("Base Directory: ").append(skill.aliasPath).append("\n");

            if (includeFiles) {
                sb.append("<skill_files>\n").append(sampleFiles(skill.realPath)).append("</skill_files>\n");
            }
            sb.append("</skill_content>\n");
            return sb.toString();
        } catch (IOException e) {
            return "Load skill " + skill.aliasPath + " failed.";
        }
    }

    private String sampleFiles(Path dir) throws IOException {
        try (Stream<Path> stream = Files.walk(dir, 2)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().equalsIgnoreCase("SKILL.md"))
                    .limit(10) // 增加到 10 个展示
                    .map(p -> "  <file>" + dir.relativize(p).toString().replace("\\", "/") + "</file>")
                    .collect(Collectors.joining("\n"));
        }
    }

    private boolean isSkillDir(Path p) {
        return Files.exists(p.resolve("SKILL.md")) || Files.exists(p.resolve("skill.md"));
    }

    private Path resolvePathExtended(String workDir, String pStr) {
        if (pStr.startsWith("@")) {
            for (Map.Entry<String, Path> e : skillPoolMap.entrySet()) {
                if (pStr.startsWith(e.getKey())) {
                    String sub = pStr.substring(e.getKey().length()).replaceFirst("^[/\\\\]", "");
                    return e.getValue().resolve(sub).normalize();
                }
            }
        }
        return Paths.get(workDir).resolve(pStr).toAbsolutePath().normalize();
    }

    public SkillDiscoverySkill skillPool(String alias, String dir) {
        if (dir != null) {
            String key = alias.startsWith("@") ? alias : "@" + alias;
            Path root = Paths.get(dir).toAbsolutePath().normalize();
            skillPoolMap.put(key, root);
            scanAndCache(key, root);
        }
        return this;
    }

    private void scanAndCache(String alias, Path root) {
        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), 3, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (isSkillDir(dir)) {
                        String rel = root.relativize(dir).toString().replace("\\", "/");
                        String aliasPath = alias + (rel.isEmpty() ? "" : "/" + rel);
                        skillMap.put(aliasPath, new SkillDir(aliasPath, dir, parseDescription(dir)));
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (dir.getFileName().toString().startsWith(".")) return FileVisitResult.SKIP_SUBTREE;
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            LOG.error("Scan skill pool failed: {}", root, e);
        }
    }

    private String parseDescription(Path dir) {
        Path md = dir.resolve("SKILL.md");
        if (!Files.exists(md)) md = dir.resolve("skill.md");

        try (Stream<String> lines = Files.lines(md, StandardCharsets.UTF_8)) {
            List<String> allLines = lines.map(String::trim).collect(Collectors.toList());

            if (allLines.isEmpty()) return "专家技能规约。";

            // 检查是否包含 YAML Front Matter (以 --- 开头)
            if (allLines.get(0).equals("---")) {
                for (int i = 1; i < allLines.size(); i++) {
                    String line = allLines.get(i);
                    if (line.equals("---")) break; // YAML 结束

                    // 寻找 description 字段
                    if (line.toLowerCase().startsWith("description:")) {
                        String desc = line.substring(line.indexOf(":") + 1).trim();
                        // 去掉两端的引号
                        if (desc.startsWith("\"") && desc.endsWith("\"")) {
                            desc = desc.substring(1, desc.length() - 1);
                        }
                        return formatDesc(desc);
                    }
                }
            }

            // 策略 2：如果没有 YAML 或没找到 description，寻找第一个一级标题 (#)
            // 然后取其后的第一个非空、非引用、非代码块的段落
            boolean foundMainTitle = false;
            for (String line : allLines) {
                if (line.startsWith("# ")) {
                    foundMainTitle = true;
                    continue;
                }
                if (foundMainTitle && !line.isEmpty() && !line.startsWith("#")
                        && !line.startsWith("`") && !line.startsWith(">")) {
                    return formatDesc(line);
                }
            }

            // 策略 3：兜底逻辑
            return allLines.stream()
                    .filter(l -> !l.isEmpty() && !l.startsWith("-") && !l.startsWith("#"))
                    .findFirst()
                    .map(this::formatDesc)
                    .orElse("专业领域专家规约。");

        } catch (Exception e) {
            return "专家技能规约。";
        }
    }

    private String formatDesc(String desc) {
        // 增加长度到 150，确保 LLM 能看到足够的语义信息
        return desc.length() > 150 ? desc.substring(0, 147) + "..." : desc;
    }

    public static class SkillDir {
        final String aliasPath;
        final Path realPath;
        final String description;

        SkillDir(String aliasPath, Path realPath, String description) {
            this.aliasPath = aliasPath;
            this.realPath = realPath;
            this.description = description;
        }

        public String getAliasPath() {
            return aliasPath;
        }

        public Path getRealPath() {
            return realPath;
        }

        public String getDescription() {
            return description;
        }
    }
}