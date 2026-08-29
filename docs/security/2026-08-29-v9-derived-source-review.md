# v9 Derived Source Security Review

## Scope

This review covers manifest-controlled insertion of `NoRequestAllocationOverlap` into the digest-pinned SimpleAllocator source, transport into the Podman sandbox, oracle attribution, and v8 compatibility.

## Must Fix

### Resolved: v9 manifest acceptance was inconsistent with the declared schema

`CorpusFetcher` previously rejected the version 3/v9 manifest accepted by the stratified validator. It now accepts only the explicit supported combinations: version 1 without a profile, version 2 for the legacy v1-v8 profiles, and version 3 for the exact v9 profile. A prepopulated verified-cache test exercises this path without network access.

### Resolved: the adopted invariant was not cryptographically bound to its edit

The validator now requires exactly one adopted target invariant and one derived source edit on the same entry. The edit must target that entry's root module, and its `definedOperator` must equal the adopted invariant name. Regression tests reject split entries, multiple adopted names, non-root edits, and operator mismatches.

### Resolved: report schema selection depended on observing a kill

Categorized experiment results and the v9 profile now select report schema 4 even when no attributed kill is observed. A regression test covers a categorized no-kill report.

### Resolved: current application identity was stale

The application JAR was rebuilt twice from clean, uncached inputs. Both builds produced SHA-256 `76114011b9d1d63f706879a4d7dbe68d6a8f991c5335963adc7075b38d60b36a` with size `3015208` bytes, and `config/current-application.json` records that identity.

## Should Fix

- `definedOperator` is metadata and is not structurally matched against the inserted TLA+ text. For this exact v9 intervention, the full inserted bytes are digest-pinned and SANY plus the baseline validates the derived module. A future generic edit facility should parse or otherwise prove the declared operator binding.
- The derived workspace relies on the verified host snapshot and container limits for aggregate file count and byte bounds. A future hardening pass should add explicit workspace-level limits.
- The generic `validate-manifest` and `corpus-experiment` entry points validate individual edit metadata but do not apply the full cross-entry stratified constraints. The public `gcd-stratified-census` and `gcd-stratified-experiment` paths used for v9 do apply them.

## Notes

- Edit paths, digests, sizes, offsets, UTF-8 boundaries, control characters, bidirectional controls, arithmetic overflow, and insertion size are checked before Podman starts and again inside the private derived workspace.
- The edit is transported as one argument to `ProcessBuilder`; no shell evaluates it.
- The source cache and read-only snapshot remain unchanged. A sandbox test proves that a valid edit affects census output only inside the container scratch copy.
- TLC runs without network access, with a read-only root filesystem, dropped capabilities, bounded resources and output, and exact labeled-resource cleanup.
- The v9 intervention is post-selected from a v8 witness. It demonstrates the closed loop but does not estimate out-of-sample discovery value.
