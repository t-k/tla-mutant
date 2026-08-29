# Prospective Assessment Provenance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the canonical prospective closure assessment identify every consumed artifact by role, SHA-256 digest, and byte size.

**Architecture:** Upgrade the assessment to schema version 2 with an immutable, exact-role `inputArtifacts` list. A focused provenance builder converts the bounded snapshots already used by CLI preflight into canonical assessment entries, while the closure validator remains responsible for the decision and receives the completed provenance list as an explicit capability.

**Tech Stack:** Java 21 records, Jackson, Picocli, JUnit 5, Gradle, Spotless, rootless Podman sandbox tests.

---

## File structure

- Modify `src/main/java/dev/tlamutant/prospective/ProspectiveClosureAssessment.java`: define schema version 2 provenance records and fail-closed role validation.
- Create `src/main/java/dev/tlamutant/prospective/ProspectiveClosureProvenance.java`: convert bounded snapshots into the canonical 18-role list.
- Modify `src/main/java/dev/tlamutant/prospective/ProspectiveClosureValidator.java`: require provenance and embed it in the decision artifact.
- Modify `src/main/java/dev/tlamutant/cli/Main.java`: snapshot the validator JAR and pass every exact input snapshot to the provenance builder.
- Create `src/test/java/dev/tlamutant/prospective/ProspectiveClosureAssessmentTest.java`: cover exact role-set and identity invariants.
- Create `src/test/java/dev/tlamutant/prospective/ProspectiveClosureProvenanceTest.java`: cover snapshot-to-identity conversion.
- Modify `src/test/java/dev/tlamutant/prospective/ProspectiveClosureValidatorTest.java`: supply provenance and preserve decision behavior.
- Modify `src/integrationTest/java/dev/tlamutant/prospective/HistoricalArtifactFreezeIntegrationTest.java`: verify the regenerated canonical assessment and its receipt-bound identities.
- Modify `config/current-application.json`: bind the reproducible schema version 2 validator JAR.
- Regenerate `docs/experiments/gcd-prospective-closure-assessment-v11.json`: publish schema version 2 evidence.
- Modify `docs/experiments/gcd-prospective-final-assessment.md`, `docs/security/2026-08-29-v10-prospective-review.md`, and `README.md`: record the new assessment and resolved provenance finding.
- Update this plan as an execution coverage ledger.

## Execution coverage ledger

The original task split was adjusted during the RED phase because the assessment constructor, validator signature, and CLI call form one Java compilation boundary. All three failure tests were established before the minimal production change, then the integrated slice was made GREEN without changing the approved architecture.

| Obligation | Status | Evidence |
|---|---|---|
| AP01 | implementation_verified | `ProspectiveClosureAssessmentTest` accepts exactly the canonical 18 roles; the generated artifact contains 18 roles. |
| AP02 | implementation_verified | Constructor tests reject uppercase digest, uppercase role, and zero size; production requires lowercase 64-hex SHA-256 and positive size. |
| AP03 | implementation_verified | Unit tests reject missing, unexpected, duplicate, and reversed role lists. |
| AP04 | experiment_verified | CLI generation used the validated bounded snapshots; `HistoricalArtifactFreezeIntegrationTest` recomputes every repository-resident identity. |
| AP05 | experiment_verified | Integration assertions separate receipt-bound execution JAR `0c84489d...` from validator JAR `99cc4020...`. |
| AP06 | experiment_verified | Schema version 1 and 2 projections excluding schema/provenance were byte-identical under `jq` normalization and `cmp`. |
| AP07 | jar_verified | Two clean no-build-cache builds were byte-identical at SHA-256 `99cc4020d7ecd691b2285c1d9d12a1299155228b6e8ff346e6cf54193b1a6148`, size `3152637`; `verifyFrozenApplication` passed. |
| AP08 | cleanup_verified | Spotless, 154 unit tests, 23 integration tests, and four sandbox tests passed with zero failures, errors, or skips; owned resources, daemon, machine, and orphan audits are clean. |

### Task 1: Fail-closed schema version 2 assessment

**Files:**
- Create: `src/test/java/dev/tlamutant/prospective/ProspectiveClosureAssessmentTest.java`
- Modify: `src/main/java/dev/tlamutant/prospective/ProspectiveClosureAssessment.java`

