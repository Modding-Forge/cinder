package com.cinder.condition;

import com.cinder.resource.NamespaceId;
import com.cinder.resource.PropertiesFile;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionPropertiesReaderTest {

    @Test
    void citConditions_parseItemsDamageStackEnchantmentsAndNbt()
            throws Exception {
        PropertiesFile props = props("""
                items=minecraft:diamond_sword minecraft:iron_sword
                damage=1-10
                stackSize=1
                enchantmentIDs=minecraft:sharpness minecraft:mending
                enchantmentLevels=3-5
                nbt.display.Name=pattern:Blade*
                """);
        ConditionSet set = ConditionPropertiesReader.readCitConditions(props);

        ConditionSetTest.FakeContext context =
                new ConditionSetTest.FakeContext()
                        .string(ConditionKey.ITEM_ID, null,
                                "minecraft:diamond_sword")
                        .integer(ConditionKey.DAMAGE, null, 7)
                        .integer(ConditionKey.STACK_SIZE, null, 1)
                        .id(ConditionKey.ENCHANTMENT_ID, null,
                                NamespaceId.parse("minecraft:sharpness"))
                        .integer(ConditionKey.ENCHANTMENT_LEVEL, null, 4)
                        .string(ConditionKey.NBT_RAW, "display.Name",
                                "Blade of Tests");

        assertTrue(set.matches(context));
        assertEquals(6, set.conditions().size());
    }

    @Test
    void citConditions_matchItemsOverridesItemsAlias() throws Exception {
        PropertiesFile props = props("""
                items=minecraft:stick
                matchItems=minecraft:diamond
                """);
        ConditionSet set = ConditionPropertiesReader.readCitConditions(props);

        assertTrue(set.matches(new ConditionSetTest.FakeContext()
                .string(ConditionKey.ITEM_ID, null, "minecraft:diamond")));
        assertFalse(set.matches(new ConditionSetTest.FakeContext()
                .string(ConditionKey.ITEM_ID, null, "minecraft:stick")));
    }

    @Test
    void guiConditions_parseCommonAndContainerSpecificKeys()
            throws Exception {
        PropertiesFile props = props("""
                container=chest
                name=ipattern:Treasure*
                biomes=minecraft:plains minecraft:forest
                heights=(-10)-80
                large=true
                levels=1-4
                variants=dispenser dropper
                colors=red blue
                """);
        ConditionSet set = ConditionPropertiesReader.readGuiConditions(props);

        ConditionSetTest.FakeContext context =
                new ConditionSetTest.FakeContext()
                        .string(ConditionKey.SCREEN_ID, null, "chest")
                        .string(ConditionKey.CUSTOM_NAME, null,
                                "treasure chest")
                        .string(ConditionKey.BIOME, null,
                                "minecraft:plains")
                        .integer(ConditionKey.HEIGHT, null, 64)
                        .bool(ConditionKey.CONTAINER_FLAG, "large", true)
                        .integer(ConditionKey.CONTAINER_FLAG, "levels", 3)
                        .string(ConditionKey.CONTAINER_FLAG, "variants",
                                "dropper")
                        .string(ConditionKey.CONTAINER_FLAG, "colors",
                                "red");

        assertTrue(set.matches(context));
        assertEquals(8, set.conditions().size());
    }

    @Test
    void malformedRange_reportsKeyAndValue() throws Exception {
        PropertiesFile props = props("damage=10-1\n");

        ConditionParseException error = assertThrows(
                ConditionParseException.class,
                () -> ConditionPropertiesReader.readCitConditions(props));

        assertEquals("damage", error.key());
        assertEquals("10-1", error.value());
    }

    @Test
    void malformedBoolean_reportsKeyAndValue() throws Exception {
        PropertiesFile props = props("large=yes\n");

        ConditionParseException error = assertThrows(
                ConditionParseException.class,
                () -> ConditionPropertiesReader.readGuiConditions(props));

        assertEquals("large", error.key());
        assertEquals("yes", error.value());
    }

    private static PropertiesFile props(String body) throws Exception {
        return PropertiesFile.parse(new StringReader(body));
    }
}
