package dev.tlamutant.prospective;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tlamutant.security.BoundedFileSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProspectiveInterventionPreflightTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void validatesTheCompleteFrozenV10ChainBeforeAuthorizingTheV11Manifest() throws Exception {
        Map<String, Path> inputs = inputs();
        Path receipt = receipt("intervention-census", inputs);

        ProspectiveInterventionPreflight.ValidatedInputs validated = new ProspectiveInterventionPreflight()
                .validate(new ProspectiveInterventionPreflight.Paths(
                        receipt,
                        inputs,
                        temporaryDirectory.resolve("cache"),
                        List.of(temporaryDirectory.resolve("census.json"))));

        assertEquals("prospective-v11", validated.manifest().purpose());
        assertEquals(1, validated.review().accepted());
        assertEquals("gcd-prospective-v11", validated.protocol().profile());
    }

    @Test
    void rejectsAnIdentityMismatchBeforeParsingTheChangedEvidence() throws Exception {
        Map<String, Path> inputs = inputs();
        Path receipt = receipt("intervention-census", inputs);
        Files.writeString(inputs.get("review"), "not-json");

        IllegalArgumentException failure =
                assertThrows(IllegalArgumentException.class, () -> new ProspectiveInterventionPreflight()
                        .validate(new ProspectiveInterventionPreflight.Paths(
                                receipt,
                                inputs,
                                temporaryDirectory.resolve("cache"),
                                List.of(temporaryDirectory.resolve("census.json")))));

        assertEquals("Prospective receipt identity mismatch for role: review", failure.getMessage());
    }

    private Map<String, Path> inputs() throws Exception {
        Path application = temporaryDirectory.resolve("application.jar");
        Path tools = temporaryDirectory.resolve("tla2tools.jar");
        Files.writeString(application, "frozen prospective application");
        Files.writeString(tools, "frozen tla tools");
        Path review = temporaryDirectory.resolve("review.json");
        Files.copy(Path.of("docs/experiments/gcd-prospective-review-v10.json"), review);
        Map<String, Path> inputs = new LinkedHashMap<>();
        inputs.put("v10-discovery-receipt", Path.of("docs/experiments/gcd-prospective-receipt-v10.json"));
        inputs.put("v10-experiment-receipt", Path.of("docs/experiments/gcd-prospective-experiment-receipt-v10.json"));
        inputs.put("v10-protocol", Path.of("config/prospective-protocol-v10.json"));
        inputs.put("v10-manifest", Path.of("corpus/gcd-prospective-manifest-v10.json"));
        inputs.put("v10-census", Path.of("docs/experiments/gcd-prospective-census-v10.json"));
        inputs.put("v10-discovery", Path.of("docs/experiments/gcd-prospective-v10.json"));
        inputs.put("dossier", Path.of("docs/experiments/gcd-prospective-review-dossier-v10.json"));
        inputs.put("review", review);
        inputs.put("v11-protocol", Path.of("config/prospective-protocol-v11.json"));
        inputs.put("v11-manifest", Path.of("corpus/gcd-prospective-manifest-v11.json"));
        inputs.put("policy", Path.of("config/execution-policy.json"));
        inputs.put("tools", tools);
        inputs.put("application", application);
        return inputs;
    }

    private Path receipt(String phase, Map<String, Path> inputs) throws Exception {
        List<ProspectiveReceipt.Artifact> artifacts = new ArrayList<>();
        for (Map.Entry<String, Path> input : inputs.entrySet()) {
            BoundedFileSnapshot snapshot = BoundedFileSnapshot.read(input.getValue(), 256L * 1024 * 1024);
            artifacts.add(new ProspectiveReceipt.Artifact(input.getKey(), snapshot.sha256(), snapshot.sizeBytes()));
        }
        Path path = temporaryDirectory.resolve("receipt.json");
        new ObjectMapper()
                .writeValue(path.toFile(), new ProspectiveReceipt(1, phase, "gcd-prospective-v11", artifacts));
        return path;
    }
}
