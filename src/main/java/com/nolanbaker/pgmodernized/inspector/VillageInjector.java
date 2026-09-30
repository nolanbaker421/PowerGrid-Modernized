package com.nolanbaker.pgmodernized.inspector;

import com.mojang.datafixers.util.Pair;
import com.nolanbaker.pgmodernized.PowerGridModernized;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Adds the Electrical Training Center to every village's house pool. Template pools cannot be
 * extended from a datapack, so the loaded pools are edited in place just before the server starts.
 */
public final class VillageInjector {
    public static final String TEMPLATE = PowerGridModernized.MOD_ID + ":village/training_center";
    /** Against pool weights totalling roughly 70 to 90: about one centre per village or two. */
    public static final int WEIGHT = 3;
    private static final List<String> VILLAGES = List.of("plains", "desert", "savanna", "snowy", "taiga");

    private VillageInjector() {}

    @SubscribeEvent
    public static void onServerStarting(ServerAboutToStartEvent event) {
        var access = event.getServer().registryAccess();
        var pools = access.registryOrThrow(Registries.TEMPLATE_POOL);
        var processors = access.registryOrThrow(Registries.PROCESSOR_LIST);
        var empty = processors.getHolderOrThrow(ResourceKey.create(Registries.PROCESSOR_LIST, ResourceLocation.withDefaultNamespace("empty")));
        var element = StructurePoolElement.single(TEMPLATE, empty).apply(StructureTemplatePool.Projection.RIGID);
        for(var village : VILLAGES) {
            var pool = pools.get(ResourceLocation.withDefaultNamespace("village/" + village + "/houses"));
            if(pool == null)
                continue;
            try {
                inject(pool, element);
            } catch(ReflectiveOperationException | ClassCastException e) {
                PowerGridModernized.LOGGER.warn("Could not add the training centre to the {} village pool", village, e);
                return;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void inject(StructureTemplatePool pool, StructurePoolElement element) throws ReflectiveOperationException {
        Field rawField = StructureTemplatePool.class.getDeclaredField("rawTemplates");
        Field listField = StructureTemplatePool.class.getDeclaredField("templates");
        rawField.setAccessible(true);
        listField.setAccessible(true);
        var raw = (List<Pair<StructurePoolElement, Integer>>) rawField.get(pool);
        for(var pair : raw) {
            if(pair.getFirst().toString().contains(TEMPLATE))
                return;   // already there: the same registry survived a restart
        }
        var newRaw = new ArrayList<>(raw);
        newRaw.add(Pair.of(element, WEIGHT));
        rawField.set(pool, newRaw);
        var templates = (ObjectArrayList<StructurePoolElement>) listField.get(pool);
        for(int i = 0; i < WEIGHT; ++i)
            templates.add(element);
    }
}
