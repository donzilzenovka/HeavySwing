package com.drzenovka.heavyswing.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    public static boolean enableHeavySwingAnimation = true;
    public static boolean enableSoundFiltering = true;
    public static boolean debugMode = false;

    public static boolean enableUnderwaterAmbience = true;
    public static float underwaterVolume = 1.0f;

    public static boolean enableUnderwaterShader = true;

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        try {
            configuration.load();

            enableHeavySwingAnimation = configuration.getBoolean(
                "enableHeavySwingAnimation",
                "General",
                enableHeavySwingAnimation,
                "Toggle heavySwing tool animation on or off."
            );

            enableSoundFiltering = configuration.getBoolean(
                "enableHeavySwingSoundFiltering",
                "General",
                enableSoundFiltering,
                "Toggle distance, submersion and occlusion based sound filtering on or off."
            );

            debugMode = configuration.getBoolean(
                "debugMode",
                "General",
                debugMode,
                "Enable debug audio logging."
            );

            enableUnderwaterAmbience = configuration.getBoolean(
                "enableUnderwaterAmbience",
                "Sound",
                enableUnderwaterAmbience,
                "Enable looping underwater sound."
            );
            underwaterVolume = configuration.getFloat(
                "underwaterVolume",
                "Sound",
                underwaterVolume,
                0.0F,
                1.0F,
                "Volume for underwater ambience (0.0 - 1.0)."
            );

            enableUnderwaterShader = configuration.getBoolean(
                "enableUnderwaterShader",
                "Visual",
                enableUnderwaterShader,
                "Enable shader effect when underwater."
            );
        } finally {
            if (configuration.hasChanged()) {
                configuration.save();
            }
        }
    }
}
