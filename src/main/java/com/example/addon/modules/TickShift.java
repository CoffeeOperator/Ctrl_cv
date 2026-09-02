package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.ChatFormatting;

public class TickShift extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Integer> maxTicks = sgGeneral.add(new IntSetting.Builder().name("max-ticks").defaultValue(20).min(1).sliderMax(40).build());
    public final Setting<Double> speedMultiplier = sgGeneral.add(new DoubleSetting.Builder().name("speed").defaultValue(2.0).min(1.0).sliderMax(4.0).build());

    public int chargedTicks = 0;
    private boolean isDischarging = false;

    public TickShift() {
        super(AddonTemplate.CATEGORY, "TickShift", "Charges up ticks while standing still and releases them to accelerate game time.");
    }

    @Override
    public void onActivate() { chargedTicks = 0; isDischarging = false; }

    @Override
    public void onDeactivate() { chargedTicks = 0; isDischarging = false; }

    @EventHandler
    public void onTickPre(TickEvent.Pre event) {
        if (mc.player == null) return;

        boolean isMoving = mc.player.xxa != 0.0f || mc.player.zza != 0.0f || mc.options.keyJump.isDown();

        if (!isMoving) {
            isDischarging = false;
            if (chargedTicks < maxTicks.get()) {
                chargedTicks++;
            }
        } else {
            if (chargedTicks > 0) {
                isDischarging = true;
            } else {
                isDischarging = false;
            }
        }
    }

    @EventHandler
    public void onTickPost(TickEvent.Post event) {
        if (mc.player == null || !isDischarging || chargedTicks <= 0) return;

        // Bypasses the broken Timer Mixin entirely by re-processing additional tick actions on the same frame window
        int releaseBurst = Math.min(chargedTicks, (int) (speedMultiplier.get() - 1.0));
        for (int i = 0; i < releaseBurst; i++) {
            if (chargedTicks > 0) {
                // Forces your local player client ticking and movement matrices to process additional iterations natively
                mc.player.tick();
                if (mc.player.connection != null) {
                    mc.player.aiStep();
                }
                chargedTicks--;
            }
        }

        if (chargedTicks <= 0) {
            isDischarging = false;
        }
    }

    @Override
    public String getInfoString() {
        return (chargedTicks >= maxTicks.get() ? ChatFormatting.GREEN.toString() : "") + chargedTicks;
    }
}
