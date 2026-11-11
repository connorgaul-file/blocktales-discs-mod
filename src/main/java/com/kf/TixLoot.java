package com.kf;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

import java.util.List;

public class TixLoot {
    private static final float chance = 0.175f;

    private static final int minAmount = 2;
    private static final int maxAmount = 12;
    private static final List<String> tablePrefixes = List.of("chests/");

    public static void register() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, _) -> {
            if (!source.isBuiltin()) {
                return;
            }

            String path = key.identifier().getPath();
            boolean gotTix = false;
            for (String prefix : tablePrefixes) {
                if (path.startsWith(prefix)) {
                    gotTix = true;
                    break;
                }
            }
            if (!gotTix) {
                return;
            }
            Item tix = Discs.REGISTERED_MATERIALS.get("tix");
            if (tix == null) {
                System.err.println("[Discs] tix item not found, is there a materials/tix.json in assets");
                return;
            }

            tableBuilder.pool(LootPool.lootPool()
                    .setRolls(ContextIntProviders.exactly(1))
                    .when(LootItemRandomChanceCondition.randomChance(chance))
                    .add(LootItem.lootTableItem(tix)
                            .apply(SetItemCountFunction.setCount(ContextIntProviders.between(minAmount, maxAmount))))
                    .build());
        });
    }
}