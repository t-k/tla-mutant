package dev.tlamutant.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class TlaToolsClasspathTest {
    @Test
    void missingToolsProduceABoundedActionableDiagnosticWithoutTheLinkageCause() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> TlaToolsClasspath.requireForCensus(ClassLoader.getPlatformClassLoader()));

        assertTrue(failure.getMessage().contains("census requires TLA+ Tools on the JVM classpath"));
        assertTrue(failure.getMessage().contains("dev.tlamutant.cli.Main census"));
        assertTrue(failure.getMessage().length() <= 500);
        assertFalse(failure.getMessage().contains("NoClassDefFoundError"));
        assertNull(failure.getCause());
    }

    @Test
    void testRuntimeWithPinnedToolsPassesTheGuard() {
        assertDoesNotThrow(() -> TlaToolsClasspath.requireForCensus(TlaToolsClasspath.class.getClassLoader()));
    }
}
