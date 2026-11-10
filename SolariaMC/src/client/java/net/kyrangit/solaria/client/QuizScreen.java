package net.kyrangit.solaria.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kyrangit.solaria.quiz.QuizPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Observatory-styled quiz terminal. The host owns grading: this screen only shows the question it was sent and
 * returns display indices. Multiple choice and true/false use one button per answer; "order" builds a sequence
 * by clicking items; "match" assigns a right-hand answer to the highlighted left-hand item.
 */
public final class QuizScreen extends Screen {
	private static final int LINE = 12;
	private static final int OK = 0xFF55D17A;
	private static final int BAD = 0xFFE05555;
	private static final int WARN = 0xFFE3A33A;
	private static final int MUTED = 0xFF8CA0B4;
	private static final int ACCENT = 0xFF80E8D7;
	private static final int BODY = 0xFFCFCFCF;

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

	private final List<Button> choiceButtons = new ArrayList<>();
	private final List<Button> leftButtons = new ArrayList<>();
	private int contentTop;
	private int footerY;

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

	private int panelWidth() {
		return Math.max(1, Math.min(560, this.width - 16));
	}

	private int contentWidth() {
		return Math.max(40, panelWidth() - 32);
	}

	private boolean compact() {
		return this.height < 300;
	}

	@Override
	protected void init() {
		choiceButtons.clear();
		leftButtons.clear();
		int cw = contentWidth();
		int cx = this.width / 2 - cw / 2;
		footerY = this.height - 36;
		int half = Math.max(20, (Math.min(cw, 320) - 6) / 2);
		if (result != null) {
			addRenderableWidget(Button.builder(Component.literal("Play again"),
				b -> ClientPlayNetworking.send(new QuizPackets.Start(body)))
				.bounds(this.width / 2 - half - 3, footerY, half, 20).build());
			addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
				.bounds(this.width / 2 + 3, footerY, half, 20).build());
			return;
		}
		if (question == null) {
			addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
				.bounds(this.width / 2 - half / 2, footerY, half, 20).build());
			return;
		}
		contentTop = 48 + Math.min(5, promptLines().size()) * LINE + LINE + 4;
		int rowH = compact() ? 18 : 20;
		int gap = compact() ? 2 : 4;
		int y = contentTop;
		boolean locked = feedback != null;
		List<String> choices = question.choices();
		if ("match".equals(question.kind())) {
			int colW = (cw - 8) / 2;
			for (int i = 0; i < question.left().size(); i++) {
				final int left = i;
				Button b = Button.builder(Component.literal(letter(i) + ". " + question.left().get(i)), x -> {
					selectedLeft = left;
					rebuildWidgets();
				}).bounds(cx, y + i * (rowH + gap), colW, rowH).build();
				b.active = !locked;
				leftButtons.add(addRenderableWidget(b));
			}
			for (int i = 0; i < choices.size(); i++) {
				final int choice = i;
				int owner = pairing.indexOf(i);
				String tag = owner >= 0 ? "  [" + letter(owner) + "]" : "";
				Button b = Button.builder(Component.literal(choices.get(i) + tag), x -> assignMatch(choice))
					.bounds(cx + colW + 8, y + i * (rowH + gap), colW, rowH).build();
				b.active = !locked;
				choiceButtons.add(addRenderableWidget(b));
			}
		} else {
			int w = Math.min(cw, 340);
			int x0 = this.width / 2 - w / 2;
			for (int i = 0; i < choices.size(); i++) {
				final int choice = i;
				String label = choices.get(i);
				if ("order".equals(question.kind())) {
					int pos = sequence.indexOf(i);
					if (pos >= 0) label = (pos + 1) + ". " + label;
				}
				Button b = Button.builder(Component.literal(label), x -> pick(choice))
					.bounds(x0, y + i * (rowH + gap), w, rowH).build();
				b.active = !locked && !("order".equals(question.kind()) && sequence.contains(i));
				choiceButtons.add(addRenderableWidget(b));
			}
		}
		if (locked) {
			String label = question.index() + 1 >= question.total() ? "See my score" : "Next question";
			addRenderableWidget(Button.builder(Component.literal(label), b -> ClientPlayNetworking.send(new QuizPackets.Next()))
				.bounds(this.width / 2 - half / 2, footerY, half, 20).build());
		} else if ("order".equals(question.kind()) || "match".equals(question.kind())) {
			Button reset = Button.builder(Component.literal("Reset"), b -> {
				sequence.clear();
				Collections.fill(pairing, -1);
				selectedLeft = 0;
				rebuildWidgets();
			}).bounds(this.width / 2 - half - 3, footerY, half, 20).build();
			addRenderableWidget(reset);
			boolean complete = "order".equals(question.kind()) ? sequence.size() == choices.size() : !pairing.contains(-1);
			Button submit = Button.builder(Component.literal("Check answer"), b -> submit(
				"order".equals(question.kind()) ? List.copyOf(sequence) : List.copyOf(pairing)))
				.bounds(this.width / 2 + 3, footerY, half, 20).build();
			submit.active = complete;
			submit.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(complete
				? "Lock in your answer" : "Place every item first")));
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
		for (Button b : choiceButtons) b.active = false;
		for (Button b : leftButtons) b.active = false;
	}

	private List<FormattedCharSequence> promptLines() {
		return question == null ? List.of() : this.font.split(Component.literal(question.prompt()), contentWidth());
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
			case "order" -> "Click the items in the right order, then Check answer.";
			case "match" -> "Pick a match for the highlighted letter, then Check answer.";
			default -> "Pick one answer.";
		};
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		int pw = panelWidth();
		int left = (this.width - pw) / 2;
		int cw = contentWidth();
		g.fill(0, 0, this.width, this.height, 0xD509101D);
		long now = System.currentTimeMillis();
		for (int i = 0; i < 60; i++) {
			int sx = Math.floorMod(i * 7919 + (int) (now / (110 + i % 3 * 80)), Math.max(1, this.width));
			int sy = Math.floorMod(i * 104729, Math.max(1, this.height));
			g.fill(sx, sy, sx + 1 + i % 2, sy + 1 + i % 2, i % 5 == 0 ? 0xFFFFF4C0 : 0xA0DDEBFF);
		}
		roundedPanel(g, left, 8, pw, this.height - 16, 8, 0xF018263B);
		frameBorder(g, left + 2, 10, pw - 4, this.height - 20, 0xFF2D6E78);
		String title = "SOLARIA / " + (body.isEmpty() ? "QUIZ" : capitalize(body).toUpperCase() + " QUIZ");
		g.centeredText(this.font, Component.literal(title), this.width / 2, 16, ACCENT);
		if (result != null) {
			renderResult(g);
		} else if (question == null) {
			g.centeredText(this.font, Component.literal("Waiting for the host..."), this.width / 2, 48, BODY);
		} else {
			renderQuestion(g, cw);
		}
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		if (question != null) renderHighlights(g);
	}

	private void renderQuestion(GuiGraphicsExtractor g, int cw) {
		int total = Math.max(1, question.total());
		int done = question.index() + (feedback != null ? 1 : 0);
		int barW = Math.min(cw, 320);
		int bx = this.width / 2 - barW / 2;
		g.fill(bx, 28, bx + barW, 31, 0xFF0C1420);
		g.fill(bx, 28, bx + barW * done / total, 31, ACCENT);
		int score = feedback != null ? feedback.score() : question.score();
		String diff = capitalize(question.difficulty());
		int diffColour = switch (question.difficulty()) {
			case "easy" -> OK;
			case "hard" -> BAD;
			default -> WARN;
		};
		String info = "Question " + (question.index() + 1) + "/" + total + "   ";
		String scoreText = "   Score " + score;
		int infoW = this.font.width(info) + this.font.width(diff) + this.font.width(scoreText);
		int ix = this.width / 2 - infoW / 2;
		g.text(this.font, info, ix, 35, MUTED);
		g.text(this.font, diff, ix + this.font.width(info), 35, diffColour);
		g.text(this.font, scoreText, ix + this.font.width(info) + this.font.width(diff), 35, MUTED);
		int y = 48;
		List<FormattedCharSequence> lines = promptLines();
		for (int i = 0; i < Math.min(5, lines.size()); i++) {
			g.centeredText(this.font, lines.get(i), this.width / 2, y, 0xFFFFFFFF);
			y += LINE;
		}
		g.centeredText(this.font, Component.literal(hint()).withStyle(ChatFormatting.ITALIC), this.width / 2, y, MUTED);
		int rows = Math.max(question.choices().size(), question.left().size());
		int rowH = compact() ? 18 : 20;
		int gap = compact() ? 2 : 4;
		int fy = contentTop + rows * (rowH + gap) + 4;
		if (feedback == null) return;
		int colour = feedback.correct() ? OK : BAD;
		List<Component> out = new ArrayList<>();
		out.add(Component.literal(feedback.correct() ? "Correct!" : "Not quite.").withColor(colour & 0xFFFFFF));
		String answerLine = answerSummary();
		if (!feedback.correct() && answerLine != null) out.add(Component.literal(answerLine).withColor(WARN & 0xFFFFFF));
		if (!feedback.explanation().isBlank()) out.add(Component.literal(feedback.explanation()).withColor(BODY & 0xFFFFFF));
		String host = sourceHost(feedback.source());
		if (host != null) out.add(Component.literal("Source: " + host).withColor(MUTED & 0xFFFFFF));
		for (Component c : out) {
			for (FormattedCharSequence line : this.font.split(c, cw)) {
				if (fy + LINE > footerY - 2) return;
				g.centeredText(this.font, line, this.width / 2, fy, 0xFFFFFFFF);
				fy += LINE;
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

	private void renderHighlights(GuiGraphicsExtractor g) {
		if ("match".equals(question.kind())) {
			if (feedback == null && selectedLeft < leftButtons.size()) outline(g, leftButtons.get(selectedLeft), ACCENT);
			if (feedback != null) {
				List<Integer> correct = feedback.correctAnswer();
				for (int i = 0; i < leftButtons.size() && i < pairing.size(); i++) {
					boolean ok = i < correct.size() && correct.get(i).equals(pairing.get(i));
					outline(g, leftButtons.get(i), ok ? OK : BAD);
				}
			}
			return;
		}
		if (feedback == null) return;
		List<Integer> correct = feedback.correctAnswer();
		if ("order".equals(question.kind())) {
			for (int k = 0; k < sequence.size(); k++) {
				int d = sequence.get(k);
				if (d < choiceButtons.size()) {
					outline(g, choiceButtons.get(d), k < correct.size() && correct.get(k) == d ? OK : BAD);
				}
			}
			return;
		}
		if (!correct.isEmpty() && correct.getFirst() >= 0 && correct.getFirst() < choiceButtons.size()) {
			outline(g, choiceButtons.get(correct.getFirst()), OK);
		}
		if (!feedback.correct() && lastPick >= 0 && lastPick < choiceButtons.size()) {
			outline(g, choiceButtons.get(lastPick), BAD);
		}
	}

	private void renderResult(GuiGraphicsExtractor g) {
		int y = Math.max(36, this.height / 2 - 50);
		int percent = result.total() <= 0 ? 0 : Math.round(100f * result.score() / result.total());
		int colour = percent >= 75 ? OK : percent >= 50 ? WARN : BAD;
		g.centeredText(this.font, Component.literal("Quiz complete"), this.width / 2, y, 0xFFFFFFFF);
		y += LINE + 6;
		String scoreText = result.score() + " / " + result.total() + "  (" + percent + "%)";
		g.pose().pushMatrix();
		g.pose().translate(this.width / 2f, y);
		g.pose().scale(2f, 2f);
		g.centeredText(this.font, Component.literal(scoreText), 0, 0, colour);
		g.pose().popMatrix();
		y += 24;
		String best = result.newBest() ? "New personal best!" : "Best score: " + result.best() + " / " + result.total();
		g.centeredText(this.font, Component.literal(best), this.width / 2, y, result.newBest() ? OK : MUTED);
		y += LINE + 4;
		for (FormattedCharSequence line : this.font.split(Component.literal(result.message()), contentWidth())) {
			if (y + LINE > footerY - 2) break;
			g.centeredText(this.font, line, this.width / 2, y, BODY);
			y += LINE;
		}
	}

	private static void outline(GuiGraphicsExtractor g, Button b, int colour) {
		frameBorder(g, b.getX() - 1, b.getY() - 1, b.getWidth() + 2, b.getHeight() + 2, colour);
		frameBorder(g, b.getX() - 2, b.getY() - 2, b.getWidth() + 4, b.getHeight() + 4, colour);
	}

	private static void frameBorder(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
		if (width <= 2 || height <= 2) return;
		graphics.fill(x, y, x + width, y + 1, color);
		graphics.fill(x, y + height - 1, x + width, y + height, color);
		graphics.fill(x, y, x + 1, y + height, color);
		graphics.fill(x + width - 1, y, x + width, y + height, color);
	}

	private static void roundedPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
		int radius, int color) {
		if (width <= 0 || height <= 0) return;
		int r = Math.min(radius, Math.min(width, height) / 2);
		graphics.fill(x, y + r, x + width, y + height - r, color);
		for (int row = 0; row < r; row++) {
			double dy = r - row - 0.5;
			int inset = (int) Math.ceil(r - Math.sqrt(r * r - dy * dy));
			graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
			graphics.fill(x + inset, y + height - row - 1, x + width - inset, y + height - row, color);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
