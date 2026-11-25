package leapbtw.downtime.client;

import com.mojang.realmsclient.RealmsMainScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.time.LocalTime;
import java.util.Locale;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class DowntimeClient implements ClientModInitializer {
    public static final BreakTimer TIMER = new BreakTimer();
    public static final int BREAK_MINIMUM_SECONDS = BreakTimer.BREAK_MINIMUM_SECONDS;
    private static final BreakAlarm ALARM = new BreakAlarm();
    public static DowntimeConfig config;
    private static boolean openConfig;
    private static boolean inWorld;
    private static boolean scheduledSepia;
    // A changed interval takes effect after leaving and rejoining the world.
    private static int cycleMinutes;

    @Override
    public void onInitializeClient() {
        config = DowntimeConfig.load();
        cycleMinutes = config.minutes();
        BreakStateFile.restore(TIMER, System.currentTimeMillis());
        initCommands();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long nowMillis = System.currentTimeMillis();
            boolean worldReady = client.level != null && client.player != null;
            if (worldReady && !inWorld) {
                inWorld = true;
                onWorldJoined(nowMillis);
            } else if (inWorld && client.level == null && isOutsideMenu(client.gui.screen())) {
                inWorld = false;
                if (TIMER.leaveWorld(nowMillis)) BreakStateFile.save(TIMER);
                if (!TIMER.isBreakDue()) cycleMinutes = config.minutes();
                ALARM.reset();
            }

            if (openConfig) {
                openConfig = false;
                client.gui.setScreen(new DowntimeConfigScreen(null));
            }
            boolean playing = worldReady && !client.isPaused();
            announce(TIMER.tick(System.nanoTime(), nowMillis, playing, cycleMinutes));
            SepiaSchedule schedule = config.sepiaSchedule();
            scheduledSepia = worldReady && schedule.enabled() && schedule.isActive(LocalTime.now());
            if (worldReady) {
                int strikes = ALARM.poll(System.nanoTime());
                for (int i = 0; i < strikes; i++) {
                    client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BELL_BLOCK, 1.0F));
                }
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (TIMER.isBreakDue() && TIMER.leaveWorld(System.currentTimeMillis())) BreakStateFile.save(TIMER);
        });
    }

    private static boolean isOutsideMenu(Screen screen) {
        // Minecraft can temporarily clear the world for respawns, loading and server reconfiguration.
        return screen instanceof TitleScreen || screen instanceof JoinMultiplayerScreen
                || screen instanceof SelectWorldScreen || screen instanceof DisconnectedScreen
                || screen instanceof RealmsMainScreen;
    }

    private static void onWorldJoined(long nowMillis) {
        long awaySeconds = TIMER.breakAwaySeconds(nowMillis);
        if (!TIMER.returnToWorld(nowMillis)) return;
        BreakStateFile.save(TIMER);
        if (TIMER.isBreakDue()) {
            showChallenge();
        } else {
            cycleMinutes = config.minutes();
            message("chat.downtime.break_complete", ChatFormatting.GREEN, formatBreakTime(awaySeconds));
        }
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
                    announce(TIMER.addMinutes(addedMinutes, cycleMinutes, System.currentTimeMillis()));
                    return 1;
                })))
                .then(argument("answer", integer()).executes(context -> {
                    long nowMillis = System.currentTimeMillis();
                    if (!TIMER.isBreakDue()) {
                        status();
                    } else if (TIMER.breakElapsedSeconds(nowMillis) < BREAK_MINIMUM_SECONDS) {
                        message("chat.downtime.break_minimum", ChatFormatting.YELLOW, BREAK_MINIMUM_SECONDS / 60);
                    } else if (TIMER.answer(getInteger(context, "answer"), nowMillis)) {
                        BreakStateFile.save(TIMER);
                        message("chat.downtime.resumed", ChatFormatting.YELLOW, 10);
                        playWarningSound();
                    } else {
                        message("chat.downtime.incorrect", ChatFormatting.RED);
                    }
                    return 1;
                }))));
    }

    private static void announce(BreakTimer.Notice notice) {
        switch (notice) {
            case WARNING -> {
                message("chat.downtime.warning", ChatFormatting.YELLOW);
                playWarningSound();
            }
            case BREAK -> {
                BreakStateFile.save(TIMER);
                ALARM.start(System.nanoTime());
                message("chat.downtime.break", ChatFormatting.GOLD);
                showChallenge();
            }
            default -> { }
        }
    }

    private static void playWarningSound() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.ANVIL_DESTROY, 1.0F));
    }

    public static boolean grayscale() {
        Minecraft client = Minecraft.getInstance();
        return TIMER.isBreakDue() && client.level != null && client.player != null;
    }

    public static boolean sepia() {
        Minecraft client = Minecraft.getInstance();
        return !TIMER.isBreakDue() && (TIMER.isPreBreak() || scheduledSepia)
                && client.level != null && client.player != null;
    }

    public static long breakElapsedSeconds() {
        return TIMER.breakElapsedSeconds(System.currentTimeMillis());
    }

    public static long breakAwaySeconds() {
        return TIMER.breakAwaySeconds(System.currentTimeMillis());
    }

    public static String formatBreakTime(long seconds) {
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
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
