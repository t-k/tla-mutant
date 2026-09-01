# Census Classpath and Open-Issue Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking. The repository Agent DAG selected a single writer; do not dispatch parallel writers.

**Goal:** Replace the packaged census command's raw missing-TLA-Tools linkage failure with an actionable explicit-classpath diagnostic, verify the documented real-SANY invocation, and close the two audited local issues without losing the separate local-Podman request.

**Architecture:** Keep TLA+ Tools outside the application fat JAR. A package-private classpath guard runs before direct census input handling and checks representative SANY classes without dynamically loading a path. Real packaged-process tests cover both the missing-Tools failure and the supported application-plus-Tools classpath, while existing sandbox paths remain unchanged.

**Tech Stack:** Java 21, Picocli, JUnit 5, Jackson, Gradle, real TLA+ Tools/SANY, rootless Podman for the unchanged containment gate.

---

## File map

- Create src/main/java/dev/tlamutant/cli/TlaToolsClasspath.java: dependency-presence guard and bounded diagnostic.
- Modify src/main/java/dev/tlamutant/cli/Main.java: invoke the guard first in CensusCommand.call.
- Create src/test/java/dev/tlamutant/cli/TlaToolsClasspathTest.java: missing/present class-loader checks.
- Create src/integrationTest/java/dev/tlamutant/cli/PackagedCensusCliIntegrationTest.java: packaged-process regression and documented invocation.
- Create src/test/java/dev/tlamutant/cli/CorpusExperimentCommandTest.java: characterize default and explicit corpus oracle routing.
- Modify README.md: trusted-local-census command and host-trust boundary.
- Modify config/current-application.json, docs/experiments/gcd-prospective-closure-assessment-v11.json, and HistoricalArtifactFreezeIntegrationTest.java: current reproducible validator identity.
- Create docs/security/2026-09-01-census-classpath-diagnostic-review.md: final security decision.
- Move the two reviewed ignored issue files to docs.local/issues/closed and create a new ignored local-Podman issue.
- Update docs.local/logs/2026-09-01/2026-09-01-001-open-issue-review.md.

### Task 1: Drive the classpath diagnostic from a packaged-process regression

**Files:**
- Create: src/integrationTest/java/dev/tlamutant/cli/PackagedCensusCliIntegrationTest.java
- Create: src/test/java/dev/tlamutant/cli/TlaToolsClasspathTest.java
- Create: src/main/java/dev/tlamutant/cli/TlaToolsClasspath.java
- Modify: src/main/java/dev/tlamutant/cli/Main.java:198-203

- [ ] **Step 1: Write the packaged-process regression before production code**

Create PackagedCensusCliIntegrationTest with two tests. The first command is:

~~~java
ProcessResult missing = run(List.of(
        javaBinary(),
        "-jar",
        applicationJar(),
        "census",
        "--model-dir",
        "src/testFixtures/resources/tla/census",
        "--module",
        "CensusFixture.tla",
        "--config",
        "CensusFixture.cfg",
        "--work-dir",
        temporaryDirectory.resolve("work").toString(),
        "--derived-source-edit-json",
        "not-json"));

assertNotEquals(0, missing.exitCode(), missing.output());
assertTrue(missing.output().contains("census requires TLA+ Tools on the JVM classpath"));
assertTrue(missing.output().contains("dev.tlamutant.cli.Main census"));
assertFalse(missing.output().contains("NoClassDefFoundError"));
assertFalse(Files.exists(temporaryDirectory.resolve("work")));
~~~

The second test runs the supported command and parses real census JSON:

~~~java
String classpath = applicationJar()
        + System.getProperty("path.separator")
        + toolsJar();
ProcessResult supported = run(List.of(
        javaBinary(),
        "-cp",
        classpath,
        "dev.tlamutant.cli.Main",
        "census",
        "--model-dir",
        "src/testFixtures/resources/tla/census",
        "--module",
        "CensusFixture.tla",
        "--config",
        "CensusFixture.cfg"));

assertEquals(0, supported.exitCode(), supported.output());
JsonNode report = new ObjectMapper().readTree(supported.output());
assertEquals(1, report.required("schemaVersion").intValue());
assertEquals(4, report.required("sites").size());
~~~

