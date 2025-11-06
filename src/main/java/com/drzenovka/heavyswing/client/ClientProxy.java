package com.drzenovka.heavyswing.client;

import com.drzenovka.heavyswing.CommonProxy;
import com.drzenovka.heavyswing.HeavySwingHandler;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.common.MinecraftForge;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {

        super.init(event);

        if (FMLCommonHandler.instance().getSide().isClient()) {
            MinecraftForge.EVENT_BUS.register(new HeavySwingHandler());
            FMLCommonHandler.instance().bus().register(new HeavySwingHandler());
        }
    }
}
