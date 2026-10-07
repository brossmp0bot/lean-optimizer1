package com.leanoptimizer.client;

import com.leanoptimizer.LeanOptimizer;
import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Keeps the game near a target FPS by trading render distance and entity distance, and caps the
 * frame rate while the window is unfocused. It only ever changes values through the game's own
 * option objects and always remembers the player's own settings so it can put them back.
 */
public final class AdaptiveTuner {
	private static final double ENTITY_STEP = 0.25;
	private static final int UNLIMITED_FPS = 260;

	private final LeanConfig config;

	/** One FPS reading per second, newest last. */
	private final Deque<Integer> samples = new ArrayDeque<>();
	private int tickCounter;
	private int cooldownTicks;

	// The player's own settings, and the last value this tuner wrote (to detect manual changes).
	private Integer baseRender;
	private Integer lastRender;
	private Double baseEntity;
	private Double lastEntity;

	/** The player's frame rate cap from before the window lost focus, or null while focused. */
	private Integer savedFpsLimit;

	private String lastAction = "none yet";

	public AdaptiveTuner(LeanConfig config) {
		this.config = config;
	}

	public void tick(Minecraft mc) {
		if (mc.options == null) {
			return;
		}
		if (!config.enabled) {
			restore(mc);
			return;
		}

		handleFocus(mc);

		boolean playing = mc.level != null && mc.screen == null && mc.isWindowActive();
		if (!config.adaptiveTuning || !playing) {
			samples.clear();
			tickCounter = 0;
			return;
		}

		trackPlayerSettings(mc);

		if (cooldownTicks > 0) {
			cooldownTicks--;
		}
		if (++tickCounter < 20) {
			return;
		}
		tickCounter = 0;

		samples.addLast(mc.getFps());
		while (samples.size() > config.sampleSeconds) {
			samples.removeFirst();
		}
		if (samples.size() < config.sampleSeconds || cooldownTicks > 0) {
			return;
		}

		double average = samples.stream().mapToInt(Integer::intValue).average().orElse(0);
		double lowerBelow = config.targetFps * 0.9;
		double raiseAbove = raiseThreshold(mc);

		boolean changed = false;
		if (average < lowerBelow) {
			changed = lower(mc, average);
		} else if (average >= raiseAbove) {
			changed = raise(mc, average);
		}
		if (changed) {
			samples.clear();
			cooldownTicks = config.cooldownSeconds * 20;
		}
	}

	/** Put every setting this tuner touched back to what the player chose. */
	public void restore(Minecraft mc) {
		if (mc.options == null) {
			return;
		}
		if (savedFpsLimit != null) {
			mc.options.framerateLimit().set(savedFpsLimit);
			savedFpsLimit = null;
		}
		if (baseRender != null && lastRender != null && !baseRender.equals(mc.options.renderDistance().get())) {
			mc.options.renderDistance().set(baseRender);
		}
		if (baseEntity != null && lastEntity != null
				&& Math.abs(baseEntity - mc.options.entityDistanceScaling().get()) > 1e-6) {
			mc.options.entityDistanceScaling().set(baseEntity);
		}
		baseRender = null;
		lastRender = null;
		baseEntity = null;
		lastEntity = null;
		samples.clear();
		tickCounter = 0;
		cooldownTicks = 0;
	}

	public String status(Minecraft mc) {
		if (mc.options == null) {
			return "not ready";
		}
		return "enabled=" + config.enabled
				+ ", fps=" + mc.getFps()
				+ " (target " + config.targetFps + ")"
				+ ", render distance=" + mc.options.renderDistance().get()
				+ (baseRender != null ? " (yours: " + baseRender + ")" : "")
				+ ", entity distance=" + String.format("%.2f", mc.options.entityDistanceScaling().get())
				+ ", last action: " + lastAction;
	}

	// ---------------------------------------------------------------------------------------

	private void handleFocus(Minecraft mc) {
		if (config.limitWhenUnfocused && !mc.isWindowActive()) {
			int current = mc.options.framerateLimit().get();
			if (savedFpsLimit == null) {
				savedFpsLimit = current;
			}
			int wanted = Math.min(savedFpsLimit, roundToTen(config.unfocusedFpsLimit));
			if (current != wanted) {
				mc.options.framerateLimit().set(wanted);
			}
		} else if (savedFpsLimit != null) {
			mc.options.framerateLimit().set(savedFpsLimit);
			savedFpsLimit = null;
		}
	}

	/** Adopt the player's settings as the baseline, including after they change them by hand. */
	private void trackPlayerSettings(Minecraft mc) {
		int render = mc.options.renderDistance().get();
		if (lastRender == null || render != lastRender) {
			baseRender = render;
			lastRender = render;
		}
		double entity = mc.options.entityDistanceScaling().get();
		if (lastEntity == null || Math.abs(entity - lastEntity) > 1e-6) {
			baseEntity = entity;
			lastEntity = entity;
		}
	}

	private double raiseThreshold(Minecraft mc) {
		double threshold = config.targetFps * 1.5;
		int limit = mc.options.framerateLimit().get();
		if (limit < UNLIMITED_FPS) {
			// A frame rate cap stops FPS from ever reaching 1.5x target; judge headroom against the cap.
			threshold = Math.min(threshold, limit * 0.93);
		}
		// Always keep a dead band above the "lower" threshold so changes do not flip-flop.
		return Math.max(threshold, config.targetFps * 0.95);
	}

	private boolean lower(Minecraft mc, double average) {
		double entity = mc.options.entityDistanceScaling().get();
		if (entity - ENTITY_STEP >= config.minEntityDistanceScaling - 1e-6) {
			double next = round2(Math.max(config.minEntityDistanceScaling, entity - ENTITY_STEP));
			mc.options.entityDistanceScaling().set(next);
			lastEntity = mc.options.entityDistanceScaling().get();
			return log("entity distance " + format(entity) + " -> " + format(lastEntity), average);
		}
		int render = mc.options.renderDistance().get();
		if (render > config.minRenderDistance) {
			mc.options.renderDistance().set(render - 1);
			lastRender = mc.options.renderDistance().get();
			return log("render distance " + render + " -> " + lastRender, average);
		}
		return false;
	}

	private boolean raise(Minecraft mc, double average) {
		int render = mc.options.renderDistance().get();
		if (baseRender != null && render < baseRender) {
			mc.options.renderDistance().set(render + 1);
			lastRender = mc.options.renderDistance().get();
			return log("render distance " + render + " -> " + lastRender, average);
		}
		double entity = mc.options.entityDistanceScaling().get();
		if (baseEntity != null && entity + 1e-6 < baseEntity) {
			double next = round2(Math.min(baseEntity, entity + ENTITY_STEP));
			mc.options.entityDistanceScaling().set(next);
			lastEntity = mc.options.entityDistanceScaling().get();
			return log("entity distance " + format(entity) + " -> " + format(lastEntity), average);
		}
		return false;
	}

	private boolean log(String action, double average) {
		lastAction = action + " (avg " + Math.round(average) + " fps)";
		LeanOptimizer.LOGGER.info("Adaptive tuning: {}", lastAction);
		return true;
	}

	private static int roundToTen(int value) {
		return Math.max(10, Math.round(value / 10f) * 10);
	}

	private static double round2(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	private static String format(double value) {
		return String.format("%.2f", value);
	}
}
