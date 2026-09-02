package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class MioPhase extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .defaultValue(Mode.Clip)
        .build()
    );

    private final Setting<Double> blocksOffset = sgGeneral.add(new DoubleSetting.Builder()
        .name("clip-distance")
        .description("The micro-step distance used to glide your player model through the wall grid.")
        .defaultValue(0.03)
        .min(0.01)
        .sliderMax(0.1)
        .visible(() -> mode.get() == Mode.Clip)
        .build()
    );

    private final Setting<Boolean> useWebs = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-web")
        .description("Automatically places a cobweb to slow your movement matrix, ensuring a 100% stable pearl clip.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Pearl)
        .build()
    );

    private final Setting<Boolean> webDoubles = sgGeneral.add(new BoolSetting.Builder()
        .name("web-doubles")
        .description("Places a secondary cobweb at head height to fully force block phase clips.")
        .defaultValue(false)
        .visible(() -> mode.get() == Mode.Pearl && useWebs.get())
        .build()
    );

    private final Setting<Boolean> attackBlock = sgGeneral.add(new BoolSetting.Builder()
        .name("packet-haste")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Pearl)
        .build()
    );

    public enum Mode { Pearl, Clip }

    public MioPhase() {
        super(AddonTemplate.CATEGORY, "mio-phase", "Advanced phase exploit matrix optimized to bypass modern anti-cheat rubber-banding loops.");
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.getConnection() == null) return;

        // --- MODE 1: PURE PACKET CLIP (Anti-Cheat Input Muting) ---
        if (mode.get() == Mode.Clip) {
            if (mc.player.horizontalCollision) {
                // Fixed: Completely freezes local movement inputs to stop your client from sending conflicting key packets
                mc.player.setDeltaMovement(0, 0, 0);
                mc.player.xxa = 0.0f;
                mc.player.zza = 0.0f;

                double yawRad = Math.toRadians(mc.player.getYRot());
                double cos = Math.cos(yawRad);
                double sin = Math.sin(yawRad);

                double offsetX = -sin * blocksOffset.get();
                double offsetZ = cos * blocksOffset.get();

                Vec3 startPos = mc.player.position();
                Vec3 targetPos = startPos.add(offsetX, 0, offsetZ);

                // Send staggered positional packet bursts down the pipe on the exact same frame window
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(startPos.x + (offsetX * 0.3), startPos.y, startPos.z + (offsetZ * 0.3), mc.player.onGround(), false));
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(startPos.x + (offsetX * 0.6), startPos.y, startPos.z + (offsetZ * 0.6), mc.player.onGround(), false));

                // Final Step: Execute true player coordinate assignment
                mc.player.setPos(targetPos.x, targetPos.y, targetPos.z);
                mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(
                    targetPos.x, targetPos.y, targetPos.z,
                    mc.player.getYRot(), mc.player.getXRot(),
                    mc.player.onGround(), false
                ));
            }
        }

        // --- MODE 2: PEARL CLIP NETWORK DESYNC (With Cobweb Scaffolding) ---
        if (mode.get() == Mode.Pearl) {
            if (mc.player.horizontalCollision) {
                int pearlSlot = findPearlSlot();
                if (pearlSlot == -1 || mc.player.getCooldowns().isOnCooldown(new ItemStack(Items.ENDER_PEARL))) return;

                BlockPos feetPos = mc.player.blockPosition();

                // Automated Web Deployment Stage
                if (useWebs.get()) {
                    FindItemResult webResult = InvUtils.findInHotbar(Items.COBWEB);
                    if (webResult.found()) {
                        // Deploy web at feet
                        if (mc.level.getBlockState(feetPos).canBeReplaced()) {
                            BlockUtils.place(feetPos, webResult, false, 50, true, false);
                        }
                        // Deploy web at head if doubles are checked
                        if (webDoubles.get()) {
                            BlockPos headPos = feetPos.above();
                            if (mc.level.getBlockState(headPos).canBeReplaced()) {
                                BlockUtils.place(headPos, webResult, false, 50, true, false);
                            }
                        }
                    }
                }

                int prevSlot = mc.player.getInventory().getSelectedSlot();
                InvUtils.swap(pearlSlot, false);

                if (attackBlock.get()) {
                    mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, feetPos, Direction.UP));
                    mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, feetPos, Direction.UP));
                }

                float currentYaw = mc.player.getYRot();
                float clipPitch = 89.2f;

                mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(currentYaw, clipPitch, mc.player.onGround(), false));
                mc.getConnection().send(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0, currentYaw, clipPitch));
                mc.player.swing(InteractionHand.MAIN_HAND);

                InvUtils.swap(prevSlot, false);
            }
        }
    }

    private int findPearlSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(Items.ENDER_PEARL)) return i;
        }
        return -1;
    }
}

