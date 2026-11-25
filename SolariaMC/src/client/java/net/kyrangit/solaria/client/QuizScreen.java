package net.kyrangit.solaria.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kyrangit.solaria.client.ui.SolariaButton;
import net.kyrangit.solaria.client.ui.SolariaUiKit;
import net.kyrangit.solaria.client.ui.UiLayoutMetrics;
import net.kyrangit.solaria.quiz.QuizPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Ceramic-slate quiz terminal using {@link SolariaUiKit}. The host owns grading: this screen
 * shows the current question and returns display indices.
 */
public final class QuizScreen extends Screen {
	private static final int LINE = 10;

	private QuizPackets.Question question;
	private QuizPackets.Feedback feedback;
	private QuizPackets.Result result;
	private String body = "";
	/** Order: display indices in the chosen sequence. */
	private final List<Integer> sequence = new ArrayList<>();
	/** Match: pairing[leftIndex] = display choice index, or -1. */
	private final List<Integer> pairing = new ArrayList<>();
	private int selectedLeft;
	private int lastPick = -1;

	private final List<SolariaButton> choiceButtons = new ArrayList<>();
	private final List<SolariaButton> leftButtons = new ArrayList<>();
	private UiLayoutMetrics.QuizLayout layout;

	public QuizScreen() {
		super(Component.literal("Solaria quiz"));
	}

	public void receive(QuizPackets.Question packet) {
		question = packet;
		body = packet.body();
		feedback = null;
		result = null;
		sequence.clear();
		pairing.clear();
		for (int i = 0; i < packet.left().size(); i++) pairing.add(-1);
		selectedLeft = 0;
		lastPick = -1;
		rebuildWidgets();
	}

	public void receive(QuizPackets.Feedback packet) {
		if (question == null) return;
		feedback = packet;
		rebuildWidgets();
	}

	public void receive(QuizPackets.Result packet) {
		result = packet;
		body = packet.body();
		question = null;
		feedback = null;
		rebuildWidgets();
	}

	private UiLayoutMetrics.QuizLayout computeLayout() {
		int maxPromptW = Math.min(416, Math.max(200, this.width - 12)) - 28;
		int pLines = question == null ? 1 : this.font.split(Component.literal(question.prompt()), Math.max(60, maxPromptW)).size();
		if (result != null) {
			return UiLayoutMetrics.QuizLayout.compute(this.width, this.height, 1, -1, false, false, 2, true);
		}
		if (question == null) {
			return UiLayoutMetrics.QuizLayout.compute(this.width, this.height, 1, -1, false, false, 1, false);
		}
		boolean isMatch = "match".equals(question.kind());
		int choiceCount = isMatch ? Math.max(question.left().size(), question.choices().size()) : question.choices().size();
		int footerCount = feedback != null ? 1 : ("order".equals(question.kind()) || isMatch ? 2 : 0);
		return UiLayoutMetrics.QuizLayout.compute(
			this.width, this.height, pLines, choiceCount, isMatch, feedback != null, footerCount, false);
	}

