package net.kyrangit.solaria.scan;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.kyrangit.solaria.config.ConfigManager;
import net.kyrangit.solaria.service.AiServiceClient;
import net.kyrangit.solaria.service.RuntimeResponses;
import net.kyrangit.solaria.service.ServiceException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.*;
import java.util.concurrent.*;

/**
 * Host-owned sessions. HTTP never runs on the game thread; stale completions are discarded.
 */
public final class HostScanSessions {
    private final ConfigManager config;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private ThreadPoolExecutor workers;

    private static final class Session {
        String id, requestId = UUID.randomUUID().toString().replace("-", ""),
            participant = UUID.randomUUID().toString().replace("-", "");
        ScanUpload upload;
        long deadline;
        int total;
        Future<?> job;
        RuntimeResponses.Prediction prediction;
        ReviewGate gate;
        long nextPoll;
        boolean polling;
        RuntimeResponses.Review review;

        void cancel() {
            if (job != null) job.cancel(true);
        }
    }

    public HostScanSessions(ConfigManager config) {
        this.config = config;
    }

    public void install() {
        ServerPlayNetworking.registerGlobalReceiver(ScanPackets.Upload.TYPE, (p, c) -> receive(c.server(), c.player(), p));
        ServerPlayNetworking.registerGlobalReceiver(ScanPackets.Decision.TYPE, (p, c) -> {
            Session s = sessions.get(c.player().getUUID());
            if (s == null || !s.id.equals(p.id())) return;
            if (!p.agree()) {
                remove(c.player().getUUID());
                return;
            }
            if (s.gate == null) return;
            if (!s.gate.agree(p.revision())) {
                feedback(c.player(), s);
                return;
            }
            feedback(c.player(), s);
            s.nextPoll = 0;
        });
        ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> {
            remove(h.getPlayer().getUUID());
            cooldowns.remove(h.getPlayer().getUUID());
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (UUID id : List.copyOf(sessions.keySet())) {
                Session s = sessions.get(id);
                if (s.prediction != null && s.prediction.scanId() != null && !s.polling
                    && System.nanoTime() >= s.nextPoll) poll(server, id, s);
                if (System.nanoTime() >= s.deadline) {
                    ServerPlayer p = server.getPlayerList().getPlayer(id);
                    if (p != null) send(p, s.id, "error", null, "SCAN_TIMEOUT");
                    remove(id);
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            sessions.values().forEach(Session::cancel);
            sessions.clear();
            cooldowns.clear();
            if (workers != null) {
                workers.shutdownNow();
                workers = null;
            }
        });
    }

    private void remove(UUID id) {
        Session s = sessions.remove(id);
        if (s != null) s.cancel();
        if (workers != null) workers.purge();
    }

    private void receive(MinecraftServer server, ServerPlayer player, ScanPackets.Upload p) {
        UUID playerId = player.getUUID();
        if (!p.id().matches("[a-f0-9]{32}")) return;
        if (player.gameMode.getGameModeForPlayer() != GameType.CREATIVE) {
            send(player, p.id(), "error", null, "PERMISSION_DENIED");
            return;
        }
        long now = System.nanoTime();
        Session s = sessions.get(playerId);
        try {
            if (p.sequence() == 0) {
                if (now < cooldowns.getOrDefault(playerId, 0L) || sessions.size() >= 32 && s == null) {
                    send(player, p.id(), "error", null, "QUEUE_FULL");
                    return;
                }
                cooldowns.put(playerId, now + 2_000_000_000L);
                remove(playerId);
                var policy = config.current();
                s = new Session();
                s.id = p.id();
                s.total = p.total();
                s.deadline = now + policy.scanChunkTimeoutMs() * 1_000_000L;
                s.upload = new ScanUpload(p.total(), policy.maxScanBytes(), policy.maxScanChunks(), s.deadline);
                sessions.put(playerId, s);
            }
            if (s == null || !s.id.equals(p.id()) || s.upload == null) return;
            if (p.total() != s.total) throw new IllegalArgumentException();
            if (!s.upload.append(p.sequence(), p.bytes(), now)) return;
            byte[] image = s.upload.finish();
            s.upload = null;
            if (image.length < 2 || image[0] != (byte) 255 || image[1] != (byte) 216)
                throw new IllegalArgumentException();
            var policy = config.current();
            s.deadline = now + (2L * policy.requestTimeoutMs() + 5000) * 1_000_000L;
            send(player, s.id, "classifying", null, "");
            if (workers == null) workers = new ThreadPoolExecutor(4, 4, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(28), new ThreadPoolExecutor.AbortPolicy());
            Session current = s;
            s.job = workers.submit(() -> {
                RuntimeResponses.Prediction result = null;
                String failure = "";
                try (var client = AiServiceClient.fromEnvironment(policy)) {
                    result = client.classify(image, "image/jpeg", current.requestId, current.participant);
                } catch (ServiceException e) {
                    failure = e.reasonCode().name();
                } catch (RuntimeException e) {
                    failure = "SERVICE_UNAVAILABLE";
                }
                var prediction = result;
                var reason = failure;
                server.execute(() -> {
                    if (sessions.get(playerId) != current) return;
                    ServerPlayer online = server.getPlayerList().getPlayer(playerId);
                    if (online == null) {
                        remove(playerId);
                        return;
                    }
                    current.prediction = prediction;
                    if (prediction != null) current.gate = new ReviewGate(prediction.label(),
                        prediction.accepted() && prediction.qualityPassed());
                    current.deadline = System.nanoTime() + 600_000_000_000L;
                    send(online, current.id, prediction == null ? "error" : "prediction", prediction, reason);
                });
            });
        } catch (RejectedExecutionException e) {
            remove(playerId);
            send(player, p.id(), "error", null, "QUEUE_FULL");
        } catch (IllegalArgumentException e) {
            remove(playerId);
            send(player, p.id(), "error", null, "INPUT_INVALID");
        }
    }

    private void poll(MinecraftServer server, UUID id, Session s) {
        s.nextPoll = System.nanoTime() + 3_000_000_000L;
        s.polling = true;
        var policy = config.current();
        int revision = s.gate.revision();
        String application = s.gate.application();
        var acknowledged = s.review;
        try {
            s.job = workers.submit(() -> {
                RuntimeResponses.Review result = null;
                String failure = "";
                try (var client = AiServiceClient.fromEnvironment(policy)) {
                    // Acknowledgements are CAS writes: a newer teacher revision rejects this write.
                    if (acknowledged != null && revision > 0 && acknowledged.revision() == revision)
                        client.acknowledge(acknowledged, application);
                    result = client.review(s.prediction.scanId(), s.requestId, s.participant, revision);
                } catch (ServiceException e) {
                    failure = e.reasonCode().name();
                } catch (RuntimeException e) {
                    failure = "SERVICE_UNAVAILABLE";
                }
                var review = result;
                var reason = failure;
                server.execute(() -> {
                    if (sessions.get(id) != s) return;
                    s.polling = false;
                    ServerPlayer player = server.getPlayerList().getPlayer(id);
                    if (player == null) { remove(id); return; }
                    if (review == null) {
                        // Conflict means fetch again without replaying the old acknowledgement.
                        if (reason.equals("REVIEW_CONFLICT")) s.review = null;
                        if (reason.equals("NOT_FOUND") || reason.equals("SERVICE_PROTOCOL_ERROR")) {
                            send(player, s.id, "error", null, reason);
                            remove(id);
                        }
                        return;
                    }
                    s.review = review;
                    if (review.teacherDecision() && s.gate.apply(review.revision(), review.finalLabel(), review.accepted()))
                        feedback(player, s);
                });
            });
        } catch (RejectedExecutionException e) {
            s.polling = false;
        }
    }

    private static void feedback(ServerPlayer player, Session s) {
        JsonObject json = new JsonObject();
        json.addProperty("status", s.gate.ready() ? "ready" : !s.gate.accepted() ? "rejected"
            : s.gate.consent() ? "waiting_teacher" : "correction");
        json.addProperty("revision", s.gate.revision());
        json.addProperty("label", s.gate.label() == null ? "unknown" : s.gate.label().id());
        json.addProperty("mock", s.prediction.mock());
        json.addProperty("confidence", s.prediction.confidence());
        ServerPlayNetworking.send(player, new ScanPackets.Result(s.id, json.toString()));
    }

    private static void send(ServerPlayer player, String id, String status, RuntimeResponses.Prediction p, String reason) {
        JsonObject json = new JsonObject();
        json.addProperty("status", status);
        json.addProperty("reason", reason);
        if (p != null) {
            json.addProperty("label", p.label() == null ? "unknown" : p.label().id());
            json.addProperty("confidence", p.confidence());
            json.addProperty("accepted", p.accepted());
            json.addProperty("quality", p.qualityReason());
            json.addProperty("mock", p.mock());
        }
        ServerPlayNetworking.send(player, new ScanPackets.Result(id, json.toString()));
    }
}
