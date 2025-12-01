package com.drzenovka.heavyswing.client;

import com.drzenovka.heavyswing.client.audio.HeavySwingSoundTickHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;

import com.drzenovka.heavyswing.client.audio.HeavySwingSoundHandler;
import com.drzenovka.heavyswing.common.CommonProxy;
import com.drzenovka.heavyswing.common.HeavySwing;
import com.drzenovka.heavyswing.handler.HeavySwingHandler;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        HeavySwingHandler handler = new HeavySwingHandler();
        MinecraftForge.EVENT_BUS.register(handler);
        FMLCommonHandler.instance()
            .bus()
            .register(handler);

        injectSoundHandler();

    }

    public static void injectSoundHandler() {
        try {
            Minecraft mc = Minecraft.getMinecraft();

            SoundHandler original = null;
            Field targetField = null;

            for (Field f : Minecraft.class.getDeclaredFields()) {
                f.setAccessible(true);
                if (SoundHandler.class.isAssignableFrom(f.getType())) {
                    original = (SoundHandler) f.get(mc);
                    targetField = f;
                    break;
                }
            }
            HeavySwingSoundHandler wrapped = new HeavySwingSoundHandler(original);

            targetField.set(mc, wrapped);
            HeavySwingSoundTickHandler tickHandler = new HeavySwingSoundTickHandler(wrapped); // <-- CORRECTED LINE

            MinecraftForge.EVENT_BUS.register(tickHandler);
            FMLCommonHandler.instance().bus().register(tickHandler);

            HeavySwing.LOG.info("[HeavySwing] Injected custom SoundHandler successfully and registered Tick Handler.");
        } catch (Exception e) {
            HeavySwing.LOG.error("[HeavySwing] Failed to inject SoundHandler", e);
        }
    }
}
