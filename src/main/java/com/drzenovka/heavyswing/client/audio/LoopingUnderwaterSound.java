package com.drzenovka.heavyswing.client.audio;

import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSound;
import net.minecraft.util.ResourceLocation;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class LoopingUnderwaterSound extends PositionedSound implements ISound {

    public LoopingUnderwaterSound() {
        super(new ResourceLocation("heavyswing:underwater_ambience"));
        this.repeat = true;          // loop
        this.field_147665_h = 0;     // delay between loops
        this.volume = 0.45F;         // subtle volume
        this.field_147663_c = 0.5F;  // pitch
        this.xPosF = 0.0F;           // positional sound X (can stay 0)
        this.yPosF = 0.0F;           // positional sound Y
        this.zPosF = 0.0F;           // positional sound Z
        this.field_147666_i = ISound.AttenuationType.NONE; // no positional attenuation
    }
}
