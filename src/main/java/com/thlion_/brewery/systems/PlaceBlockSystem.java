package com.thlion_.brewery.systems;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.thlion_.brewery.utils.Utils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class PlaceBlockSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
    // Teleporter-related block identifiers to whitelist
    private static final String[] TELEPORTER_TAGS = {
        "Teleporter",
        "Portal",
        "Warp",
        "FastTravel"
    };
    
    // Hytale's built-in teleporter block names
    private static final String[] TELEPORTER_BLOCKS = {
        "hytale:teleporter",
        "hytale:portal_frame",
        "hytale:warp_stone"
    };

    public PlaceBlockSystem() {
        super(PlaceBlockEvent.class);
    }

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PlaceBlockEvent event
    ) {
        // Skip if already cancelled by another system
        if (event.isCancelled()) return;

        ItemStack itemStack = event.getItemInHand();
        if (itemStack == null) return;

        Item item = itemStack.getItem();
        if (this.isTeleporterItem(item.getId())) {
            return;
        }

        // Check if item has brewery-specific tags
        if (!Utils.hasBreweryTag(item)) {
            return; // Not a brewery item, allow placement
        }

        // Only prevent placement of partially consumed brewery drinks
        boolean hasBreweryDrinkTag = Utils.isItemStackHasTag(item, "Type", "Brewery_Drink");
        boolean isPartiallyConsumed = itemStack.getDurability() != itemStack.getMaxDurability();

        if (hasBreweryDrinkTag && isPartiallyConsumed) {
            event.setCancelled(true);
        }
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }

    /**
     * Check if an item is a teleporter-related block
     */
    private boolean isTeleporterItem(@Nonnull String itemId) {
        // Check exact block IDs
        for (String teleporterBlock : TELEPORTER_BLOCKS) {
            if (itemId.equals(teleporterBlock)) {
                return true;
            }
        }

        // Check if item ID contains teleporter keywords
        String lowerItemId = itemId.toLowerCase();
        for (String tag : TELEPORTER_TAGS) {
            if (lowerItemId.contains(tag.toLowerCase())) {
                return true;
            }
        }

        return false;
    }
}
