package dev.tlamutant.tlc;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TlcRunnerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void legacyProfileRunsFromTheModelRootWithRelativeModuleAndConfigArguments() throws Exception {
        Fixture fixture = fixture();
        Path state = temporaryDirectory.resolve("result/states.jsonl");

        TlcExecution execution =
                fixture.runner().run(fixture.model(), "Spec.tla", "Spec.cfg", state, Duration.ofSeconds(30));

        assertEquals(OracleOutcome.PASSED, execution.oracle(), execution.stderr());
        assertEquals(
                fixture.model().toRealPath(),
                Path.of(Files.readString(sibling(state, ".cwd"), UTF_8).trim()).toRealPath());
        assertEquals("Spec.cfg\nSpec.tla\n", Files.readString(sibling(state, ".arguments"), UTF_8));
    }

    @Test
    void fullPropertyRejectsEscapedModuleAndConfigAtTheLaunchBoundary() throws Exception {
        Fixture fixture = fixture();
        Path outside = temporaryDirectory.resolve("Outside.tla");
        Files.writeString(outside, "---- MODULE Outside ----\n====\n", UTF_8);

        assertThrows(IllegalArgumentException.class, () -> fixture.runner()
                .runFullProperty(
                        fixture.model(),
                        "../Outside.tla",
                        "Spec.cfg",
                        temporaryDirectory.resolve("escape.jsonl"),
                        Duration.ofSeconds(30)));
        assertThrows(IllegalArgumentException.class, () -> fixture.runner()
                .runFullProperty(
                        fixture.model(),
                        "Spec.tla",
                        outside.toString(),
                        temporaryDirectory.resolve("absolute.jsonl"),
                        Duration.ofSeconds(30)));
    }

    @Test
    void cleanupFailureMakesAnOtherwiseCompletedRunUnresolved() throws Exception {
        Fixture fixture = fixture(path -> {
            throw new IOException("injected cleanup failure");
        });

        TlcExecution execution = fixture.runner()
                .runFullProperty(
                        fixture.model(),
                        "Spec.tla",
                        "Spec.cfg",
                        temporaryDirectory.resolve("cleanup.jsonl"),
                        Duration.ofSeconds(30));

        assertEquals(ExecutionStatus.TOOL_ERROR, execution.status());
        assertEquals(OracleOutcome.NOT_EVALUATED, execution.oracle());
        assertTrue(execution.stderr().contains("TLC run cleanup failed"), execution.stderr());
    }

    @Test
    void strictCleanerRemovesTheExactRunDirectory() throws Exception {
        Path root = Files.createDirectories(temporaryDirectory.resolve("run/metadata"));
        Files.writeString(root.resolve("trace"), "data", UTF_8);

        TlcRunner.deleteRunDirectory(root.getParent());

        assertFalse(Files.exists(root.getParent()));
    }

    private Fixture fixture() throws Exception {
        return fixture(TlcRunner::deleteRunDirectory);
    }

    private Fixture fixture(TlcRunner.RunDirectoryCleaner cleaner) throws Exception {
        Path model = Files.createDirectories(temporaryDirectory.resolve("model-" + System.nanoTime()));
        Files.writeString(model.resolve("Spec.tla"), "---- MODULE Spec ----\n====\n", UTF_8);
        Files.writeString(model.resolve("Spec.cfg"), "SPECIFICATION Spec\n", UTF_8);
        Path application =
                Files.writeString(temporaryDirectory.resolve("application-" + System.nanoTime()), "app", UTF_8);
        Path tools = Files.writeString(temporaryDirectory.resolve("tools-" + System.nanoTime()), "tools", UTF_8);
        Path java = temporaryDirectory.resolve("java-" + System.nanoTime());
        Files.writeString(
                java,
                "#!/bin/sh\n"
                        + "state=''\nconfig=''\nmodule=''\nprevious=''\n"
                        + "for argument in \"$@\"; do\n"
                        + "  case \"$argument\" in -Dtla.mutant.state.path=*) state=${argument#*=};; esac\n"
                        + "  if [ \"$previous\" = '-config' ]; then config=$argument; fi\n"
                        + "  previous=$argument\nmodule=$argument\n"
                        + "done\n"
                        + "pwd > \"$state.cwd\"\nprintf '%s\\n%s\\n' \"$config\" \"$module\" > \"$state.arguments\"\n"
                        + "printf '%s\\n' '{\"type\":\"complete\",\"states\":0}' > \"$state\"\n"
                        + "printf '%s\\n' '@!@!@STARTMSG 2193:0 @!@!@' 'Model checking completed.' '@!@!@ENDMSG 2193 @!@!@'\n",
                UTF_8);
        try {
            Files.setPosixFilePermissions(
                    java,
                    Set.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE));
        } catch (UnsupportedOperationException ignored) {
            assertTrue(java.toFile().setExecutable(true));
        }
        return new Fixture(
                model,
                new TlcRunner(
                        java.toAbsolutePath(),
                        application.toAbsolutePath(),
                        tools.toAbsolutePath(),
                        100,
                        1024,
                        cleaner));
    }

    private static Path sibling(Path state, String suffix) {
        return Path.of(state + suffix);
    }

    private record Fixture(Path model, TlcRunner runner) {}
}