- [ ] **Step 1: Write failing tests for the valid role set and malformed variants**

Create helpers that build one `InputArtifact` for every role exposed by `requiredInputRoles()`, then assert that a valid assessment copies the list and that missing, unexpected, duplicate, noncanonical, malformed-digest, and zero-size entries throw `IllegalArgumentException`.

```java
@Test
void acceptsExactlyTheCanonicalClosureInputRoles() {
    List<ProspectiveClosureAssessment.InputArtifact> inputs = validInputs();
    ProspectiveClosureAssessment assessment = assessment(inputs);
    assertEquals(2, assessment.schemaVersion());
    assertEquals(ProspectiveClosureAssessment.requiredInputRoles(),
            assessment.inputArtifacts().stream()
                    .map(ProspectiveClosureAssessment.InputArtifact::role)
                    .toList());
}

@Test
void rejectsAnIncompleteOrNoncanonicalProvenanceSet() {
    List<ProspectiveClosureAssessment.InputArtifact> missing = new ArrayList<>(validInputs());
    missing.removeLast();
    assertThrows(IllegalArgumentException.class, () -> assessment(missing));

    List<ProspectiveClosureAssessment.InputArtifact> reversed = new ArrayList<>(validInputs());
    Collections.reverse(reversed);
    assertThrows(IllegalArgumentException.class, () -> assessment(reversed));
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
./gradlew test --tests dev.tlamutant.prospective.ProspectiveClosureAssessmentTest
```

Expected: compilation fails because `InputArtifact`, `inputArtifacts`, and `requiredInputRoles` do not exist.

- [ ] **Step 3: Implement the minimal schema and validation**

Add `inputArtifacts` before `outcomes`, require schema version 2, expose the canonical role list, and validate each flat record.

```java
private static final List<String> REQUIRED_INPUT_ROLES = List.of(
        "application",
        "census-receipt",
        "dossier",
        "policy",
        "review",
        "tools",
        "v10-census",
        "v10-discovery",
        "v10-discovery-receipt",
        "v10-experiment-receipt",
        "v10-manifest",
        "v10-protocol",
        "v11-census",
        "v11-manifest",
        "v11-protocol",
        "v11-result",
        "validation-receipt",
        "validator-application");

public static List<String> requiredInputRoles() {
    return REQUIRED_INPUT_ROLES;
}

public record InputArtifact(String role, String sha256, long sizeBytes) {
    public InputArtifact {
        if (role == null
                || !role.matches("[a-z0-9][a-z0-9-]{0,63}")
                || sha256 == null
                || !sha256.matches("[0-9a-f]{64}")
                || sizeBytes <= 0) {
            throw new IllegalArgumentException("Prospective closure input artifact is invalid");
        }
    }
}
```

Copy the supplied list and require its projected roles to equal `REQUIRED_INPUT_ROLES` exactly. This one comparison rejects omissions, additions, duplicates, and order changes.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the same focused Gradle command. Expected: PASS with no failures.

- [ ] **Step 5: Commit the schema slice**

```bash
git add src/main/java/dev/tlamutant/prospective/ProspectiveClosureAssessment.java src/test/java/dev/tlamutant/prospective/ProspectiveClosureAssessmentTest.java
git commit -m "feat: add closure assessment provenance schema"
```

### Task 2: Build provenance from the exact bounded snapshots

**Files:**
- Create: `src/main/java/dev/tlamutant/prospective/ProspectiveClosureProvenance.java`
- Create: `src/test/java/dev/tlamutant/prospective/ProspectiveClosureProvenanceTest.java`

- [ ] **Step 1: Write a failing snapshot conversion test**

Create one temporary regular file per experiment receipt role plus separate validation receipt, result, and validator files. Read each with `BoundedFileSnapshot`, call the wished-for API, and compare every output identity with its source snapshot.

