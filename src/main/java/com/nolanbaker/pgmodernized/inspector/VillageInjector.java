package com.nolanbaker.pgmodernized.inspector;

import com.mojang.datafixers.util.Pair;
import com.nolanbaker.pgmodernized.PgmConfig;
import com.nolanbaker.pgmodernized.PowerGridModernized;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.ListPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Puts the Electrical Training Center in villages. Template pools cannot be extended from a
 * datapack, so the loaded pools are edited in place just before the server starts, two ways:
 * <ul>
 * <li>Every element of each village's {@code town_centers} pool is wrapped in a list element that
 * also places the centre's annex template, a copy of the building standing a few blocks east of the
 * well. That is one training centre per village, guaranteed, with no weights involved.</li>
 * <li>The plain building is added to each {@code houses} pool at a configurable weight for extras.</li>
 * </ul>
 * Only villages generated after the mod was installed get one: existing chunks are never changed.
 */
public final class VillageInjector {
    public static final String HOUSE = PowerGridModernized.MOD_ID + ":village/training_center";
    public static final String ANNEX = PowerGridModernized.MOD_ID + ":village/training_center_annex_";
    private static final List<String> VILLAGES = List.of("plains", "desert", "savanna", "snowy", "taiga");

    private VillageInjector() {}

    @SubscribeEvent
    public static void onServerStarting(ServerAboutToStartEvent event) {
        var access = event.getServer().registryAccess();
        var pools = access.registryOrThrow(Registries.TEMPLATE_POOL);
        var processors = access.registryOrThrow(Registries.PROCESSOR_LIST);
        var empty = processors.getHolderOrThrow(ResourceKey.create(Registries.PROCESSOR_LIST, ResourceLocation.withDefaultNamespace("empty")));
        int centres = 0, houses = 0;
        int weight = PgmConfig.TRAINING_CENTER_WEIGHT.get();
        boolean atCentre = PgmConfig.TRAINING_CENTER_AT_CENTER.get();
        for(var village : VILLAGES) {
            try {
                if(atCentre && wrapCentres(pools.get(ResourceLocation.withDefaultNamespace("village/" + village + "/town_centers")), village, empty))
                    ++centres;
                if(weight > 0 && addHouse(pools.get(ResourceLocation.withDefaultNamespace("village/" + village + "/houses")), empty, weight))
                    ++houses;
            } catch(ReflectiveOperationException | ClassCastException e) {
                PowerGridModernized.LOGGER.warn("Could not add the training centre to the {} village pools", village, e);
                return;
            }
        }
        PowerGridModernized.LOGGER.info("Electrical Training Center: beside the centre of {} village types, in the house pools of {} (weight {})", centres, houses, weight);
    }

    /** Every centre element becomes [centre, annex], same weight and projection, so each village gets exactly one. */
    private static boolean wrapCentres(StructureTemplatePool pool, String village, Holder<StructureProcessorList> empty) throws ReflectiveOperationException {
        if(pool == null)
            return false;
        var raw = rawTemplates(pool);
        for(var pair : raw) {
            if(pair.getFirst() instanceof ListPoolElement)
                return true;   // already wrapped: the same registry survived a restart
        }
        var annex = StructurePoolElement.single(ANNEX + village, empty).apply(StructureTemplatePool.Projection.RIGID);
        var wrapped = new ArrayList<Pair<StructurePoolElement, Integer>>();
        for(var pair : raw) {
            var centre = pair.getFirst();
            wrapped.add(Pair.of(new ListPoolElement(List.of(centre, annex), centre.getProjection()), pair.getSecond()));
        }
        replace(pool, wrapped);
        return true;
    }

    private static boolean addHouse(StructureTemplatePool pool, Holder<StructureProcessorList> empty, int weight) throws ReflectiveOperationException {
        if(pool == null)
            return false;
        var raw = rawTemplates(pool);
        for(var pair : raw) {
            if(pair.getFirst().toString().contains(HOUSE))
                return true;
        }
        var element = StructurePoolElement.single(HOUSE, empty).apply(StructureTemplatePool.Projection.RIGID);
        var extended = new ArrayList<>(raw);
        extended.add(Pair.of(element, weight));
        replace(pool, extended);
        return true;
    }

    // ---- the pool's two private lists ----

    @SuppressWarnings("unchecked")
    private static List<Pair<StructurePoolElement, Integer>> rawTemplates(StructureTemplatePool pool) throws ReflectiveOperationException {
        Field field = StructureTemplatePool.class.getDeclaredField("rawTemplates");
        field.setAccessible(true);
        return (List<Pair<StructurePoolElement, Integer>>) field.get(pool);
    }

    private static void replace(StructureTemplatePool pool, List<Pair<StructurePoolElement, Integer>> raw) throws ReflectiveOperationException {
        Field rawField = StructureTemplatePool.class.getDeclaredField("rawTemplates");
        Field listField = StructureTemplatePool.class.getDeclaredField("templates");
        rawField.setAccessible(true);
        listField.setAccessible(true);
        var templates = new ObjectArrayList<StructurePoolElement>();
        for(var pair : raw) {
            for(int i = 0; i < pair.getSecond(); ++i)
                templates.add(pair.getFirst());
        }
        rawField.set(pool, raw);
        listField.set(pool, templates);
    }
}
