package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public class TotemPopCounter extends Module {
    public TotemPopCounter() {
        super(AddonTemplate.CATEGORY, "totem-pop-counter", "Logs pop metrics cleanly directly into local chat channel.");
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null || mc.level == null) return;
        if (event.packet instanceof ClientboundEntityEventPacket packet && packet.getEventId() == 35) {
            Entity entity = packet.getEntity(mc.level);
            if (entity instanceof Player player && player != mc.player) {
                ChatUtils.info("(highlight)" + player.getName().getString() + "(default) popped a totem!");
            }
        }
    }
}
