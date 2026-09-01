package dev.tlamutant.cli;

import java.io.File;
import java.util.List;
import java.util.Objects;

final class TlaToolsClasspath {
    private static final List<String> REQUIRED_CLASSES =
            List.of("tla2sany.drivers.SANY", "tla2sany.semantic.SemanticNode", "tlc2.tool.impl.ModelConfig");

    private TlaToolsClasspath() {}

    static void requireForCensus() {
        requireForCensus(TlaToolsClasspath.class.getClassLoader());
    }

    static void requireForCensus(ClassLoader classLoader) {
        Objects.requireNonNull(classLoader, "classLoader");
        for (String className : REQUIRED_CLASSES) {
            try {
                Class.forName(className, false, classLoader);
            } catch (ClassNotFoundException | LinkageError unavailable) {
                throw new IllegalStateException(diagnostic());
            }
        }
    }

    private static String diagnostic() {
        String classpath = "build/libs/tla-mutant-all.jar" + File.pathSeparator + ".tools/tla2tools-1.8.0.jar";
        return "census requires TLA+ Tools on the JVM classpath. "
                + "The application JAR intentionally excludes TLA+ Tools. "
                + "Run from the repository root: java -cp \""
                + classpath
                + "\" dev.tlamutant.cli.Main census ...";
    }
}
