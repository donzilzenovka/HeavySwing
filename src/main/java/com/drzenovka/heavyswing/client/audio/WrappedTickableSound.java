package com.drzenovka.heavyswing.client.audio;

import net.minecraft.client.audio.ITickableSound;

/** Retains the original sound's tick/update and completion lifecycle. */
public final class WrappedTickableSound extends WrappedSound implements ITickableSound {
    private final ITickableSound original;

    public WrappedTickableSound(ITickableSound original, float volume, float pitch) {
        super(original, volume, pitch);
        this.original = original;
    }

    @Override
    public void update() {
        original.update();
    }

    @Override
    public boolean isDonePlaying() {
        return original.isDonePlaying();
    }
}
