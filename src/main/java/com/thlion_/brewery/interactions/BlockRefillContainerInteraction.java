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
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class BlockRefillContainerInteraction extends SimpleBlockInteraction {
    public static final BuilderCodec<BlockRefillContainerInteraction> CODEC;

    protected Map<String, RefillState> refillStateMap;
    @Nullable
    protected int[] allowedBlockIds;
    @Nullable
    protected Int2ObjectOpenHashMap<String> blockToState;

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
            InteractionSyncData state = context.getState();

            InteractionSyncData clientState = context.getClientState();
            if (clientState == null) return;

            BlockPosition blockPosition = clientState.blockPosition;
            if (blockPosition == null) {
                state.state = InteractionState.Failed;
                return;
            }

            Ref<ChunkStore> section = world.getChunkStore().getChunkSectionReference(
                ChunkUtil.chunkCoordinate(blockPosition.x),
                ChunkUtil.chunkCoordinate(blockPosition.y),
                ChunkUtil.chunkCoordinate(blockPosition.z)
            );
            if (section != null) {
                BlockSection blockSection = section.getStore().getComponent(section, BlockSection.getComponentType());
                if (blockSection != null) {
                    int blockId = blockSection.get(blockPosition.x, blockPosition.y, blockPosition.z);
                    int[] allowedBlockIds = this.getAllowedBlockIds();
                    if (allowedBlockIds != null && Arrays.binarySearch(allowedBlockIds, blockId) < 0) {
                        state.state = InteractionState.Failed;
                        return;
                    }

                    String newState = this.getBlockToState().get(blockId);
                    if (newState == null) {
                        state.state = InteractionState.Failed;
                        return;
                    }

                    ItemStack current = context.getHeldItem();
                    assert current != null;

                    Item newItemAsset = current.getItem().getItemForState(newState);
                    if (newItemAsset == null) {
                        state.state = InteractionState.Failed;
                        return;
                    }

                    ItemContainer itemContainer = context.getHeldItemContainer();
                    assert itemContainer != null;

                    RefillState refillState = this.refillStateMap.get(newState);
                    if (newItemAsset.getId().equals(current.getItemId())) {
                        if (refillState != null) {
                            double newDurability = MathUtil.maxValue(refillState.durability, current.getMaxDurability());
                            if (newDurability <= current.getDurability()) {
                                state.state = InteractionState.Failed;
                                return;
                            }

                            ItemStack newItem = current.withIncreasedDurability(newDurability);
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
                            .removeItemStackFromSlot(context.getHeldItemSlot(), current, 1);

                        if (!removeEmptyTransaction.succeeded()) {
                            state.state = InteractionState.Failed;
                            return;
                        }

                        ItemStack refilledContainer = new ItemStack(newItemAsset.getId(), 1);
                        if (refillState != null && refillState.durability >= 0.0D) {
                            refilledContainer = refilledContainer.withDurability(refillState.durability);
                        }

                        if (current.getQuantity() == 1) {
                            ItemStackSlotTransaction addFilledTransaction = context.getHeldItemContainer()
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

                            context.setHeldItem(context.getHeldItemContainer().getItemStack(context.getHeldItemSlot()));
                        }
                    }

                    if (refillState != null && refillState.getTransformBlock() != null) {
                        int transformedBlock = BlockType.getBlockIdOrUnknown(refillState.getTransformBlock(), "Unknown block %s", refillState.getTransformBlock());
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
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void simulateInteractWithBlock(@Nonnull InteractionType interactionType, @Nonnull InteractionContext interactionContext, @Nullable ItemStack itemStack, @Nonnull World world, @Nonnull Vector3i vector3i) {}

    static {
        CODEC = BuilderCodec.builder(BlockRefillContainerInteraction.class, BlockRefillContainerInteraction::new, SimpleBlockInteraction.CODEC)
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
                interaction.blockToState = null;
            })
            .build();
    }

    protected static class RefillState {
        public static final BuilderCodec<RefillState> CODEC;

        protected String[] allowedBlocks;
        protected String transformBlock;
        protected double durability = -1.0D;

        public String[] getAllowedBlocks() {
            return this.allowedBlocks;
        }

        public String getTransformBlock() {
            return this.transformBlock;
        }

        public double getDurability() {
            return this.durability;
        }

        static {
            CODEC = BuilderCodec.builder(RefillState.class, RefillState::new)
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
                )
                .add()
                .append(
                    new KeyedCodec<>("Durability", Codec.DOUBLE),
                    (interaction, o) -> interaction.durability = o,
                    RefillState::getDurability
                )
                .add()
                .build();
        }
    }
}
