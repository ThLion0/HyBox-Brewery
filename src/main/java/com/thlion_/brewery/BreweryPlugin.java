package com.thlion_.brewery;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import com.thlion_.brewery.commands.BreweryDrunkCommand;
import com.thlion_.brewery.components.DrunkComponent;
import com.thlion_.brewery.config.BreweryConfig;
import com.thlion_.brewery.events.BreweryPlayerReadyEvent;
import com.thlion_.brewery.interactions.BlockRefillContainerInteraction;
import com.thlion_.brewery.interactions.DrunkUpInteraction;
import com.thlion_.brewery.systems.OnDeathSystem;
import com.thlion_.brewery.systems.PlaceBlockSystem;
import com.thlion_.brewery.systems.SoberUpSystem;

import javax.annotation.Nonnull;

@SuppressWarnings("unused")
public class BreweryPlugin extends JavaPlugin {
    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static BreweryPlugin instance;
    private static Config<BreweryConfig> config;

    private ComponentType<EntityStore, DrunkComponent> drunkComponentType;
    private SoberUpSystem soberUpSystem;

    public BreweryPlugin(@Nonnull JavaPluginInit init) {
        super(init);

        instance = this;
        config = this.withConfig("BreweryConfig", BreweryConfig.CODEC);
    }

    @Override
    protected void setup() {
        super.setup();

        config.save();

        var entityStoreRegistry = this.getEntityStoreRegistry();
        var interactionRegistry = this.getCodecRegistry(Interaction.CODEC);

        // Registering components, core of the mechanic
        this.drunkComponentType = entityStoreRegistry
            .registerComponent(DrunkComponent.class, "DrunkComponent", DrunkComponent.CODEC);

        // Registering systems, used to decrease drunk over time
        this.soberUpSystem = new SoberUpSystem();

        entityStoreRegistry.registerSystem(new OnDeathSystem());
        entityStoreRegistry.registerSystem(this.soberUpSystem);

        // Prevents player to place mug with low durability
        entityStoreRegistry.registerSystem(new PlaceBlockSystem());

        // Registering interactions, used to get drunk after drink
        interactionRegistry.register("Brewery_Drunk_Up", DrunkUpInteraction.class, DrunkUpInteraction.CODEC);

        interactionRegistry.register("BlockRefillContainer", BlockRefillContainerInteraction.class, BlockRefillContainerInteraction.CODEC);

        // Registering events, used to add component
        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, BreweryPlayerReadyEvent::handle);

        // Useful debug command
        this.getCommandRegistry().registerCommand(new BreweryDrunkCommand());
    }

    public static BreweryPlugin get() {
        return instance;
    }

    public static BreweryConfig getConfig() {
        return config.get();
    }

    public ComponentType<EntityStore, DrunkComponent> getDrunkComponentType() {
        return this.drunkComponentType;
    }

    public SoberUpSystem getSoberUpSystem() {
        if (this.soberUpSystem == null) {
            throw new IllegalAccessError("Sober Up system is not setup.");
        }

        return this.soberUpSystem;
    }
}
