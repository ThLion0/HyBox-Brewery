package com.thlion_.brewery.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.thlion_.brewery.BreweryPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class DrunkComponent implements Component<EntityStore> {
    public static final BuilderCodec<DrunkComponent> CODEC;

    public static final float MAX_DRUNK_LEVEL = 100.0F;

    private float drunkLevel;
    private float elapsedTime;
    private float effectTime;

    public DrunkComponent() {
        this.drunkLevel = 0.0F;
        this.elapsedTime = 0.0F;
        this.effectTime = 0.0F;
    }

    public DrunkComponent(@Nonnull DrunkComponent other) {
        this.drunkLevel = other.drunkLevel;
        // Reset timers on copy to prevent desync during teleportation
        this.elapsedTime = 0.0F;
        this.effectTime = 0.0F;
    }

    public float getElapsedTime() {
        return this.elapsedTime;
    }

    public void addElapsedTime(float value) {
        this.elapsedTime += value;
    }

    public void setElapsedTime(float value) {
        this.elapsedTime = value;
    }

    public float getEffectTime() {
        return this.effectTime;
    }

    public void addEffectTime(float value) {
        this.effectTime += value;
    }

    public void setEffectTime(float value) {
        this.effectTime = value;
    }

    public float getDrunkLevel() {
        return this.drunkLevel;
    }

    public void setDrunkLevel(float value) {
        this.drunkLevel = Math.clamp(value, 0.0F, MAX_DRUNK_LEVEL);
    }

    public void increaseDrunk(float value) {
        this.drunkLevel = Math.min(this.drunkLevel + value, MAX_DRUNK_LEVEL);
    }

    public void decreaseDrunk(float value) {
        this.drunkLevel = Math.max(this.drunkLevel - value, 0.0F);
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Nullable
    @Override
    public Component<EntityStore> clone() {
        return new DrunkComponent(this);
    }

    static {
        CODEC = BuilderCodec.builder(DrunkComponent.class, DrunkComponent::new)
            .append(
                new KeyedCodec<>("DrunkLevel", Codec.FLOAT),
                (state, o) -> state.drunkLevel = o,
                DrunkComponent::getDrunkLevel
            )
            .add()
            .build();
    }

    public static ComponentType<EntityStore, DrunkComponent> getComponentType() {
        return BreweryPlugin.get().getDrunkComponentType();
    }
}
