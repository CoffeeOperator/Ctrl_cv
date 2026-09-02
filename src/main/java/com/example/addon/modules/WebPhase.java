package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class WebPhase extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> pitchAngle = sgGeneral.add(new DoubleSetting.Builder()
        .name("pitch-angle")
        .description("The custom look angle applied when throwing the pearl down.")
        .defaultValue(80.0)
        .min(70.0)
        .max(90.0)
        .sliderRange(70.0, 90.0)
        .build()
    );

    private final Setting<Boolean> autoDisable = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-disable")
        .description("Automatically turns the module off after phasing successfully.")
        .defaultValue(true)
        .build()
    );

    private boolean placementDone;
    private boolean pearlThrown;
    private int delayTicks;

    public WebPhase() {
        super(AddonTemplate.CATEGORY, "web-phase", "Places a cobweb at your feet and throws a pearl down to phase through blocks.");
    }

    @Override
    public void onActivate() {
        placementDone = false;
        pearlThrown = false;
        delayTicks = 0;
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.getConnection() == null) return;

        BlockPos feetPos = mc.player.blockPosition();

        if (!placementDone) {
            int webSlot = findItemSlot(Blocks.COBWEB.asItem());
            if (webSlot == -1) {
                info("No cobwebs found in hotbar! Disabling.");
                toggle();
                return;
            }

            int prevSlot = mc.player.getInventory().getSelectedSlot();
            InvUtils.swap(webSlot, false);

            BlockHitResult hitResult = new BlockHitResult(Vec3.atCenterOf(feetPos), Direction.UP, feetPos, false);
            mc.getConnection().send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hitResult, 0));
            mc.player.swing(InteractionHand.MAIN_HAND);

            InvUtils.swap(prevSlot, false);
            placementDone = true;
            return;
        }

        if (placementDone && !pearlThrown) {
            if (mc.level.getBlockState(feetPos).getBlock() != Blocks.COBWEB) {
                delayTicks++;
                if (delayTicks > 10) {
                    info("Web placement timed out or failed. Disabling.");
                    toggle();
                }
                return;
            }

            int pearlSlot = findItemSlot(Items.ENDER_PEARL);
            if (pearlSlot == -1) {
                info("No pearls found in hotbar! Disabling.");
                toggle();
                return;
            }

            int prevSlot = mc.player.getInventory().getSelectedSlot();
            InvUtils.swap(pearlSlot, false);

            // Fixed: Utilizes the capital-R method call structure used natively by contemporary 1.21.1 versions
            float currentYaw = mc.player.getYRot();
            float targetPitch = pitchAngle.get().floatValue();

            mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(currentYaw, targetPitch, mc.player.onGround(), false));
            mc.getConnection().send(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0, currentYaw, targetPitch));
            mc.player.swing(InteractionHand.MAIN_HAND);

            InvUtils.swap(prevSlot, false);
            pearlThrown = true;

            if (autoDisable.get()) {
                toggle();
            }
        }
    }

    private int findItemSlot(net.minecraft.world.item.Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(item)) return i;
        }
        return -1;
    }
}
