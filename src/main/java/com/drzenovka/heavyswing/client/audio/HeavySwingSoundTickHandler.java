package com.drzenovka.heavyswing.client.audio;

import com.drzenovka.heavyswing.common.HeavySwing;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundHandler;

import net.minecraft.client.renderer.EntityRenderer; // Used to find the shader field
import net.minecraft.client.shader.ShaderGroup; // The object that holds the shader data
import java.lang.reflect.Field;

import net.minecraft.client.util.JsonException;
import net.minecraft.util.ResourceLocation; // Used for loading the JSON

// Note: Ensure your LoopingUnderwaterSound class is accessible here.
// You will need the SoundLoopHelper.LoopingUnderwaterSound class from before.

public class HeavySwingSoundTickHandler {

    private final HeavySwingSoundHandler soundHandler;
    private ISound underwaterLoop = null; // Stored here, managed by this handler
    private boolean playerUnderwaterStatus = false;

    private static Field shaderGroupField;
    private ShaderGroup underwaterShader = null;
    private static final ResourceLocation SHADER_LOCATION =
        new ResourceLocation("minecraft:shaders/post/blobs2.json");

    // The constructor takes the actual injected SoundHandler instance
    public HeavySwingSoundTickHandler(HeavySwingSoundHandler handler) {
        this.soundHandler = handler;
    }

    private static Field getShaderGroupField() throws NoSuchFieldException {
        if (shaderGroupField == null) {
            // SRG/Obfuscated name for the ShaderGroup field in EntityRenderer (Minecraft 1.7.10 / 1.8)
            shaderGroupField = ReflectionHelper.findField(EntityRenderer.class, "theShaderGroup", "field_147706_p");
        }
        return shaderGroupField;
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

            // 1. Load the shader
            try {
                // Only load the shader once
                if (this.underwaterShader == null) {
                    // The ShaderGroup constructor needs the Resource Manager and the active camera (mc.thePlayer)
                    this.underwaterShader = new ShaderGroup(
                        mc.getTextureManager(),
                        mc.getResourceManager(),
                        mc.getFramebuffer(),
                        SHADER_LOCATION
                    );
                    // Set the shader's initial projection matrix
                    this.underwaterShader.createBindFramebuffers(mc.displayWidth, mc.displayHeight);
                }
                // 2. ACTIVATE SHADER via Reflection
                mc.entityRenderer.theShaderGroup = this.underwaterShader;

                // 1. Instantiate the custom looping sound if needed
                //if (this.underwaterLoop == null) {
                    this.underwaterLoop = new LoopingUnderwaterSound();
                //}

                // 2. Start the sound
                if (!sh.isSoundPlaying(this.underwaterLoop)) {
                    sh.playSound(this.underwaterLoop);
                    HeavySwing.LOG.info("[HeavySwing] Starting underwater ambience loop (Dedicated Handler).");
                }
                this.playerUnderwaterStatus = true;
            } catch (JsonException e) {
                throw new RuntimeException(e);
            }
        }

            // --- STOP the sound loop ---
        else if (!isCurrentlyUnderwater && this.playerUnderwaterStatus) {
            if (this.underwaterLoop != null) {
                sh.stopSound(this.underwaterLoop);
                this.underwaterLoop = null;
            }
                // 2. REMOVE SHADER via Reflection

                    mc.entityRenderer.theShaderGroup = null;

                    // Note: If you want to be extremely clean, you should call
                    // theShaderGroup.deleteShaderGroup() before setting it to null,
                    // but setting to null often suffices for older vanilla shaders.


            HeavySwing.LOG.info("[HeavySwing] Stopping underwater ambience loop (Dedicated Handler).");
            this.playerUnderwaterStatus = false;
        }
    }
}
