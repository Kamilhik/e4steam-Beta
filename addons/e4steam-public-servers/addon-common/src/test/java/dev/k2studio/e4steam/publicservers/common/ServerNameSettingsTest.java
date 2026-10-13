package dev.k2studio.e4steam.publicservers.common;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
class ServerNameSettingsTest {
    @TempDir Path directory;
    @Test void preservesRussianMixedTextAndSpacingAcrossRestart() throws Exception {
        ServerNameSettings first = new ServerNameSettings(); first.load(directory);
        assertEquals("", first.custom());
        first.save("Мир Kamilchik  — Выживание");
        ServerNameSettings second = new ServerNameSettings(); second.load(directory);
        assertEquals("Мир Kamilchik  — Выживание", second.custom());
        second.load(directory);
        assertEquals("Мир Kamilchik  — Выживание", second.custom());
        second.save("");
        ServerNameSettings third = new ServerNameSettings(); third.load(directory);
        assertEquals("", third.custom());
        assertEquals("Вася server", ServerDisplayName.fromCreator("Вася"));
    }
    @Test void invalidInputDoesNotOverwriteSavedName() throws Exception {
        ServerNameSettings settings = new ServerNameSettings(); settings.load(directory);
        settings.save("Наш мир");
        assertThrows(IllegalArgumentException.class, () -> settings.save("a".repeat(49)));
        assertThrows(IllegalArgumentException.class, () -> settings.save("Мир\nПодмена"));
        assertEquals("Наш мир", settings.custom());
        ServerNameSettings again = new ServerNameSettings(); again.load(directory);
        assertEquals("Наш мир", again.custom());
    }
}
