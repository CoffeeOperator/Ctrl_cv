package com.example.addon;

import com.example.addon.commands.CommandExample;
import com.example.addon.hud.HudExample;
import com.example.addon.modules.*;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.NoRotate;
import org.slf4j.Logger;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();

    // Updated: Changes your ClickGUI module tab name directly to your project name
    public static final Category CATEGORY = new Category("Ctrl_cv");
    public static final HudGroup HUD_GROUP = new HudGroup("Ctrl_cv");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Ctrl_cv Addon Framework");

        // Modules
        // Fixed: Safely registers your new fixed HoleSnap module instance instead of the dead template holder
        Modules.get().add(new HoleSnap());
        Modules.get().add(new ThunderhackSpeedMine());
        Modules.get().add(new ThunderhackBreaker());
        Modules.get().add(new FastLatency());
        Modules.get().add(new SydneyAspectRatio());
        Modules.get().add(new TickShift());
        Modules.get().add(new WebPhase());
        Modules.get().add(new THMAutoTrap());
        Modules.get().add(new FOVModifier());
        Modules.get().add(new TotemPopCounter());
        Modules.get().add(new ViewModel());
        Modules.get().add(new NoRotate());
        Modules.get().add(new PearlEscape());
        Modules.get().add(new MioPhase());
        Modules.get().add(new FastWeb());
        // Commands
        Commands.add(new CommandExample());

        // HUD
        Hud.get().register(HudExample.INFO);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }

    @Override
    public GithubRepo getRepo() {
        // Points natively back to your setup repository tree structures
        return new GithubRepo("MeteorDevelopment", "meteor-addon-template");
    }
}
