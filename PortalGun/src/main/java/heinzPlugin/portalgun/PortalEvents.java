package heinzPlugin.portalgun;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Portalgun.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PortalEvents {

    // Stores portal coordinates per player UUID
    private static final Map<UUID, BlockPos> BLUE_PORTALS = new HashMap<>();
    private static final Map<UUID, BlockPos> ORANGE_PORTALS = new HashMap<>();
    // Prevents instant ping-pong teleporting back and forth
    private static final Map<UUID, Long> TELEPORT_COOLDOWNS = new HashMap<>();

    private static final int COOLDOWN_TICKS = 40; // 2 seconds (20 ticks = 1 second)

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        // Run ONLY on the server side
        if (event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Check if holding a Blaze Rod
        if (player.getMainHandItem().getItem() != Items.BLAZE_ROD) {
            return;
        }

        ServerLevel level = player.serverLevel();
        // Raycast 50 blocks ahead to see what block the player is aiming at
        HitResult hit = player.pick(50.0D, 0.0F, false);

        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hit;
            BlockPos targetPos = blockHit.getBlockPos().relative(blockHit.getDirection());
            UUID playerId = player.getUUID();

            if (player.isShiftKeyDown()) {
                // Shift + Right-Click block: Set Orange Portal
                ORANGE_PORTALS.put(playerId, targetPos);
                player.sendSystemMessage(Component.literal("§6Orange Portal placed at " + targetPos.toShortString()));
                level.playSound(null, targetPos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 1.2F);
            } else {
                // Normal Right-Click block: Set Blue Portal
                BLUE_PORTALS.put(playerId, targetPos);
                player.sendSystemMessage(Component.literal("§bBlue Portal placed at " + targetPos.toShortString()));
                level.playSound(null, targetPos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 0.8F);
            }
        } else if (hit.getType() == HitResult.Type.MISS && player.isShiftKeyDown()) {
            // Shift + Right-Click air: Remove both portals
            UUID playerId = player.getUUID();
            BlockPos removedBlue = BLUE_PORTALS.remove(playerId);
            BlockPos removedOrange = ORANGE_PORTALS.remove(playerId);
            TELEPORT_COOLDOWNS.remove(playerId);

            if (removedBlue != null || removedOrange != null) {
                player.sendSystemMessage(Component.literal("§7Portals closed."));
                level.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 1.0F);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent.Post event) {
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }

        UUID id = player.getUUID();
        BlockPos blue = BLUE_PORTALS.get(id);
        BlockPos orange = ORANGE_PORTALS.get(id);
        ServerLevel level = player.serverLevel();
        // ... keep the rest of the method the same

        // 1. Spawn particles every 4 ticks so you can see both portals in the world
        if (player.tickCount % 4 == 0) {
            if (blue != null) {
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        blue.getX() + 0.5, blue.getY() + 1.0, blue.getZ() + 0.5,
                        6, 0.2, 0.5, 0.2, 0.01);
            }
            if (orange != null) {
                level.sendParticles(ParticleTypes.FLAME,
                        orange.getX() + 0.5, orange.getY() + 1.0, orange.getZ() + 0.5,
                        6, 0.2, 0.5, 0.2, 0.01);
            }
        }

        // 2. Both portals must be placed before teleporting
        if (blue == null || orange == null) {
            return;
        }

        // 3. Check cooldown so arriving inside the exit portal doesn't immediately send you back
        long gameTime = level.getGameTime();
        if (gameTime < TELEPORT_COOLDOWNS.getOrDefault(id, 0L)) {
            return;
        }

        // 4. Teleport when stepping into either portal
        if (isInsidePortal(player, blue)) {
            teleportToPortal(player, orange, gameTime);
        } else if (isInsidePortal(player, orange)) {
            teleportToPortal(player, blue, gameTime);
        }
    }


    private static boolean isInsidePortal(ServerPlayer player, BlockPos portalPos) {
        Vec3 portalCenter = Vec3.atBottomCenterOf(portalPos);
        double dx = Math.abs(player.getX() - portalCenter.x);
        double dz = Math.abs(player.getZ() - portalCenter.z);
        double dy = Math.abs(player.getY() - portalCenter.y);
        return dx <= 0.8 && dz <= 0.8 && dy <= 1.2;
    }

    private static void teleportToPortal(ServerPlayer player, BlockPos destination, long gameTime) {
        TELEPORT_COOLDOWNS.put(player.getUUID(), gameTime + COOLDOWN_TICKS);
        player.resetFallDistance(); // Prevents fall damage when dropping through a floor portal
        player.teleportTo(destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5);
        player.serverLevel().playSound(null, destination, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
    }
}