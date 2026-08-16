// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost.utils;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.network.protocol.Packet;
import red.team.badghost.core.ClientContext;

public final class NetworkUtils {
    private NetworkUtils() {}

    public static void send(Packet<?> packet) {
        ClientPacketListener connection = ClientContext.getClient().getConnection();
        if (connection != null) {
            connection.send(packet);
        }
    }

    /**
     * Sends a packet built with a live prediction sequence id.
     *
     * <p>The action must run <em>inside</em> the handler's scope: local block edits are only
     * recorded as predictions while {@code isPredicting()} holds, and without that record the
     * server's acknowledgement cannot roll anything back, which strands ghost blocks. This
     * mirrors {@code MultiPlayerGameMode#startPrediction} exactly.</p>
     */
    public static void sendSequenced(PredictiveAction action) {
        ClientLevel level = ClientContext.getLevel();
        ClientPacketListener connection = ClientContext.getClient().getConnection();
        if (level == null || connection == null) {
            return;
        }

        try (BlockStatePredictionHandler handler = level.getBlockStatePredictionHandler().startPredicting()) {
            connection.send(action.predict(handler.currentSequence()));
        }
    }
}
