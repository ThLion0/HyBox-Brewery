package com.thlion_.brewery.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;

import java.util.HashMap;
import java.util.Map;

public class BreweryConfig {
    public static final BuilderCodec<BreweryConfig> CODEC;

    private float soberTickRate = 1.0F;
    private float soberUpPerTick = 0.1F;
    private float cameraDrunkEffectMin = 0.0F;
    private float cameraDrunkEffectMax = 0.012F;
    private float drunkRequiredTier1 = 1.0F;
    private float drunkRequiredTier2 = 15.0F;
    private float drunkRequiredTier3 = 40.0F;
    private float drunkRequiredTier4 = 70.0F;
    private Map<String, Float> drunkValues = new HashMap<>();

    public BreweryConfig() {
        this.drunkValues.put("Brewery_Ale_Mug", 5.5F);
        this.drunkValues.put("Brewery_Beer_Mug", 9.3F);
        this.drunkValues.put("Brewery_Berry_Cider_Mug", 5.0F);
        this.drunkValues.put("Brewery_Caramel_Beer_Mug", 6.0F);
        this.drunkValues.put("Brewery_Chili_Beer_Mug", 7.3F);
        this.drunkValues.put("Brewery_Cider_Mug", 6.3F);
        this.drunkValues.put("Brewery_Kvas_Mug", 1.4F);
        this.drunkValues.put("Brewery_Pumpkin_Beer_Mug", 7.2F);
        this.drunkValues.put("Brewery_Vodka_Mug", 16.0F);
        this.drunkValues.put("Brewery_Wine_Mug", 7.5F);
    }

    public float getSoberTickRate() {
        return this.soberTickRate;
    }

    public float getSoberUpPerTick() {
        return this.soberUpPerTick;
    }

    public float getCameraDrunkEffectMin() {
        return this.cameraDrunkEffectMin;
    }

    public float getCameraDrunkEffectMax() {
        return this.cameraDrunkEffectMax;
    }

    public float getDrunkRequiredTier1() {
        return this.drunkRequiredTier1;
    }

    public float getDrunkRequiredTier2() {
        return this.drunkRequiredTier2;
    }

    public float getDrunkRequiredTier3() {
        return this.drunkRequiredTier3;
    }

    public float getDrunkRequiredTier4() {
        return this.drunkRequiredTier4;
    }

    public float getDrunkValue(String id) {
        return this.drunkValues.getOrDefault(id, 0.0F);
    }

    public Map<String, Float> getDrunkValues() {
        return this.drunkValues;
    }

    static {
        CODEC = BuilderCodec.builder(BreweryConfig.class, BreweryConfig::new)
            .append(
                new KeyedCodec<>("SoberTickRate", Codec.FLOAT),
                (config, o) -> config.soberTickRate = o,
                BreweryConfig::getSoberTickRate
            )
            .add()
            .append(
                new KeyedCodec<>("SoberUpPerTick", Codec.FLOAT),
                (config, o) -> config.soberUpPerTick = o,
                BreweryConfig::getSoberUpPerTick
            )
            .add()
            .append(
                new KeyedCodec<>("CameraDrunkEffectMin", Codec.FLOAT),
                (config, o) -> config.cameraDrunkEffectMin = o,
                BreweryConfig::getCameraDrunkEffectMin
            )
            .add()
            .append(
                new KeyedCodec<>("CameraDrunkEffectMax", Codec.FLOAT),
                (config, o) -> config.cameraDrunkEffectMax = o,
                BreweryConfig::getCameraDrunkEffectMax
            )
            .add()
            .append(
                new KeyedCodec<>("DrunkRequiredTier1", Codec.FLOAT),
                (config, o) -> config.drunkRequiredTier1 = o,
                BreweryConfig::getDrunkRequiredTier1
            )
            .add()
            .append(
                new KeyedCodec<>("DrunkRequiredTier2", Codec.FLOAT),
                (config, o) -> config.drunkRequiredTier2 = o,
                BreweryConfig::getDrunkRequiredTier2
            )
            .add()
            .append(
                new KeyedCodec<>("DrunkRequiredTier3", Codec.FLOAT),
                (config, o) -> config.drunkRequiredTier3 = o,
                BreweryConfig::getDrunkRequiredTier3
            )
            .add()
            .append(
                new KeyedCodec<>("DrunkRequiredTier4", Codec.FLOAT),
                (config, o) -> config.drunkRequiredTier4 = o,
                BreweryConfig::getDrunkRequiredTier4
            )
            .add()
            .append(
                new KeyedCodec<>("FoodValues", new MapCodec<>(Codec.FLOAT, HashMap::new)),
                (config, o) -> config.drunkValues = o,
                BreweryConfig::getDrunkValues
            )
            .add()
            .build();
    }
}
