package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ThunderhackBreaker extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Target> targetMode = sgGeneral.add(new EnumSetting.Builder<Target>()
        .name("target")
        .defaultValue(Target.Breaker)
        .build()
    );

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .defaultValue(5)
        .min(1)
        .sliderMax(7)
        .build()
    );

    private final Setting<Boolean> cevPriority = sgGeneral.add(new BoolSetting.Builder()
        .name("cev-priority")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> antiShulker = sgGeneral.add(new BoolSetting.Builder()
        .name("anti-shulker")
        .defaultValue(true)
        .build()
    );

    private enum Target { AutoCrystal, Breaker }

    private BlockPos blockPos;

    public ThunderhackBreaker() {
        super(AddonTemplate.CATEGORY, "thunderhack-breaker", "Automates block breaking targets to open up enemy protections.");
    }

    @Override
    public void onActivate() {
        blockPos = null;
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        Module speedMine = Modules.get().get("thunderhack-speedmine");
        boolean speedMineActive = speedMine != null && speedMine.isActive();

        if (speedMineActive) {
            try {
                Field activeActionField = speedMine.getClass().getDeclaredField("activeAction");
                activeActionField.setAccessible(true);
                Object activeAction = activeActionField.get(speedMine);

                if (activeAction != null) return;
            } catch (Exception ignored) {}
        }

        Player target = findOptimalTarget();
        if (target == null) return;

        BlockPos enemyFeet = target.blockPosition();
        BlockPos burrow = BlockPos.containing(target.getX(), target.getY() + 0.5, target.getZ());
        BlockState burrowState = mc.level.getBlockState(burrow);

        if (blockPos != null) {
            double distanceSq = mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(blockPos));
            double maxRange = 4.5;

            if (speedMineActive) {
                try {
                    Setting<?> rangeSetting = speedMine.settings.get("range");
                    if (rangeSetting != null) maxRange = ((Number) rangeSetting.get()).doubleValue();
                } catch (Exception ignored) {}
            }

            // Fixed: Added a target validation override. If the block tracker catches air or an obsolete item state, it drops memory instantly
            if (mc.level.getBlockState(blockPos).isAir() || distanceSq > (maxRange * maxRange) || mc.level.getBlockState(blockPos).is(Blocks.BEDROCK)) {
                blockPos = null;
            } else {
                mc.gameMode.continueDestroyBlock(blockPos, Direction.UP);
                mc.player.swing(InteractionHand.MAIN_HAND);
                return;
            }
        }

        List<BreakData> list = new ArrayList<>();
        boolean inBurrow = burrowState.is(Blocks.OBSIDIAN) || burrowState.is(Blocks.ENDER_CHEST);

        if (inBurrow) {
            list.add(new BreakData(burrow, 9999f, 0.0));
        }

        for (int x = -2; x <= 2; x++) {
            for (int y = 0; y <= 3; y++) {
                for (int z = -2; z <= 2; z++) {
                    if (y > 1 && (x == -2 || z == -2 || x == 2 || z == 2)) continue;
                    BlockPos bp = BlockPos.containing(target.getX() + x, target.getY() + y, target.getZ() + z);

                    if (mc.level.getBlockState(bp).getBlock() instanceof ShulkerBoxBlock && antiShulker.get()) {
                        list.add(new BreakData(bp, 9500f, bp.distSqr(enemyFeet)));
                        continue;
                    }

                    if (mc.level.getBlockState(bp).is(Blocks.OBSIDIAN) || mc.level.getBlockState(bp).is(Blocks.ENDER_CHEST)) {
                        if (bp.equals(blockPos) || mc.level.getBlockState(bp).isAir()) continue;

                        double distanceToEnemyFeet = bp.distSqr(enemyFeet);
                        float baseWeight = 100f;

                        if (distanceToEnemyFeet <= 2.0) {
                            baseWeight = 5000f;
                        } else if (bp.getY() > target.getY() + 1 && cevPriority.get()) {
                            baseWeight = 1000f;
                        }

                        list.add(new BreakData(bp, baseWeight, distanceToEnemyFeet));
                    }
                }
            }
        }

        if (list.isEmpty()) return;

        BreakData best = list.stream()
            .min((b1, b2) -> {
                if (b1.weight() != b2.weight()) return Float.compare(b2.weight(), b1.weight());
                return Double.compare(b1.distanceToEnemy(), b2.distanceToEnemy());
            })
            .orElse(null);

        if (best != null) {
            blockPos = best.blockPos();
            mc.gameMode.continueDestroyBlock(blockPos, Direction.UP);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    private Player findOptimalTarget() {
        List<Player> targets = new ArrayList<>();
        for (Player p : mc.level.players()) {
            if (p == mc.player || !p.isAlive() || Friends.get().isFriend(p)) continue;
            if (mc.player.distanceTo(p) <= range.get()) {
                targets.add(p);
            }
        }
        if (targets.isEmpty()) return null;
        targets.sort(Comparator.comparingDouble(p -> mc.player.distanceToSqr(p)));
        return targets.get(0);
    }

    private record BreakData(BlockPos blockPos, float weight, double distanceToEnemy) {}
}

