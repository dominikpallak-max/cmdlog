package pl.cmdlog;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

public class CmdLogMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("cmdlog");

    @Override
    public void onInitialize() {
        CommandLogger.ensureFiles();

        PayloadTypeRegistry.playC2S().register(QueryPayload.ID, QueryPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ResultPayload.ID, ResultPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(QueryPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            String nick = payload.nick().trim();
            String date = payload.date().trim();

            if (!CommandLogger.isAdmin(player.getName().getString())) {
                ServerPlayNetworking.send(player,
                        new ResultPayload(nick, date, "Brak uprawnien (dodaj nick do cmdlog/admins.txt).", List.of()));
                return;
            }

            LocalDate parsed;
            try {
                parsed = LocalDate.parse(date);
            } catch (DateTimeParseException e) {
                ServerPlayNetworking.send(player,
                        new ResultPayload(nick, date, "Zly format daty (uzyj RRRR-MM-DD).", List.of()));
                return;
            }

            List<String> lines = CommandLogger.query(nick, parsed);
            ServerPlayNetworking.send(player, new ResultPayload(nick, date, "", lines));
        });

        LOGGER.info("CmdLog zaladowany.");
    }
}
