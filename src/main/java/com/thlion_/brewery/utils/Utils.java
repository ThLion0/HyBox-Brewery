package com.thlion_.brewery.utils;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.effect.ActiveEntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.util.Map;

public class Utils {
    private static final String[] effectIdList = new String[]{
        "Brewery_Drink_Effect_Sober",
        "Brewery_Drink_Effect_Little_Drunk",
        "Brewery_Drink_Effect_Drunk",
        "Brewery_Drink_Effect_Very_Drunk"
    };

    public static boolean isEffectDrunkRelated(@Nonnull ActiveEntityEffect entityEffect) {
        try {
            String effectId = getActiveEntityEffectId(entityEffect);
            for (String id : effectIdList) {
                if (effectId.equals(id)) {
                    return true;
                }
            }

            return false;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean hasActiveEffect(@Nonnull EffectControllerComponent effectComponent, @Nonnull String effectId) {
        ActiveEntityEffect[] activeEffects = effectComponent.getAllActiveEntityEffects();
        if (activeEffects == null) return false;

        for (ActiveEntityEffect effect : activeEffects) {
            try {
                String id = getActiveEntityEffectId(effect);
                if (id.equals(effectId)) {
                    return true;
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        return false;
    }

    public static String getActiveEntityEffectId(@Nonnull ActiveEntityEffect entityEffect) throws NoSuchFieldException, IllegalAccessException {
        Field f = ActiveEntityEffect.class.getDeclaredField("entityEffectId");
        f.setAccessible(true);
        return (String) f.get(entityEffect);
    }

    public static boolean isItemStackHasTag(@Nonnull Item item, @Nonnull String key, @Nonnull String tag) {
        Map<String, String[]> tags = item.getData().getRawTags();
        if (tags.isEmpty()) return false;

        String[] tagTypeValues = tags.get(key);
        if (tagTypeValues == null) return false;

        for (String itemTag : tagTypeValues) {
            if (itemTag.equals(tag)) {
                return true;
            }
        }

        return false;
    }
}
