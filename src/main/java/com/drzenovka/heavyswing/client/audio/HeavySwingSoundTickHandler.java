package com.drzenovka.heavyswing.client.audio;

import com.drzenovka.heavyswing.common.HeavySwing;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSound;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.util.JsonException;

public class HeavySwingSoundTickHandler {

    private final HeavySwingSoundHandler soundHandler;
    private ISound underwaterLoop = null; // Looping sound instance
    private ShaderGroup underwaterShader = null;
    private static final ResourceLocation SHADER_LOCATION =
        new ResourceLocation("minecraft:shaders/post/blobs2.json");

    public HeavySwingSoundTickHandler(HeavySwingSoundHandler handler) {
        this.soundHandler = handler;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;

        boolean isCurrentlyUnderwater = mc.thePlayer.isInsideOfMaterial(net.minecraft.block.material.Material.water);

        // --- ENTER WATER ---
        if (isCurrentlyUnderwater && underwaterLoop == null) {

            // Load shader if not already loaded
            if (underwaterShader == null) {
                try {
                    underwaterShader = new ShaderGroup(
                        mc.getTextureManager(),
                        mc.getResourceManager(),
                        mc.getFramebuffer(),
                        SHADER_LOCATION
                    );
                    underwaterShader.createBindFramebuffers(mc.displayWidth, mc.displayHeight);
                } catch (JsonException e) {
                    throw new RuntimeException(e);
                }
            }

            mc.entityRenderer.theShaderGroup = underwaterShader;

            // Create the looping sound and pass it to the sound handler
            underwaterLoop = new LoopingUnderwaterSound();
            soundHandler.playExternalSound(underwaterLoop);

            HeavySwing.LOG.info("[HeavySwing] Starting underwater ambience loop.");
        }

        // --- EXIT WATER ---
        else if (!isCurrentlyUnderwater && underwaterLoop != null) {

            // Stop the sound via HeavySwingSoundHandler
            soundHandler.stopExternalSound(underwaterLoop);
            underwaterLoop = null;

            // Remove shader
            if (underwaterShader != null) {
                try { underwaterShader.deleteShaderGroup(); } catch (Exception ignored) {}
                mc.entityRenderer.theShaderGroup = null;
                underwaterShader = null;
            }

            HeavySwing.LOG.info("[HeavySwing] Stopping underwater ambience loop.");
        }
    }

    private static class LoopingUnderwaterSound extends PositionedSound {
        public LoopingUnderwaterSound() {
            super(new ResourceLocation("heavyswing:underwater_ambience"));
            this.repeat = true;
            this.field_147665_h = 0;
            this.volume = 0.45F;
            this.field_147663_c = 0.5F;
            this.xPosF = 0; this.yPosF = 0; this.zPosF = 0;
            this.field_147666_i = ISound.AttenuationType.NONE;
        }
    }
}


