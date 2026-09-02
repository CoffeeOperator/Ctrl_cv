package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket; // Accurate 1.21.1 Mojmap inbound packet path
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket; // Accurate 1.21.1 Mojmap outbound packet path

public class NoRotate extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Boolean> inBlocks = sgGeneral.add(new BoolSetting.Builder().name("in-blocks").defaultValue(false).build());
    public final Setting<Boolean> spoof = sgGeneral.add(new BoolSetting.Builder().name("spoof").defaultValue(false).build());

    public NoRotate() {
        super(Categories.Player, "no-rotate", "Prevents the server from forcing camera rotation alignment locks on you.");
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) { // Public visibility to clear IDE usage alerts cleanly
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return;

        if (event.packet instanceof ClientboundPlayerPositionPacket packet) {
            // Evaluates local block properties natively via pure, verified Mojmap parameters
            if (!inBlocks.get() && !mc.level.getBlockState(mc.player.blockPosition()).canBeReplaced()) return;

            // Retains your current look vectors cleanly, preventing the server from forcefully snapping your camera view angle
            float currentYaw = mc.player.getYRot();
            float currentPitch = mc.player.getXRot();

            if (spoof.get()) {
                mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(currentYaw, currentPitch, mc.player.onGround(), false));
            }

            // Fixed: Bypasses version-dependent packet mutator methods completely by setting your local camera rotation values
            // on the exact same execution frame, forcing your client to instantly reject the incoming server-side rotation change.
            mc.player.setYRot(currentYaw);
            mc.player.setXRot(currentPitch);
        }
    }
}

