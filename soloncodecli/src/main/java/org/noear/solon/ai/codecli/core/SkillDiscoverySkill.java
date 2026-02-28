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
 * 技能自治网关
 *
 * 支持三阶段模式自动切换：
 * 1. FULL: 数量 <= dynamicThreshold，全量平铺。
 * 2. DYNAMIC: 数量 <= searchThreshold，指令内展示清单。
 * 3. SEARCH: 数量 > searchThreshold，强制搜索。
 */
public class SkillDiscoverySkill extends AbsSkill {
    private static final Logger LOG = LoggerFactory.getLogger(SkillDiscoverySkill.class);

    private final Map<String, Path> skillPools = new ConcurrentHashMap<>();
    private final Map<String, SkillDir> allSkillMap = new ConcurrentHashMap<>();

    private int dynamicThreshold = 8; // 超过此值，不再平铺注入所有 SKILL.md
    private int searchThreshold = 80;  // 超过此值，不再展示摘要清单，进入强制搜索

    @Override
    public String description() {
        return "工程技能管理器。支持从本地或资源池发现并加载专业执行规约 (SKILL.md)。";
    }

    @Override
    public String getInstruction(Prompt prompt) {
        if (skillPools.isEmpty()) return null;

        int total = allSkillMap.size();
        StringBuilder sb = new StringBuilder("\n#### 技能规约发现规范 (当前可用技能: " + total + ")\n");

        if (total <= dynamicThreshold && total > 0) {
            sb.append("当前已为你自动加载以下专业规约，请直接参考执行：\n");
            for (SkillDir skill : allSkillMap.values()) {
                sb.append(renderSkillXml(skill, false));
            }
        } else if (total <= searchThreshold) {
            sb.append("检测到多个专业技能。请根据以下清单，使用 `explain_skill` 加载具体规约：\n");
            sb.append("<available_skills>\n");
            for (SkillDir skill : allSkillMap.values()) {
                sb.append("  <skill name=\"").append(skill.aliasPath).append("\">")
                        .append(skill.description).append("</skill>\n");
            }
            sb.append("</available_skills>");
        } else {
            sb.append("由于技能库庞大，请严格遵循搜索流程：\n");
            sb.append("1. 使用 `search_skills` 关键词搜索匹配的技能路径。\n");
            sb.append("2. 使用 `explain_skill` 获取该技能的详细 SKILL.md 规约与文件参考。");
        }

        return sb.toString();
    }

    @Override
    public Collection<FunctionTool> getTools(Prompt prompt) {
        if (skillPools.isEmpty()) return null;

        int total = allSkillMap.size();
        if (total <= dynamicThreshold) {
            return tools.stream().filter(t -> t.name().equals("explain_skill")).collect(Collectors.toList());
        } else if (total <= searchThreshold) {
            return tools.stream().filter(t -> t.name().equals("explain_skill") || t.name().equals("list_skills")).collect(Collectors.toList());
        } else {
            return this.tools;
        }
    }

    @ToolMapping(name = "list_skills", description = "列出所有已挂载技能池中的可用技能清单。")
    public String listSkills() {
        if (allSkillMap.isEmpty()) return "当前没有可用的技能。";

        StringBuilder sb = new StringBuilder("可用技能列表：\n");
        for (SkillDir s : allSkillMap.values()) {
            sb.append("- ").append(s.aliasPath).append(": ").append(s.description).append("\n");
        }
        return sb.toString();
    }

    @ToolMapping(name = "search_skills", description = "在所有技能池中搜索关键字。支持空格分隔多个词。")
    public String searchSkills(@Param("query") String query) {
        String[] keys = query.toLowerCase().split("\\s+");

        List<SkillDir> matches = allSkillMap.values().stream()
                .filter(s -> Arrays.stream(keys).allMatch(k ->
                        s.aliasPath.toLowerCase().contains(k) ||
                                s.description.toLowerCase().contains(k)))
                .limit(15)
                .collect(Collectors.toList());

        if (matches.isEmpty()) return "未找到匹配技能。";

        StringBuilder sb = new StringBuilder("<search_results>\n");
        for (SkillDir s : matches) {
            sb.append("  <skill path=\"").append(s.aliasPath).append("\">")
                    .append(s.description).append("</skill>\n");
        }
        sb.append("</search_results>");
        return sb.toString();
    }

    @ToolMapping(name = "explain_skill", description = "加载特定技能的完整 SKILL.md 规约及其文件参考。")
    public String explainSkill(@Param("path") String path, String __workDir) throws IOException {
        // 1. 优先从内存 Map 查找逻辑路径
        SkillDir cachedSkill = allSkillMap.get(path);
        if (cachedSkill != null) {
            return renderSkillXml(cachedSkill, true);
        }

        // 2. 回退到物理路径解析
        Path target = resolvePathExtended(__workDir, path);
        if (!isSkillDir(target)) return "Error: 路径 " + path + " 不是有效的技能目录 (缺少 SKILL.md)";

        return renderSkillXml(new SkillDir(path, target, null), true);
    }

    @ToolMapping(name = "refresh_skills", description = "重新扫描所有技能池，更新技能列表。")
    public String refreshSkills() {
        allSkillMap.clear();
        skillPools.forEach(this::scanAndCache);
        return "技能库已刷新，当前可用技能数：" + allSkillMap.size();
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
            for (Map.Entry<String, Path> e : skillPools.entrySet()) {
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
            skillPools.put(key, root);
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
                        allSkillMap.put(aliasPath, new SkillDir(aliasPath, dir, parseDescription(dir)));
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
            return lines.filter(l -> !l.trim().isEmpty() && !l.startsWith("#"))
                    .findFirst()
                    .map(l -> l.length() > 80 ? l.substring(0, 77) + "..." : l.trim())
                    .orElse("Professional skill guidelines.");
        } catch (Exception e) {
            return "Skill guidelines.";
        }
    }

    private static class SkillDir {
        final String aliasPath;
        final Path realPath;
        final String description;

        SkillDir(String aliasPath, Path realPath, String description) {
            this.aliasPath = aliasPath;
            this.realPath = realPath;
            this.description = description;
        }
    }
}