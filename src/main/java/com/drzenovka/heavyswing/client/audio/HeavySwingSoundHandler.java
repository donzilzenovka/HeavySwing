package com.drzenovka.heavyswing.client.audio;

import com.drzenovka.heavyswing.handler.HeavySwingHandler;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSound;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.audio.SoundManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;

import com.drzenovka.heavyswing.common.HeavySwing;

import cpw.mods.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

/**
 * SoundHandler wrapper that intercepts all sound requests.
 * CRITICAL: Uses reflection to substitute the newly created (and inactive) SoundManager
 * with the original, running SoundManager instance from Minecraft.
 */
public class HeavySwingSoundHandler extends SoundHandler {
    boolean logDebug = true;
    boolean noOrigin;

    // --- New Fields for Occlusion Cache ---
    private Long lastOcclusionCheckTime = 0L;
    private float cachedOcclusionFactor = 1.0f;
    private static final long OCCLUSION_CACHE_DURATION_MS = 50; // Check every 50ms (every 1-2 ticks)

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
        final Minecraft mc = Minecraft.getMinecraft();
        boolean inNether = mc.theWorld != null && mc.theWorld.provider.dimensionId == -1;
        boolean markUnplayable = false;

        if (sound == null) {
            super.playSound(null);
            return;
        }



        // Perform logging
        ResourceLocation soundLocation = sound.getPositionedSoundLocation();

        if ("heavyswing:underwater".equals(soundLocation.getResourcePath())) {
            super.playSound(sound);
            return; // Bypasses all volume/pitch/occlusion modifications
        }

        String logMessage = "[HeavySwing Intercept] Sound: " + soundLocation.getResourcePath();
        String logVolumeData = "";

