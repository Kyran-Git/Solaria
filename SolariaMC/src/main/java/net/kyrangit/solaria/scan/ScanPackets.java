package net.kyrangit.solaria.scan;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.kyrangit.solaria.SolariaMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Versioned bounded game packets. Never carry tokens, usernames or client-selected classifications.
 */
public final class ScanPackets {
    private ScanPackets() {
    }

    public record Upload(String id, int total, int sequence, byte[] bytes) implements CustomPacketPayload {
        public static final Type<Upload> TYPE = new Type<>(SolariaMod.id("scan_upload_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Upload> CODEC = CustomPacketPayload.codec(
                (p, b) -> {
                    b.writeUtf(p.id, 64);
                    b.writeVarInt(p.total);
                    b.writeVarInt(p.sequence);
                    b.writeByteArray(p.bytes);
                },
                b -> new Upload(b.readUtf(64), b.readVarInt(), b.readVarInt(), b.readByteArray(ScanUpload.CHUNK_BYTES)));

        @Override
        public Type<Upload> type() {
            return TYPE;
        }
    }

    public record Decision(String id, int revision, boolean agree) implements CustomPacketPayload {
        public static final Type<Decision> TYPE = new Type<>(SolariaMod.id("scan_decision_v2"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Decision> CODEC = CustomPacketPayload.codec(
                (p, b) -> {
                    b.writeUtf(p.id, 64);
                    b.writeVarInt(p.revision);
                    b.writeBoolean(p.agree);
                },
                b -> new Decision(b.readUtf(64), b.readVarInt(), b.readBoolean()));

        @Override
        public Type<Decision> type() {
            return TYPE;
        }
    }

    /**
     * Status JSON is generated only by the host after validating the service response.
     */
    public record Result(String id, String json) implements CustomPacketPayload {
        public static final Type<Result> TYPE = new Type<>(SolariaMod.id("scan_result_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Result> CODEC = CustomPacketPayload.codec(
                (p, b) -> {
                    b.writeUtf(p.id, 64);
                    b.writeUtf(p.json, 4096);
                },
                b -> new Result(b.readUtf(64), b.readUtf(4096)));

        @Override
        public Type<Result> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Upload.TYPE, Upload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Decision.TYPE, Decision.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Result.TYPE, Result.CODEC);
    }
}
