package com.fbskills;

import com.fbskills.command.SkillsCommand;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import java.util.List;

/**
 * Bootstrap entry point for modern Paper / Folia server lifecycle.
 * Registers Brigadier-backed commands and handles early initialization.
 */
public class FBSkillsBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();
            commands.register(
                    "skills",
                    "FBSkills progression navigation, leaderboards, and administrative tools.",
                    List.of("fbskills", "skill", "fbskill", "progression"),
                    new SkillsCommand()
            );
        });
    }
}
