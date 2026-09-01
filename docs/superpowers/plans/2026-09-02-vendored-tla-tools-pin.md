# Vendored TLA+ Tools Pin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provision the exact receipt-bound TLA+ Tools JAR from a reviewed Git blob instead of the mutable upstream v1.8.0 prerelease asset.

**Architecture:** Commit the unchanged 4.5 MB JAR under a revision-specific vendor path, describe its provenance and licenses, and make Gradle validate and atomically copy it into the existing `.tools` location. Preserve the historical byte identity, retain `fetchTlaTools` as a network-free compatibility alias, and reject path escape, symbolic links, missing files, and byte changes before replacing the runtime copy.

**Tech Stack:** Gradle Kotlin DSL, Java 21, JUnit 5, SHA-256, Git signed tags

---

## File structure

- Create: `vendor/tla-tools/1.8.0-9787e65/tla2tools.jar` — exact receipt-bound upstream binary, unchanged.
- Create: `vendor/tla-tools/1.8.0-9787e65/README.md` — provenance, comparison, source availability, and authenticity limits.
- Create: `vendor/tla-tools/1.8.0-9787e65/THIRD_PARTY_NOTICES.md` — redistribution and embedded-license notice.
- Modify: `config/tla-tools.lock.json` — local canonical path plus upstream provenance.
- Modify: `build.gradle.kts` — network-free provisioning and compatibility alias.
- Modify: `src/test/java/dev/tlamutant/build/ToolchainLockTest.java` — lock, binary, license, manifest, and build-script security checks.
- Create: `docs/security/2026-09-02-vendored-tla-tools-pin-review.md` — initial and final Security Specialist review.
- Create: `docs.local/logs/2026-09-02/2026-09-02-001-vendored-tla-tools-pin.md` — ignored Japanese work log.

## Coverage ledger

| ID | Obligation | Risk | Checks | Status before implementation |
|---|---|---|---|---|
| `PIN-01` | The vendored source has the historical size and SHA-256. | Critical | JUnit file digest; Gradle source verification; post-commit shell digest | Debt |
| `PIN-02` | Provisioning performs no network access and has no rolling fallback. | Critical | Build-script JUnit check; `--offline --rerun-tasks`; source review | Debt |
| `PIN-03` | The source path cannot be absolute, escape the project, or traverse a symbolic link. | High | Lock test; Gradle containment and component checks; disposable negative cases | Debt |
| `PIN-04` | A missing, truncated, or changed source fails before replacing the runtime copy. | Critical | Disposable checkout truncation test; destination digest check | Debt |
| `PIN-05` | The destination is copied through an owner-only temporary file and atomically replaced only after verification. | High | Source review; Gradle implementation; successful rerun | Debt |
| `PIN-06` | Existing `fetchTlaTools` callers remain compatible. | Medium | `fetchTlaTools --offline --rerun-tasks`; task dependency check | Debt |
| `PIN-07` | Embedded licenses and upstream source availability remain visible. | High | JAR entry test; tracked notices; manual review | Debt |
| `PIN-08` | v8-v11 receipts and reports remain unchanged. | Critical | Path-scoped Git diff; historical freeze tests | Debt |
| `PIN-09` | The archive tag identifies the reviewed commit without claiming upstream signature. | High | Signed-tag verification and message check | Debt |

The overlap between `PIN-01`, `PIN-02`, `PIN-04`, and `PIN-08` is intentional. The same artifact identity is checked at unit, build-task, disposable-checkout, and historical-receipt layers because failure at any one layer has a different security consequence.

Optional verification tools are not applicable. The behavior space has five explicit file/path classes and no concurrency or combinatorial state interactions; bounded negative tests provide stronger evidence than generated PICT, TLA+, Alloy, property-testing, or fuzzing artifacts for this change.

### Task 1: Add failing lock and provenance tests

**Files:**
- Modify: `src/test/java/dev/tlamutant/build/ToolchainLockTest.java`
- Test: `src/test/java/dev/tlamutant/build/ToolchainLockTest.java`

- [ ] **Step 1: Replace the URL-only assertions with the new lock contract**

Add constants for the exact vendor path, upstream URL, upstream revision, size, and digest. Resolve the vendor path against the normalized project root and require containment, a regular non-symbolic-link file, exact size, and exact digest.

Use this structure:

```java
private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath().normalize();
private static final Path LOCK = PROJECT_ROOT.resolve("config/tla-tools.lock.json");
private static final String EXPECTED_VENDOR_PATH =
        "vendor/tla-tools/1.8.0-9787e65/tla2tools.jar";
private static final String EXPECTED_UPSTREAM_URL =
        "https://github.com/tlaplus/tlaplus/releases/download/v1.8.0/tla2tools.jar";
private static final String EXPECTED_UPSTREAM_REVISION =
        "9787e65714c37d94eebab40774bff401bd9f616d";
private static final long EXPECTED_SIZE = 4_487_757L;
private static final String EXPECTED_SHA256 =
        "eabd140a70f49eb9305a3bd3f3df944eddf87e5a90d329789085f8953a80533a";
```

