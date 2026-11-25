package leapbtw.downtime.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class DowntimeConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggerFactory.getLogger("downtime");
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("downtime.json");
    private int intervalMinutes = 60;
    private SepiaSchedule sepiaSchedule = SepiaSchedule.DEFAULT;

    private record StoredConfig(Integer intervalMinutes, JsonElement sepiaSchedule) { }
    private record StoredSchedule(boolean enabled, String start, String end) { }

    public static int normalize(int minutes) {
        return 30 + Math.round((Math.clamp(minutes, 30, 240) - 30) / 15.0f) * 15;
    }

    public static DowntimeConfig load() {
        DowntimeConfig config = new DowntimeConfig();
        if (Files.exists(FILE)) {
            try (var reader = Files.newBufferedReader(FILE)) {
                StoredConfig saved = GSON.fromJson(reader, StoredConfig.class);
                if (saved != null) {
                    if (saved.intervalMinutes() != null) config.intervalMinutes = normalize(saved.intervalMinutes());
                    config.sepiaSchedule = readSchedule(saved.sepiaSchedule());
                }
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn("Cannot read downtime.json; using defaults", exception);
            }
        }
        return config;
    }

    private static SepiaSchedule readSchedule(JsonElement element) {
        if (element == null || element.isJsonNull()) return SepiaSchedule.DEFAULT;
        try {
            StoredSchedule saved = GSON.fromJson(element, StoredSchedule.class);
            return new SepiaSchedule(saved.enabled(), SepiaSchedule.parseTime(saved.start()),
                    SepiaSchedule.parseTime(saved.end()));
        } catch (RuntimeException exception) {
            LOGGER.warn("Cannot read the sepia schedule; leaving it disabled", exception);
            return SepiaSchedule.DEFAULT;
        }
    }

    public int minutes() { return intervalMinutes; }
    public SepiaSchedule sepiaSchedule() { return sepiaSchedule; }

    public boolean save(int minutes, SepiaSchedule schedule) {
        int normalized = normalize(minutes);
        StoredSchedule savedSchedule = new StoredSchedule(schedule.enabled(), SepiaSchedule.formatTime(schedule.start()),
                SepiaSchedule.formatTime(schedule.end()));
        StoredConfig saved = new StoredConfig(normalized, GSON.toJsonTree(savedSchedule));
        try {
            Files.createDirectories(FILE.getParent());
            Path temp = FILE.resolveSibling("downtime.json.tmp");
            Files.writeString(temp, GSON.toJson(saved));
            try {
                Files.move(temp, FILE, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
            intervalMinutes = normalized;
            sepiaSchedule = schedule;
            return true;
        } catch (IOException exception) {
            LOGGER.error("Cannot save downtime.json", exception);
            return false;
        }
    }
}
