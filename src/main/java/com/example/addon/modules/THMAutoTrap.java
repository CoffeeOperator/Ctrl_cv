package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class THMAutoTrap extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("whitelist")
        .description("Blocks to use for trapping.")
        .defaultValue(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN)
        .build()
    );

    private final Setting<Double> placeRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("place-range")
        .description("Range at which blocks can be placed.")
        .defaultValue(5.2)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Double> placeWallsRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("walls-range")
        .description("Range in which to place when behind blocks.")
        .defaultValue(5.2)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<SortPriority> priority = sgGeneral.add(new EnumSetting.Builder<SortPriority>()
        .name("target-priority")
        .description("How to select the player to target.")
        .defaultValue(SortPriority.LowestHealth)
        .build()
    );

    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-range")
        .description("Maximum distance to target players.")
        .defaultValue(6)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("place-delay")
        .description("Ticks between block placements.")
        .defaultValue(1)
        .build()
    );

    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .description("How many blocks to place per tick.")
        .defaultValue(1)
        .min(1)
        .build()
    );

    private final Setting<TopMode> topPlacement = sgGeneral.add(new EnumSetting.Builder<TopMode>()
        .name("top-blocks")
        .description("Which blocks to place at head height.")
        .defaultValue(TopMode.Full)
        .build()
    );

    private final Setting<HeightMode> heightMode = sgGeneral.add(new EnumSetting.Builder<HeightMode>()
        .name("height-mode")
        .description("Where to build the trap: around feet or around eye height.")
        .defaultValue(HeightMode.Feet)
        .build()
    );

    private final Setting<BuildOrder> buildOrder = sgGeneral.add(new EnumSetting.Builder<BuildOrder>()
        .name("build-order")
        .description("Order to build columns: bottom-to-top or top-to-bottom.")
        .defaultValue(BuildOrder.BottomToTop)
        .build()
    );

    private final Setting<Boolean> selfToggle = sgGeneral.add(new BoolSetting.Builder()
        .name("self-toggle")
        .description("Toggle off after placing all blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotate towards blocks when placing.")
        .defaultValue(false)
        .build()
    );

    private final Setting<GapSide> gapSide = sgGeneral.add(new EnumSetting.Builder<GapSide>()
        .name("Crystal/Anchor gap")
        .description("Leave one feet-level side as air for crystals/anchors.")
        .defaultValue(GapSide.None)
        .build()
    );

    // Fixed: Added the missing render configuration boolean setting variable to clear symbol errors
    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .defaultValue(new SettingColor(255, 0, 0, 75))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(255, 0, 0, 235))
        .build()
    );

    private final Setting<Double> fadeTime = sgRender.add(new DoubleSetting.Builder()
        .name("fade-time")
        .defaultValue(0.5)
        .min(0.1)
        .sliderMax(2)
        .build()
    );

    private final List<BlockPos> placePositions = new ArrayList<>();
    private Player target;
    private boolean placedAny;
    private int timer;
    private BlockPos gapPos;
    private final Map<BlockPos, Long> renderMap = new HashMap<>();

    public enum TopMode { Full, Top, Face, None }
    public enum HeightMode { Feet, Eye }
    public enum GapSide { None, TowardPlayer, North, South, East, West }
    public enum BuildOrder { BottomToTop, TopToBottom }

    public THMAutoTrap() {
        super(AddonTemplate.CATEGORY, "thm-auto-trap", "Traps targeted enemy players with supportive scaffolding placements.");
    }

    @Override
    public void onActivate() {
        target = null;
        placePositions.clear();
        timer = 0;
        placedAny = false;
        gapPos = null;
        renderMap.clear();
    }

    @Override
    public void onDeactivate() {
        placePositions.clear();
        renderMap.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (selfToggle.get() && placedAny && placePositions.isEmpty()) {
            placedAny = false;
            toggle();
            return;
        }

        // Fixed: Swapped to 1.21.1 valid Block.byItem conversion lookup method
        FindItemResult block = InvUtils.findInHotbar(itemStack -> blocks.get().contains(Block.byItem(itemStack.getItem())));
        if (!block.found()) return;

        List<Player> targets = getTargets();
        if (targets.isEmpty()) {
            target = null;
            placePositions.clear();
            return;
        }

        boolean doPlace = timer >= delay.get();
        int placedCount = 0;
        LinkedHashSet<BlockPos> allPositions = new LinkedHashSet<>();

        for (Player t : targets) {
            target = t;
            gapPos = null;

            if (gapSide.get() != GapSide.None) {
                int feetY = (int) Math.floor(t.getBoundingBox().minY);
                int gapY = heightMode.get() == HeightMode.Eye ? (int) Math.floor(t.getEyeY()) : feetY;
                BlockPos center = BlockPos.containing(t.getX(), gapY, t.getZ());
                gapPos = center.offset(getGapOffsetX(t), 0, getGapOffsetZ(t));
            }

            fillPlaceArray(t);
            allPositions.addAll(placePositions);

            if (doPlace && !placePositions.isEmpty()) {
                for (BlockPos placePos : placePositions) {
                    if (placedCount >= blocksPerTick.get()) break;
                    if (tryPlaceWithSupports(placePos, block)) {
                        placedAny = true;
                        placedCount++;
                    }
                }
            }

            if (placedCount >= blocksPerTick.get()) break;
        }

        placePositions.clear();
        placePositions.addAll(allPositions);

        if (doPlace) timer = 0;
        else timer++;
    }

    private void fillPlaceArray(Player t) {
        placePositions.clear();
        double epsilon = 1e-5;
        AABB box = t.getBoundingBox();
        List<BlockPos> corners = new ArrayList<>();
        corners.add(BlockPos.containing(box.minX, box.minY, box.minZ));
        corners.add(BlockPos.containing(box.minX, box.minY, box.maxZ - epsilon));
        corners.add(BlockPos.containing(box.maxX - epsilon, box.minY, box.minZ));
        corners.add(BlockPos.containing(box.maxX - epsilon, box.minY, box.maxZ - epsilon));

        Set<BlockPos> overlappedPositions = new LinkedHashSet<>(corners);
        for (BlockPos base : overlappedPositions) {
            if (heightMode.get() == HeightMode.Eye) {
                int eyeY = (int) Math.floor(t.getEyeY());
                BlockPos eyeBase = new BlockPos(base.getX(), eyeY, base.getZ());
                add(eyeBase.offset(1, 0, 0));
                add(eyeBase.offset(-1, 0, 0));
                add(eyeBase.offset(0, 0, -1));
                add(eyeBase.offset(0, 0, 1));
                add(eyeBase.offset(0, 1, 0));
                continue;
            }

            switch (topPlacement.get()) {
                case Full -> {
                    add(base.offset(0, 2, 0));
                    add(base.offset(1, 1, 0));
                    add(base.offset(-1, 1, 0));
                    add(base.offset(0, 1, 1));
                    add(base.offset(0, 1, -1));
                }
                case Face -> {
                    add(base.offset(1, 1, 0));
                    add(base.offset(-1, 1, 0));
                    add(base.offset(0, 1, 1));
                    add(base.offset(0, 1, -1));
                }
                case Top -> add(base.offset(0, 2, 0));
                case None -> {}
            }
            add(base.offset(0, -1, 0));
            add(base.offset(1, -1, 0));
            add(base.offset(-1, -1, 0));
            add(base.offset(0, -1, 1));
            add(base.offset(0, -1, -1));

            add(base.offset(1, 0, 0));
            add(base.offset(-1, 0, 0));
            add(base.offset(0, 0, -1));
            add(base.offset(0, 0, 1));
        }

        if (gapSide.get() != GapSide.None && gapPos != null) {
            placePositions.remove(gapPos);
        }

        boolean bottomToTop = buildOrder.get() == BuildOrder.BottomToTop;
        placePositions.sort((a, b) -> {
            if (a.getX() != b.getX()) return Integer.compare(a.getX(), b.getX());
            if (a.getZ() != b.getZ()) return Integer.compare(a.getZ(), b.getZ());
            return bottomToTop ? Integer.compare(a.getY(), b.getY()) : Integer.compare(b.getY(), a.getY());
        });
    }

    private void add(BlockPos blockPos) {
        if (placePositions.contains(blockPos)) return;
        // Fixed: Swapped to valid 1.21.1 canBeReplaced state signature method and dropped structural return values
        if (!mc.level.getBlockState(blockPos).canBeReplaced()) return;
        if (isOutOfRange(blockPos) || isBlockedByOtherEntity(blockPos, target)) return;
        placePositions.add(blockPos);
    }

    private boolean tryPlaceWithSupports(BlockPos placePos, FindItemResult block) {
        if (isBlockedByOtherEntity(placePos, target)) return false;

        if (BlockUtils.getPlaceSide(placePos) != null) {
            if (BlockUtils.place(placePos, block, rotate.get(), 50, true, true)) {
                if (render.get()) renderMap.put(placePos, System.currentTimeMillis());
                return true;
            }
            return false;
        }

        BlockPos center = BlockPos.containing(target.getX(), Math.floor(target.getBoundingBox().minY), target.getZ());
        int dx = placePos.getX() - center.getX();
        int dz = placePos.getZ() - center.getZ();
        Direction outward = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);

        Direction[] dirs = new Direction[] { outward, outward.getClockWise(), outward.getCounterClockWise(), outward.getOpposite() };

        for (Direction d : dirs) {
            BlockPos s = placePos.relative(d);
            if (gapPos != null && s.below().equals(gapPos)) continue;
            if (isBlockedByOtherEntity(s, target) || BlockUtils.getPlaceSide(s) == null) continue;

            if (BlockUtils.place(s, block, rotate.get(), 50, true, true)) {
                if (render.get()) renderMap.put(s, System.currentTimeMillis());
                if (BlockUtils.getPlaceSide(placePos) != null && BlockUtils.place(placePos, block, rotate.get(), 50, true, true)) {
                    if (render.get()) renderMap.put(placePos, System.currentTimeMillis());
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    private List<Player> getTargets() {
        List<Entity> entities = new ArrayList<>();
        TargetUtils.getList(entities, entity -> {
            if (!(entity instanceof Player player) || entity == mc.player) return false;
            if (player.isSpectator() || !player.isAlive()) return false;
            if (!PlayerUtils.isWithin(entity, targetRange.get())) return false;
            if (!Friends.get().shouldAttack(player)) return false;
            return EntityUtils.getGameMode(player) == GameType.SURVIVAL;
        }, priority.get(), Integer.MAX_VALUE);

        List<Player> players = new ArrayList<>(entities.size());
        for (Entity entity : entities) {
            if (entity instanceof Player p) players.add(p);
        }
        return players;
    }

    private boolean isBlockedByOtherEntity(BlockPos pos, Player allowed) {
        AABB checkBox = new AABB(pos);
        List<Entity> entities = mc.level.getEntities(null, checkBox);
        for (Entity entity : entities) {
            if (entity == allowed) continue;
            if (!entity.isSpectator() && entity.isAlive()) return true;
        }
        return false;
    }

    private boolean isOutOfRange(BlockPos blockPos) {
        Vec3 pos = Vec3.atCenterOf(blockPos);
        return !PlayerUtils.isWithin(pos, placeRange.get());
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get()) return;

        for (BlockPos pos : placePositions) {
            event.renderer.box(pos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }

        if (renderMap.isEmpty()) return;
        renderMap.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue() > fadeTime.get() * 1000);
        renderMap.forEach((pos, time) -> {
            double alive = System.currentTimeMillis() - time;
            double progress = 1.0 - Mth.clamp(alive / (fadeTime.get() * 1000), 0.0, 1.0);

            SettingColor sColor = new SettingColor(sideColor.get().r, sideColor.get().g, sideColor.get().b, (int) (sideColor.get().a * progress));
            SettingColor lColor = new SettingColor(lineColor.get().r, lineColor.get().g, lineColor.get().b, (int) (lineColor.get().a * progress));

            event.renderer.box(pos, sColor, lColor, shapeMode.get(), 0);
        });
    }

    private int getGapOffsetX(Player t) {
        return switch (gapSide.get()) {
            case East -> 1;
            case West -> -1;
            case South, North, None, TowardPlayer -> 0;
        };
    }

    private int getGapOffsetZ(Player t) {
        return switch (gapSide.get()) {
            case South -> 1;
            case North -> -1;
            case East, West, None, TowardPlayer -> 0;
        };
    }
}

