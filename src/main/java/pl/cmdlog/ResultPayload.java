package pl.cmdlog;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

public record ResultPayload(String nick, String date, String error, List<String> lines) implements CustomPayload {
    public static final Id<ResultPayload> ID = new Id<>(Identifier.of("cmdlog", "result"));
    public static final PacketCodec<RegistryByteBuf, ResultPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, ResultPayload::nick,
            PacketCodecs.STRING, ResultPayload::date,
            PacketCodecs.STRING, ResultPayload::error,
            PacketCodecs.STRING.collect(PacketCodecs.toList()), ResultPayload::lines,
            ResultPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
