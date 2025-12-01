package com.drzenovka.heavyswing.handler;

import com.drzenovka.heavyswing.config.Config;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.client.audio.PositionedSoundRecord;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class HeavySwingHandler {

    private final Minecraft mc = Minecraft.getMinecraft();
    private static String activeStepPrefix = null;

    private boolean clickPending = false;
    private static int localSwingTick = 0;
    private boolean strikeSoundPlayed = false;

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!Config.enableHeavySwingAnimation) return;
        if (event.phase != TickEvent.Phase.END) return;
        if (!event.player.worldObj.isRemote) return;
        if (mc.thePlayer != event.player) return;

        EntityPlayer player = mc.thePlayer;
        ItemStack held = player.getHeldItem();

        if (held == null) {
            clickPending = false;
            localSwingTick = 0;
            strikeSoundPlayed = false;
            return;
        }

        Class<?>[] allowedClasses = { ItemPickaxe.class, ItemAxe.class, ItemHoe.class, ItemSpade.class };
        boolean valid = false;
        for (Class<?> clazz : allowedClasses) {
            if (clazz.isInstance(held.getItem())) {
                valid = true;
                break;
            }
        }

        if (!valid) {
            clickPending = false;
            localSwingTick = 0;
            strikeSoundPlayed = false;
            return;
        }

        // Detect new click
        boolean attackPressed = mc.gameSettings.keyBindAttack.getIsKeyPressed();
        if (attackPressed && !clickPending && localSwingTick == 0) {
            clickPending = true;
            localSwingTick = 1; // start swing
            strikeSoundPlayed = false; // reset for new swing
        }

        // Animate swing only if active
        if (localSwingTick > 0) {
            localSwingTick++;

            // Map localSwingTick to swingProgress 0.0–1.0
            float progress;
            // total swing duration
            int maxSwingTicks = 12;
            if (localSwingTick <= 3) {
                // Fast initial strike (0–0.25)
                progress = localSwingTick / 3f * 0.25f;
            } else if (localSwingTick <= maxSwingTicks) {
                // Slow follow-through (0.25–1.0)
                progress = 0.25f + (localSwingTick - 3) / (float) (maxSwingTicks - 3) * 0.75f;
            } else {
                // Swing finished
                progress = 0f;
                localSwingTick = 0;
                clickPending = false;
                strikeSoundPlayed = false;
            }

            // Override vanilla swingProgress for the local player
            player.swingProgress = progress;
            player.prevSwingProgress = progress; // smooth interpolation

            if (!strikeSoundPlayed && localSwingTick == 3) { // second tick = impact
                World world = player.worldObj;
                double reach = 4.5D; // normal player reach
                MovingObjectPosition mop = player.rayTrace(reach, 1.0F);

                if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                    int x = mop.blockX;
                    int y = mop.blockY;
                    int z = mop.blockZ;

                    Block block = world.getBlock(x, y, z);
                    // --- LOCATE THIS SECTION (inside the block!=null check) ---
                    if (block != null) {
                        activeStepPrefix = "step." + block.stepSound.soundName;

                        // ... (local variables removed, which is good) ...
                        // ... (the old player.playSound() line was here) ...

                        // ... REPLACE THE AUDIO CALL WITH THIS ...

                        // 1. Get the raw sound name and default properties from the block's step sound
                        String soundName = block.stepSound.getBreakSound();
                        float defaultVolume = block.stepSound.getVolume();
                        float defaultPitch = block.stepSound.getPitch();
                        ResourceLocation soundLocation = new ResourceLocation(soundName);

                        // 2. Create the PositionedSoundRecord using the String name.
                        // This constructor is designed for sounds registered in the SoundRegistry.
                        PositionedSoundRecord strikeSound = new PositionedSoundRecord(
                            soundLocation,         // Use the String sound name
                            defaultVolume,     // Pass default volume
                            defaultPitch,      // Pass default pitch
                            (float) (x + 0.5), // Sound source X
                            (float) (y + 0.5), // Sound source Y
                            (float) (z + 0.5)  // Sound source Z
                        );

                        // 3. Output the call to the SoundHandler (your interceptor)
                        mc.getSoundHandler().playSound(strikeSound);

                        // ... (rest of the code continues) ...
                    }
                }
                strikeSoundPlayed = true;
            }
        }
        // At the end of swing (reset)
        if (localSwingTick == 0) {
            activeStepPrefix = null;
        }
    }

    public static boolean isSwingActive() {
        return localSwingTick > 0;
    }

    public static boolean isStepBlocked(String stepSound) {
        if (activeStepPrefix == null) return false;
        return stepSound.startsWith(activeStepPrefix);
    }
    /*
    private float getDistanceVolume(double soundX, double soundY, double soundZ, double listenerX, double listenerY, double listenerZ) {
        double dx = soundX - listenerX;
        double dy = soundY - listenerY;
        double dz = soundZ - listenerZ;
        double distanceSq = dx*dx + dy*dy + dz*dz;

        double maxDistance = 8.0; // blocks, adjust as needed
        if (distanceSq > maxDistance * maxDistance) return 0f;

        // Simple linear attenuation
        float factor = 1.0f - (float)(Math.sqrt(distanceSq) / maxDistance);
        return Math.max(factor, 0f);
    }

     */

}