```java
List<ProspectiveClosureAssessment.InputArtifact> actual = ProspectiveClosureProvenance.create(
        snapshots,
        validationReceipt,
        result,
        validatorApplication);

assertEquals(ProspectiveClosureAssessment.requiredInputRoles(),
        actual.stream().map(ProspectiveClosureAssessment.InputArtifact::role).toList());
assertEquals(validationReceipt.sha256(), artifact(actual, "validation-receipt").sha256());
assertEquals(result.sha256(), artifact(actual, "v11-result").sha256());
assertEquals(validatorApplication.sha256(), artifact(actual, "validator-application").sha256());
assertEquals(snapshots.get("application").sha256(), artifact(actual, "application").sha256());
```

Also remove one receipt role and assert that creation fails before an incomplete provenance list can be returned.

- [ ] **Step 2: Run the focused test and verify RED**

```bash
./gradlew test --tests dev.tlamutant.prospective.ProspectiveClosureProvenanceTest
```

Expected: compilation fails because `ProspectiveClosureProvenance` does not exist.

- [ ] **Step 3: Implement the focused provenance builder**

```java
public final class ProspectiveClosureProvenance {
    private ProspectiveClosureProvenance() {}

    public static List<ProspectiveClosureAssessment.InputArtifact> create(
            Map<String, BoundedFileSnapshot> receiptInputs,
            BoundedFileSnapshot validationReceipt,
            BoundedFileSnapshot result,
            BoundedFileSnapshot validatorApplication) {
        Objects.requireNonNull(receiptInputs, "receiptInputs");
        if (!receiptInputs.keySet().equals(ProspectiveReceipt.INTERVENTION_EXPERIMENT_ROLES)) {
            throw new IllegalArgumentException("Prospective closure provenance input roles are incomplete");
        }
        Map<String, BoundedFileSnapshot> all = new HashMap<>(receiptInputs);
        all.put("validation-receipt", Objects.requireNonNull(validationReceipt, "validationReceipt"));
        all.put("v11-result", Objects.requireNonNull(result, "result"));
        all.put("validator-application", Objects.requireNonNull(validatorApplication, "validatorApplication"));
        return ProspectiveClosureAssessment.requiredInputRoles().stream()
                .map(role -> artifact(role, all.get(role)))
                .toList();
    }
}
```

The private `artifact` helper constructs the flat assessment record from `sha256()` and `sizeBytes()`.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the same focused command. Expected: PASS.

- [ ] **Step 5: Commit the provenance builder slice**

```bash
git add src/main/java/dev/tlamutant/prospective/ProspectiveClosureProvenance.java src/test/java/dev/tlamutant/prospective/ProspectiveClosureProvenanceTest.java
git commit -m "feat: derive closure provenance from snapshots"
```

### Task 3: Thread provenance through validation and CLI generation

**Files:**
- Modify: `src/test/java/dev/tlamutant/prospective/ProspectiveClosureValidatorTest.java`
- Modify: `src/main/java/dev/tlamutant/prospective/ProspectiveClosureValidator.java`
- Modify: `src/main/java/dev/tlamutant/cli/Main.java`

- [ ] **Step 1: Make closure validator tests require provenance**

Add a `provenance()` fixture producing all required roles and call the new signature in every test.

```java
private static List<ProspectiveClosureAssessment.InputArtifact> provenance() {
    return ProspectiveClosureAssessment.requiredInputRoles().stream()
            .map(role -> new ProspectiveClosureAssessment.InputArtifact(role, "0".repeat(64), 1))
            .toList();
}
```

Assert the verified assessment retains exactly that immutable list.

- [ ] **Step 2: Run closure validator tests and verify RED**

```bash
./gradlew test --tests dev.tlamutant.prospective.ProspectiveClosureValidatorTest
```

Expected: compilation fails because the validator signature and assessment constructor do not yet accept the supplied provenance.

- [ ] **Step 3: Update the validator API minimally**

Add `List<ProspectiveClosureAssessment.InputArtifact> inputArtifacts` to `evaluate`, require it non-null, and construct schema version 2 with the list immediately before outcomes.

```java
return new ProspectiveClosureAssessment(
        2,
        intervention.profile(),
        ProspectiveProtocol.REVIEWER_MODE,
        closed == outcomes.size() ? "VERIFIED" : "UNRESOLVED",
        outcomes.size(),
        closed,
        rejectedByBaseline,
        unresolved,
        inputArtifacts,
        outcomes);
```