        // Check if sound is positional and log details
        if (mc.thePlayer != null && sound instanceof PositionedSound ps) {

            float psDefaultVolume = ps.getVolume();
            float psDefaultPitch = ps.getPitch();

            double sx = ps.getXPosF();
            double sy = ps.getYPosF();
            double sz = ps.getZPosF();

            double px = mc.thePlayer.posX;
            double py = mc.thePlayer.posY;
            double pz = mc.thePlayer.posZ;

            float distanceFactor = getDistanceVolume(sx, sy, sz, px, py, pz);
            float occlusionFactor = getOcclusionFactorCached(sx, sy, sz, px, py, pz);
            //float occlusionFactor = getOcclusionFactor(sx, sy, sz, px, py, pz);
            if (distanceFactor <= 0f) markUnplayable = true; // skip inaudible sounds

            float underwaterFactor = 1f;
            underwaterFactor = getSubmergedVolumeFactor(ps);

            float finalVolume = ps.getVolume()
                * distanceFactor
                * occlusionFactor
                * underwaterFactor;

            logVolumeData = ("distance:" + distanceFactor + ", occulsion: " + occlusionFactor + ", underwater: " + underwaterFactor);
            if (finalVolume <= 0.0f) markUnplayable = true;

            setSoundVolume(ps, finalVolume);
            //if (finalVolume < 0.01f && !mc.thePlayer.isInsideOfMaterial(net.minecraft.block.material.Material.air)) markUnplayable = true;

            //float finalPitch = ps.getPitch() - (distanceFactor < 1f ? (0.5f * (1f - distanceFactor)) : 0f);

            float finalPitch;
            if(mc.thePlayer.isInsideOfMaterial(net.minecraft.block.material.Material.water)){
                finalPitch = ps.getPitch() - (0.9f);
            } else {
                finalPitch = ps.getPitch() - (distanceFactor < 1f ? (0.2f * (1f - distanceFactor)) : 0f);
            }

            setSoundPitch(ps, finalPitch);

            noOrigin = (ps.getXPosF() == ps.getYPosF() && ps.getYPosF() == ps.getZPosF());

            String name = ps.getPositionedSoundLocation().getResourcePath();
            // If we're in a swing, block step sounds
            if (name.startsWith("step.") && HeavySwingHandler.isStepBlocked((name))) {
                if (HeavySwingHandler.isSwingActive()) {
                    // skip playing this sound
                    markUnplayable = true;
                }
            } else if (name.startsWith("game.player.hurt")
                    || (name.startsWith("portal.portal"))
                    || (name.startsWith("underwater_ambience"))){
                setSoundVolume(ps, psDefaultVolume);
                setSoundPitch(ps, psDefaultPitch);
                markUnplayable = false;
            } else if (noOrigin) {
                setSoundVolume(ps, psDefaultVolume);
                setSoundPitch(ps, psDefaultPitch);
                markUnplayable = false;
            }else if (inNether){
                setSoundVolume(ps, ps.getVolume() - 0.1f);
                setSoundPitch(ps, ps.getPitch() - 0.4f);
            }else if (underwaterFactor < 1.0f){
                if(name.startsWith("game.player.swim")){
                    setSoundVolume(ps, psDefaultVolume);
                    setSoundPitch(ps, psDefaultPitch - 0.3f);
                    markUnplayable = false;
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
        if(markUnplayable){
            logMessage += " (Muted)";
        }
        if(noOrigin){
            logMessage += " (NoOrigin)";
        }
        if (logDebug) {
            HeavySwing.LOG.info(logMessage);
            HeavySwing.LOG.info(logVolumeData);
        }


        // Forward the call to the original sound handler (which now uses the injected, running SoundManager)
        if(!markUnplayable) {
            super.playSound(sound);

        }
    }

    //Improved inverse square
    private float getDistanceVolume(double sx, double sy, double sz, double px, double py, double pz) {

        double dx = sx - px;
        double dy = sy - py;
        double dz = sz - pz;
        double distance = Math.sqrt(dx*dx + dy*dy + dz*dz);

        double minDistance = 1.0;     // sounds close to ear stay full volume
        double maxDistance = 16.0;    // slightly longer reach

        if (distance >= maxDistance) return 0f;
        if (distance <= minDistance) return 1f;

        // Smooth attenuation: inverse square but clamped to 0–1 range
        double norm = (distance - minDistance) / (maxDistance - minDistance);
        double attenuation = 1.0 - (norm * norm); // squared curve for smoothness

        return (float)Math.max(attenuation, 0f);
    }

    public static void setSoundVolume(PositionedSound sound, float volume) {
        try {
            Field volumeField = PositionedSound.class.getDeclaredField("volume");
            volumeField.setAccessible(true);
            volumeField.setFloat(sound, volume);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //change pitch based on distance
    public static void setSoundPitch(PositionedSound sound, float pitch) {
        try {
            Field pitchField = PositionedSound.class.getDeclaredField("field_147663_c");
            pitchField.setAccessible(true);
            pitchField.setFloat(sound, pitch);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private float getOcclusionFactorCached(double sx, double sy, double sz, double px, double py, double pz) {
        long currentTime = System.currentTimeMillis();

        // If the cache is fresh, return the last computed value
        if (currentTime - lastOcclusionCheckTime < OCCLUSION_CACHE_DURATION_MS) {
            return cachedOcclusionFactor;
        }

        // Cache expired, re-calculate
        cachedOcclusionFactor = getOcclusionFactor(sx, sy, sz, px, py, pz);
        lastOcclusionCheckTime = currentTime;

        return cachedOcclusionFactor;
    }

    private float getOcclusionFactor(double sx, double sy, double sz, double px, double py, double pz) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return 1f;

        final int steps = 20;
        int solidCount = 0;
        Set<String> counted = new HashSet<>();

        double dx = (sx - px) / steps;
        double dy = (sy - py) / steps;
        double dz = (sz - pz) / steps;

        // Start slightly outside player to avoid counting self-block
        double cx = px + 0.001;
        double cy = py + mc.thePlayer.getEyeHeight() + 0.001;
        double cz = pz + 0.001;

        for (int i = 0; i < steps; i++) {
            cx += dx;
            cy += dy;
            cz += dz;

            int bx = (int)Math.floor(cx);
            int by = (int)Math.floor(cy);
            int bz = (int)Math.floor(cz);

            String key = bx + "," + by + "," + bz;
            if (!mc.theWorld.isAirBlock(bx, by, bz) && !counted.contains(key)) {
                solidCount++;
                counted.add(key);
            }

            if (solidCount > 6) break;
        }
        solidCount = Math.max(0, solidCount - 1);

        return switch (solidCount) {
            case 0 -> 1.00f; // open
            case 1 -> 0.55f; // single obstruction
            case 2 -> 0.35f; // muffled
            case 3 -> 0.15f; // strongly occluded
            case 4 -> 0.05f; // almost silent
            default -> 0.00f; // fully blocked
        };
    }

    private float getSubmergedVolumeFactor(PositionedSound ps) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return 1f;

        // Simplify listener check to only care about water
        boolean listenerInWater = mc.thePlayer.isInsideOfMaterial(net.minecraft.block.material.Material.water);

        // This line is often redundant if listenerInWater is true,
        // but keep it for completeness if a sound plays from water when the player is outside.
        boolean sourceInWater = isInBlock(Blocks.water, ps.getXPosF(), ps.getYPosF() + 1, ps.getZPosF());

        if (!sourceInWater && !listenerInWater) return 1f;

        return 0.25f;
    }

    private boolean isInBlock(Block blockToCheck, double x, double y, double z) {
        Minecraft mc = Minecraft.getMinecraft();
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);

        net.minecraft.block.Block block = mc.theWorld.getBlock(bx, by, bz);
        return block == blockToCheck;
    }

}
