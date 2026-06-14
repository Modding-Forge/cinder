package com.cinder.fabric.cit;

import com.cinder.condition.ConditionContext;
import com.cinder.condition.ConditionKey;
import com.cinder.resource.NamespaceId;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * ItemStack-backed {@link ConditionContext} for CIT evaluation.
 *
 * <p>Purpose: adapts modern Minecraft components first and keeps raw NBT as a
 * conservative string fallback. The adapter is created only after the
 * per-item prefilter found candidates.
 *
 * <p>Performance: render-path object, but only allocated for candidate items.
 */
public final class CitConditionContext implements ConditionContext {

    private final ItemStack stack;
    private final String hand;
    private final ItemEnchantments enchantments;

    public CitConditionContext(ItemStack stack, String hand) {
        this.stack = stack;
        this.hand = hand == null ? "any" : hand;
        this.enchantments = stack.getEnchantments();
    }

    @Override
    public boolean has(ConditionKey key, String qualifier) {
        return switch (key) {
            case ITEM_ID, STACK_SIZE, HAND -> true;
            case DAMAGE, DAMAGE_PERCENT, DAMAGE_MASK ->
                    stack.isDamageableItem();
            case ENCHANTMENT_ID, ENCHANTMENT_LEVEL ->
                    !enchantments.isEmpty();
            case CUSTOM_NAME -> stack.has(DataComponents.CUSTOM_NAME);
            case LORE -> stack.has(DataComponents.LORE);
            case COMPONENT -> componentValue(qualifier) != null;
            case NBT_RAW -> legacyNbtValue(qualifier) != null;
            default -> false;
        };
    }

    @Override
    public String stringValue(ConditionKey key, String qualifier) {
        return switch (key) {
            case ITEM_ID -> BuiltInRegistries.ITEM.getKey(stack.getItem())
                    .toString();
            case HAND -> hand;
            case CUSTOM_NAME -> componentToString(
                    stack.get(DataComponents.CUSTOM_NAME));
            case LORE -> loreToString(stack.get(DataComponents.LORE));
            case COMPONENT -> componentValue(qualifier);
            case NBT_RAW -> legacyNbtValue(qualifier);
            default -> null;
        };
    }

    @Override
    public int intValue(ConditionKey key, String qualifier, int fallback) {
        return switch (key) {
            case STACK_SIZE -> stack.getCount();
            case DAMAGE -> stack.getDamageValue();
            case DAMAGE_PERCENT -> damagePercent(fallback);
            case DAMAGE_MASK -> stack.getDamageValue();
            case ENCHANTMENT_LEVEL -> highestEnchantmentLevel();
            default -> fallback;
        };
    }

    @Override
    public boolean booleanValue(ConditionKey key,
                                String qualifier,
                                boolean fallback) {
        return fallback;
    }

    @Override
    public boolean contains(ConditionKey key,
                            String qualifier,
                            NamespaceId value) {
        if (key == ConditionKey.ITEM_ID) {
            Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return itemId != null && itemId.getNamespace()
                    .equals(value.namespace())
                    && itemId.getPath().equals(value.path());
        }
        if (key == ConditionKey.ENCHANTMENT_ID) {
            for (var entry : enchantments.entrySet()) {
                if (entry.getKey().getRegisteredName()
                        .equals(value.toString())) {
                    return true;
                }
            }
            return false;
        }
        return ConditionContext.super.contains(key, qualifier, value);
    }

    private int damagePercent(int fallback) {
        int max = stack.getMaxDamage();
        if (max <= 0) {
            return fallback;
        }
        return Math.round((stack.getDamageValue() * 100.0F) / max);
    }

    private int highestEnchantmentLevel() {
        int highest = 0;
        for (var entry : enchantments.entrySet()) {
            highest = Math.max(highest, entry.getIntValue());
        }
        return highest;
    }

    private String componentValue(String qualifier) {
        if (qualifier == null || qualifier.isBlank()) {
            return null;
        }
        Identifier id = Identifier.tryParse(qualifier);
        if (id == null) {
            id = Identifier.fromNamespaceAndPath("minecraft", qualifier);
        }
        DataComponentType<?> type =
                BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(id);
        if (type == null || !stack.has(type)) {
            return null;
        }
        Object value = stack.get(type);
        return value == null ? null : value.toString();
    }

    private String legacyNbtValue(String qualifier) {
        if (qualifier == null) {
            return null;
        }
        return switch (qualifier) {
            case "display.Name" -> componentToString(
                    stack.get(DataComponents.CUSTOM_NAME));
            case "display.Lore" -> loreToString(stack.get(DataComponents.LORE));
            default -> componentValue(qualifier);
        };
    }

    private static String componentToString(Component component) {
        return component == null ? null : component.getString();
    }

    private static String loreToString(ItemLore lore) {
        return lore == null ? null : lore.lines().toString();
    }
}
