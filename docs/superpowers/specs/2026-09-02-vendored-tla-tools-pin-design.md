# Vendored TLA+ Tools Pin Design

## Context

The project pins TLA+ Tools v1.8.0 by URL, byte size, and SHA-256. The pinned URL points to the upstream `v1.8.0` prerelease asset. Upstream documents that this prerelease is rebuilt from `master`, so the bytes at that URL are not immutable.

The project originally reviewed and pinned this artifact:

- Size: 4,487,757 bytes
- SHA-256: `eabd140a70f49eb9305a3bd3f3df944eddf87e5a90d329789085f8953a80533a`
- Manifest revision: `9787e65714c37d94eebab40774bff401bd9f616d`
- TLC version string: `TLC2 Version 2026.08.21.155922 (rev: 9787e65)`

The asset currently served at the same URL is a different unsigned artifact:

- Size: 4,487,774 bytes
- SHA-256: `dbcc75552f21978a4846688b8e23be1a6b6c0b3fcee35d78fec2df167958ec94`
- Manifest revision: `95b800c676b3312ce97d7478d1081cd468ece75c`
- TLC version string: `TLC2 Version 2026.09.01.002747 (rev: 95b800c)`

The existing downloader rejects the replacement because it exceeds the pinned size. This is correct fail-closed behavior, but it means a fresh checkout can no longer obtain the artifact required by the build and the frozen v8 through v11 experiment receipts.

## Comparison result

The old and current upstream assets have the same 2,223 archive entries. Their extracted contents differ only in `META-INF/MANIFEST.MF` and 14 class files. The changed classes have identical fields, methods, descriptors, constants, instructions, exception tables, local-variable tables, and stack-map tables. Their differences are limited to `LineNumberTable` values caused by upstream comment and Javadoc changes. The two upstream commits between the manifests change CI configuration and comments, not executable source.

This supports treating the current asset as execution-logic equivalent for evaluation purposes. It does not make the artifacts identical. Their digests, manifests, Git revisions, version output, and diagnostic line numbers differ. The new artifact cannot replace the old one in byte-bound historical receipts.

## Goals

- Make the exact reviewed artifact available from every complete Git checkout.
- Preserve the existing artifact size and SHA-256 in all v8 through v11 receipts and reports.
- Remove normal build and verification dependence on the mutable upstream prerelease asset.
- Continue to fail closed if either the vendored source or provisioned copy differs by one byte.
- Preserve the existing `.tools/tla2tools-1.8.0.jar` runtime path so CLI commands and historical manifests do not need to change.
- Record enough provenance and licensing information for public redistribution and later audit.

## Non-goals

- Do not update historical experiments to the current upstream artifact.
- Do not describe the vendored artifact as signed or independently authenticated by upstream.
- Do not make the rolling prerelease a fallback source.
- Do not introduce a second current-versus-historical runtime toolchain.
- Do not recompute experiment results, receipts, manifests, or frozen application identities unless verification demonstrates that a source change requires it.
- Do not publish a separate GitHub Release asset in this change.

## Selected design

### Artifact layout

Commit the exact existing artifact as a normal Git object:

```text
vendor/tla-tools/1.8.0-9787e65/
  tla2tools.jar
  README.md
  THIRD_PARTY_NOTICES.md
```

Git LFS will not be used. A normal Git blob makes the artifact available with the repository history and binds it to Git's object identity. The repository will grow by approximately 4.5 MB before Git compression.

`README.md` will record:

- The upstream project and original rolling URL.
- The date on which the artifact was originally pinned by this project.
- Exact size and SHA-256.
- Manifest revision, version string, and unsigned status.
- The relationship to the frozen experiment receipts.
- The 2026-09-01 upstream replacement and the bounded comparison result.
- The fact that repository history proves which bytes this project preserved, not that upstream cryptographically signed them.

`THIRD_PARTY_NOTICES.md` will identify the upstream project and licenses represented in the JAR. The JAR will remain byte-for-byte unchanged, including its embedded `META-INF/LICENSE.md` and `License.txt` files. This project does not reinterpret or remove upstream notices.

### Lock schema

`config/tla-tools.lock.json` will continue to be the authoritative current lock and will retain the existing `version`, `sizeBytes`, and `sha256` values. Replace the executable download URL with explicit provenance and local-source fields:

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

The build must resolve `vendorPath` below the project root, normalize it, and reject absolute paths, traversal outside the project, symbolic-link sources, non-regular files, and size or digest mismatches. `upstreamUrl` is provenance only and must not be opened by any Gradle task.

### Gradle task behavior

Add `provisionTlaTools` as the task that creates `.tools/tla2tools-1.8.0.jar`.

The task will:

1. Parse the authoritative lock.
2. Resolve and validate the vendored source path.
3. Verify the source size and SHA-256 before copying.
4. Create an owner-only temporary file next to the destination.
5. Copy the source without following symbolic links.
6. Verify the temporary copy's size and SHA-256.
7. Atomically replace the destination.
8. Delete the temporary file on every failure path.

