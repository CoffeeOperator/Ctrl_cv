package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.GetFovEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class FOVModifier extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> fov = sgGeneral.add(new DoubleSetting.Builder()
        .name("FOV")
        .defaultValue(120.0)
        .min(30.0)
        .sliderMax(150.0)
        .build()
    );

    public FOVModifier() {
        super(AddonTemplate.CATEGORY, "fov-modifier", "Provides expanded camera Field of View adjustments natively.");
    }

    @EventHandler
    private void onGetFov(GetFovEvent event) {
        event.fov = fov.get().floatValue();
    }
}
