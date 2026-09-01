# TLA+ Tools 1.8.0, revision 9787e65

This directory preserves the exact `tla2tools.jar` used by the frozen `tla-mutant` v8 through v11 experiments.

## Artifact identity

- File: `tla2tools.jar`
- Size: 4,487,757 bytes
- SHA-256: `eabd140a70f49eb9305a3bd3f3df944eddf87e5a90d329789085f8953a80533a`
- Build timestamp: `2026-08-21T15:59:22.332Z`
- Manifest revision: `9787e65714c37d94eebab40774bff401bd9f616d`
- TLC version: `TLC2 Version 2026.08.21.155922 (rev: 9787e65)`
- Signature status: unsigned

The project first committed this identity on 2026-08-29. Historical manifests, receipts, reports, and assessments bind the file by size and SHA-256.

## Upstream provenance

The artifact was obtained from the TLA+ upstream `v1.8.0` prerelease asset:

`https://github.com/tlaplus/tlaplus/releases/download/v1.8.0/tla2tools.jar`

Upstream source for the recorded manifest revision is available at:

`https://github.com/tlaplus/tlaplus/tree/9787e65714c37d94eebab40774bff401bd9f616d`

Upstream build instructions are available in `DEVELOPING.md` at that revision. The JAR retains its embedded license files unchanged; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Why the binary is tracked

The upstream v1.8.0 prerelease asset is rolling. On 2026-09-01, the same URL began serving a different artifact: 4,487,774 bytes with SHA-256 `dbcc75552f21978a4846688b8e23be1a6b6c0b3fcee35d78fec2df167958ec94` and manifest revision `95b800c676b3312ce97d7478d1081cd468ece75c`.

The two archives contain the same 2,223 entries. Their extracted contents differ in the manifest and 14 class files. The changed classes have identical instructions, APIs, constants, exception tables, local-variable tables, and stack-map tables; their differences are limited to source line numbers after upstream comment and Javadoc changes. The new build is therefore execution-logic equivalent under this comparison, but it is not the same artifact and cannot satisfy the historical byte-bound receipts.

`tla-mutant` stores the reviewed binary as a normal Git object so a complete checkout can provision the historical toolchain without reading the mutable prerelease URL. Git history and the project archive tag attest which bytes this project preserved. They do not constitute an upstream signature or independent cryptographic proof that upstream produced the original unsigned JAR.