	@Override
	protected void init() {
		choiceButtons.clear();
		leftButtons.clear();
		layout = computeLayout();
		addRenderableWidget(new SolariaButton(layout.closeButton(), Component.literal("Close"),
			SolariaButton.Style.CLOSE_ICON, b -> onClose()));

		if (result != null) {
			addRenderableWidget(new SolariaButton(layout.footerButtons().get(0), Component.literal("Play again"),
				SolariaButton.Style.PRIMARY, b -> ClientPlayNetworking.send(new QuizPackets.Start(body))));
			addRenderableWidget(new SolariaButton(layout.footerButtons().get(1), Component.literal("Close"),
				SolariaButton.Style.DEFAULT, b -> onClose()));
			return;
		}
		if (question == null) {
			addRenderableWidget(new SolariaButton(layout.footerButtons().getFirst(), Component.literal("Close"),
				SolariaButton.Style.DEFAULT, b -> onClose()));
			return;
		}

		boolean locked = feedback != null;
		List<String> choices = question.choices();
		List<Integer> correct = locked ? feedback.correctAnswer() : List.of();
		if ("match".equals(question.kind())) {
			for (int i = 0; i < question.left().size() && i < layout.leftButtons().size(); i++) {
				final int left = i;
				SolariaButton b = new SolariaButton(layout.leftButtons().get(i),
					Component.literal(question.left().get(i)), SolariaButton.Style.DEFAULT,
					x -> {
						selectedLeft = left;
						rebuildWidgets();
					}).withKeycap(letter(i));
				if (locked) {
					boolean ok = i < correct.size() && i < pairing.size() && correct.get(i).equals(pairing.get(i));
					b.withHighlight(ok ? SolariaButton.Highlight.CORRECT : SolariaButton.Highlight.WRONG);
				} else if (i == selectedLeft) {
					b.withHighlight(SolariaButton.Highlight.SELECTED);
				}
				b.active = !locked;
				leftButtons.add(addRenderableWidget(b));
			}
			for (int i = 0; i < choices.size() && i < layout.choiceButtons().size(); i++) {
				final int choice = i;
				int owner = pairing.indexOf(i);
				String tag = owner >= 0 ? " [" + letter(owner) + "]" : "";
				SolariaButton b = new SolariaButton(layout.choiceButtons().get(i),
					Component.literal(choices.get(i) + tag), SolariaButton.Style.DEFAULT,
					x -> assignMatch(choice));
				if (!locked && owner >= 0 && owner == selectedLeft) {
					b.withHighlight(SolariaButton.Highlight.SELECTED);
				}
				b.active = !locked;
				choiceButtons.add(addRenderableWidget(b));
			}
		} else {
			boolean isOrder = "order".equals(question.kind());
			for (int i = 0; i < choices.size() && i < layout.choiceButtons().size(); i++) {
				final int choice = i;
				String keycap = isOrder
					? (sequence.contains(i) ? String.valueOf(sequence.indexOf(i) + 1) : "-")
					: letter(i);
				SolariaButton b = new SolariaButton(layout.choiceButtons().get(i),
					Component.literal(choices.get(i)), SolariaButton.Style.DEFAULT,
					x -> pick(choice)).withKeycap(keycap);
				if (locked) {
					if (isOrder) {
						int pos = sequence.indexOf(i);
						if (pos >= 0) {
							boolean ok = pos < correct.size() && correct.get(pos) == i;
							b.withHighlight(ok ? SolariaButton.Highlight.CORRECT : SolariaButton.Highlight.WRONG);
						}
					} else if (!correct.isEmpty() && correct.getFirst() == i) {
						b.withHighlight(SolariaButton.Highlight.CORRECT);
					} else if (!feedback.correct() && lastPick == i) {
						b.withHighlight(SolariaButton.Highlight.WRONG);
					}
				}
				b.active = !locked && !(isOrder && sequence.contains(i));
				choiceButtons.add(addRenderableWidget(b));
			}
		}

		if (locked && !layout.footerButtons().isEmpty()) {
			String label = question.index() + 1 >= question.total() ? "See my score" : "Next question";
			addRenderableWidget(new SolariaButton(layout.footerButtons().getFirst(), Component.literal(label),
				SolariaButton.Style.PRIMARY, b -> ClientPlayNetworking.send(new QuizPackets.Next())));
		} else if (("order".equals(question.kind()) || "match".equals(question.kind())) && layout.footerButtons().size() >= 2) {
			addRenderableWidget(new SolariaButton(layout.footerButtons().get(0), Component.literal("Reset"),
				SolariaButton.Style.DEFAULT, b -> {
					sequence.clear();
					Collections.fill(pairing, -1);
					selectedLeft = 0;
					rebuildWidgets();
				}));
			boolean complete = "order".equals(question.kind()) ? sequence.size() == choices.size() : !pairing.contains(-1);
			SolariaButton submit = new SolariaButton(layout.footerButtons().get(1), Component.literal("Check answer"),
				SolariaButton.Style.PRIMARY, b -> submit(
					"order".equals(question.kind()) ? List.copyOf(sequence) : List.copyOf(pairing)));
			submit.active = complete;
			submit.setTooltip(Tooltip.create(Component.literal(complete ? "Lock in your answer" : "Place every item first")));
			addRenderableWidget(submit);
		}
	}

	private void pick(int display) {
		if (feedback != null || question == null) return;
		if ("order".equals(question.kind())) {
			if (!sequence.contains(display)) sequence.add(display);
			rebuildWidgets();
			return;
		}
		lastPick = display;
		submit(List.of(display));
	}

	private void assignMatch(int display) {
		if (feedback != null || question == null || pairing.isEmpty()) return;
		int previous = pairing.indexOf(display);
		if (previous >= 0) pairing.set(previous, -1);
		pairing.set(selectedLeft, display);
		int next = pairing.indexOf(-1);
		if (next >= 0) selectedLeft = next;
		rebuildWidgets();
	}

