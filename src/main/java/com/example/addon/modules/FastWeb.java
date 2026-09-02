package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import java.util.Comparator;

public class FastWeb extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> speed = sgGeneral.add(new IntSetting.Builder()
        .name("webs-per-tick")
        .defaultValue(2)
        .min(1)
        .sliderMax(5)
        .build()
    );

    private final Setting<Boolean> doubles = sgGeneral.add(new BoolSetting.Builder()
        .name("doubles")
        .defaultValue(true)
        .build()
    );

    public FastWeb() {
        super(AddonTemplate.CATEGORY, "fast-web", "Places cobwebs at the feet and head of the nearest enemy player.");
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        FindItemResult web = InvUtils.findInHotbar(Items.COBWEB);
        if (!web.found()) return;

        // Finds the closest enemy player within 5 blocks who is not a friend
        Player target = mc.level.players().stream()
            .filter(p -> p != mc.player && p.isAlive() && !Friends.get().isFriend(p))
            .filter(p -> mc.player.distanceTo(p) <= 5.0f)
            .min(Comparator.comparingDouble(p -> mc.player.distanceToSqr(p)))
            .orElse(null);

        if (target == null) return;

        BlockPos enemyFeet = target.blockPosition();
        int placed = 0;

        // Places at enemy feet position
        if (mc.level.getBlockState(enemyFeet).canBeReplaced() && placed < speed.get()) {
            if (BlockUtils.place(enemyFeet, web, false, 50, true, false)) {
                placed++;
            }
        }

        // Places at enemy head position
        if (doubles.get() && placed < speed.get()) {
            BlockPos enemyHead = enemyFeet.above();
            if (mc.level.getBlockState(enemyHead).canBeReplaced()) {
                BlockUtils.place(enemyHead, web, false, 50, true, false);
            }
        }
    }
}
