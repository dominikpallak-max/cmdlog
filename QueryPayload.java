package pl.cmdlog;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Klient -> serwer: "pokaz komendy gracza <nick> z dnia <date>" (date = RRRR-MM-DD). */
public record QueryPayload(String nick, String date) implements CustomPayload {
    public static final Id<QueryPayload> ID = new Id<>(Identifier.of("cmdlog", "query"));
    public static final PacketCodec<RegistryByteBuf, QueryPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, QueryPayload::nick,
            PacketCodecs.STRING, QueryPayload::date,
            QueryPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
