package com.drzenovka.heavyswing.handler;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

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

        Class<?>[] allowedClasses = { ItemPickaxe.class, ItemAxe.class, ItemHoe.class, ItemSword.class,
            ItemSpade.class };
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
                double reach = 5.0D; // normal player reach
                MovingObjectPosition mop = player.rayTrace(reach, 1.0F);

                if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                    int x = mop.blockX;
                    int y = mop.blockY;
                    int z = mop.blockZ;

                    Block block = world.getBlock(x, y, z);
                    if (block != null) {
                        activeStepPrefix = "step." + block.stepSound.soundName;
                        // Get block's step sound
                        float volume = block.stepSound.getVolume();
                        float pitch = block.stepSound.getPitch();

                        // Play sound at player's position
                        player.playSound(block.stepSound.getBreakSound(), volume, pitch);
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

}
