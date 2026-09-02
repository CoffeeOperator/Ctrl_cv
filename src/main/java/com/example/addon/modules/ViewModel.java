package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import net.minecraft.world.item.ItemStack;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

public class ViewModel extends Module {
    private final SettingGroup sgTranslation = settings.createGroup("Translation");
    private final SettingGroup sgScale = settings.createGroup("Scale");

    public final Setting<Double> translateX = sgTranslation.add(new DoubleSetting.Builder().name("translate-x").defaultValue(0.0).min(-2.0).sliderMax(2.0).build());
    public final Setting<Double> translateY = sgTranslation.add(new DoubleSetting.Builder().name("translate-y").defaultValue(0.0).min(-2.0).sliderMax(2.0).build());
    public final Setting<Double> translateZ = sgTranslation.add(new DoubleSetting.Builder().name("translate-z").defaultValue(0.0).min(-2.0).sliderMax(2.0).build());

    public final Setting<Double> scaleX = sgScale.add(new DoubleSetting.Builder().name("scale-x").defaultValue(1.0).min(0.0).sliderMax(3.0).build());
    public final Setting<Double> scaleY = sgScale.add(new DoubleSetting.Builder().name("scale-y").defaultValue(1.0).min(0.0).sliderMax(3.0).build());
    public final Setting<Double> scaleZ = sgScale.add(new DoubleSetting.Builder().name("scale-z").defaultValue(1.0).min(0.0).sliderMax(3.0).build());

    public ViewModel() {
        super(AddonTemplate.CATEGORY, "view-model", "Customizes scale and positional translation vectors of held items.");
    }
}
