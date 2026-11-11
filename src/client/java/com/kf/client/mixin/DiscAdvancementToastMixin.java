package com.kf.client.mixin;

import com.kf.Discs;
import com.kf.ModSounds;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AdvancementToast.class)
public abstract class DiscAdvancementToastMixin {
    @Shadow
    @Final
    private AdvancementHolder advancement;

    @Inject(method = "getSoundEvent", at = @At("HEAD"), cancellable = true)
    private void discs$customSound(CallbackInfoReturnable<SoundEvent> cir) {
        if (!advancement.id().getNamespace().equals(Discs.MOD_ID)) {
            return;
        }

        boolean challenge = advancement.value().display()
                .map(display -> display.type() == AdvancementType.CHALLENGE)
                .orElse(false);

        cir.setReturnValue(challenge ? ModSounds.ADVANCEMENT_CHALLENGE : ModSounds.ADVANCEMENT);
    }
}