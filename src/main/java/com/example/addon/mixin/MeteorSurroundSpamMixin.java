package com.example.addon.mixin;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.combat.Surround; // Native Meteor Surround Module class path
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = Surround.class, remap = false)
public class MeteorSurroundSpamMixin {

    @Unique
    private Setting<Integer> spamPackets;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        Surround surround = (Surround) (Object) this;
        // Accesses the baseline default settings panel tab group natively on the module initialization pass
        SettingGroup sgGeneral = surround.settings.getDefaultGroup();

        // Fixed: Injects the packet speed flood slider natively into Meteor's existing UI panel view
        spamPackets = sgGeneral.add(new IntSetting.Builder()
            .name("spam-packets-per-tick")
            .description("How many proactive obsidian placement packets to force down the network pipeline per tick frame.")
            .defaultValue(1)
            .min(1)
            .sliderMax(5)
            .build()
        );
    }

    @Inject(method = "onTick", at = @At("HEAD"))
    private void onTickPre(TickEvent.Pre event, CallbackInfo ci) {
        Surround surround = (Surround) (Object) this;
        var mc = net.minecraft.client.Minecraft.getInstance();

        if (mc.player == null || mc.level == null || !surround.isActive()) return;

        // Finds your obsidian stock safely inside your primary hotbar tracker channels
        FindItemResult obsidian = InvUtils.findInHotbar(Items.OBSIDIAN);
        if (!obsidian.found()) return;

        BlockPos playerPos = mc.player.blockPosition();
        List<BlockPos> targetPositions = new ArrayList<>();

        // Maps the standard protective 4-block ring coordinates surrounding your feet
        targetPositions.add(playerPos.north());
        targetPositions.add(playerPos.south());
        targetPositions.add(playerPos.east());
        targetPositions.add(playerPos.west());

        int packetBurstCount = spamPackets.get();

        // Continuous Network Flood Loop: Bypasses air checks entirely to forcefully flood the server queue
        for (int i = 0; i < packetBurstCount; i++) {
            for (BlockPos pos : targetPositions) {
                // Fires pure interaction transactions continuously to claim and hold the defensive placement grid
                BlockUtils.place(pos, obsidian, false, 50, true, false);
            }
        }
    }
}
