package features.ai.cli;

import org.junit.jupiter.api.Test;
import org.noear.solon.Solon;
import org.noear.solon.ai.codecli.Config;
import org.noear.solon.ai.codecli.core.SkillDiscoverySkill;
import org.noear.solon.ai.codecli.core.SkillManager;
import org.noear.solon.core.util.Assert;
import org.noear.solon.test.SolonTest;

import java.util.Map;

/**
 *
 * @author noear 2026/2/28 created
 *
 */
@SolonTest
public class SkillTest {
    @Test
    public void case1() {
        Config config = Solon.cfg().toBean("solon.code.cli", Config.class);

        SkillManager skillManager = new SkillManager();
        if(Assert.isNotEmpty(config.skillPools)) {
            for (Map.Entry<String, String> entry : config.skillPools.entrySet()) {
                skillManager.registerPool(entry.getKey(), entry.getValue());
            }
        }

        SkillDiscoverySkill discoverySkill = new SkillDiscoverySkill(skillManager);

        //video generation animation
        //AI image video media generation

        //discoverySkill.searchSkills()

        String desc = skillManager.getSkillMap().get("@shared/docusign-automation").getDescription();
        System.out.println(desc);
        assert desc.length() > 30;

        String list = discoverySkill.searchSkills("video generation animation");
        System.out.println(list);
        assert list.length() > 100;
    }
}
