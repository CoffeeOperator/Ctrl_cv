package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class HoleSnap extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .defaultValue(5.0)
        .min(1.0)
        .sliderMax(8.0)
        .build()
    );

    private final Setting<Boolean> doubles = sgGeneral.add(new BoolSetting.Builder()
        .name("double-holes")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> quads = sgGeneral.add(new BoolSetting.Builder()
        .name("quad-holes")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .defaultValue(0.2873)
        .min(0.05)
        .sliderMax(0.5)
        .build()
    );

    private final List<Hole> holes = new ArrayList<>();
    private Hole target;

    public HoleSnap() {
        super(AddonTemplate.CATEGORY, "hole-snap", "Pulls you toward the nearest safe hole.");
    }

    @Override
    public void onActivate() {
        target = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        holes.clear();
        target = null;

        int r = (int) Math.ceil(range.get());
        BlockPos playerPos = mc.player.blockPosition();

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos blockPos = playerPos.offset(x, y, z);
                    Hole hole = findHole(blockPos);
                    if (hole != null) {
                        holes.add(hole);
                    }
                }
            }
        }

        if (holes.isEmpty()) return;

        holes.sort(Comparator.comparingDouble(h -> mc.player.distanceToSqr(h.center)));
        target = holes.get(0);
    }

    @EventHandler
    private void onPlayerMove(PlayerMoveEvent event) {
        if (target == null || mc.player == null) return;
        if (mc.player.fallDistance >= 5.0f) return;

        Vec3 pos = mc.player.position();
        double diffX = target.center.x - pos.x;
        double diffZ = target.center.z - pos.z;
        double distSq = diffX * diffX + diffZ * diffZ;

        if (distSq < 0.001) return;

        double dist = Math.sqrt(distSq);
        double moveX = (diffX / dist) * Math.min(speed.get(), dist);
        double moveZ = (diffZ / dist) * Math.min(speed.get(), dist);

        Vec3 motion = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(moveX, motion.y, moveZ);
    }

    private Hole findHole(BlockPos pos) {
        if (mc.level == null) return null;
        if (!mc.level.getBlockState(pos).isAir()) return null;
        if (!mc.level.getBlockState(pos.above()).isAir()) return null;
        if (!mc.level.getBlockState(pos.above(2)).isAir()) return null;

        if (!PlayerUtils.isWithin(Vec3.atBottomCenterOf(pos), range.get())) return null;

        if (isSafeSingle(pos)) {
            return new Hole(Vec3.atBottomCenterOf(pos));
        }

        if (doubles.get()) {
            BlockPos xPos = pos.offset(1, 0, 0);
            if (isColumnClear(xPos) && isSafeDouble(pos, xPos, Direction.EAST)) {
                return new Hole(Vec3.atBottomCenterOf(pos).add(0.5, 0, 0));
            }

            BlockPos zPos = pos.offset(0, 0, 1);
            if (isColumnClear(zPos) && isSafeDouble(pos, zPos, Direction.SOUTH)) {
                return new Hole(Vec3.atBottomCenterOf(pos).add(0, 0, 0.5));
            }
        }

        if (quads.get()) {
            BlockPos xPos = pos.offset(1, 0, 0);
            BlockPos zPos = pos.offset(0, 0, 1);
            BlockPos xzPos = pos.offset(1, 0, 1);

            if (isColumnClear(xPos) && isColumnClear(zPos) && isColumnClear(xzPos) && isSafeQuad(pos)) {
                return new Hole(Vec3.atBottomCenterOf(pos).add(0.5, 0, 0.5));
            }
        }

        return null;
    }

    private boolean isColumnClear(BlockPos pos) {
        if (mc.level == null) return false;
        return mc.level.getBlockState(pos).isAir()
            && mc.level.getBlockState(pos.above()).isAir()
            && mc.level.getBlockState(pos.above(2)).isAir();
    }

    private boolean isSafeSingle(BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP) continue;
            if (!isSafeBlock(pos.relative(dir))) return false;
        }
        return true;
    }

    private boolean isSafeDouble(BlockPos pos, BlockPos otherPos, Direction axis) {
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP || dir == axis) continue;
            if (!isSafeBlock(pos.relative(dir))) return false;
        }
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP || dir == axis.getOpposite()) continue;
            if (!isSafeBlock(otherPos.relative(dir))) return false;
        }
        return true;
    }

    private boolean isSafeQuad(BlockPos pos) {
        BlockPos[] corners = { pos, pos.offset(1, 0, 0), pos.offset(0, 0, 1), pos.offset(1, 0, 1) };
        for (BlockPos corner : corners) {
            if (!isSafeBlock(corner.below())) return false;
        }
        if (!isSafeBlock(pos.north()) || !isSafeBlock(pos.west())) return false;
        if (mc.level == null || !isSafeBlock(pos.offset(1, 0, 1).south()) || !isSafeBlock(pos.offset(1, 0, 1).east())) return false;
        return true;
    }

    private boolean isSafeBlock(BlockPos pos) {
        if (mc.level == null) return false;
        BlockState state = mc.level.getBlockState(pos);
        return state.getBlock().getExplosionResistance() >= 600 && state.getBlock() != Blocks.AIR;
    }

    private record Hole(Vec3 center) {}
}