- [ ] **Step 4: Update CLI generation from exact snapshots**

Read the validator binary with the same bounded, no-symlink snapshot primitive and pass the generated provenance into validation.

```java
BoundedFileSnapshot validatorSnapshot =
        BoundedFileSnapshot.read(applicationJar(), MAXIMUM_APPLICATION_BYTES);
List<ProspectiveClosureAssessment.InputArtifact> provenance = ProspectiveClosureProvenance.create(
        validated.snapshots(),
        validated.receiptSnapshot(),
        resultSnapshot,
        validatorSnapshot);
ProspectiveClosureAssessment assessment = ProspectiveClosureValidator.evaluate(
        validated.dossier(),
        validated.ledger(),
        validated.review(),
        validated.v10Discovery(),
        result,
        provenance);
```

Import `ProspectiveClosureProvenance` and define or reuse a 128 MiB application snapshot bound in `Main`.

- [ ] **Step 5: Run focused and integration compilation checks**

```bash
./gradlew test --tests dev.tlamutant.prospective.ProspectiveClosureValidatorTest integrationTestClasses
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit the CLI threading slice**

```bash
git add src/main/java/dev/tlamutant/cli/Main.java src/main/java/dev/tlamutant/prospective/ProspectiveClosureValidator.java src/test/java/dev/tlamutant/prospective/ProspectiveClosureValidatorTest.java
git commit -m "feat: bind closure decisions to provenance"
```

### Task 4: Rebuild and regenerate the canonical assessment

**Files:**
- Modify: `config/current-application.json`
- Modify: `docs/experiments/gcd-prospective-closure-assessment-v11.json`
- Modify: `src/integrationTest/java/dev/tlamutant/prospective/HistoricalArtifactFreezeIntegrationTest.java`

- [ ] **Step 1: Build the validator JAR twice from clean uncached inputs**

```bash
./gradlew clean fatJar --no-build-cache
cp build/libs/tla-mutant-all.jar /tmp/tla-mutant-provenance-validator-first.jar
shasum -a 256 build/libs/tla-mutant-all.jar
stat -f '%z' build/libs/tla-mutant-all.jar
./gradlew clean fatJar --no-build-cache
cmp -s /tmp/tla-mutant-provenance-validator-first.jar build/libs/tla-mutant-all.jar
```

Expected: `cmp` exits 0. Record the new digest and size in `config/current-application.json` with `apply_patch`.

- [ ] **Step 2: Verify the historical execution JAR before generation**

```bash
shasum -a 256 /tmp/tla-mutant-v11-closure-first.jar
stat -f '%z' /tmp/tla-mutant-v11-closure-first.jar
```

Expected: SHA-256 `0c84489df67046b7418cc7dea6f9b794ec17f9de42664049814fe48d02b378dd`, size `3148231`.

- [ ] **Step 3: Preserve the schema version 1 output temporarily and generate schema version 2**

Move the existing canonical output to `build/reports/gcd-prospective-closure-assessment-v11-schema-v1.json`, then run the current validator JAR with the frozen v10/v11 paths, `--execution-application /tmp/tla-mutant-v11-closure-first.jar`, and the canonical output path. Compare decision fields and outcomes with the preserved schema version 1 artifact; only schema/provenance fields may differ.

- [ ] **Step 4: Add frozen identity integration assertions**

Extend `HistoricalArtifactFreezeIntegrationTest` with the regenerated assessment identity and parse it to assert:

```java
ProspectiveClosureAssessment assessment = new ObjectMapper().readValue(
        Path.of("docs/experiments/gcd-prospective-closure-assessment-v11.json").toFile(),
        ProspectiveClosureAssessment.class);
assertEquals(2, assessment.schemaVersion());
assertEquals(ProspectiveClosureAssessment.requiredInputRoles(),
        assessment.inputArtifacts().stream()
                .map(ProspectiveClosureAssessment.InputArtifact::role)
                .toList());
assertEquals("0c84489df67046b7418cc7dea6f9b794ec17f9de42664049814fe48d02b378dd",
        artifact(assessment, "application").sha256());
