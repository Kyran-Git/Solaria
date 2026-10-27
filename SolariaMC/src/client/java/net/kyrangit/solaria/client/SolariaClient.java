package net.kyrangit.solaria.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.kyrangit.solaria.SolariaMod;
import net.minecraft.network.chat.Component;

/** Client-only entrypoint; future HTTP and UI code must stay in this source set. */
public final class SolariaClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SolariaMod.config();
		// Install the scanner request/open bridge. The bridge is thread-safe
		// and only touches Minecraft.setScreen on the client tick thread.
		ScannerClientBridge.install();
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
			net.kyrangit.solaria.scan.ScanPackets.Result.TYPE, (packet, context) -> {
				if (context.client().screen instanceof ScannerScreen scanner) scanner.receiveResult(packet);
			});
		// Client-side /solariascan command: lets students open the camera UI
		// without having the item in hand. Registered on the client so each
		// player's own client opens their own screen (not the host's).
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
			dispatcher.register(ClientCommands.literal("solariascan").executes(context -> {
				ScannerClientBridge.requestOpen();
				context.getSource().sendFeedback(Component.translatable("solaria.scan.command.opening"));
				return 1;
			}))
		);
	}
}

