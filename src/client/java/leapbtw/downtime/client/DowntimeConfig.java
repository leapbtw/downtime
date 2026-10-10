package leapbtw.downtime.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class DowntimeConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("downtime.json");
    private int intervalMinutes = 60;

    public static int normalize(int minutes) {
        return 30 + Math.round((Math.clamp(minutes, 30, 240) - 30) / 15.0f) * 15;
    }

    public static DowntimeConfig load() {
        if (Files.exists(FILE)) {
            try (var reader = Files.newBufferedReader(FILE)) {
                DowntimeConfig config = GSON.fromJson(reader, DowntimeConfig.class);
                if (config != null) {
                    config.intervalMinutes = normalize(config.intervalMinutes);
                    return config;
                }
            } catch (IOException | RuntimeException exception) {
                LoggerFactory.getLogger("downtime").warn("Cannot read downtime.json; using 60 minutes", exception);
            }
        }
        return new DowntimeConfig();
    }

    public int minutes() { return intervalMinutes; }

    public boolean save(int minutes) {
        int old = intervalMinutes;
        intervalMinutes = normalize(minutes);
        try {
            Files.createDirectories(FILE.getParent());
            Path temp = FILE.resolveSibling("downtime.json.tmp");
            Files.writeString(temp, GSON.toJson(this));
            Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException exception) {
            intervalMinutes = old;
            LoggerFactory.getLogger("downtime").error("Cannot save downtime.json", exception);
            return false;
        }
    }
}
