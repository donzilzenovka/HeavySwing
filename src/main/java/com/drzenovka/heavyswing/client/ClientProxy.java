package com.drzenovka.heavyswing.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraftforge.common.MinecraftForge;

import com.drzenovka.heavyswing.client.audio.HeavySwingSoundHandler;
import com.drzenovka.heavyswing.common.CommonProxy;
import com.drzenovka.heavyswing.common.HeavySwing;
import com.drzenovka.heavyswing.handler.HeavySwingHandler;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        HeavySwingHandler handler = new HeavySwingHandler();
        MinecraftForge.EVENT_BUS.register(handler);
        FMLCommonHandler.instance()
            .bus()
            .register(handler);

        // Changing to a later Forge event for greater stability if needed,
        // but for now, sticking to init() and trusting the inheritance fix.
        injectSoundHandler();

    }

    public static void injectSoundHandler() {
        try {
            Minecraft mc = Minecraft.getMinecraft();

            // Grab the existing SoundHandler
            SoundHandler original = ReflectionHelper
                .getPrivateValue(Minecraft.class, mc, "mcSoundHandler", "field_147126_aw");

            // Wrap it
            SoundHandler wrapped = new HeavySwingSoundHandler(original);

            // Replace the private field
            ReflectionHelper.setPrivateValue(Minecraft.class, mc, wrapped, "mcSoundHandler", "field_147126_aw");

            HeavySwing.LOG.info("[HeavySwing] Injected custom SoundHandler successfully!");
        } catch (Exception e) {
            HeavySwing.LOG.error("[HeavySwing] Failed to inject SoundHandler", e);
        }
    }
}
