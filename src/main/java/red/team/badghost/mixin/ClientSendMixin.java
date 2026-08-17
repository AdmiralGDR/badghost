// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.mixin;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import red.team.badghost.core.PacketLog;

/**
 * Counts what the client sends, so the claim that this mod adds nothing to it can be checked.
 *
 * <p>{@code ClientCommonPacketListenerImpl#send} is the one place every outgoing packet passes
 * through on its way to the connection, and the class exists only on the client — the integrated
 * server has its own listener — so nothing here can see or touch server traffic.</p>
 *
 * <p>Deliberately an {@code @Inject} at {@code HEAD} that cannot cancel and returns nothing. An
 * observer that could alter the packet would be measuring itself. The counter it calls does nothing
 * at all unless recording was switched on, which only the self-test harness does.</p>
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientSendMixin {

    @Inject(method = "send", at = @At("HEAD"))
    private void badghost$countOutgoing(Packet<?> packet, CallbackInfo ci) {
        PacketLog.record(packet);
    }
}
