package com.leanoptimizer.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.leanoptimizer.LeanOptimizer;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Gson-backed config stored at {@code config/lean-optimizer.json}. */
public final class LeanConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("lean-optimizer.json");

	/** Master switch. */
	public boolean enabled = true;

	// ---- Adaptive tuning (while playing in a world) ----

	/** Lower render distance / entity distance when FPS is under target, restore when there is headroom. */
	public boolean adaptiveTuning = true;
	/** FPS the tuner tries to stay above. */
	public int targetFps = 60;
	/** Never go below this render distance (chunks). */
	public int minRenderDistance = 6;
	/** Never go below this entity distance scaling (vanilla range is 0.5 - 5.0). */
	public double minEntityDistanceScaling = 0.5;
	/** Seconds the FPS average must stay bad/good before a change is made. */
	public int sampleSeconds = 8;
	/** Minimum seconds between two changes, so chunk reloads do not thrash. */
	public int cooldownSeconds = 30;

	// ---- Unfocused window ----

	/** Cap the frame rate while the game window is not focused. */
	public boolean limitWhenUnfocused = true;
	/** Frame rate cap while unfocused (rounded to a multiple of 10 by the game). */
	public int unfocusedFpsLimit = 20;

	public static LeanConfig load() {
		LeanConfig config = new LeanConfig();
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				LeanConfig read = GSON.fromJson(reader, LeanConfig.class);
				if (read != null) {
					config = read;
				}
			} catch (Exception e) {
				LeanOptimizer.LOGGER.warn("Could not read {}, using defaults", FILE, e);
			}
		}
		config.sanitize();
		config.save(); // writes defaults / fixes up bad values
		return config;
	}

	public void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			LeanOptimizer.LOGGER.warn("Could not write {}", FILE, e);
		}
	}

	private void sanitize() {
		targetFps = clamp(targetFps, 20, 360);
		minRenderDistance = clamp(minRenderDistance, 2, 32);
		minEntityDistanceScaling = Math.max(0.5, Math.min(5.0, minEntityDistanceScaling));
		sampleSeconds = clamp(sampleSeconds, 3, 60);
		cooldownSeconds = clamp(cooldownSeconds, 10, 600);
		unfocusedFpsLimit = clamp(unfocusedFpsLimit, 10, 260);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
