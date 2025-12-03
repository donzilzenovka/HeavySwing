package com.drzenovka.heavyswing.client.audio;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSound;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.audio.SoundEventAccessorComposite;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.audio.SoundManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;

import com.drzenovka.heavyswing.common.HeavySwing;
import com.drzenovka.heavyswing.config.Config;
import com.drzenovka.heavyswing.handler.HeavySwingHandler;
import net.minecraft.util.Vec3;

public class HeavySwingSoundHandler extends SoundHandler {

    private static final long OCCLUSION_CACHE_DURATION_MS = 50L; // check every ~50ms

    // Occlusion cache (single value for last check)
    private long lastOcclusionCheckTime = 0L;
    private float cachedOcclusionFactor = 1.0f;

    public HeavySwingSoundHandler(SoundHandler originalHandler) {
        super(getResourceManagerFrom(originalHandler), Minecraft.getMinecraft().gameSettings);

        // Locate original SoundManager instance from the provided handler (type-scan)
        SoundManager originalManager = getPrivateFieldValueByType(originalHandler, SoundManager.class);
        if (originalManager == null) {
            HeavySwing.LOG
                .error("CRITICAL FAILURE: Original SoundManager reflection returned null. Sounds will not play.");
            return;
        }

        // Inject running SoundManager into 'this' instance (type-scan on SoundHandler)
        boolean injected = setPrivateFieldByType(this, originalManager);
        if (injected) {
            HeavySwing.LOG.info("SoundManager substitution successful. Audio delegation should now work.");
        } else {
            HeavySwing.LOG
                .error("CRITICAL FAILURE: Could not inject original SoundManager. Sounds will likely not play.");
        }
    }

    private final Map<ISound, ISound> activeExternalSounds = new HashMap<>();

    public void playExternalSound(ISound sound, float volume, float pitch) {
        if (activeExternalSounds.containsKey(sound)) return;

        ISound wrapped = sound; // default: maybe wrap logic here if needed
        if (sound.getPositionedSoundLocation()
            .getResourcePath()
            .startsWith("heavyswing:underwater")) {
            wrapped = new WrappedSound(sound, volume, pitch);
        }

        activeExternalSounds.put(sound, wrapped);
        super.playSound(wrapped);
    }

    public void stopExternalSound(ISound sound) {
        ISound wrapped = activeExternalSounds.remove(sound);
        if (wrapped != null) {
            super.stopSound(wrapped);
        }
    }

    // -------------------- Utilities: type-safe reflection helpers --------------------

    private static IResourceManager getResourceManagerFrom(SoundHandler handler) {
        // Try type-based lookup for IResourceManager on SoundHandler
        IResourceManager mgr = getPrivateFieldValueByType(handler, IResourceManager.class);
        if (mgr == null) {
            throw new IllegalStateException("Failed to get IResourceManager from SoundHandler");
        }
        return mgr;
    }

