package org.noear.solon.ai.codecli.core.skills;

import org.noear.solon.ai.chat.prompt.Prompt;
import org.noear.solon.ai.skills.lucene.LuceneSkill;

import java.nio.file.Files;

/**
 *
 * @author noear 2026/2/19 created
 *
 */
public class CodeLuceneSkill extends LuceneSkill {
    public CodeLuceneSkill(String workDir) {
        super(workDir);
    }

    @Override
    public boolean isSupported(Prompt prompt) {
        if (exists("CLAUDE.md") || exists("pom.xml") || exists("package.json") ||
                exists("go.mod") || exists(".git")) {
            return true;
        }

        if (exists("src") || exists("lib") || exists(".github")) {
            return true;
        }

        if (prompt != null) {
            String cmd = prompt.getUserContent();
            if (cmd == null) return false;
            String cmdLower = cmd.toLowerCase();
            String[] codeKeywords = {"代码", "编程", "构建", "测试", "项目", "init", "compile"};
            for (String kw : codeKeywords) {
                if (cmdLower.contains(kw)) return true;
            }
        }
        return false;
    }

    private boolean exists(String path) {
        return Files.exists(rootPath.resolve(path));
    }
}
