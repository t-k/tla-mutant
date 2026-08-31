package dev.tlamutant.mutation;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class MutationSupportSourceTest {
    @Test
    void uoiTraversalDoesNotReintroduceWholeGraphMaterialization() throws Exception {
        String support = Files.readString(Path.of("src/main/java/dev/tlamutant/mutation/MutationSupport.java"), UTF_8);
        String uoi = Files.readString(Path.of("src/main/java/dev/tlamutant/mutation/UoiMutator.java"), UTF_8);

        assertTrue(support.contains("visitApplications("));
        assertFalse(support.contains("List<OpApplNode>"));
        assertFalse(support.contains("collect("));
        assertFalse(uoi.contains("MutationSupport.applications("));
    }
}
