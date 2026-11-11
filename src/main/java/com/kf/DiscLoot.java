package com.kf;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.AlternativesEntry;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DiscLoot {

    private static final Map<String, List<LootEntry>> LOOT = new HashMap<>();

    public static void register() {
        loadLootFiles();

        LootTableEvents.MODIFY.register((key, tableBuilder, source, _) -> {
            if (!source.isBuiltin()) {
                return;
            }

            String tableId = key.identifier().toString();
            List<LootEntry> entries = LOOT.get(tableId);

            if (entries != null && !entries.isEmpty()) {
                injectSafeChancePool(tableBuilder, entries);
            }
        });
    }

    private static void loadLootFiles() {
        FabricLoader.getInstance().getModContainer(Discs.MOD_ID).ifPresent(container -> {
            var path = container.findPath("data/" + Discs.MOD_ID + "/loot");
            if (path.isEmpty()) return;

            try (var files = Files.walk(path.get())) {
                files.filter(f -> f.toString().endsWith(".json")).forEach(file -> {
                    try (BufferedReader reader = Files.newBufferedReader(file)) {
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        String lootTable = json.get("loot_table").getAsString();

                        if (!lootTable.contains(":")) {
                            lootTable = "minecraft:" + lootTable;
                        }

                        List<LootEntry> list = LOOT.computeIfAbsent(lootTable, _ -> new ArrayList<>());

                        json.getAsJsonArray("entries").forEach(element -> {
                            JsonObject obj = element.getAsJsonObject();

                            // translates the json to minecraft (from % to 0-1)
                            float chancePercent = obj.get("chance").getAsFloat();
                            float finalChance = chancePercent / 100.0f;

                            list.add(new LootEntry(
                                    obj.get("disc").getAsString(),
                                    finalChance
                            ));
                        });
                    } catch (Exception e) {
                        System.err.println("[Discs] Failed loading loot file: " + file);
                    }
                });
            } catch (Exception e) {
                System.err.println("[Discs] Error walking loot directory: " + e.getMessage());
            }
        });
    }

    private static void injectSafeChancePool(net.minecraft.world.level.storage.loot.LootTable.Builder table, List<LootEntry> entries) {
        List<LootPoolEntryContainer.Builder<?>> itemEntries = new ArrayList<>();

        for (LootEntry entry : entries) {
            Item disc = resolveDisc(entry.disc());

            if (disc == null) {
                System.err.println("[Discs] unknown disc in loot JSON: " + entry.disc());
                continue;
            }

            itemEntries.add(LootItem.lootTableItem(disc)
                    .when(LootItemRandomChanceCondition.randomChance(entry.chance()))
                    .when(DiscCooldownCondition.cooldown()));
        }

        if (!itemEntries.isEmpty()) {
            LootPoolEntryContainer.Builder<?>[] entriesArray = itemEntries.toArray(new LootPoolEntryContainer.Builder<?>[0]);

            table.pool(LootPool.lootPool()
                    .setRolls(ContextIntProviders.exactly(1))
                    .add(AlternativesEntry.alternatives(entriesArray))
                    .build());
        }
    }

    private static Item resolveDisc(String discId) {
        String fullId = discId.contains(":") ? discId : Discs.MOD_ID + ":" + discId;
        return Discs.REGISTERED_DISCS.get(fullId);
    }

    private record LootEntry(String disc, float chance) {}
}