Implement run with ProcessBuilder.redirectErrorStream(true), redirect output to a TempDir file, wait at most 20 seconds, then SIGTERM for 2 seconds and destroyForcibly for at most 5 seconds in finally. javaBinary is java.home/bin/java; the JAR paths come from tla.mutant.test.appJar and tla.mutant.test.toolsJar.

- [ ] **Step 2: Run the regression and verify RED**

~~~sh
./gradlew integrationTest --tests dev.tlamutant.cli.PackagedCensusCliIntegrationTest --no-build-cache --rerun-tasks --console=plain
~~~

Expected: the explicit-classpath test passes. The missing-Tools test fails because current output contains NoClassDefFoundError and not the required diagnostic.

- [ ] **Step 3: Add the minimal classpath guard**

Create TlaToolsClasspath.java:

~~~java
package dev.tlamutant.cli;

import java.io.File;
import java.util.List;
import java.util.Objects;

final class TlaToolsClasspath {
    private static final List<String> REQUIRED_CLASSES = List.of(
            "tla2sany.drivers.SANY",
            "tla2sany.semantic.SemanticNode",
            "tlc2.tool.impl.ModelConfig");

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
        String classpath = "build/libs/tla-mutant-all.jar"
                + File.pathSeparator
                + ".tools/tla2tools-1.8.0.jar";
        return "census requires TLA+ Tools on the JVM classpath. "
                + "The application JAR intentionally excludes TLA+ Tools. "
                + "Run from the repository root: java -cp \""
                + classpath
                + "\" dev.tlamutant.cli.Main census ...";
    }
}
~~~

Add this as the first statement of CensusCommand.call, before parseDerivedSourceEdits:

~~~java
TlaToolsClasspath.requireForCensus();
~~~

- [ ] **Step 4: Add deterministic helper coverage**

Create TlaToolsClasspathTest with:

~~~java
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
    assertDoesNotThrow(() ->
            TlaToolsClasspath.requireForCensus(TlaToolsClasspath.class.getClassLoader()));
}
~~~

- [ ] **Step 5: Verify GREEN and format**

~~~sh
./gradlew spotlessApply test --tests dev.tlamutant.cli.TlaToolsClasspathTest integrationTest --tests dev.tlamutant.cli.PackagedCensusCliIntegrationTest --no-build-cache --rerun-tasks --console=plain
git diff --check
~~~

Expected: four focused tests pass and no census child remains.

- [ ] **Step 6: Commit the diagnostic slice**

~~~sh
git add src/main/java/dev/tlamutant/cli/Main.java src/main/java/dev/tlamutant/cli/TlaToolsClasspath.java src/test/java/dev/tlamutant/cli/TlaToolsClasspathTest.java src/integrationTest/java/dev/tlamutant/cli/PackagedCensusCliIntegrationTest.java
git commit -m "fix: diagnose missing census tools classpath"
~~~

### Task 2: Cover existing corpus oracle routing

**Files:**
- Create: src/test/java/dev/tlamutant/cli/CorpusExperimentCommandTest.java

- [ ] **Step 1: Add a characterization test for implemented routing**

Create a Picocli parse helper with all required corpus-experiment options and assert:

~~~java
@Test
void defaultsToStateInvariantsAndAcceptsExplicitFullProperty() {
    assertEquals(OracleMode.STATE_INVARIANTS, parse().oracleMode);
    assertEquals(OracleMode.FULL_PROPERTY, parse("--oracle-mode", "full-property").oracleMode);
}
~~~

The helper builds this complete base argument list before appending the optional values:

~~~java
List<String> arguments = new ArrayList<>(List.of(
        "corpus-experiment",
        "--manifest", "/manifest.json",
        "--policy", "/policy.json",
        "--tools-jar", "/tools.jar",
        "--cache-dir", "/cache",
        "--json-report", "/report.json",
        "--markdown-report", "/report.md"));
~~~

Return the Main.CorpusExperimentCommand user object from parsed.subcommand().commandSpec().userObject(). This is characterization coverage for existing behavior, not a production-code RED step.

- [ ] **Step 2: Run and commit the characterization**

~~~sh
./gradlew test --tests dev.tlamutant.cli.CorpusExperimentCommandTest --no-build-cache --rerun-tasks --console=plain
git add src/test/java/dev/tlamutant/cli/CorpusExperimentCommandTest.java
git commit -m "test: cover corpus oracle mode routing"
~~~