assertEquals(currentApplication.sha256(), artifact(assessment, "validator-application").sha256());
```

For repository-resident roles, resolve the known role-to-path map and recompute the digest and size from the actual file. For `application`, compare to the receipt-bound identity because the historical binary is intentionally not committed.

- [ ] **Step 5: Run artifact and frozen application checks**

```bash
./gradlew integrationTest --tests dev.tlamutant.prospective.HistoricalArtifactFreezeIntegrationTest verifyFrozenApplication --rerun-tasks
```

Expected: PASS and BUILD SUCCESSFUL.

- [ ] **Step 6: Commit the regenerated machine evidence**

```bash
git add config/current-application.json docs/experiments/gcd-prospective-closure-assessment-v11.json src/integrationTest/java/dev/tlamutant/prospective/HistoricalArtifactFreezeIntegrationTest.java
git commit -m "experiment: publish self-contained closure provenance"
```

### Task 5: Update public interpretation and close coverage

**Files:**
- Modify: `README.md`
- Modify: `docs/experiments/gcd-prospective-final-assessment.md`
- Modify: `docs/security/2026-08-29-v10-prospective-review.md`
- Modify: `docs/superpowers/plans/2026-08-29-prospective-assessment-provenance.md`

- [ ] **Step 1: Update narrative provenance and historical digest notes**

Document the new schema version 2 assessment digest/size, validator JAR digest/size, exact 18-role coverage, unchanged closure decision, and historical schema version 1 digest `2c603ebb122f5dc3d9ec7883d0c63e0a7b65d197baec6167cd0c93662d1f52cd` with size `1288`.

- [ ] **Step 2: Mark AP01-AP08 with evidence in this plan**

Add a compact execution ledger beneath the obligations that names the relevant unit, integration, reproducibility, and cleanup evidence. Do not mark an obligation verified before its command has run.

- [ ] **Step 3: Run formatting and non-sandbox verification**

```bash
./gradlew spotlessCheck test integrationTest verifyFrozenApplication --rerun-tasks
```

Expected: BUILD SUCCESSFUL with zero test failures.

- [ ] **Step 4: Obtain independent correctness and security review**

Request read-only review of the final diff. Security output must use Must Fix / Should Fix / Notes. Resolve every blocker and Must Fix before continuing.

- [ ] **Step 5: Run final sandbox verification and exact cleanup audit**

Start `podman-machine-default` in an owned persistent shell, run:

```bash
./gradlew spotlessCheck test integrationTest sandboxTest verifyFrozenApplication --rerun-tasks
```

Then verify zero `dev.tlamutant.run` containers and volumes, stop the Gradle daemon, stop the Podman machine, exit the owned shell, and audit PPID 1 target processes plus `REAP_DRY_RUN=1 reap-orphan-devprocs`.

- [ ] **Step 6: Commit documentation and review closure**

```bash
git add README.md docs/experiments/gcd-prospective-final-assessment.md docs/security/2026-08-29-v10-prospective-review.md docs/superpowers/plans/2026-08-29-prospective-assessment-provenance.md
git commit -m "docs: close assessment provenance review"
```

### Task 6: Integrate and record local work

**Files:**
- Create: `docs.local/logs/2026-08-29/2026-08-29-007-assessment-provenance.md`

- [ ] **Step 1: Fast-forward the clean feature branch to main**

Confirm main is clean and the branch descends from main, then use `git merge --ff-only`.

- [ ] **Step 2: Verify the merged main checkout**

```bash
./gradlew spotlessCheck test integrationTest verifyFrozenApplication --rerun-tasks
./gradlew --stop
```

Expected: BUILD SUCCESSFUL, no Gradle daemon afterward, and Podman remains stopped.

- [ ] **Step 3: Write the ignored Japanese work log**

Record the design, RED/GREEN evidence, new identities, assessment regeneration, review results, test counts, cleanup, commits, and remaining limitations under `docs.local/logs/2026-08-29/2026-08-29-007-assessment-provenance.md` using `apply_patch`.

- [ ] **Step 4: Remove the merged worktree and branch**

Verify the feature branch is an ancestor of main, remove only its explicit `.worktree/` path, prune worktree metadata, and delete the merged feature branch.
