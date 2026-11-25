package leapbtw.downtime.client;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class BreakStateFile {
    private static final Gson GSON = new Gson();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("downtime-break.json");

    private record SavedBreak(BreakTimer.Challenge challenge, long startedAtMillis, long leftAtMillis) { }

    static void restore(BreakTimer timer, long nowMillis) {
        if (!Files.exists(FILE)) return;
        try (var reader = Files.newBufferedReader(FILE)) {
            SavedBreak saved = GSON.fromJson(reader, SavedBreak.class);
            if (saved == null || saved.challenge() == null || saved.challenge().operation() == null
                    || saved.startedAtMillis() <= 0 || saved.leftAtMillis() < -1) {
                throw new IllegalArgumentException("Invalid break state");
            }
            // If the game stopped without recording a departure, start counting absence now.
            long leftAtMillis = saved.leftAtMillis() < 0 ? nowMillis : saved.leftAtMillis();
            timer.restoreBreak(saved.challenge(), saved.startedAtMillis(), leftAtMillis);
            if (saved.leftAtMillis() < 0) save(timer);
        } catch (IOException | RuntimeException exception) {
            LoggerFactory.getLogger("downtime").warn("Cannot read saved break", exception);
        }
    }

    static void save(BreakTimer timer) {
        try {
            if (!timer.isBreakDue()) {
                Files.deleteIfExists(FILE);
                return;
            }
            Files.createDirectories(FILE.getParent());
            Path temp = FILE.resolveSibling("downtime-break.json.tmp");
            SavedBreak saved = new SavedBreak(timer.challenge(), timer.breakStartedAtMillis(), timer.leftAtMillis());
            Files.writeString(temp, GSON.toJson(saved));
            try {
                Files.move(temp, FILE, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            LoggerFactory.getLogger("downtime").error("Cannot save break state", exception);
        }
    }
}