    private static <T> T getPrivateFieldValueByType(Object target, Class<T> type) {
        if (target == null) return null;
        Field f = findAccessibleFieldByType(target.getClass(), type);
        if (f != null) {
            try {
                Object val = f.get(target);
                return (T) val;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static boolean setPrivateFieldByType(Object target, Object value) {
        if (target == null) return false;
        Field f = findAccessibleFieldByType(target.getClass(), SoundManager.class);
        if (f != null) {
            try {
                f.set(target, value);
                return true;
            } catch (Throwable t) {
                HeavySwing.LOG.error("Failed to set private field by type", t);
                return false;
            }
        }
        return false;
    }

    private static Field findAccessibleFieldByType(Class<?> targetClass, Class<?> type) {
        Class<?> currentClass = targetClass;
        // Search the class and all its superclasses
        while (currentClass != null) {
            for (Field f : currentClass.getDeclaredFields()) {
                if (type.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        return f;
                    } catch (Throwable t) {
                        HeavySwing.LOG.warn("Failed to set accessible on field of type " + type.getSimpleName(), t);
                    }
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        return null;
    }

    @Override
    public void playSound(ISound sound) {
        final Minecraft mc = Minecraft.getMinecraft();

        if (sound == null) {
            super.playSound(null);
            return;
        }

        // --- Ignore music discs ---
        String soundClass = sound.getClass().getSimpleName();
        if (soundClass.contains("MovingSound") || soundClass.contains("Streaming")) {
            super.playSound(sound);
            return;
        }

        ResourceLocation soundLocation = null;
        try {
            soundLocation = sound.getPositionedSoundLocation();
        } catch (Throwable ignored) {}

        if (soundLocation != null) {
            String path = soundLocation.getResourcePath();
            if (path!= null) {
                if (path.startsWith("records.") ||
                path.startsWith("music.") ||
                path.startsWith("streaming") ||
                path.startsWith("record.") || path.contains("jukebox")) {
                    super.playSound(sound);
                    return;
                }
            }
        }

        // endof --- Ignore music discs ---

        if (!Config.enableSoundFiltering) {

            // Preserve step-sound suppression
            if (sound instanceof PositionedSound ps) {
                String name = ps.getPositionedSoundLocation()
                    .getResourcePath();
                if (name.startsWith("step.") && HeavySwingHandler.isStepBlocked(name)) {
                    if (HeavySwingHandler.isSwingActive()) {
                        if (Config.debugMode) HeavySwing.LOG.info("[HeavySwing] Sound filtering disabled.");
                        return; // block the sound
                    }
                }
            }

            super.playSound(sound);
            return;
        }

        // Bypass specific HeavySwing sound entirely
        if (soundLocation != null && "heavyswing:underwater".equals(soundLocation.getResourcePath())) {
            super.playSound(sound);
            return;
        }

        boolean markUnplayable = false;

        // If we have player + a PositionedSound (positional), compute transforms
        if (mc.thePlayer != null && sound instanceof PositionedSound ps) {

            // record defaults
            float psDefaultVolume = safeGetVolume(ps);
            float psDefaultPitch = safeGetPitch(ps);

            double sx = ps.getXPosF();
            double sy = ps.getYPosF();
            double sz = ps.getZPosF();

            double px = mc.thePlayer.posX;
            double py = mc.thePlayer.posY;
            double pz = mc.thePlayer.posZ;

            float distanceFactor = getDistanceVolume(sx, sy, sz, px, py, pz);
            //float occlusionFactor = getOcclusionFactorCached(sx, sy, sz, px, py, pz);
            float occlusionFactor = OcclusionCalculator.getOcclusion(Vec3.createVectorHelper(sx,sy,sz), Vec3.createVectorHelper(px,py,pz));
            if (distanceFactor <= 0f) markUnplayable = true;

            float underwaterFactor = getSubmergedVolumeFactor(ps);

            float finalVolume = psDefaultVolume * distanceFactor * occlusionFactor * underwaterFactor;
            if (finalVolume <= 0f) markUnplayable = true;

            float finalPitch;
            if (mc.thePlayer.isInsideOfMaterial(net.minecraft.block.material.Material.water)) {
                finalPitch = psDefaultPitch - 0.45f;
            } else {
                finalPitch = psDefaultPitch - (distanceFactor < 1f ? (0.2f * (1f - distanceFactor)) : 0f);
            }

            boolean noOrigin = (ps.getXPosF() == ps.getYPosF() && ps.getYPosF() == ps.getZPosF());

            String name = ps.getPositionedSoundLocation()
                .getResourcePath();

            // swing-step blocking and special-cases
            if (name.startsWith("step.") && HeavySwingHandler.isStepBlocked(name)) {
                if (HeavySwingHandler.isSwingActive()) {
                    markUnplayable = true;
                }
            } else if (name.startsWith("game.player.hurt") || name.startsWith("portal.portal")
                || name.startsWith("underwater_ambience")) {
                    // keep defaults
                    finalVolume = psDefaultVolume;
                    finalPitch = psDefaultPitch;
                    markUnplayable = false;
                } else if (noOrigin) {
                    finalVolume = psDefaultVolume;
                    finalPitch = psDefaultPitch;
                    markUnplayable = false;
                } else
                    if (mc.theWorld != null && mc.theWorld.provider != null && mc.theWorld.provider.dimensionId == -1) {
                        // nether adjustment
                        finalVolume = psDefaultVolume - 0.1f;
                        finalPitch = psDefaultPitch - 0.4f;
                    } else if (underwaterFactor < 1.0f) {
                        if (name.startsWith("game.player.swim")) {
                            finalVolume = psDefaultVolume;
                            finalPitch = psDefaultPitch - 0.3f;
                            markUnplayable = false;
                        }
                    }

            String logMessage = String.format(
                "%s Sound: %s @ Pos(%.2f, %.2f, %.2f), Vol: %.2f -> %.2f, Pitch: %.2f -> %.2f",
                "[HeavySwing Intercept]",
                ps.getPositionedSoundLocation()
                    .getResourcePath(),
                ps.getXPosF(),
                ps.getYPosF(),
                ps.getZPosF(),
                psDefaultVolume,
                finalVolume,
                psDefaultPitch,
                finalPitch);

            String logVolumeData = String.format(
                "distance:%.2f, occlusion:%.2f, underwater:%.2f",
                distanceFactor,
                occlusionFactor,
                underwaterFactor);

            if (Config.debugMode) {
                HeavySwing.LOG.info(logMessage);
                HeavySwing.LOG.info(logVolumeData);
            }

            if (!markUnplayable) {
                // wrap the original sound with our custom volume/pitch (no reflection)
                ISound wrapped = new WrappedSound(ps, finalVolume, finalPitch);
                super.playSound(wrapped);
            } else {
                if (Config.debugMode)
                    HeavySwing.LOG.info("[HeavySwing] Muted sound: " + ps.getPositionedSoundLocation());
            }
            return;
        }

        // Non-positioned or player not present: just forward
        if (Config.debugMode) {
            String name = (soundLocation != null) ? soundLocation.getResourcePath()
                : sound.getClass()
                    .getSimpleName();
            HeavySwing.LOG.info("[HeavySwing Intercept] (Unpositioned) Sound: " + name);
        }
        super.playSound(sound);
    }

    // -------------------- Helpers --------------------

    private static float safeGetVolume(ISound s) {
        try {
            return s.getVolume();
        } catch (Throwable t) {
            return 1.0f;
        }
    }

    private static float safeGetPitch(ISound s) {
        try {
            return s.getPitch();
        } catch (Throwable t) {
            return 1.0f;
        }
    }

    // Improved inverse square attenuation (clamped)
    private float getDistanceVolume(double sx, double sy, double sz, double px, double py, double pz) {
        double dx = sx - px;
        double dy = sy - py;
        double dz = sz - pz;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        double minDistance = 1.0;
        double maxDistance = 16.0;

        if (distance >= maxDistance) return 0f;
        if (distance <= minDistance) return 1f;

        double norm = (distance - minDistance) / (maxDistance - minDistance);
        double attenuation = 1.0 - (norm * norm); // smooth curve
        return (float) Math.max(attenuation, 0f);
    }

    private float getOcclusionFactorCached(double sx, double sy, double sz, double px, double py, double pz) {
        long now = System.currentTimeMillis();
        if (now - lastOcclusionCheckTime < OCCLUSION_CACHE_DURATION_MS) {
            return cachedOcclusionFactor;
        }
        cachedOcclusionFactor = getOcclusionFactor(sx, sy, sz, px, py, pz);
        lastOcclusionCheckTime = now;
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

        double cx = px + 0.001;
        double cy = py + mc.thePlayer.getEyeHeight() + 0.001;
        double cz = pz + 0.001;

        for (int i = 0; i < steps; i++) {
            cx += dx;
            cy += dy;
            cz += dz;

            int bx = (int) Math.floor(cx);
            int by = (int) Math.floor(cy);
            int bz = (int) Math.floor(cz);

            String key = bx + "," + by + "," + bz;
            if (!mc.theWorld.isAirBlock(bx, by, bz) && !counted.contains(key)) {
                solidCount++;
                counted.add(key);
            }
            if (solidCount >= 5) break;
        }

        solidCount = Math.max(0, solidCount - 1);

        return switch (solidCount) {
            case 0 -> 1.00f;
            case 1 -> 0.55f;
            case 2 -> 0.35f;
            case 3 -> 0.15f;
            case 4 -> 0.05f;
            default -> 0.00f;
        };
    }

    private float getSubmergedVolumeFactor(PositionedSound ps) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return 1f;

        boolean listenerInWater = mc.thePlayer.isInsideOfMaterial(net.minecraft.block.material.Material.water);
        boolean sourceInWater = isInBlock(ps.getXPosF(), ps.getYPosF() + 1, ps.getZPosF());

        if (!sourceInWater && !listenerInWater) return 1f;
        return 0.25f;
    }

    private boolean isInBlock(double x, double y, double z) {
        Minecraft mc = Minecraft.getMinecraft();
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        net.minecraft.block.Block block = mc.theWorld.getBlock(bx, by, bz);
        return block == Blocks.water;
    }

    // ---------- TheBetweenLands compatibility hacky fix ------------
    @Override
    public SoundEventAccessorComposite getSound(ResourceLocation soundLocation) {
        // 1. Try to get the real sound metadata
        SoundEventAccessorComposite realAccessor = super.getSound(soundLocation);

        if (realAccessor != null) {
            return realAccessor;
        }

        // 2. CRASH MITIGATION: If lookup fails, provide a dummy object
        // This is now necessary because a valid sound (step sound) is failing lookup
        // when exposed to the event bus, even in-game.
        if (soundLocation != null) {
            // Use the universal DummySoundAccessor
            return new DummySoundAccessor(soundLocation);
        }

        return null;
    }

    private static class DummySoundAccessor extends SoundEventAccessorComposite {

        public DummySoundAccessor(ResourceLocation location) {
            // Obfuscated constructor in 1.7.10 (bti, String, float, SoundCategory)
            // We'll use the deobf names or a best-effort call.
            // The actual constructor needed is likely (ResourceLocation, float, float) or similar.

            // Due to the complexity of the obfuscated constructor, we rely on the parent class
            // having a simple constructor or mock the getSoundCategory method if possible.
            // Assuming your setup allows for basic inheritance/mocking:
            super(location, 1.0f, 1.0f, SoundCategory.MUSIC); // Best guess for a basic construction
        }

        // Crucially, override the method The Betweenlands is trying to access.
        @Override
        public SoundCategory getSoundCategory() {
            // Return the expected category to satisfy The Betweenlands' check (SoundCategory.MUSIC)
            return SoundCategory.MUSIC;
        }
    }

    // -------------- End of hacky fix -----------


}
