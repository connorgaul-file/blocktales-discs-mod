package com.kf;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kf.entity.ModEntities;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.Rarity;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@NullMarked
public class Discs implements ModInitializer {
	public static final String MOD_ID = "discs";

	public static final String[] CHAPTER_ORDER = {
			"preprologue", "prologue", "demo1", "demo2", "demo3", "demo4", "demo5", "demo6", "demo7"
	};

	public static final Set<String> CHAPTER_SET = Set.of(CHAPTER_ORDER);
	public static final Set<String> BIG_STACKS = Set.of("tix", "band_of_tix");

	private static final Component PLAIN_NAME = Component.literal("Music Disc");

	public static final Map<String, List<Item>> discsPerChapter = new LinkedHashMap<>(16);
	public static final List<Item> modMaterials = new ArrayList<>();

	public static final List<Item> orderedDiscs = new ArrayList<>();

	public static final List<Item> TEMPLATES = new ArrayList<>();

	public static final Map<Item, Integer> discPrices = new HashMap<>(128);
	public static final Set<Item> bossDiscs = new HashSet<>(32);
	public static final Map<String, Item> REGISTERED_DISCS = new HashMap<>(128);
	public static final Map<String, Item> REGISTERED_MATERIALS = new HashMap<>(16);

	public static @Nullable Item tabIcon;
	public static @Nullable CreativeModeTab discsTab;

	static {
		for (String chapter : CHAPTER_ORDER) {
			discsPerChapter.put(chapter, new ArrayList<>(16));
		}
	}

	public static final ResourceKey<CreativeModeTab> TAB_KEY = ResourceKey.create(
			Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(MOD_ID, "main_tab")
	);

	@Override
	public void onInitialize() {
		FabricLoader.getInstance().getModContainer(MOD_ID).ifPresent(mod -> {
			mod.findPath("assets/" + MOD_ID + "/bosses").ifPresent(BossData::loadFromJar);

			Path itemAssets = mod.findPath("assets/" + MOD_ID + "/items").orElse(null);

			if (itemAssets == null) return;
			Map<String, List<String>> discoveredItems = new HashMap<>();

			try (var files = Files.walk(itemAssets)) {
				files.filter(Files::isRegularFile)
						.filter(file -> file.toString().endsWith(".json"))
						.sorted()
						.forEach(file -> {
							Path relativePath = itemAssets.relativize(file);

							if (relativePath.getNameCount() >= 2) {
								String folderDir = relativePath.getName(0).toString();
								String itemName = relativePath.getFileName().toString().replace(".json", "");

								discoveredItems.computeIfAbsent(folderDir, _ -> new ArrayList<>()).add(itemName);
							}
						});
			} catch (Exception e) {
				System.err.println("[Discs] failed to scan for item files: " + e.getMessage());
			}

			if (discoveredItems.containsKey("materials")) {
				for (String itemName : discoveredItems.get("materials")) {
					registerMaterial(itemName);
				}
			}

			for (String chapter : CHAPTER_ORDER) {
				if (discoveredItems.containsKey(chapter)) {
					for (String trackName : discoveredItems.get(chapter)) {
						registerDisc(trackName, chapter);
					}
				}
			}
			for (String folder : discoveredItems.keySet()) {
				if (!folder.equals("materials") && !CHAPTER_SET.contains(folder)) {
					for (String trackName : discoveredItems.get(folder)) {
						registerDisc(trackName, folder);
					}
				}
			}

			modMaterials.sort((a, b) -> {
				int indexA = getMaterialSortIndex(a);
				int indexB = getMaterialSortIndex(b);
				if (indexA != indexB) {
					return Integer.compare(indexA, indexB);
				}
				return BuiltInRegistries.ITEM.getKey(a).getPath().compareTo(BuiltInRegistries.ITEM.getKey(b).getPath());
			});

			Path tagPath = mod.findPath("data/" + MOD_ID + "/tags/item/all_discs.json").orElse(null);
			Set<Item> taggedDiscs = new HashSet<>();

			if (tagPath != null && Files.exists(tagPath)) {
				try (BufferedReader reader = Files.newBufferedReader(tagPath)) {
					JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
					if (json.has("values")) {
						json.getAsJsonArray("values").forEach(el -> {
							String id = el.getAsString();
							if (!id.contains(":")) id = MOD_ID + ":" + id;

							Item disc = REGISTERED_DISCS.get(id);
							if (disc != null) {
								orderedDiscs.add(disc);
								taggedDiscs.add(disc);
							}
						});
					}
				} catch (Exception e) {
					System.err.println("[Discs] failed to read all_discs tag for ordering: " + e.getMessage());
				}
			}

			for (List<Item> chapterList : discsPerChapter.values()) {
				for (Item disc : chapterList) {
					if (!taggedDiscs.contains(disc)) {
						orderedDiscs.add(disc);
						taggedDiscs.add(disc);
					}
				}
			}
		});

		CreativeModeTab mainTab = FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.discs.main_tab"))
				.icon(() -> {
					Item iconItem = tabIcon;
					return new ItemStack(iconItem != null ? iconItem : Items.JUKEBOX);
				})
				.displayItems((_, output) -> {
					modMaterials.forEach(output::accept);
					orderedDiscs.forEach(output::accept);
				})
				.build();
		discsTab = mainTab; // uhhh yes

		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY, mainTab);

