package heinzPlugin.portalgun;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

public class PortanGunItem extends Item {
    public PortanGunItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);

        // Run portal logic on the server side only
        if (!level.isClientSide()) {
            // Raycast up to 50 blocks where the player is looking
            HitResult hit = player.pick(50.0D, 0.0F, false);

            level.playSound(null, player.blockPosition(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 1.2F);

            player.displayClientMessage(Component.literal("Fired portal at: " + hit.getLocation()), true);


        }
        // biar tak bisa di spam portal gun nya
        player.getCooldowns().addCooldown(this, 15);

        return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide());

    }
}
