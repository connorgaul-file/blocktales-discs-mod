package com.kf.client;

import com.kf.Discs;
import com.kf.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;

public class DiscsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRenderers.register(ModEntities.DISC_TRADER, DiscTraderRenderer::new);
		ClientTickEvents.END_CLIENT_TICK.register(_ -> LyricsTracker.tick());

		HudElementRegistry.attachElementAfter(
				VanillaHudElements.HOTBAR,
				Identifier.fromNamespaceAndPath(Discs.MOD_ID, "lyrics"),
				new LyricsHud()
		);

		// boss visuals (name gradient, frame, outer, phrases) live in assets/discs/bosses/*.json
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
				Identifier.fromNamespaceAndPath(Discs.MOD_ID, "bosses"),
				new BossReloadListener()
		);

		AnimatedDiscText.init();
	}
}
