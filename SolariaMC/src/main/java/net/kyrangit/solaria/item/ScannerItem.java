package net.kyrangit.solaria.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * Phase 4 camera. Right-click opens the scanner screen on the client.
 *
 * <p>Capture, upload, and classification are not wired yet; the screen's
 * state machine ({@code ScanState}) makes that explicit instead of
 * pretending a scan succeeded. This item exists so the full gameplay path
 * -- holding a camera, right-clicking, seeing an actionable UI -- works end
 * to end today, and so later phases just need to swap the body of the
 * state transitions.
 *
 * <p>The actual screen class lives in the client source set; the common
 * side only knows that there is a request-to-open signal. We send that
 * signal via a small client-only bridge, loaded reflectively so the common
 * class never imports client-only types.
 */
public final class ScannerItem extends Item {

	public ScannerItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			// Reflective bridge: the client sourceset holds the screen class; a
			// dedicated server never loads it. Failures are swallowed so the
			// item is still safe to right-click on any environment.
			requestOpenScannerScreen();
			return InteractionResult.SUCCESS;
		}
		// Server side: nothing to validate yet (no upload path); let the client open the GUI.
		return InteractionResult.CONSUME;
	}

	@Override
	public void appendHoverText(
		ItemStack stack,
		TooltipContext context,
		TooltipDisplay display,
		Consumer<Component> tooltip,
		TooltipFlag flag
	) {
		tooltip.accept(Component.translatable("item.solariamod.scanner.tooltip.line1"));
		tooltip.accept(Component.translatable("item.solariamod.scanner.tooltip.line2"));
	}

	/**
	 * Dispatch to the client-side screen opener without a hard dependency on
	 * client-only classes. Any failure (dedicated server, missing class,
	 * initialisation error) is swallowed and ignored because the item must
	 * never crash the game on right-click.
	 */
	private static void requestOpenScannerScreen() {
		try {
			Class<?> bridge = Class.forName("net.kyrangit.solaria.client.ScannerClientBridge");
			bridge.getMethod("requestOpen").invoke(null);
		} catch (ReflectiveOperationException | LinkageError ignored) {
			// No client bridge available (dedicated server, obfuscation mismatch,
			// client sourceset not loaded). We treat the right-click as a no-op.
		}
	}
}
