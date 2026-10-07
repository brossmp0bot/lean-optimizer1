package com.leanoptimizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared constants. The mod itself is client-only; see
 * {@code com.leanoptimizer.client.LeanOptimizerClient}.
 */
public final class LeanOptimizer {
	public static final String MOD_ID = "leanoptimizer";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private LeanOptimizer() {
	}
}
