package link.e4steam;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionalCompatibilityTest {
    @Test
    void runsCompatibleIntegration() {
        AtomicBoolean called = new AtomicBoolean();

        OptionalCompatibility.run("test-success", () -> called.set(true));

        assertTrue(called.get());
    }

    @Test
    void isolatesModRuntimeAndLinkageFailures() {
        assertDoesNotThrow(() -> OptionalCompatibility.run(
                "test-runtime-failure",
                () -> { throw new IllegalStateException("changed widget API"); }
        ));
        assertDoesNotThrow(() -> OptionalCompatibility.run(
                "test-linkage-failure",
                () -> { throw new NoClassDefFoundError("optional mod class"); }
        ));
    }

    @Test
    void doesNotHideFatalVmErrors() {
        assertThrows(OutOfMemoryError.class, () -> OptionalCompatibility.run(
                "test-fatal-error",
                () -> { throw new OutOfMemoryError("test"); }
        ));
    }
}
