package net.kyrangit.solaria.dimension;

import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.kyrangit.solaria.SolariaMod;
import net.kyrangit.solaria.blueprint.InternalStructure;
import net.kyrangit.solaria.blueprint.InternalStructure.Layer;
import net.kyrangit.solaria.blueprint.InternalStructure.LayerSpan;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import org.joml.Vector3f;

/**
 * Spawns persistent floating {@link Display.TextDisplay} callouts in front of each planet's
 * standalone {@link InternalStructure} column once per dimension.
 */
public final class InternalStructureLabels {

	private static final float LABEL_Z_OFFSET = 2.5f;

	private InternalStructureLabels() {
	}

	/**
	 * Lazy holder so attachment registration only loads inside Fabric's mixin runtime,
	 * keeping headless blueprint/generator unit tests safe.
	 */
	public static final class Attachment {
		public static final AttachmentType<Boolean> SPAWNED = AttachmentRegistry.create(
			SolariaMod.id("internal_labels_v1"), b -> b.persistent(Codec.BOOL));

		private Attachment() {
		}
	}

	/** Classloads {@link Attachment} during mod initialization. */
	public static void register() {
		var unused = Attachment.SPAWNED;
	}

	/**
	 * Spawns header and per-stratum floating labels along the arrival-facing (+Z) face of the
	 * internal structure column if this dimension has not received them yet.
	 */
	public static void ensureLabels(ServerLevel level, PlanetChunkGenerator generator) {
		if (level == null || generator == null) {
			return;
		}
		InternalStructure structure = generator.internalStructure();
		if (structure == null || level.getAttachedOrElse(Attachment.SPAWNED, false)) {
			return;
		}

		double labelX = structure.centerX() + 0.5;
		double labelZ = structure.maxZ() + LABEL_Z_OFFSET;
		// Force-load the target chunk before adding entities so distant columns (e.g. Jupiter/Saturn) still receive labels.
		level.getChunk(((int) Math.floor(labelX)) >> 4, ((int) Math.floor(labelZ)) >> 4);
		String id = structure.astronomyClass().id();
		String title = Character.toUpperCase(id.charAt(0)) + id.substring(1) + " Interior";

		MutableComponent header = Component.literal(title)
			.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
			.append(Component.literal("\nCore (bottom) -> Surface (top)")
				.withStyle(ChatFormatting.WHITE))
			.append(Component.literal("\nThin crusts & atmospheres are not to scale")
				.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		spawnTextDisplay(level, labelX, structure.maxY() + 4.5, labelZ,
			header, 260, 0xB80B101B, 3.0f, 4.0f);

		for (LayerSpan span : structure.layers()) {
			Layer layer = span.layer();
			double midY = (span.minY() + span.maxY()) * 0.5 + 0.5;
			String scaleNote = layer.toScale()
				? " (" + layer.scaleLabel() + ")"
				: " (" + layer.scaleLabel() + ", not to scale)";
			MutableComponent body = Component.literal(layer.name())
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
				.append(Component.literal(scaleNote)
					.withStyle(layer.toScale() ? ChatFormatting.YELLOW : ChatFormatting.AQUA))
				.append(Component.literal("\n" + layer.composition())
					.withStyle(ChatFormatting.GRAY));
			spawnTextDisplay(level, labelX, midY, labelZ,
				body, 220, 0xA00D1320, 2.0f, 3.0f);
		}

		level.setAttached(Attachment.SPAWNED, true);
	}

	private static void spawnTextDisplay(
		ServerLevel level,
		double x,
		double y,
		double z,
		Component text,
		int lineWidth,
		int backgroundColor,
		float scale,
		float viewRange
	) {
		Display.TextDisplay display = new Display.TextDisplay(EntityType.TEXT_DISPLAY, level);
		display.setPos(x, y, z);
		display.setText(text);
		display.setLineWidth(lineWidth);
		display.setBackgroundColor(backgroundColor);
		display.setFlags(Display.TextDisplay.FLAG_SHADOW);
		display.setBillboardConstraints(Display.BillboardConstraints.VERTICAL);
		display.setBrightnessOverride(Brightness.FULL_BRIGHT);
		display.setTransformation(new Transformation(null, null, new Vector3f(scale, scale, scale), null));
		display.setViewRange(viewRange);
		display.setInvulnerable(true);
		display.setNoGravity(true);
		level.addFreshEntity(display);
	}
}
