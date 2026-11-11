package com.kf;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.jspecify.annotations.NullMarked;
// import org.jspecify.annotations.Nullable;  kinda not used but might be used so ill keep it

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// cd is 5 mins
// rejoining completely bypasses this but im not working overtime to add saving logic okay thanks bye
@NullMarked
public class DiscCooldownCondition implements LootItemCondition {

    private static final long COOLDOWN_TICKS = 5 * 60 * 20;

    private static final Map<UUID, Long> lastFind = new HashMap<>();

    private static final DiscCooldownCondition INSTANCE = new DiscCooldownCondition();
    public static final MapCodec<DiscCooldownCondition> CODEC = MapCodec.unit(INSTANCE);

    private DiscCooldownCondition() {
    }

    @Override
    public MapCodec<DiscCooldownCondition> codec() {
        return CODEC;
    }

    @Override
    public boolean test(LootContext context) {
        // basically it differentiates entity from chests so it can work (indirect kills do not count but that's something out of my control)
        Entity entity = context.getOptional(LootContextParams.LAST_DAMAGE_PLAYER);
        if (entity == null) {
            entity = context.getOptional(LootContextParams.THIS_ENTITY);
        }
        if (entity == null) {
            entity = context.getOptional(LootContextParams.INTERACTING_ENTITY);
        }

        if (!(entity instanceof Player player)) {
            return true;
        }

        long now = context.getLevel().getGameTime();
        Long last = lastFind.get(player.getUUID());

        if (last != null && now >= last && now - last < COOLDOWN_TICKS) {
            return false;
        }

        lastFind.put(player.getUUID(), now);
        return true;
    }

    // directly copied from the vanilla helper
    public static LootItemCondition.Builder cooldown() {
        return () -> INSTANCE;
    }

    // just in case? i guess
    public static void register() {
        Registry.register(
                BuiltInRegistries.LOOT_CONDITION_TYPE,
                Identifier.fromNamespaceAndPath(Discs.MOD_ID, "disc_cooldown"),
                CODEC
        );
    }
}