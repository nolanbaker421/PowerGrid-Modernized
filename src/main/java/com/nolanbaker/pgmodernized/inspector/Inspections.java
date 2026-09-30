package com.nolanbaker.pgmodernized.inspector;

import com.nolanbaker.pgmodernized.PgmConfig;
import com.nolanbaker.pgmodernized.PowerGridModernized;
import com.nolanbaker.pgmodernized.registry.ModEntities;
import com.nolanbaker.pgmodernized.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import org.patryk3211.powergrid.electricity.base.IElectric;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps track of who has been doing electrical work lately and, now and then, sends an
 * {@link ElectricalInspectorEntity} their way. Also teaches village smiths to buy copper scrap.
 */
public final class Inspections {
    /** Emeralds for a stack of scrap at any village smith. */
    public static final int SCRAP_PER_EMERALD = 8;

    private static final Map<UUID, Long> lastWork = new HashMap<>();
    private static final Map<UUID, BlockPos> lastWorkPos = new HashMap<>();
    private static final Map<UUID, Long> nextVisit = new HashMap<>();
    private static final Map<UUID, UUID> active = new HashMap<>();

    private Inspections() {}

    /**
     * Any right-click, placement, pull or splice on an electrical block counts as work. An inspector
     * already about (idle from an egg, or on his way out) who sees it comes over and asks, creative
     * players included.
     */
    public static void noteWork(ServerPlayer player, BlockPos pos) {
        lastWork.put(player.getUUID(), player.level().getGameTime());
        lastWorkPos.put(player.getUUID(), pos.immutable());
        int range = PgmConfig.INSPECTOR_NOTICE_RANGE.get();
        var nearby = player.serverLevel().getEntitiesOfClass(ElectricalInspectorEntity.class,
                player.getBoundingBox().inflate(range), ElectricalInspectorEntity::available);
        if(nearby.isEmpty())
            return;
        var inspector = nearby.get(0);
        inspector.assign(player, pos);
        active.put(player.getUUID(), inspector.getUUID());
    }

    public static boolean isElectrical(BlockState state) {
        var key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return key.getNamespace().equals(PowerGridModernized.MOD_ID) || key.getNamespace().equals("powergrid");
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if(event.getEntity() instanceof ServerPlayer player && isElectrical(event.getPlacedBlock()))
            noteWork(player, event.getPos());
    }

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if(!(event.getEntity() instanceof ServerPlayer player))
            return;
        if(isElectrical(event.getLevel().getBlockState(event.getPos())) || IElectric.getAt(event.getLevel(), event.getPos()) != null)
            noteWork(player, event.getPos());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        var id = event.getEntity().getUUID();
        lastWork.remove(id);
        lastWorkPos.remove(id);
        active.remove(id);
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if(!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0)
            return;
        if(player.isCreative() || player.isSpectator())
            return;
        var level = player.serverLevel();
        long now = level.getGameTime();
        var id = player.getUUID();
        var current = active.get(id);
        if(current != null) {
            if(level.getEntity(current) instanceof ElectricalInspectorEntity inspector && inspector.isAlive())
                return;
            active.remove(id);
            nextVisit.put(id, now + PgmConfig.INSPECTOR_COOLDOWN_SECONDS.get() * 20L);
        }
        Long worked = lastWork.get(id);
        if(worked == null || now - worked > PgmConfig.INSPECTOR_WORK_WINDOW_SECONDS.get() * 20L)
            return;
        if(nextVisit.getOrDefault(id, 0L) > now)
            return;
        int chance = PgmConfig.INSPECTOR_CHANCE.get();
        if(chance <= 0 || level.random.nextInt(chance) != 0)
            return;
        var inspector = spawn(level, player);
        if(inspector != null)
            active.put(id, inspector.getUUID());
    }

    /** Puts him on the ground twelve to twenty blocks off, out of sight if the terrain allows. */
    private static ElectricalInspectorEntity spawn(ServerLevel level, ServerPlayer player) {
        var random = level.random;
        for(int attempt = 0; attempt < 12; ++attempt) {
            double angle = random.nextDouble() * Math.PI * 2;
            double range = 12 + random.nextDouble() * 8;
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * range);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * range);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            var pos = new BlockPos(x, y, z);
            if(Math.abs(y - player.getY()) > 12)
                continue;
            if(!level.getBlockState(pos.below()).isSolid() || !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                    || !level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty())
                continue;
            var inspector = ModEntities.ELECTRICAL_INSPECTOR.create(level);
            if(inspector == null)
                return null;
            inspector.moveTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360f, 0f);
            inspector.assign(player, lastWorkPos.get(player.getUUID()));
            if(!level.addFreshEntity(inspector))
                return null;
            return inspector;
        }
        return null;
    }

    // ---- scrap buyers ----

    @SubscribeEvent
    public static void onTrades(VillagerTradesEvent event) {
        var type = event.getType();
        if(type != VillagerProfession.TOOLSMITH && type != VillagerProfession.ARMORER && type != VillagerProfession.WEAPONSMITH)
            return;
        event.getTrades().get(1).add((trader, random) ->
                new MerchantOffer(new ItemCost(ModItems.COPPER_SCRAP.get(), SCRAP_PER_EMERALD), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
    }
}
