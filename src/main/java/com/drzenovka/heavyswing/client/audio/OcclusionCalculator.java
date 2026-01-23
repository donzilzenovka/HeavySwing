package com.drzenovka.heavyswing.client.audio;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class OcclusionCalculator {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final World world = mc.theWorld;

    // --- caching (keep your existing BLOCK_COEFF and POS_CACHE implementations) ---
    private static final Map<Block, Float> BLOCK_COEFF = new HashMap<>();
    private static final int POS_CACHE_SIZE = 256;
    private static final int POS_TTL = 10;

    private static class PosEntry {

        final float coeff;
        final int expires;

        PosEntry(float c, int e) {
            coeff = c;
            expires = e;
        }
    }

    private static final LinkedHashMap<Long, PosEntry> POS_CACHE = new LinkedHashMap<Long, PosEntry>(
        POS_CACHE_SIZE,
        0.75f,
        true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, PosEntry> eldest) {
            return size() > POS_CACHE_SIZE;
        }
    };

    private static long pack(int x, int y, int z) {
        return (((long) x & 0x3FFFFFF) << 38) | (((long) z & 0x3FFFFFF) << 12) | ((long) y & 0xFFF);
    }

    private static float computeBlockCoeff(Block b) {
        Material m = b.getMaterial();
        if (m == Material.anvil) return 0.65f;
        if (m == Material.air) return 0f;
        if (m == Material.rock) return 0.9f;
        if (m == Material.cloth) return 0.95f;
        if (m == Material.water) return 0.85f;
        if (m == Material.cactus) return 0.2f;
        if (m == Material.snow) return 0.9f;
        if (m == Material.glass) return 0.65f;
        if (m == Material.wood) return 0.85f;
        if (m == Material.leaves) return 0.3f;
        if (m == Material.lava) return 0.9f;
        if (m == Material.carpet) return 0.85f;
        if (m == Material.grass) return 0.7f;
        return 0.4f;
    }

    private static float getCoeffForBlock(Block b) {
        Float cached = BLOCK_COEFF.get(b);
        if (cached != null) return cached;
        float c = computeBlockCoeff(b);
        BLOCK_COEFF.put(b, c);
        return c;
    }

    private static float getCoeffAtPos(int x, int y, int z) {
        long key = pack(x, y, z);
        int now = (int) (world != null ? world.getTotalWorldTime() : 0);
        PosEntry e = POS_CACHE.get(key);
        if (e != null && e.expires >= now) return e.coeff;
        Block b = world.getBlock(x, y, z);
        float c = getCoeffForBlock(b);
        POS_CACHE.put(key, new PosEntry(c, now + POS_TTL));
        return c;
    }

    // ----------------- Behaviour tuning -----------------
    private static final float MAX_DISTANCE = 32.0f; // distance at which distanceFactor reaches 0
    private static final int SAMPLES = 5; // how many checkpoints along line
    private static final float DISTANCE_SKIP_THRESHOLD = 0.9f; // if distanceFactor >= this, skip occlusion
    private static final float EARLY_SILENT_THRESHOLD = 0.03f; // early exit if near silent

    /**
     * Returns a transmission float in [0.0, 1.0]
     * 1.0 = fully audible / no occlusion
     * 0.0 = completely muted
     */
    public static float getOcclusion(Vec3 src, Vec3 dst) {
        if (world == null || src == null || dst == null) return 1.0f;

        // distance
        double dx = dst.xCoord - src.xCoord;
        double dy = dst.yCoord - src.yCoord;
        double dz = dst.zCoord - src.zCoord;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // normalized distance factor (1.0 close, 0.0 far). Clamp to [0,1].
        float distanceFactor = 1.0f - (float) (distance / Math.max(1.0f, MAX_DISTANCE));
        if (distanceFactor < 0f) distanceFactor = 0f;
        if (distanceFactor > 1f) distanceFactor = 1f;

        // if close enough, skip occlusion entirely (fast-path)
        if (distanceFactor >= DISTANCE_SKIP_THRESHOLD) {
            return 1.0f; // full transmission
        }

        // sample only blocks directly between src and dst
        float transmissionSum = 0.0f;
        int sampleCount = 0;

        // iterate checkpoints (skip i=0 and i==SAMPLES to avoid sampling on the exact source/listener points)
        for (int i = 1; i < SAMPLES; i++) {
            float t = i / (float) SAMPLES;
            double sx = src.xCoord + dx * t;
            double sy = src.yCoord + dy * t;
            double sz = src.zCoord + dz * t;

            int bx = MathHelper.floor_double(sx);
            int by = MathHelper.floor_double(sy);
            int bz = MathHelper.floor_double(sz);

            float coeff = getCoeffAtPos(bx, by, bz); // [0..1], 0=air,1=full block
            float sampleTransmission = 1.0f - coeff; // 0..1, where air=>1.0 (passes), wool=>0.2 (mostly blocked)

            transmissionSum += sampleTransmission;
            sampleCount++;

            // optional early exit: if even the average so far is below threshold and there's no chance to recover,
            // we can break. This is conservative — only break if it's already effectively silent.
            float avgSoFar = transmissionSum / sampleCount;
            if (avgSoFar * distanceFactor < EARLY_SILENT_THRESHOLD) {
                return 0.0f;
            }
        }

        // average the sample transmissions (this respects only blocks directly between src and dst)
        float avgTransmission = sampleCount > 0 ? (transmissionSum / sampleCount) : 1.0f;

        // combine with distance factor (attenuate based on distance)
        float finalTransmission = avgTransmission * distanceFactor;

        // clamp and return (ensure within [0,1])
        if (finalTransmission < 0.1f) finalTransmission = 0f; // TEST bumped to 0.1 to eliminate slight noise
        if (finalTransmission > 1f) finalTransmission = 1f;
        return finalTransmission;
    }
}
