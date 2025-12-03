package com.drzenovka.heavyswing.handler;

import com.drzenovka.heavyswing.config.Config;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;

public class SwimHandler {

    @SubscribeEvent
    public void onPlayerUpdate(LivingUpdateEvent event) {
        if (!(event.entity instanceof EntityPlayer)) return;
        if (!Config.enableSwimmingMechanic) return;

        EntityPlayer player = (EntityPlayer) event.entity;

        if (player.isInWater()) {

            boolean sneaking = player.isSneaking();
            boolean wearingArmor = isWearingHeavyArmor(player);

            // Determine if player's EYES are still inside water
            int x = (int) Math.floor(player.posX);
            int y = (int) Math.floor(player.posY + player.getEyeHeight() - 0.15f);
            int z = (int) Math.floor(player.posZ);

            boolean eyesInWater = player.worldObj.getBlock(x, y, z) == Blocks.water;

            // If sneaking or wearing armor → sink normally
            if (sneaking || wearingArmor) return;

            // Apply buoyancy ONLY while fully submerged
            if (eyesInWater) {

                double targetSpeed = 0.02;   // gentle upward speed 0.02 + 0.02 neutral buoyancy
                double accel       = 0.025;   // smooth approach

                if (player.motionY < targetSpeed) {
                    player.motionY += accel;
                    if (player.motionY > targetSpeed)
                        player.motionY = targetSpeed;
                }
            }
            // else: at surface — stop rising
        }
    }

    private boolean isWearingHeavyArmor(EntityPlayer player) {

        for (ItemStack stack : player.inventory.armorInventory) {
            if (stack == null) continue;

            String combined = "";

            // Unlocalized name from stack
            if (stack.getUnlocalizedName() != null)
                combined += stack.getUnlocalizedName().toLowerCase();

            // Unlocalized name from Item
            if (stack.getItem() != null && stack.getItem().getUnlocalizedName() != null)
                combined += " " + stack.getItem().getUnlocalizedName().toLowerCase();

            // Registry name
            try {
                GameRegistry.UniqueIdentifier id =
                    GameRegistry.findUniqueIdentifierFor(stack.getItem());
                if (id != null) {
                    combined += " " + (id.modId + ":" + id.name).toLowerCase();
                }
            } catch (Throwable ignored) {}

            // Check against config-defined keywords
            for (String keyword : Config.heavyArmorKeywords) {
                if (combined.contains(keyword.toLowerCase())) {
                    return true; // armor is “heavy”
                }
            }
        }

        return false;
    }


}