	private void submit(List<Integer> answer) {
		ClientPlayNetworking.send(new QuizPackets.Answer(answer));
		for (SolariaButton b : choiceButtons) b.active = false;
		for (SolariaButton b : leftButtons) b.active = false;
	}

	private static String letter(int i) {
		return String.valueOf((char) ('A' + i));
	}

	private static String capitalize(String id) {
		return id.isEmpty() ? id : Character.toUpperCase(id.charAt(0)) + id.substring(1);
	}

	private String hint() {
		return switch (question.kind()) {
			case "true_false" -> "Is this statement true or false?";
			case "order" -> "Click the items in order, then Check answer.";
			case "match" -> "Pick a match for the highlighted letter.";
			default -> "Pick one answer below.";
		};
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		if (layout == null) {
			layout = computeLayout();
		}
		SolariaUiKit.drawBackdrop(g, this.width, this.height);
		String title = body.isEmpty() ? "Solar Quiz" : capitalize(body) + " Quiz";
		String chipText = question != null ? (question.index() + 1) + " / " + Math.max(1, question.total())
			: result != null ? "Done" : "Sync";
		int chipDot = result != null ? SolariaUiKit.DOT_GREEN : SolariaUiKit.DOT_CYAN;
		int headerH = layout.compact() ? 22 : 26;

		SolariaUiKit.drawPanel(g, this.font, layout.panel(), title, headerH, layout.headerChip(), chipText, chipDot);

		if (result != null) {
			renderResult(g);
		} else if (question == null) {
			UiLayoutMetrics.Rect waitWell = new UiLayoutMetrics.Rect(
				layout.panel().x() + 14, layout.panel().y() + headerH + 10,
				layout.panel().width() - 28, layout.panel().height() - headerH - 42);
			SolariaUiKit.drawAccentWell(g, waitWell, SolariaUiKit.WELL_LIGHT, SolariaUiKit.CYAN_ACCENT);
			int midY = waitWell.y() + Math.max(6, (waitWell.height() - 8) / 2);
			SolariaUiKit.drawCenteredLines(g, this.font, Component.literal("Waiting for the host..."),
				waitWell.x() + waitWell.width() / 2, midY, waitWell.width() - 12, LINE, waitWell.bottom() - 4,
				SolariaUiKit.TEXT_DARK, false);
		} else {
			renderQuestion(g);
		}
		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}

	private void renderQuestion(GuiGraphicsExtractor g) {
		int total = Math.max(1, question.total());
		int done = question.index() + (feedback != null ? 1 : 0);
		SolariaUiKit.drawProgressBar(g, layout.progressBar(), (float) done / total);

		int diffDot = switch (question.difficulty()) {
			case "easy" -> SolariaUiKit.DOT_GREEN;
			case "hard" -> SolariaUiKit.DOT_ROSE;
			default -> SolariaUiKit.DOT_AMBER;
		};
		SolariaUiKit.drawChip(g, this.font, layout.difficultyChip(), capitalize(question.difficulty()), diffDot);
		int score = feedback != null ? feedback.score() : question.score();
		SolariaUiKit.drawChip(g, this.font, layout.scoreChip(), "Score " + score, SolariaUiKit.DOT_GREEN);

		UiLayoutMetrics.Rect qw = layout.questionWell();
		SolariaUiKit.drawAccentWell(g, qw, SolariaUiKit.WELL_LIGHT, SolariaUiKit.CYAN_ACCENT);
		int qTextW = qw.width() - 16;
		int y = qw.y() + 5;
		int maxY = qw.bottom() - 4;
		for (FormattedCharSequence line : this.font.split(Component.literal(question.prompt()), Math.max(20, qTextW))) {
			if (y + 8 > maxY) break;
			g.text(this.font, line, qw.x() + 9, y, SolariaUiKit.TEXT_DARK, false);
			y += LINE;
		}
		if (y + 8 <= maxY) {
			g.text(this.font, Component.literal(hint()).withStyle(ChatFormatting.ITALIC),
				qw.x() + 9, y, SolariaUiKit.TEXT_MUTED, false);
		}

		if (feedback != null && layout.feedbackWell() != null) {
			UiLayoutMetrics.Rect fw = layout.feedbackWell();
			int dot = feedback.correct() ? SolariaUiKit.DOT_GREEN : SolariaUiKit.DOT_ROSE;
			SolariaUiKit.drawAccentWell(g, fw, SolariaUiKit.WELL_LIGHT, dot);
			int fy = fw.y() + 4;
			int fwTextW = fw.width() - 16;
			String head = feedback.correct() ? "Correct!" : "Not quite.";
			String summary = answerSummary();
			if (!feedback.correct() && summary != null) {
				head = head + " " + summary;
			}
			for (FormattedCharSequence line : this.font.split(Component.literal(head), Math.max(20, fwTextW))) {
				if (fy + 8 > fw.bottom() - 3) break;
				g.text(this.font, line, fw.x() + 9, fy, dot, false);
				fy += LINE;
			}
			if (!feedback.explanation().isBlank()) {
				for (FormattedCharSequence line : this.font.split(Component.literal(feedback.explanation()), Math.max(20, fwTextW))) {
					if (fy + 8 > fw.bottom() - 3) break;
					g.text(this.font, line, fw.x() + 9, fy, SolariaUiKit.TEXT_DARK, false);
					fy += LINE;
				}
			}
			String host = sourceHost(feedback.source());
			if (host != null && fy + 8 <= fw.bottom() - 2) {
				g.text(this.font, SolariaUiKit.clipText(this.font, "Source: " + host, fwTextW),
					fw.x() + 9, fy, SolariaUiKit.TEXT_MUTED, false);
			}
		}
	}

