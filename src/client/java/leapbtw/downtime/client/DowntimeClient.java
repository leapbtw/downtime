package leapbtw.downtime.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class DowntimeClient implements ClientModInitializer {
    public static final BreakTimer TIMER = new BreakTimer();
    public static DowntimeConfig config;
    private static boolean openConfig;
    // A changed interval takes effect on the next cycle, never dismissing a pending break.
    private static int cycleMinutes;

    @Override
    public void onInitializeClient() {
        config = DowntimeConfig.load();
        cycleMinutes = config.minutes();
        initCommands();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openConfig) {
                openConfig = false;
                client.gui.setScreen(new DowntimeConfigScreen(null));
            }
            boolean playing = client.level != null && client.player != null && !client.isPaused();
            announce(TIMER.tick(System.nanoTime(), playing, cycleMinutes));
        });
        ScreenEvents.AFTER_INIT.register((client, screen, _, _) -> {
            // These are actual exits. A null world, respawn, configuration phase or loading
            // screen is NOT an exit: proxies may replace the world and player repeatedly.
            if (client.level == null && (screen instanceof TitleScreen
                    || screen instanceof JoinMultiplayerScreen || screen instanceof SelectWorldScreen
                    || screen instanceof DisconnectedScreen)) {
                TIMER.reset();
                cycleMinutes = config.minutes();
            }
        });
    }

    private static void initCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, _) ->
            dispatcher.register(literal("downtime")
                .executes(_ -> { status(); return 1; })
                .then(literal("config").executes(_ -> { openConfig = true; return 1; }))
                .then(literal("addminutes").then(argument("n", integer(1)).executes(context -> {
                    if (TIMER.isBreakDue()) {
                        message("chat.downtime.break_active", ChatFormatting.YELLOW);
                        showChallenge();
                        return 0;
                    }
                    int addedMinutes = getInteger(context, "n");
                    message("chat.downtime.minutes_added", ChatFormatting.AQUA, addedMinutes);
                    announce(TIMER.addMinutes(addedMinutes, cycleMinutes));
                    return 1;
                })))
                .then(argument("answer", integer()).executes(context -> {
                    if (!TIMER.isBreakDue()) {
                        status();
                    } else if (TIMER.answer(getInteger(context, "answer"))) {
                        cycleMinutes = config.minutes();
                        message("chat.downtime.resumed", ChatFormatting.GREEN, cycleMinutes);
                    } else {
                        message("chat.downtime.incorrect", ChatFormatting.RED);
                    }
                    return 1;
                }))));
    }

    private static void announce(BreakTimer.Notice notice) {
        switch (notice) {
            case WARNING -> message("chat.downtime.warning", ChatFormatting.YELLOW);
            case BREAK -> {
                message("chat.downtime.break", ChatFormatting.GOLD);
                showChallenge();
            }
            default -> { }
        }
    }

    public static boolean grayscale() {
        Minecraft client = Minecraft.getInstance();
        return TIMER.isBreakDue() && client.level != null && client.player != null;
    }

    private static void status() {
        if (TIMER.isBreakDue()) showChallenge();
        else {
            long seconds = TIMER.remainingSeconds(cycleMinutes);
            message("chat.downtime.status", ChatFormatting.AQUA, seconds / 60, seconds % 60);
        }
    }

    private static void showChallenge() {
        message("chat.downtime.challenge", ChatFormatting.GOLD, TIMER.challenge().expression());
    }

    private static void message(String key, ChatFormatting color, Object... args) {
        Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(
                Component.literal("[Downtime] ").append(Component.translatable(key, args)).withStyle(color));
    }
}