		ModEntities.register();
		FabricDefaultAttributeRegistry.register(ModEntities.DISC_TRADER, WanderingTrader.createMobAttributes());
		DiscCooldownCondition.register();
		ModCommands.register();
		TixLoot.register();
		ModSounds.register();
		DiscLoot.register();
		DiscLyrics.register();
	}

	private static int getMaterialSortIndex(Item item) {
		String path = BuiltInRegistries.ITEM.getKey(item).getPath();
		String name = path.substring(path.lastIndexOf('/') + 1);

		if (!name.endsWith("template")) {
			return 100;
		}

		if (name.contains("modern")) return 1;
		if (name.contains("old")) return 2;
		if (name.contains("cold")) return 3;
		if (name.contains("toxic")) return 4;
		if (name.contains("ghost")) return 5;
		// nts: remember to add here when releasing new demos
		return 50;
	}

	private static void registerMaterial(String itemName) {
		String registryPath = "materials/" + itemName;
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, registryPath));

		Item.Properties properties = new Item.Properties().setId(itemKey);
		if (BIG_STACKS.contains(itemName)) {
			properties = properties.stacksTo(99);
		}

		Item materialItem = new Item(properties);

		Registry.register(BuiltInRegistries.ITEM, itemKey, materialItem);
		modMaterials.add(materialItem);
		REGISTERED_MATERIALS.put(itemName, materialItem);

		if (itemName.endsWith("template")) {
			TEMPLATES.add(materialItem);
		}

		discPrices.put(materialItem, DiscPricing.getPrice("materials", itemName));
	}

	private static void registerDisc(String trackName, String chapter) {
		String registryPath = chapter + "/" + trackName;

		Identifier audioId = Identifier.fromNamespaceAndPath(MOD_ID, "music." + chapter + "." + trackName);
		ResourceKey<SoundEvent> soundKey = ResourceKey.create(Registries.SOUND_EVENT, audioId);
		SoundEvent audioEvent = SoundEvent.createVariableRangeEvent(audioId);
		Registry.register(BuiltInRegistries.SOUND_EVENT, soundKey, audioEvent);

		ResourceKey<Item> discKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, registryPath));
		ResourceKey<JukeboxSong> songData = ResourceKey.create(Registries.JUKEBOX_SONG, Identifier.fromNamespaceAndPath(MOD_ID, registryPath));

		// boss status + tooltip style come from assets/discs/bosses/<bossId>.json
		String bossId = BossData.bossIdForDisc(registryPath);
		boolean isBoss = bossId != null;

		Item.Properties properties = new Item.Properties()
				.setId(discKey)
				.stacksTo(1)
				.jukeboxPlayable(songData)
				.rarity(Rarity.EPIC);

		if (bossId != null) {
			properties = properties.component(DataComponents.TOOLTIP_STYLE, Identifier.fromNamespaceAndPath(MOD_ID, bossId));
		}

		Item vinyl = new Item(properties) {
			@Override
			public Component getName(ItemStack stack) {
				BossData.Boss boss = BossData.forDisc(registryPath);
				return boss == null ? PLAIN_NAME : boss.displayName(System.currentTimeMillis());
			}
		};

		Registry.register(BuiltInRegistries.ITEM, discKey, vinyl);

		if (tabIcon == null || ("demo4".equals(chapter) && "theancients".equals(trackName))) {
			tabIcon = vinyl;
		}

		discsPerChapter.computeIfAbsent(chapter, _ -> new ArrayList<>(16)).add(vinyl);
		discPrices.put(vinyl, DiscPricing.getPrice(chapter, trackName));
		REGISTERED_DISCS.put(MOD_ID + ":" + registryPath, vinyl);

		if (isBoss) {
			bossDiscs.add(vinyl);
		}
	}
}
