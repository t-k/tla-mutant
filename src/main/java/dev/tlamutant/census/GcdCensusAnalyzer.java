package dev.tlamutant.census;

import dev.tlamutant.mutation.GcdSiteAnalyzer;
import dev.tlamutant.mutation.SourceDocument;
import dev.tlamutant.semantic.SanyAnalyzer;
import dev.tlamutant.semantic.SemanticProject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.regex.Pattern;

public final class GcdCensusAnalyzer {
    private static final Pattern ANSI = Pattern.compile("\\x1B(?:[@-Z\\\\-_]|\\[[0-?]*[ -/]*[@-~])");
    private static final Pattern CONTROL_AND_BIDI = Pattern.compile(
            "[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F-\\x9F\\u061C\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]");

    public GcdCensusReport analyze(Path modelDirectory, String moduleFile, String configFile) throws IOException {
        Path root = modelDirectory.toAbsolutePath().normalize();
        SemanticProject project = new SanyAnalyzer().analyze(root, moduleFile, configFile);
        SourceDocument source = SourceDocument.read(root.resolve(moduleFile));
        var sites = new GcdSiteAnalyzer()
                .analyze(project, source).stream()
                        .map(site -> new GcdCensusSite(
                                site.candidate().id(),
                                site.candidate().module(),
                                site.candidate().definition(),
                                bounded(site.candidate().original()),
                                site.guardLevel(),
                                site.remainingStateConjuncts(),
                                site.actionSuccessorConjuncts(),
                                site.conforms()))
                        .toList();
        return new GcdCensusReport(1, moduleFile, configFile, sites);
    }

    private static String bounded(String input) {
        String normalized = CONTROL_AND_BIDI
                .matcher(ANSI.matcher(input).replaceAll(""))
                .replaceAll("")
                .replace('\r', ' ')
                .replace('\n', ' ');
        return normalized.substring(0, Math.min(normalized.length(), 500));
    }
}