Expected: one test passes.

### Task 3: Document trusted local census startup

**Files:**
- Modify: README.md after the Verify section

- [ ] **Step 1: Add the exact command and trust boundary**

~~~~markdown
## Run a trusted local census

The direct census command parses the model with SANY in the host JVM, so use it only for trusted local input. Put the separately verified application and TLA+ Tools JARs on the JVM classpath:

~~~sh
java -cp build/libs/tla-mutant-all.jar:.tools/tla2tools-1.8.0.jar dev.tlamutant.cli.Main census \
  --model-dir path/to/model \
  --module Spec.tla \
  --config Spec.cfg
~~~

Use the corpus census commands for public or otherwise untrusted specifications; those commands retain the rootless Podman boundary.
~~~~

- [ ] **Step 2: Check the documented command shape and commit**

~~~sh
rg -n "Run a trusted local census|dev.tlamutant.cli.Main census|trusted local input|rootless Podman" README.md src/integrationTest/java/dev/tlamutant/cli/PackagedCensusCliIntegrationTest.java
git add README.md
git commit -m "docs: document trusted local census startup"
~~~

Expected: README and the integration test both use java -cp, Main, and census in that order.

### Task 4: Re-freeze the reproducible application identity

**Files:**
- Modify: config/current-application.json
- Modify: docs/experiments/gcd-prospective-closure-assessment-v11.json:79-82
- Modify: src/integrationTest/java/dev/tlamutant/prospective/HistoricalArtifactFreezeIntegrationTest.java:61-65

- [ ] **Step 1: Build twice and compare bytes**

~~~sh
freeze_dir=$(mktemp -d)
./gradlew clean fatJar --no-build-cache --rerun-tasks --console=plain
cp build/libs/tla-mutant-all.jar "$freeze_dir/first.jar"
./gradlew clean fatJar --no-build-cache --rerun-tasks --console=plain
cmp "$freeze_dir/first.jar" build/libs/tla-mutant-all.jar
shasum -a 256 build/libs/tla-mutant-all.jar
stat -f '%z' build/libs/tla-mutant-all.jar
~~~

Expected: cmp exits 0. Record the concrete SHA-256 and byte size printed by the final commands.

- [ ] **Step 2: Update current validator identities**

Use apply_patch to replace the old current application digest and size in config/current-application.json and the validator-application record in the v11 assessment. Do not alter the historical role=application execution identity.

- [ ] **Step 3: Recompute and bind the assessment identity**

~~~sh
shasum -a 256 docs/experiments/gcd-prospective-closure-assessment-v11.json
stat -f '%z' docs/experiments/gcd-prospective-closure-assessment-v11.json
~~~

Use apply_patch to replace the assessment Identity literal in HistoricalArtifactFreezeIntegrationTest with those exact measured values.

- [ ] **Step 4: Verify and commit frozen identities**

~~~sh
./gradlew integrationTest --tests dev.tlamutant.prospective.HistoricalArtifactFreezeIntegrationTest verifyFrozenApplication --no-build-cache --rerun-tasks --console=plain
git add config/current-application.json docs/experiments/gcd-prospective-closure-assessment-v11.json src/integrationTest/java/dev/tlamutant/prospective/HistoricalArtifactFreezeIntegrationTest.java
git commit -m "chore: freeze census diagnostic application identity"
~~~

Expected: all tasks pass and execution/validator application digests remain distinct. Delete only the exact freeze_dir created above after verifying it is below the platform temporary directory.

### Task 5: Close audited local issues without losing residual scope

**Files in the main checkout because docs.local is ignored:**
- Move both reviewed files from docs.local/issues/open to docs.local/issues/closed.
- Create docs.local/issues/open/2026-09-01-manifest-free-local-podman-experiment.md.
- Modify docs.local/logs/2026-09-01/2026-09-01-001-open-issue-review.md.

- [ ] **Step 1: Add closure status and evidence**

The census issue records that Tools separation remains, missing classes now fail before SANY with the supported classpath command, the README command is integration-tested, and local Podman execution moved to a separate issue. The full-property issue appends that requirements 1 through 5 were re-audited on 2026-09-01.

