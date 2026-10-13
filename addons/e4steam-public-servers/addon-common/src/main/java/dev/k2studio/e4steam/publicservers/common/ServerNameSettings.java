package dev.k2studio.e4steam.publicservers.common;

import dev.k2studio.e4steam.publicservers.contract.TextPolicy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** UTF-8 local preference. An empty value selects the nickname-based default. */
public final class ServerNameSettings {
    private Path file;
    private String custom = "";
    public synchronized void load(Path gameDirectory) {
        Path next = gameDirectory.resolve("config/e4steam-public-server-name.txt");
        if (next.equals(file)) return;
        file = next;
        custom = "";
        try {
            if (Files.exists(file)) custom = checked(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | IllegalArgumentException ignored) {
            System.getLogger("e4steam Public Servers").log(System.Logger.Level.WARNING, "Could not read public world name preference");
        }
    }
    public synchronized String custom() { return custom; }
    public synchronized void save(String value) throws IOException {
        String checked = checked(value);
        if (file == null) throw new IOException("Name preference has not been initialized");
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), "public-world-name-", ".tmp");
        try {
            Files.writeString(temp, checked, StandardCharsets.UTF_8);
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
            custom = checked;
        } finally { Files.deleteIfExists(temp); }
    }
    private static String checked(String value) {
        return value == null || value.isBlank() ? "" : TextPolicy.title(value);
    }
}
