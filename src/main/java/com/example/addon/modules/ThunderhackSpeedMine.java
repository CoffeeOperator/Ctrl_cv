package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class ThunderhackSpeedMine extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .defaultValue(Mode.Packet)
        .build()
    );

    public final Setting<Boolean> instantRebreak = sgGeneral.add(new BoolSetting.Builder()
        .name("instant-rebreak")
        .description("Sends the stop packet before start packet exploit sequence to instantly break re-placed blocks again and again.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SwitchMode> switchMode = sgGeneral.add(new EnumSetting.Builder<SwitchMode>()
        .name("switch-mode")
        .defaultValue(SwitchMode.Silent)
        .build()
    );

    private final Setting<Integer> swapDelay = sgGeneral.add(new IntSetting.Builder()
        .name("swap-delay")
        .defaultValue(50)
        .build()
    );

    private final Setting<Double> factor = sgGeneral.add(new DoubleSetting.Builder()
        .name("factor")
        .defaultValue(1.0)
        .build()
    );

    public final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .defaultValue(4.2)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>().name("shape-mode").defaultValue(ShapeMode.Both).build());

    // Strict Single-Action Target Trackers
    public MineAction activeAction = null;
    private BlockPos lastBrokenPos = null;
    private Direction lastDirection = Direction.UP;

    public enum Mode { Packet, GrimInstant }
    public enum SwitchMode { Silent, Normal }

    public ThunderhackSpeedMine() {
        super(AddonTemplate.CATEGORY, "thunderhack-speedmine", "High-performance mining bypass engine optimized for strict single-target throughput.");
    }

    @Override
    public void onActivate() { activeAction = null; lastBrokenPos = null; }

    @Override
    public void onDeactivate() { activeAction = null; lastBrokenPos = null; }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        // Fixed: If a block is placed back down on our last broken position, instantly pop it again and again
        if (instantRebreak.get() && lastBrokenPos != null && !mc.level.getBlockState(lastBrokenPos).isAir()) {
            int pickSlot = getTool(lastBrokenPos);
            int prevSlot = mc.player.getInventory().getSelectedSlot();

            if (pickSlot != -1) InvUtils.swap(pickSlot, false);

            // Exploit Packet Order: STOP packet sent BEFORE the START packet to force an instant tick break
            mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, lastBrokenPos, lastDirection));
            mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, lastBrokenPos, lastDirection));
            mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, lastBrokenPos, lastDirection));

            if (pickSlot != -1) InvUtils.swap(prevSlot, false);
            // Fixed: Removed the breaking return statement so the main activeAction thread logic can execute simultaneously!
        }

        if (activeAction != null) {
            boolean isComplete = activeAction.update();
            if (isComplete) {
                lastBrokenPos = activeAction.pos;
                lastDirection = activeAction.direction;
                activeAction = null;
            }
        }
    }

    @EventHandler
    private void onStartBreakingBlock(StartBreakingBlockEvent event) {
        if (mc.player == null || mc.level == null || mc.player.isCreative()) return;
        if (activeAction != null && activeAction.pos.equals(event.blockPos)) return;

        activeAction = new MineAction(event.blockPos, event.direction);
        event.setCancelled(true);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (activeAction == null) return;
        double size = Math.min(1.0, activeAction.progress);
        BlockPos p = activeAction.pos;

        int red = (int) ((1.0 - size) * 255);
        int green = (int) (size * 255);

        Color renderSide = new Color(red, green, 0, 75);
        Color renderLine = new Color(red, green, 0, 235);

        event.renderer.box(p.getX() + 0.5 - (size / 2), p.getY() + 0.5 - (size / 2), p.getZ() + 0.5 - (size / 2),
            p.getX() + 0.5 + (size / 2), p.getY() + 0.5 + (size / 2), p.getZ() + 0.5 + (size / 2),
            renderSide, renderLine, shapeMode.get(), 0);
    }

    public float getBlockStrength(BlockState state, BlockPos position) {
        if (state.isAir()) return 0.02f;
        float hardness = state.getDestroySpeed(mc.level, position);
        if (hardness < 0) return 0;
        return getDigSpeed(state, position) / hardness / 30f;
    }

    public float getDigSpeed(BlockState state, BlockPos position) {
        int slot = getTool(position);
        ItemStack stack = slot != -1 ? mc.player.getInventory().getItem(slot) : mc.player.getMainHandItem();
        float digSpeed = stack.getDestroySpeed(state);

        if (digSpeed > 1.0f && !stack.isEmpty()) {
            int efficiencyModifier = EnchantmentHelper.getItemEnchantmentLevel(mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY), stack);
            if (efficiencyModifier > 0) digSpeed += (float) (StrictMath.pow(efficiencyModifier, 2) + 1);
        }

        if (mc.player.hasEffect(MobEffects.HASTE)) digSpeed *= 1 + (mc.player.getEffect(MobEffects.HASTE).getAmplifier() + 1) * 0.2F;
        if (mc.player.hasEffect(MobEffects.MINING_FATIGUE)) digSpeed *= (float) Math.pow(0.3f, mc.player.getEffect(MobEffects.MINING_FATIGUE).getAmplifier() + 1);
        if (mc.player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)) digSpeed *= (float) mc.player.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED);
        if (!mc.player.onGround()) digSpeed /= 5;
        return digSpeed < 0 ? 0 : digSpeed * factor.get().floatValue();
    }

    public int getTool(BlockPos pos) {
        int index = -1;
        float currentFastest = 1.0f;
        if (mc.level == null) return -1;
        BlockState state = mc.level.getBlockState(pos);
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                float destroySpeed = stack.getDestroySpeed(state);
                if (destroySpeed > currentFastest) {
                    currentFastest = destroySpeed;
                    index = i;
                }
            }
        }
        return index;
    }

    public class MineAction {
        public final BlockPos pos;
        public float progress;
        public final Direction direction;

        public MineAction(BlockPos pos, Direction direction) {
            this.pos = pos;
            this.direction = direction == null ? Direction.UP : direction;
            this.progress = 0;
            start();
        }

        private void start() {
            mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, direction));
            mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, pos, direction));
        }

        public boolean update() {
            if (mc.level.getBlockState(pos).isAir()) return false;

            progress += getBlockStrength(mc.level.getBlockState(pos), pos);

            if (progress >= 1.0f) {
                int pickSlot = getTool(pos);
                int prevSlot = mc.player.getInventory().getSelectedSlot();

                if (pickSlot != -1) InvUtils.swap(pickSlot, false);
                mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, direction));
                if (pickSlot != -1) InvUtils.swap(prevSlot, false);
                return true;
            }
            return false;
        }
    }
}
