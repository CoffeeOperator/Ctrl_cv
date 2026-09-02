package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.Comparator;

public class PearlEscape extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> triggerDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("distance")
        .defaultValue(10.0)
        .min(5.0)
        .sliderMax(15.0)
        .build()
    );

    public PearlEscape() {
        super(AddonTemplate.CATEGORY, "pearl-escape", "Automatically throws an ender pearl at the ground when enemies get within 10 blocks.");
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        // Guard Clause: Completely blocks NullPointer warnings by confirming player components exist
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return;

        Player threat = mc.level.players().stream()
            .filter(p -> p != mc.player && p.isAlive() && !Friends.get().isFriend(p))
            .min(Comparator.comparingDouble(p -> mc.player.distanceToSqr(p)))
            .orElse(null);

        if (threat == null || mc.player.distanceTo(threat) > triggerDistance.get()) return;

        int pearlSlot = findPearlSlot();

        // Fixed: Passed a newly instantiated ItemStack object instance to clear the cooldown signature error
        if (pearlSlot == -1 || mc.player.getCooldowns().isOnCooldown(new ItemStack(Items.ENDER_PEARL))) return;

        // Fixed: Swapped private selection parameter out for the public getSelectedSlot() method
        int prevSlot = mc.player.getInventory().getSelectedSlot();
        InvUtils.swap(pearlSlot, false);

        float currentYaw = mc.player.getYRot();
        float escapePitch = 90.0f;

        mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(currentYaw, escapePitch, mc.player.onGround(), false));
        mc.getConnection().send(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0, currentYaw, escapePitch));
        mc.player.swing(InteractionHand.MAIN_HAND);

        InvUtils.swap(prevSlot, false);
    }

    private int findPearlSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(Items.ENDER_PEARL)) return i;
        }
        return -1;
    }
}