`verifyTlaTools` will depend on `provisionTlaTools` and will continue to verify the provisioned destination independently.

Keep `fetchTlaTools` as a compatibility alias that depends on `provisionTlaTools`. Its description will state that it performs no network access. Existing automation that names `fetchTlaTools` will continue to work, but the task will not contain an HTTP client or a URL allowlist.

Running with `--rerun-tasks` will safely revalidate and recopy the vendored bytes instead of forcing a network download.

### Historical compatibility

The destination filename, artifact bytes, size, and SHA-256 remain unchanged. Existing experiment receipts, manifests, census reports, mutation reports, and assessments must remain byte-for-byte unchanged.

The historical reproduction commands may continue to pass `.tools/tla2tools-1.8.0.jar`. The only change is how that file is provisioned.

### Archive attestation

After the implementation, security review, and complete verification pass, create a signed annotated tag named:

```text
toolchain/tla2tools-1.8.0-9787e65
```

The tag message will state the vendored path, exact size, SHA-256, upstream manifest revision, and unsigned upstream status. The tag attests which bytes this project archived. It is not an upstream signature and does not upgrade the artifact's original authenticity.

Pushing the tag is part of publishing the repaired public toolchain. The tag must not be created or pushed before the final reviewed commit is known.

## Security model

The authoritative trust relation is:

```text
reviewed digest in tracked lock
  -> tracked vendor Git blob
  -> verified owner-only temporary copy
  -> verified .tools runtime copy
  -> receipt-bound TLC execution
```

There is no network edge in this chain. A repository writer who can change both the lock and vendored binary can still propose a different toolchain, so normal code review, branch protection, and the signed archive tag remain necessary.

The old JAR is unsigned. Its equality with the artifact used by this project's historical receipts is strong evidence of continuity within this repository, but it is not cryptographic proof that upstream produced it. This limitation must remain visible in the provenance and security review documents.

## Test design

Use TDD for the tracked behavior:

1. Extend `ToolchainLockTest` to require `vendorPath`, `upstreamUrl`, `upstreamRevision`, exact size, exact digest, normalized in-repository path, regular non-symlink file, and matching vendored bytes. This test must fail before the vendor artifact and new lock schema exist.
2. Add a test that opens the JAR and requires the embedded license files, manifest revision, and expected main class.
3. Update build-script characterization to require `verifyTlaTools -> provisionTlaTools` and a network-free `fetchTlaTools` alias.
4. In a disposable checkout, truncate the vendored JAR by one byte and require `provisionTlaTools` to fail before replacing the destination.
5. In another disposable checkout, remove `.tools`, run provisioning with network disabled, and require the exact destination size and digest.
6. Run unit, integration, sandbox, formatting, and frozen-application verification with `--rerun-tasks`.
7. Confirm that no tracked file under `docs/experiments`, `corpus`, or historical configuration changed.

The negative checks operate only on validated temporary checkouts. They must not modify or delete the canonical vendor artifact or a broad workspace path.

## Security review gates

The public security review will use `Must Fix / Should Fix / Notes` and must cover:

- No network or fallback path in provisioning.
- Exact source and destination verification.
- Path containment and symbolic-link rejection.
- Owner-only temporary files and atomic replacement.
- License and provenance documentation.
- Preservation of historical receipts.
- Clear unsigned-artifact limitation.
- Signed tag meaning and scope.

No final merge, tag, or push is allowed while a `Must Fix` finding remains.

## README impact

The public README redesign may keep this quick-start command:

```sh
./gradlew verifyTlaTools verifyFrozenApplication fatJar
```

After this change, the command provisions the exact reviewed artifact from the repository and verifies both the tool and application identities. The README will state that the repository carries the pinned TLA+ Tools binary because the upstream v1.8.0 prerelease asset is rolling.

The detailed v8 through v11 reproduction commands remain valid without changing their tool paths or historical digest claims.

## Acceptance criteria

- A fresh complete checkout can provision TLA+ Tools without network access to the upstream release.
- `--rerun-tasks` does not contact or consume the rolling upstream asset.
- The vendored source and `.tools` destination both match 4,487,757 bytes and SHA-256 `eabd140a70f49eb9305a3bd3f3df944eddf87e5a90d329789085f8953a80533a`.
- Missing, truncated, changed, traversing, absolute, or symbolic-link vendor inputs fail closed.
- Existing `fetchTlaTools` invocations remain compatible and network-free.
- Embedded licenses, upstream provenance, unsigned status, and comparison limits are documented.
- No historical receipt, manifest, experiment report, or assessment changes.
- Unit, integration, sandbox, formatting, and frozen-application verification pass.
- Security review has no `Must Fix` findings.
- A signed annotated archive tag identifies the final reviewed commit and exact artifact.