	private String answerSummary() {
		List<Integer> correct = feedback.correctAnswer();
		List<String> choices = question.choices();
		if (correct.isEmpty()) return null;
		for (int d : correct) if (d < 0 || d >= choices.size()) return null;
		return switch (question.kind()) {
			case "order" -> {
				List<String> parts = new ArrayList<>();
				for (int d : correct) parts.add(choices.get(d));
				yield "Correct order: " + String.join(" > ", parts);
			}
			case "match" -> {
				List<String> parts = new ArrayList<>();
				for (int i = 0; i < Math.min(correct.size(), question.left().size()); i++) {
					parts.add(letter(i) + " = " + choices.get(correct.get(i)));
				}
				yield "Pairs: " + String.join(", ", parts);
			}
			default -> "Answer: " + choices.get(correct.getFirst());
		};
	}

	/** Shows only the host name so long URLs never overflow; the full link is in the teacher's JSON files. */
	static String sourceHost(String source) {
		if (source == null || source.isBlank()) return null;
		try {
			String host = URI.create(source).getHost();
			return host == null ? null : host.startsWith("www.") ? host.substring(4) : host;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private void renderResult(GuiGraphicsExtractor g) {
		UiLayoutMetrics.Rect panel = layout.panel();
		int headerH = layout.compact() ? 22 : 26;
		int footerTop = layout.footerButtons().isEmpty() ? panel.bottom() - 8 : layout.footerButtons().getFirst().y() - 8;
		UiLayoutMetrics.Rect well = new UiLayoutMetrics.Rect(
			panel.x() + 14, panel.y() + headerH + 8,
			panel.width() - 28, Math.max(40, footerTop - (panel.y() + headerH + 8)));

		int percent = result.total() <= 0 ? 0 : Math.round(100f * result.score() / result.total());
		int dot = percent >= 75 ? SolariaUiKit.DOT_GREEN : percent >= 50 ? SolariaUiKit.DOT_AMBER : SolariaUiKit.DOT_ROSE;
		SolariaUiKit.drawAccentWell(g, well, SolariaUiKit.WELL_LIGHT, dot);

		int cx = well.x() + well.width() / 2;
		int y = well.y() + 8;
		SolariaUiKit.drawCenteredLines(g, this.font, Component.literal("Quiz complete"),
			cx, y, well.width() - 16, LINE, well.bottom() - 4, SolariaUiKit.TEXT_DARK, false);
		y += 14;
		String scoreText = result.score() + " / " + result.total() + "  (" + percent + "%)";
		g.pose().pushMatrix();
		g.pose().translate((float) cx, (float) y);
		g.pose().scale(1.6f, 1.6f);
		int sw = this.font.width(scoreText);
		g.text(this.font, scoreText, -sw / 2, 0, dot, false);
		g.pose().popMatrix();
		y += 20;

		String best = result.newBest() ? "New personal best!" : "Best score: " + result.best() + " / " + result.total();
		SolariaUiKit.drawCenteredLines(g, this.font, Component.literal(best),
			cx, y, well.width() - 16, LINE, well.bottom() - 4,
			result.newBest() ? SolariaUiKit.DOT_GREEN : SolariaUiKit.TEXT_MUTED, false);
		y += 13;
		SolariaUiKit.drawCenteredLines(g, this.font, Component.literal(result.message()),
			cx, y, well.width() - 16, LINE, well.bottom() - 4, SolariaUiKit.TEXT_DARK, false);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