In `pinsTheReviewedTlaToolsArtifact`, assert the new `vendorPath`, `upstreamUrl`, and `upstreamRevision` fields. Use `MessageDigest` and the existing byte-array hex pattern to compare the actual vendor bytes.

- [ ] **Step 2: Add a JAR provenance test**

Open the vendored file with `JarFile` and require:

```java
assertNotNull(jar.getJarEntry("META-INF/LICENSE.md"));
assertNotNull(jar.getJarEntry("License.txt"));
assertEquals(EXPECTED_UPSTREAM_REVISION, manifest.getMainAttributes().getValue("X-Git-Revision"));
assertEquals("tlc2.TLC", manifest.getMainAttributes().getValue("Main-Class"));
```

Also assert that no top-level `META-INF/*.SF`, `*.RSA`, `*.DSA`, `*.EC`, or `SIG-*` entry exists, documenting that the reviewed upstream artifact is unsigned.

- [ ] **Step 3: Add a build-script security characterization test**

Read `build.gradle.kts` and assert that the final implementation must contain `vendorPath`, `upstreamRevision`, `provisionTlaTools`, `NOFOLLOW_LINKS`, `ATOMIC_MOVE`, and `dependsOn(provisionTlaTools)`. Assert that it contains none of these network implementation strings:

```java
assertFalse(script.contains("java.net.URI"));
assertFalse(script.contains("java.net.http.HttpClient"));
assertFalse(script.contains("java.net.http.HttpRequest"));
assertFalse(script.contains("java.net.http.HttpResponse"));
```

- [ ] **Step 4: Run the focused test and verify red**

Run:

```sh
./gradlew test --tests dev.tlamutant.build.ToolchainLockTest \
  -x fetchTlaTools \
  --no-build-cache --rerun-tasks --console=plain
```

Expected: FAIL because `vendorPath` and the vendored artifact do not exist and the build still contains network imports.

### Task 2: Add the exact artifact and public provenance

**Files:**
- Create: `vendor/tla-tools/1.8.0-9787e65/tla2tools.jar`
- Create: `vendor/tla-tools/1.8.0-9787e65/README.md`
- Create: `vendor/tla-tools/1.8.0-9787e65/THIRD_PARTY_NOTICES.md`
- Modify: `config/tla-tools.lock.json`

- [ ] **Step 1: Reverify the source copy before adding it**

Run against the main checkout's preserved copy:

```sh
test "$(stat -f %z /Users/tk/work/tla-mutant/.tools/tla2tools-1.8.0.jar)" = 4487757
test "$(shasum -a 256 /Users/tk/work/tla-mutant/.tools/tla2tools-1.8.0.jar | awk '{print $1}')" = eabd140a70f49eb9305a3bd3f3df944eddf87e5a90d329789085f8953a80533a
```

Expected: both checks exit zero.

- [ ] **Step 2: Copy the binary without transforming it**

Run:

```sh
mkdir -p vendor/tla-tools/1.8.0-9787e65
install -m 0644 /Users/tk/work/tla-mutant/.tools/tla2tools-1.8.0.jar \
  vendor/tla-tools/1.8.0-9787e65/tla2tools.jar
```

Re-run the exact size and digest checks on the vendored path before staging it.

- [ ] **Step 3: Write the provenance README**

Document the following facts in plain English:

- Exact artifact size, SHA-256, manifest revision, build timestamp, and TLC version string.
- Original upstream rolling URL and source revision URL.
- The artifact is unsigned.
- The artifact is preserved because v8-v11 receipts bind it by bytes.
- On 2026-09-01, the same URL served `dbcc7555...`, a different build with instruction-equivalent bytecode but different manifest and line tables.
- Source is available from the upstream Git revision and can be built using upstream's documented build process.
- Git history and the project tag attest only which bytes this project archived.

- [ ] **Step 4: Write third-party notices**

State that the unchanged JAR contains `META-INF/LICENSE.md` and `License.txt`, that redistribution retains those embedded notices, and that source is available at the recorded upstream revision. Identify the TLA+ Tools MIT notice and the embedded Eclipse Public License 2.0/GPL secondary-license text without claiming a project-level license change. State that this notice is an engineering record, not legal advice.

- [ ] **Step 5: Replace the lock schema**

Use the exact JSON from the approved design:

```json
{
  "version": "1.8.0",
  "vendorPath": "vendor/tla-tools/1.8.0-9787e65/tla2tools.jar",
  "upstreamUrl": "https://github.com/tlaplus/tlaplus/releases/download/v1.8.0/tla2tools.jar",
  "upstreamRevision": "9787e65714c37d94eebab40774bff401bd9f616d",
  "sizeBytes": 4487757,
  "sha256": "eabd140a70f49eb9305a3bd3f3df944eddf87e5a90d329789085f8953a80533a"
}
```

- [ ] **Step 6: Run the focused test and confirm that only the build-script assertions remain red**

Run the focused command from Task 1.

Expected: lock, binary, manifest, and license checks pass; network/provisioning build-script assertions still fail.

### Task 3: Replace the downloader with verified local provisioning

**Files:**
- Modify: `build.gradle.kts`
- Test: `src/test/java/dev/tlamutant/build/ToolchainLockTest.java`

- [ ] **Step 1: Remove network imports and expand `TlaToolsLock`**

Remove `URI`, `HttpClient`, `HttpRequest`, and `HttpResponse`. Add `LinkOption` and change the lock type to:

```kotlin
data class TlaToolsLock(
    val version: String,
    val vendorPath: String,
    val upstreamUrl: String,
    val upstreamRevision: String,
    val sizeBytes: Long,
    val sha256: String,
)
```

Parse every field explicitly so a malformed or incomplete lock fails during task realization.

- [ ] **Step 2: Add shared size and digest verification**

Add:

```kotlin
fun verifyTlaToolsArtifact(path: Path, lock: TlaToolsLock, label: String) {
    require(Files.size(path) == lock.sizeBytes) { "$label size mismatch: ${Files.size(path)}" }
    val digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).hex()
    require(digest == lock.sha256) { "$label SHA-256 mismatch" }
}
```

Use this function for the vendored source, temporary copy, and provisioned destination.

- [ ] **Step 3: Add a contained, non-symlink source resolver**

Resolve from the project root and reject every symbolic-link component:

```kotlin
fun resolveVendoredArtifact(lock: TlaToolsLock): Path {
    val projectRoot = layout.projectDirectory.asFile.toPath().toAbsolutePath().normalize()
    val configured = Path.of(lock.vendorPath)
    require(!configured.isAbsolute) { "Vendored TLA+ Tools path must be relative" }
    val source = projectRoot.resolve(configured).normalize()
    require(source.startsWith(projectRoot)) { "Vendored TLA+ Tools path escapes the project" }
    var current = projectRoot
    projectRoot.relativize(source).forEach { component ->
        current = current.resolve(component)
        require(!Files.isSymbolicLink(current)) { "Vendored TLA+ Tools path must not contain symbolic links" }
    }
    require(Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
        "Vendored TLA+ Tools artifact must be a regular file"
    }
    return source
}
```

- [ ] **Step 4: Implement `provisionTlaTools`**

Declare the lock and vendored provider as inputs and `.tools/tla2tools-1.8.0.jar` as output. In `doLast`:

1. Resolve and verify the source.
2. Create the destination directory.
3. Create the temporary file beside the destination.
4. Set POSIX permissions to owner read/write when supported.
5. Copy through an input stream opened with `NOFOLLOW_LINKS`.
6. Verify the temporary file.
7. Atomically replace the destination.
8. Delete the temporary file in `finally`.

The copy body must be:

```kotlin
Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS).use { input ->
    Files.newOutputStream(temporary).use { output -> input.copyTo(output) }
}
```

- [ ] **Step 5: Preserve the compatibility alias and independent verification**

Define:

```kotlin
val fetchTlaTools by tasks.registering {
    description = "Compatibility alias for network-free vendored TLA+ Tools provisioning."
    dependsOn(provisionTlaTools)
}
```

Make `verifyTlaTools` depend on `provisionTlaTools` and call `verifyTlaToolsArtifact` for the destination.

- [ ] **Step 6: Run formatting and the focused test**

Run:

```sh
./gradlew spotlessApply
./gradlew test --tests dev.tlamutant.build.ToolchainLockTest \
  --no-build-cache --rerun-tasks --console=plain
```

Expected: PASS. `provisionTlaTools` runs; no network download occurs.

- [ ] **Step 7: Commit the implementation slice**

```sh
git add build.gradle.kts config/tla-tools.lock.json \
  src/test/java/dev/tlamutant/build/ToolchainLockTest.java \
  vendor/tla-tools/1.8.0-9787e65
git commit -m "build: vendor receipt-bound TLA tools"
```

### Task 4: Validate negative paths in disposable checkouts

**Files:**
- Test: `build.gradle.kts`
- Test: `vendor/tla-tools/1.8.0-9787e65/tla2tools.jar`

- [ ] **Step 1: Validate fresh offline provisioning**

Create a temporary checkout with `mktemp -d`, use `git archive HEAD` to populate it, and run:

```sh
./gradlew verifyTlaTools --offline --no-build-cache --rerun-tasks --console=plain
```

Expected: `BUILD SUCCESSFUL`; `.tools/tla2tools-1.8.0.jar` is created with the exact historical size and digest. Remove the explicit temporary directory after checking its path and contents.

- [ ] **Step 2: Validate truncated-source rejection**

Create another disposable archive checkout. Confirm the target starts below that temporary root, then run:

```sh
truncate -s 4487756 vendor/tla-tools/1.8.0-9787e65/tla2tools.jar
./gradlew provisionTlaTools --offline --no-build-cache --rerun-tasks --console=plain
```

Expected: FAIL with `Vendored TLA+ Tools size mismatch`; no valid destination is created or replaced.

- [ ] **Step 3: Validate path escape and symlink rejection**

In separate disposable checkouts:

- Set `vendorPath` to `../tla2tools.jar`; expect `path escapes the project`.
- Replace the vendor file with a symlink to a copied valid JAR inside the temporary root; expect `path must not contain symbolic links`.

Use `apply_patch` for the temporary JSON edit and explicit `ln -s` only inside the validated temporary checkout. Do not modify the canonical worktree.

- [ ] **Step 4: Validate the compatibility alias**

In a clean disposable checkout, run:

```sh
./gradlew fetchTlaTools --offline --no-build-cache --rerun-tasks --console=plain
```

Expected: `provisionTlaTools` and `fetchTlaTools` succeed, creating the exact destination without network access.

- [ ] **Step 5: Record exact commands and outcomes in the work log**

Record each temporary root, expected failure, actual message, destination state, and cleanup result. Do not retain the temporary binary copies.

### Task 5: Perform Security Specialist review and close findings

**Files:**
- Create: `docs/security/2026-09-02-vendored-tla-tools-pin-review.md`
- Modify: implementation files only if findings require changes

- [ ] **Step 1: Request clean-context review**

Give the Security Specialist only the original pin failure, approved spec, final diff, artifact identity evidence, negative-test results, and verification commands. Require `Must Fix / Should Fix / Notes`.

- [ ] **Step 2: Record the initial review in English**

The public review must distinguish repository attestation from upstream authenticity and must list the license/source-availability evidence.

- [ ] **Step 3: Resolve every Must Fix and justified Should Fix finding**

Use a focused red-green cycle for any code finding. Do not create the archive tag while a Must Fix remains.

- [ ] **Step 4: Request re-review and record the final verdict**

Expected: `Must Fix: none`. Commit the review and any review-driven corrections:

```sh
git add docs/security/2026-09-02-vendored-tla-tools-pin-review.md \
  build.gradle.kts config/tla-tools.lock.json \
  src/test/java/dev/tlamutant/build/ToolchainLockTest.java \
  vendor/tla-tools/1.8.0-9787e65
git commit -m "docs: record vendored toolchain security review"
```

### Task 6: Run complete verification and archive the toolchain

**Files:**
- Test: all tracked implementation and test files
- Create: `docs.local/logs/2026-09-02/2026-09-02-001-vendored-tla-tools-pin.md`

- [ ] **Step 1: Verify no historical artifact changed**

Run:

```sh
git diff 836103a --exit-code -- docs/experiments corpus
```

Expected: no output and exit zero.

- [ ] **Step 2: Run the full suite with forced task execution**

Run:

```sh
./gradlew spotlessCheck test integrationTest verifyFrozenApplication \
  --no-build-cache --rerun-tasks --console=plain
./gradlew sandboxTest --no-build-cache --rerun-tasks --console=plain
```

Expected: both builds succeed with no failed tests. Stop Gradle daemons and any Podman machine started by the task, then verify no owned test process remains.

- [ ] **Step 3: Write the ignored Japanese work log**

Record root cause, old and replacement identities, bytecode comparison, selected tournament candidate, coverage outcomes, commits, security review, tests, process cleanup, and push/tag state.

- [ ] **Step 4: Create and verify the signed annotated tag**

After the final reviewed commit is known:

```sh
git tag -s toolchain/tla2tools-1.8.0-9787e65 -m "Archive receipt-bound TLA+ Tools 1.8.0-9787e65: 4487757 bytes, SHA-256 eabd140a70f49eb9305a3bd3f3df944eddf87e5a90d329789085f8953a80533a; upstream artifact unsigned"
git tag -v toolchain/tla2tools-1.8.0-9787e65
```

Expected: signature verification succeeds and the tag points to the final reviewed commit. Do not push the tag until branch integration is complete.

- [ ] **Step 5: Resume the public README implementation plan**

Update `docs/superpowers/plans/2026-09-01-public-readme-redesign.md` so its Quick start explains vendored provisioning and does not claim that Gradle downloads the rolling upstream asset. Then execute the remainder of that plan.
