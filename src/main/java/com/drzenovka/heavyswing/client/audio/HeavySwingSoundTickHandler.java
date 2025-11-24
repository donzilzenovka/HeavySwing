package com.drzenovka.heavyswing.client.audio;

import com.drzenovka.heavyswing.common.HeavySwing;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundHandler;

// Note: Ensure your LoopingUnderwaterSound class is accessible here.
// You will need the SoundLoopHelper.LoopingUnderwaterSound class from before.

public class HeavySwingSoundTickHandler {

    private final HeavySwingSoundHandler soundHandler;
    private ISound underwaterLoop = null; // Stored here, managed by this handler
    private boolean playerUnderwaterStatus = false;

    // The constructor takes the actual injected SoundHandler instance
    public HeavySwingSoundTickHandler(HeavySwingSoundHandler handler) {
        this.soundHandler = handler;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;

        // Use the injected handler's reference to the game's SoundHandler
        SoundHandler sh = mc.getSoundHandler();

        // Check player's current submerged status
        boolean isCurrentlyUnderwater = mc.thePlayer.isInsideOfMaterial(net.minecraft.block.material.Material.water);

        // --- START the sound loop ---
        if (isCurrentlyUnderwater && !this.playerUnderwaterStatus) {

            // 1. Instantiate the custom looping sound if needed
            if (this.underwaterLoop == null) {
                this.underwaterLoop = new LoopingUnderwaterSound();
            }

            // 2. Start the sound
            if (!sh.isSoundPlaying(this.underwaterLoop)) {
                sh.playSound(this.underwaterLoop);
                HeavySwing.LOG.info("[HeavySwing] Starting underwater ambience loop (Dedicated Handler).");
            }
            this.playerUnderwaterStatus = true;
        }

        // --- STOP the sound loop ---
        else if (!isCurrentlyUnderwater && this.playerUnderwaterStatus) {
            if (this.underwaterLoop != null) {
                sh.stopSound(this.underwaterLoop);
            }
            HeavySwing.LOG.info("[HeavySwing] Stopping underwater ambience loop (Dedicated Handler).");
            this.playerUnderwaterStatus = false;
        }
    }
}
