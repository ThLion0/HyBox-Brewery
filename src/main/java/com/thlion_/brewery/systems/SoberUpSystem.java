package com.thlion_.brewery.systems;

import com.hypixel.hytale.builtin.beds.sleep.components.PlayerSleep;
import com.hypixel.hytale.builtin.beds.sleep.components.PlayerSomnolence;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.server.core.asset.type.camera.CameraEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.ActiveEntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.thlion_.brewery.BreweryPlugin;
import com.thlion_.brewery.components.DrunkComponent;
import com.thlion_.brewery.config.BreweryConfig;
import com.thlion_.brewery.utils.Utils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

public class SoberUpSystem extends EntityTickingSystem<EntityStore> {
    private static final String CAMERA_SHAKE_EFFECT = "Drunk_Shake";
    private static final float CAMERA_SHAKE_DURATION = 2.0F;

    private final float soberTickRate;
    private final float soberUpPerTick;

    private final float cameraDrunkEffectMin;
    private final float cameraDrunkEffectMax;

    private final List<DrunkTier> drunkTiers;

    public SoberUpSystem() {
        BreweryConfig config = BreweryPlugin.getConfig();

        this.soberTickRate = config.getSoberTickRate();
        this.soberUpPerTick = config.getSoberUpPerTick();

        this.cameraDrunkEffectMin = config.getCameraDrunkEffectMin();
        this.cameraDrunkEffectMax = config.getCameraDrunkEffectMax();

        this.drunkTiers = this.createTierList(config);
    }

    private @Nonnull List<DrunkTier> createTierList(@Nonnull BreweryConfig config) {
        float tier1 = config.getDrunkRequiredTier1();
        float tier2 = config.getDrunkRequiredTier2();
        float tier3 = config.getDrunkRequiredTier3();
        float tier4 = config.getDrunkRequiredTier4();

        return List.of(
            new DrunkTier(tier4, "Brewery_Drink_Effect_Very_Drunk"),
            new DrunkTier(tier3, "Brewery_Drink_Effect_Drunk"),
            new DrunkTier(tier2, "Brewery_Drink_Effect_Little_Drunk"),
            new DrunkTier(tier1, "Brewery_Drink_Effect_Sober")
        );
    }

    private record DrunkTier(float threshold, String effectName) {}

    @Override
    public void tick(
        float deltaTime,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        DrunkComponent drunkComponent = archetypeChunk.getComponent(index, DrunkComponent.getComponentType());
        Player playerComponent = archetypeChunk.getComponent(index, Player.getComponentType());
        PlayerRef playerRefComponent = archetypeChunk.getComponent(index, PlayerRef.getComponentType());
        PlayerSomnolence playerSomnolence = archetypeChunk.getComponent(index, PlayerSomnolence.getComponentType());
        if (drunkComponent == null || playerComponent == null || playerRefComponent == null) return;

        Ref<EntityStore> ref = playerComponent.getReference();
        if (ref == null) return;

        float drunkLevel = drunkComponent.getDrunkLevel();
        if (drunkLevel <= 0.0F) return;

        drunkComponent.addElapsedTime(deltaTime);
        if (drunkComponent.getElapsedTime() > this.soberTickRate) {
            drunkComponent.setElapsedTime(0.0F);

            drunkComponent.decreaseDrunk(this.soberUpPerTick);
            this.updateDrunkEffects(store, ref, null, drunkComponent, false);
        }

        drunkComponent.addEffectTime(deltaTime);
        if (drunkComponent.getEffectTime() > (CAMERA_SHAKE_DURATION - 0.5 - deltaTime)) {
            drunkComponent.setEffectTime(0.0F);

            this.applyShakeEffect(playerRefComponent, drunkLevel);
        }

        if (playerSomnolence != null) {
            if (playerSomnolence.getSleepState() instanceof PlayerSleep.Slumber) {
                drunkComponent.setDrunkLevel(0.0F);
                this.updateDrunkEffects(store, ref, null, drunkComponent, false);
                this.removeShakeEffect(playerRefComponent);
            }
        }
    }

    public void updateDrunkEffects(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nullable PlayerRef playerRefComponent,
        @Nonnull DrunkComponent drunkComponent,
        boolean isDrink
    ) {
        EffectControllerComponent effectComponent = store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (effectComponent == null) return;

        float drunkLevel = drunkComponent.getDrunkLevel();

        DrunkTier drunkTier = this.drunkTiers.stream()
            .filter(tier -> drunkLevel >= tier.threshold)
            .findFirst()
            .orElse(null);

        if (drunkTier != null) {
            if (!Utils.hasActiveEffect(effectComponent, drunkTier.effectName)) {
                this.clearDrunkEffects(store, ref, effectComponent);
                this.addNewDrunkEffect(store, ref, effectComponent, drunkTier.effectName);
            }
        } else {
            // If no drunk, then clear effects
            this.clearDrunkEffects(store, ref, effectComponent);
        }

        if (playerRefComponent != null && isDrink) {
            this.applyShakeEffect(playerRefComponent, drunkLevel);
        }
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
            DrunkComponent.getComponentType(),
            Player.getComponentType(),
            PlayerRef.getComponentType(),
            Query.not(DeathComponent.getComponentType()),
            Query.not(Invulnerable.getComponentType())
        );
    }

    private void applyShakeEffect(@Nonnull PlayerRef playerRef, float drunkLevel) {
        CameraEffect cameraEffect = CameraEffect.getAssetMap().getAsset(CAMERA_SHAKE_EFFECT);
        assert cameraEffect != null;

        float intensity = MathUtil.lerp(
            this.cameraDrunkEffectMin,
            this.cameraDrunkEffectMax,
            drunkLevel / DrunkComponent.MAX_DRUNK_LEVEL
        );

        playerRef.getPacketHandler().writeNoCache(cameraEffect.createCameraShakePacket(intensity));
    }

    private void removeShakeEffect(@Nonnull PlayerRef playerRef) {
        CameraEffect cameraEffect = CameraEffect.getAssetMap().getAsset(CAMERA_SHAKE_EFFECT);
        assert cameraEffect != null;

        playerRef.getPacketHandler().writeNoCache(cameraEffect.createCameraShakePacket(0.0F));
    }

    private void clearDrunkEffects(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull EffectControllerComponent effectComponent
    ) {
        ActiveEntityEffect[] entityEffects = effectComponent.getAllActiveEntityEffects();
        if (entityEffects != null) {
            Arrays.stream(entityEffects)
                .filter(Utils::isEffectDrunkRelated)
                .forEach(effect -> effectComponent.removeEffect(ref, effect.getEntityEffectIndex(), store));
        }
    }

    private void addNewDrunkEffect(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull EffectControllerComponent effectComponent,
        String effectName
    ) {
        EntityEffect soberEffect = EntityEffect.getAssetMap().getAsset(effectName);
        if (soberEffect == null) return;

        effectComponent.addEffect(ref, soberEffect, store);
    }
}