- [ ] **Step 2: Move both files with apply_patch**

Create docs.local/issues/closed through apply_patch move operations. Confirm the two filenames no longer appear under open and do appear under closed.

- [ ] **Step 3: Create the scoped Japanese follow-up issue**

The new issue must require a bounded no-follow local tree snapshot; symlink, special-file, and path-escape rejection; model/application/Tools/policy identities; rootless Podman with no network and read-only input; CPU, memory, PID, output, state, and wall-clock bounds; exact cleanup after timeout, malformed output, or interruption; and an explicit distinction between host and sandbox report modes. It must state that it does not replace pinned corpus or prospective protocols.

- [ ] **Step 4: Update the ignored work log**

Record commits, RED/GREEN evidence, final identities, review decisions, test counts, and process cleanup. Do not commit docs.local.

### Task 6: Review gates and full deterministic verification

**Files:**
- Create: docs/security/2026-09-01-census-classpath-diagnostic-review.md
- Modify other files only through a new RED-GREEN cycle if a review finding requires it.

- [ ] **Step 1: Run focused full-property and packaged-census suites**

~~~sh
./gradlew test --tests dev.tlamutant.tlc.TlcOutcomeClassifierTest --tests dev.tlamutant.experiment.ExperimentRunnerFullPropertyTest --tests dev.tlamutant.experiment.MutationEvidenceTest --tests dev.tlamutant.experiment.ExperimentReportTest --tests dev.tlamutant.experiment.CorpusExperimentReportTest --tests dev.tlamutant.cli.ExperimentCommandTest --tests dev.tlamutant.cli.CorpusExperimentCommandTest integrationTest --tests dev.tlamutant.experiment.FullPropertyExperimentIntegrationTest --tests dev.tlamutant.tlc.TlcRunnerIntegrationTest --tests dev.tlamutant.cli.PackagedCensusCliIntegrationTest --no-build-cache --rerun-tasks --console=plain
~~~

Expected: every selected test passes.

- [ ] **Step 2: Request mandatory Security Specialist review**

Give a clean-context reviewer only the original request, accepted design criteria, final diff, relevant files, and focused test output. Require Must Fix / Should Fix / Notes. Any Must Fix blocks integration and requires a new failing regression test before a code fix.

- [ ] **Step 3: Record and commit the approved security decision**

The tracked English record includes the reviewed commit, scope, all three required sections, and final GO or NO-GO.

~~~sh
git add docs/security/2026-09-01-census-classpath-diagnostic-review.md
git commit -m "docs: record census classpath security review"
~~~

- [ ] **Step 4: Run the complete non-Podman gate**

~~~sh
./gradlew spotlessCheck test integrationTest verifyFrozenApplication --no-build-cache --rerun-tasks --console=plain
~~~

Expected: all tasks succeed with zero failures and errors.

- [ ] **Step 5: Run Podman containment with same-shell cleanup**

Start the Podman machine and install an EXIT trap that stops Gradle and the exact machine started by this task. Run:

~~~sh
./gradlew sandboxTest --no-build-cache --rerun-tasks --console=plain
~~~

Expected: containment passes. After the trap, the machine is stopped and no task-owned container, volume, Gradle daemon, TLC JVM, or packaged census child remains.

- [ ] **Step 6: Request clean-context correctness review**

Provide only the original request, acceptance criteria, final diff, relevant files, and final test results. A blocker or high finding requires a new RED-GREEN cycle and re-review.

- [ ] **Step 7: Final repository checks**

~~~sh
git diff --check
git status --short --branch
git log --oneline --decorate -8
~~~

Expected: the feature worktree is clean.

### Task 7: Integrate locally without pushing

- [ ] **Step 1: Invoke superpowers:finishing-a-development-branch**

Confirm main and feature tracked states are clean and the base branch is main. Select the already authorized local-merge workflow. Do not pull or push.

- [ ] **Step 2: Fast-forward local main**

From /Users/tk/work/tla-mutant:

~~~sh
git merge --ff-only fix/open-issues-2026-09-01
~~~

Expected: main advances without a merge commit.

- [ ] **Step 3: Preserve the user-managed worktree**

Do not remove .worktree/open-issues-2026-09-01 or delete its checked-out branch. Record the integrated HEAD in the ignored work log and report that no push occurred.
