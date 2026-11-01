package com.kf.client;

import com.kf.BossData;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.jspecify.annotations.NullMarked;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@NullMarked
public class BossReloadListener implements PreparableReloadListener {

    @Override
    public CompletableFuture<Void> reload(SharedState currentReload, Executor taskExecutor,
                                          PreparationBarrier preparationBarrier, Executor reloadExecutor) {
        return CompletableFuture
                .supplyAsync(() -> BossData.parseAll(currentReload.resourceManager()), taskExecutor)
                .thenCompose(preparationBarrier::wait)
                .thenAcceptAsync(BossData::install, reloadExecutor);
    }
}