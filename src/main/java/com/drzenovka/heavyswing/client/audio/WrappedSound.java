package com.drzenovka.heavyswing.client.audio;

import net.minecraft.client.audio.ISound;
import net.minecraft.util.ResourceLocation;

import paulscode.sound.SoundSystemConfig;
import paulscode.sound.SoundSystemException;
import paulscode.sound.libraries.LibraryLWJGLOpenAL;

public class WrappedSound implements ISound {

    private final ISound original;
    private final float volume;
    private final float pitch;

    private static final Object OPENAL_LOCK = new Object();

    // attempt to stop second sound instance opening and crashing paulscode
    public void startSoundSystemSafely() throws SoundSystemException {
        synchronized (OPENAL_LOCK) {
            SoundSystemConfig.setNumberNormalChannels(0);
            SoundSystemConfig.addLibrary(LibraryLWJGLOpenAL.class);
        }
    }

    public WrappedSound(ISound original, float volume, float pitch) {
        this.original = original;
        this.volume = volume;
        this.pitch = pitch;
    }

    @Override
    public ResourceLocation getPositionedSoundLocation() {
        return original.getPositionedSoundLocation();
    }

    @Override
    public boolean canRepeat() {
        return original.canRepeat();
    }

    @Override
    public int getRepeatDelay() {
        return original.getRepeatDelay();
    }

    @Override
    public float getVolume() {
        return volume;
    }

    @Override
    public float getPitch() {
        return pitch;
    }

    @Override
    public float getXPosF() {
        return original.getXPosF();
    }

    @Override
    public float getYPosF() {
        return original.getYPosF();
    }

    @Override
    public float getZPosF() {
        return original.getZPosF();
    }

    @Override
    public AttenuationType getAttenuationType() {
        return original.getAttenuationType();
    }
}
