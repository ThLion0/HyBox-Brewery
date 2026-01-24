package com.thlion_.brewery.events;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.thlion_.brewery.BreweryPlugin;
import com.thlion_.brewery.components.DrunkComponent;

import javax.annotation.Nonnull;

public class BreweryPlayerReadyEvent {
    public static void handle(@Nonnull PlayerReadyEvent event) {
        Ref<EntityStore> ref = event.getPlayerRef();
        if (ref == null) return;
        
        Store<EntityStore> store = ref.getStore();
        if (store == null) return;
        
        World world = store.getExternalData().getWorld();
        if (world == null) return;

        // Ensure component exists - this handles both new players and teleportation reconnects
        world.execute(() -> {
            try {
                // Check if component already exists (teleportation case)
                DrunkComponent existing = store.getComponent(ref, DrunkComponent.getComponentType());
                
                if (existing == null) {
                    // New player - create component
                    store.ensureComponent(ref, DrunkComponent.getComponentType());
                } else {
                    // Existing player (teleportation) - reset timers to prevent desync
                    existing.setElapsedTime(0.0F);
                    existing.setEffectTime(0.0F);
                }
            } catch (Exception e) {
                BreweryPlugin.LOGGER.error("Failed to handle DrunkComponent for player", e);
            }
        });
    }
}
