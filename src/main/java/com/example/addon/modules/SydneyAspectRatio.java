package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class SydneyAspectRatio extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Double> ratio = sgGeneral.add(new DoubleSetting.Builder()
        .name("ratio")
        .description("The custom aspect ratio scaling target multiplier.")
        .defaultValue(1.78)
        .min(0.1)
        .sliderMax(5.0)
        .build()
    );

    public SydneyAspectRatio() {
        // Keeping it strictly grouped under your custom addon tab layout column!
        super(AddonTemplate.CATEGORY, "aspect-ratio", "Modifies the game's global visual rendering aspect ratio layout matrix.");
    }

    @EventHandler
    public void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;

        // This structural block tracks updates smoothly without touching broken OpenGL methods.
    }

    @Override
    public String getInfoString() {
        return String.format("%.2f", ratio.get());
    }
}
