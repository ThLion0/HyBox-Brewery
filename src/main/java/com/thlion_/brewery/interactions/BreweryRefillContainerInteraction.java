package com.thlion_.brewery.interactions;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class BreweryRefillContainerInteraction extends SimpleBlockInteraction {
    public static final BuilderCodec<BreweryRefillContainerInteraction> CODEC;

    protected Map<String, RefillState> refillStateMap;
    @Nullable
    protected int[] allowedBlockIds;
    @Nullable
    protected int[] allowedFluidIds;
    @Nullable
    protected Int2ObjectOpenHashMap<String> blockToState;
    @Nullable
    protected Int2ObjectOpenHashMap<String> fluidToState;

    protected int[] getAllowedBlockIds() {
        if (this.allowedBlockIds == null) {
            this.allowedBlockIds = this.refillStateMap.values()
                .stream()
                .map(RefillState::getAllowedBlocks)
                .flatMap(Arrays::stream)
                .mapToInt(key -> BlockType.getAssetMap().getIndex(key))
                .sorted()
                .toArray();
        }

        return this.allowedBlockIds;
    }

    protected int[] getAllowedFluidIds() {
        if (this.allowedFluidIds == null) {
            this.allowedFluidIds = this.refillStateMap.values()
                .stream()
                .map(RefillState::getAllowedFluids)
                .flatMap(Arrays::stream)
                .mapToInt(key -> Fluid.getAssetMap().getIndex(key))
                .sorted()
                .toArray();
        }

        return this.allowedFluidIds;
    }

    protected Int2ObjectMap<String> getBlockToState() {
        if (this.blockToState == null) {
            this.blockToState = new Int2ObjectOpenHashMap<>();
            this.refillStateMap.forEach((s, refillState) -> {
                for (String key : refillState.getAllowedBlocks()) {
                    this.blockToState.put(BlockType.getAssetMap().getIndex(key), s);
                }
            });
        }

        return this.blockToState;
    }

    protected Int2ObjectMap<String> getFluidToState() {
        if (this.fluidToState == null) {
            this.fluidToState = new Int2ObjectOpenHashMap<>();
            this.refillStateMap.forEach((s, refillState) -> {
                for (String key : refillState.getAllowedFluids()) {
                    this.fluidToState.put(Fluid.getAssetMap().getIndex(key), s);
                }
            });
        }

        return this.fluidToState;
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void interactWithBlock(
        @Nonnull World world,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InteractionType interactionType,
        @Nonnull InteractionContext context,
        @Nullable ItemStack itemStack,
        @Nonnull Vector3i vector3i,
        @Nonnull CooldownHandler cooldownHandler
    ) {
        Ref<EntityStore> ref = context.getEntity();
        Entity entity = EntityUtils.getEntity(ref, commandBuffer);
        if (entity instanceof LivingEntity livingEntity) {
            InteractionSyncData clientState = context.getClientState();
            if (clientState == null) return;

            BlockPosition blockPosition = clientState.blockPosition;
            if (blockPosition == null) {
                context.getState().state = InteractionState.Failed;
                return;
            }

            Ref<ChunkStore> section = world.getChunkStore().getChunkSectionReference(
                ChunkUtil.chunkCoordinate(blockPosition.x),
                ChunkUtil.chunkCoordinate(blockPosition.y),
                ChunkUtil.chunkCoordinate(blockPosition.z)
            );
            if (section == null) return;

            boolean isProcessed = this.processBlockInteraction(
                world, commandBuffer, context, section, livingEntity, blockPosition
            );

            // TODO: Fix this shit ⚠️⚠️🔥
            /*
            if (!isProcessed || context.getState().state == InteractionState.Failed) {
                context.getState().state = InteractionState.Finished;

                this.processFluidInteraction(
                    world, commandBuffer, context, section, livingEntity, blockPosition
                );
            }
             */
        }
    }

    @Override
    protected void simulateInteractWithBlock(@Nonnull InteractionType interactionType, @Nonnull InteractionContext interactionContext, @Nullable ItemStack itemStack, @Nonnull World world, @Nonnull Vector3i vector3i) {}

    private boolean processBlockInteraction(
        @Nonnull World world,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InteractionContext context,
        @Nonnull Ref<ChunkStore> section,
        @Nonnull LivingEntity livingEntity,
        @Nonnull BlockPosition blockPosition
    ) {
        InteractionSyncData state = context.getState();

        BlockSection blockSection = section.getStore().getComponent(section, BlockSection.getComponentType());
        if (blockSection == null) return false;

        int blockId = blockSection.get(blockPosition.x, blockPosition.y, blockPosition.z);
        int[] allowedBlockIds = this.getAllowedBlockIds();
        if (allowedBlockIds != null && Arrays.binarySearch(allowedBlockIds, blockId) < 0) {
            state.state = InteractionState.Failed;
            return false;
        }

        String newState = this.getBlockToState().get(blockId);
        if (newState == null) {
            state.state = InteractionState.Failed;
            return false;
        }

        RefillState refillState = this.refillStateMap.get(newState);

        this.updateItemState(
            context, commandBuffer, livingEntity, refillState, newState
        );

        if (refillState != null && refillState.getTransformBlock() != null) {
            int transformedBlock = BlockType.getBlockIdOrUnknown(
                refillState.getTransformBlock(), "Unknown block %s", refillState.getTransformBlock()
            );

            boolean placed = blockSection.set(
                blockPosition.x,
                blockPosition.y,
                blockPosition.z,
                transformedBlock
            );

            if (placed) {
                world.performBlockUpdate(blockPosition.x, blockPosition.y, blockPosition.z);
            } else {
                state.state = InteractionState.Failed;
                return false;
            }
        }

        return true;
    }

    private void processFluidInteraction(
        @Nonnull World world,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InteractionContext context,
        @Nonnull Ref<ChunkStore> section,
        @Nonnull LivingEntity livingEntity,
        @Nonnull BlockPosition blockPosition
    ) {
        InteractionSyncData state = context.getState();

        FluidSection fluidSection = section.getStore().getComponent(section, FluidSection.getComponentType());
        if (fluidSection == null) return;

        int fluidId = fluidSection.getFluidId(blockPosition.x, blockPosition.y, blockPosition.z);
        int[] allowedBlockIds = this.getAllowedFluidIds();
        if (allowedBlockIds != null && Arrays.binarySearch(allowedBlockIds, fluidId) < 0) {
            state.state = InteractionState.Failed;
            return;
        }

        String newState = this.getFluidToState().get(fluidId);
        if (newState == null) {
            state.state = InteractionState.Failed;
            return;
        }

        RefillState refillState = this.refillStateMap.get(newState);

        this.updateItemState(
            context, commandBuffer, livingEntity, refillState, newState
        );

        if (refillState != null && refillState.getTransformFluid() != null) {
            int transformedFluid = Fluid.getFluidIdOrUnknown(
                refillState.getTransformFluid(), "Unknown fluid %s", refillState.getTransformFluid()
            );

            Fluid fluidAsset = Fluid.getAssetMap().getAsset(transformedFluid);
            assert fluidAsset != null;

            boolean placed = fluidSection.setFluid(
                blockPosition.x,
                blockPosition.y,
                blockPosition.z,
                transformedFluid,
                (byte) fluidAsset.getMaxFluidLevel()
            );

            if (placed) {
                world.performBlockUpdate(blockPosition.x, blockPosition.y, blockPosition.z);
            } else {
                state.state = InteractionState.Failed;
            }
        }
    }

    private void updateItemState(
        @Nonnull InteractionContext context,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull LivingEntity livingEntity,
        @Nullable RefillState refillState,
        @Nonnull String newState
    ) {
        Ref<EntityStore> ref = context.getEntity();
        InteractionSyncData state = context.getState();

        ItemStack currentItem = context.getHeldItem();
        assert currentItem != null;

        ItemContainer itemContainer = context.getHeldItemContainer();
        assert itemContainer != null;

        Item newItemAsset = currentItem.getItem().getItemForState(newState);
        if (newItemAsset == null) {
            state.state = InteractionState.Failed;
            return;
        }

        if (newItemAsset.getId().equals(currentItem.getItemId())) {
            if (refillState != null) {
                double newDurability = MathUtil.maxValue(refillState.durability, currentItem.getMaxDurability());
                if (newDurability <= currentItem.getDurability()) {
                    state.state = InteractionState.Failed;
                    return;
                }

                ItemStack newItem = currentItem.withIncreasedDurability(newDurability);
                ItemStackSlotTransaction transaction = itemContainer
                    .setItemStackForSlot(context.getHeldItemSlot(), newItem);

                if (!transaction.succeeded()) {
                    state.state = InteractionState.Failed;
                    return;
                }

                context.setHeldItem(newItem);
            }
        } else {
            ItemStackSlotTransaction removeEmptyTransaction = itemContainer
                .removeItemStackFromSlot(context.getHeldItemSlot(), currentItem, 1);

            if (!removeEmptyTransaction.succeeded()) {
                state.state = InteractionState.Failed;
                return;
            }

            ItemStack refilledContainer = new ItemStack(newItemAsset.getId(), 1);
            if (refillState != null && refillState.durability >= 0.0D) {
                refilledContainer = refilledContainer.withDurability(refillState.durability);
            }

            if (currentItem.getQuantity() == 1) {
                ItemStackSlotTransaction addFilledTransaction = itemContainer
                    .setItemStackForSlot(context.getHeldItemSlot(), refilledContainer);

                if (!addFilledTransaction.succeeded()) {
                    state.state = InteractionState.Failed;
                    return;
                }

                context.setHeldItem(refilledContainer);
            } else {
                SimpleItemContainer.addOrDropItemStack(
                    commandBuffer,
                    ref,
                    livingEntity.getInventory().getCombinedHotbarFirst(),
                    refilledContainer
                );

                context.setHeldItem(itemContainer.getItemStack(context.getHeldItemSlot()));
            }
        }
    }

    static {
        CODEC = BuilderCodec.builder(BreweryRefillContainerInteraction.class, BreweryRefillContainerInteraction::new, SimpleBlockInteraction.CODEC)
            .appendInherited(
                new KeyedCodec<>("States", new MapCodec<>(RefillState.CODEC, HashMap::new)),
                (interaction, o) -> interaction.refillStateMap = o,
                interaction -> interaction.refillStateMap,
                (o, p) -> o.refillStateMap = p.refillStateMap
            )
            .addValidator(Validators.nonNull())
            .add()
            .afterDecode(interaction -> {
                interaction.allowedBlockIds = null;
                interaction.allowedFluidIds = null;

                interaction.blockToState = null;
                interaction.fluidToState = null;
            })
            .build();
    }

    protected static class RefillState {
        public static final BuilderCodec<RefillState> CODEC;

        protected String[] allowedBlocks;
        protected String[] allowedFluids;

        protected String transformBlock;
        protected String transformFluid;

        protected double durability = -1.0D;

        public String[] getAllowedBlocks() {
            return this.allowedBlocks;
        }

        public String getTransformBlock() {
            return this.transformBlock;
        }

        public String[] getAllowedFluids() {
            return this.allowedFluids;
        }

        public String getTransformFluid() {
            return this.transformFluid;
        }

        public double getDurability() {
            return this.durability;
        }

        static {
            CODEC = BuilderCodec.builder(RefillState.class, RefillState::new)
                // Allowed blocks, used to determinate on which blocks can be used
                .append(
                    new KeyedCodec<>("AllowedBlocks", new ArrayCodec<>(Codec.STRING, String[]::new)),
                    (interaction, o) -> interaction.allowedBlocks = o,
                    RefillState::getAllowedBlocks
                )
                .addValidator(Validators.nonNull())
                .add()

                .append(
                    new KeyedCodec<>("TransformBlock", Codec.STRING),
                    (interaction, o) -> interaction.transformBlock = o,
                    RefillState::getTransformBlock
                ).add()

                // Allowed fluids, used to determinate on which fluids can be used
                .append(
                    new KeyedCodec<>("AllowedFluids", new ArrayCodec<>(Codec.STRING, String[]::new)),
                    (interaction, o) -> interaction.allowedFluids = o,
                    RefillState::getAllowedFluids
                )
                .addValidator(Validators.nonNull())
                .add()

                .append(
                    new KeyedCodec<>("TransformFluid", Codec.STRING),
                    (interaction, o) -> interaction.transformFluid = o,
                    RefillState::getTransformFluid
                ).add()

                // Other stuff :D
                .append(
                    new KeyedCodec<>("Durability", Codec.DOUBLE),
                    (interaction, o) -> interaction.durability = o,
                    RefillState::getDurability
                ).add()

                .build();
        }
    }
}
