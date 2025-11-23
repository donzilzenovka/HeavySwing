package com.drzenovka.heavyswing.client.audio;

import com.drzenovka.heavyswing.handler.HeavySwingHandler;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSound;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.audio.SoundManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ResourceLocation;
// Removed unused import net.minecraft.util.Vec3

import com.drzenovka.heavyswing.common.HeavySwing;

import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * SoundHandler wrapper that intercepts all sound requests.
 * CRITICAL: Uses reflection to substitute the newly created (and inactive) SoundManager
 * with the original, running SoundManager instance from Minecraft.
 */
public class HeavySwingSoundHandler extends SoundHandler {

    /**
     * Constructor performs the necessary reflection and substitution.
     */
    public HeavySwingSoundHandler(SoundHandler originalHandler) {
        // 1. Call super(), which correctly sets mcResourceManager but incorrectly creates a new, inactive sndManager.
        super(getResourceManager(originalHandler), getGameSettings(originalHandler));

        // 2. Get the original, running SoundManager instance from the old handler.
        // Names: "sndManager" (MCP), "field_147694_f" (SRG)
        SoundManager originalManager = ReflectionHelper
            .getPrivateValue(SoundHandler.class, originalHandler, "sndManager", "field_147694_f");

        if (originalManager == null) {
            HeavySwing.LOG.error("CRITICAL FAILURE: Original SoundManager reflection returned null. Sounds will not play.");
            return;
        }

        // 3. Inject the original, running SoundManager into 'this' instance,
        // replacing the inactive SoundManager that was created by the super() call.
        try {
            ReflectionHelper.setPrivateValue(SoundHandler.class, this, originalManager, "sndManager", "field_147694_f");
            HeavySwing.LOG.info("SoundManager substitution successful. Audio delegation should now work.");
        } catch (Exception e) {
            HeavySwing.LOG.error("CRITICAL FAILURE: Could not inject original SoundManager. Sounds will likely not play.", e);
            e.printStackTrace();
        }
    }

    // --- Helper methods to fetch constructor arguments (using verified names) ---

    private static IResourceManager getResourceManager(SoundHandler handler) {
        // Names: "mcResourceManager" (MCP), "field_147695_g" (SRG)
        IResourceManager mgr = ReflectionHelper
            .getPrivateValue(SoundHandler.class, handler, "mcResourceManager", "field_147695_g");
        if (mgr == null) {
            throw new IllegalStateException("Failed to get IResourceManager from SoundHandler");
        }
        return mgr;
    }

    private static GameSettings getGameSettings(SoundHandler handler) {
        // 1. Get SoundManager from SoundHandler (Names: "sndManager", "field_147694_f")
        SoundManager sndManager = ReflectionHelper
            .getPrivateValue(SoundHandler.class, handler, "sndManager", "field_147694_f");
        if (sndManager == null) {
            throw new IllegalStateException("Failed to get SoundManager from SoundHandler (required for GameSettings lookup)");
        }

        // 2. Get GameSettings from SoundManager (Names: "options", "field_78903_e")
        GameSettings settings = ReflectionHelper
            .getPrivateValue(SoundManager.class, sndManager, "options", "field_78903_e");
        if (settings == null) {
            throw new IllegalStateException("Failed to get GameSettings from SoundManager");
        }
        return settings;
    }

    // --- Interception Logic ---

    /**
     * Intercepts all sound playback calls.
     */
    @Override
    public void playSound(ISound sound) {
        if (sound == null) {
            super.playSound(null);
            return;
        }

        // Perform logging
        ResourceLocation soundLocation = sound.getPositionedSoundLocation();
        String logMessage = "[HeavySwing Intercept] Sound: " + soundLocation.getResourcePath();

        // Check if sound is positional and log details
        if (sound instanceof PositionedSound) {
            PositionedSound ps = (PositionedSound) sound;

            String name = ps.getPositionedSoundLocation().getResourcePath();

            // If we're in a swing, block step sounds
            if (name.startsWith("step.")) {
                if (HeavySwingHandler.isSwingActive()) {
                    // skip playing this sound
                    return;
                }
            }

            logMessage += String.format(
                " @ Pos(%.2f, %.2f, %.2f), Vol: %.2f, Pitch: %.2f",
                ps.getXPosF(),
                ps.getYPosF(),
                ps.getZPosF(),
                ps.getVolume(),
                ps.getPitch()
            );
        } else {
            logMessage += " (Unpositioned)";
        }

        HeavySwing.LOG.info(logMessage);

        // Forward the call to the original sound handler (which now uses the injected, running SoundManager)
        super.playSound(sound);
    }
}
