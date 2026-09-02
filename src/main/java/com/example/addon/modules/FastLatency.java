package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;

public class FastLatency extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> delay = sgGeneral.add(new DoubleSetting.Builder()
        .name("delay")
        .description("The amount of milliseconds to wait before resolving your ping again.")
        .defaultValue(100.0)
        .min(0.0)
        .sliderMax(1000.0)
        .build()
    );

    private final Setting<Boolean> spikeNotifier = sgGeneral.add(new BoolSetting.Builder()
        .name("spike-notifier")
        .description("Notifies you in chat whenever your ping spikes.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> threshold = sgGeneral.add(new DoubleSetting.Builder()
        .name("threshold")
        .description("The amount of milliseconds your ping has to increase by before notifying you.")
        .defaultValue(30.0)
        .min(0.0)
        .sliderMax(1000.0)
        .visible(spikeNotifier::get)
        .build()
    );

    private long lastSentTime = 0L;
    private long lastReceivedTime = 0L;
    private long requestTime = 0L;
    private int latency = 0;

    public FastLatency() {
        super(AddonTemplate.CATEGORY, "fast-latency", "Forces the server to update and resolve your ping much faster.");
    }

    @Override
    public void onActivate() {
        latency = 0;
        lastSentTime = 0L;
        lastReceivedTime = 0L;
        requestTime = 0L;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.getConnection() == null) return;

        long currentTime = System.currentTimeMillis();

        if (currentTime - lastReceivedTime >= 1000L && currentTime - lastSentTime >= delay.get().longValue()) {
            mc.getConnection().send(new ServerboundCommandSuggestionPacket(1000, "/w "));
            requestTime = currentTime;
            lastSentTime = currentTime;
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof ClientboundCommandSuggestionsPacket packet) {
            // Fixed: Replaced standard .getId() with modern record accessor field .id() method call
            if (packet.id() == 1000) {
                int ping = (int) (System.currentTimeMillis() - requestTime);

                if (spikeNotifier.get() && (ping - latency) > threshold.get().intValue()) {
                    ChatUtils.info("Your ping has spiked to (highlight)%dms(default) from (highlight)%dms(default)!", ping, latency);
                }

                latency = ping;
                lastReceivedTime = System.currentTimeMillis();
            }
        }
    }

    @Override
    public String getInfoString() {
        return latency + "ms";
    }
}

