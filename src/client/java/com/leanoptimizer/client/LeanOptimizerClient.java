package com.leanoptimizer.client;

import com.mojang.brigadier.Command;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class LeanOptimizerClient implements ClientModInitializer {
	private static LeanConfig config;
	private static AdaptiveTuner tuner;

	@Override
	public void onInitializeClient() {
		config = LeanConfig.load();
		tuner = new AdaptiveTuner(config);

		// Lambdas (not method references) so a /leanopt reload that swaps the tuner is picked up.
		ClientTickEvents.END_CLIENT_TICK.register(client -> tuner.tick(client));

		// Hand the player's own settings back when leaving a world and when the game closes,
		// so a lowered render distance never ends up saved as their preference.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> tuner.restore(client));
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> tuner.restore(client));

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(ClientCommandManager.literal("leanopt")
						.executes(ctx -> {
							ctx.getSource().sendFeedback(Component.literal("[Lean Optimizer] " + tuner.status(Minecraft.getInstance())));
							return Command.SINGLE_SUCCESS;
						})
						.then(ClientCommandManager.literal("toggle").executes(ctx -> {
							config.enabled = !config.enabled;
							config.save();
							if (!config.enabled) {
								tuner.restore(Minecraft.getInstance());
							}
							ctx.getSource().sendFeedback(Component.literal("[Lean Optimizer] " + (config.enabled ? "enabled" : "disabled")));
							return Command.SINGLE_SUCCESS;
						}))
						.then(ClientCommandManager.literal("reload").executes(ctx -> {
							tuner.restore(Minecraft.getInstance());
							config = LeanConfig.load();
							tuner = new AdaptiveTuner(config);
							ctx.getSource().sendFeedback(Component.literal("[Lean Optimizer] config reloaded"));
							return Command.SINGLE_SUCCESS;
						}))));
	}
}
