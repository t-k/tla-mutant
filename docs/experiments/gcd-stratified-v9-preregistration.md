# GCD Stratified v9 Preregistration

## Purpose

This document freezes the v9 census and evaluation rules before any v9 mutation result is generated. The intervention was selected from the v8 SimpleAllocator Request witness: it is confirmatory evidence for one closed loop, not an out-of-sample estimate of invariant discovery value.

## Frozen Inputs

| Input | SHA-256 | Size |
|---|---|---:|
| Implementation commit | `ff5a13f31b3d9db0eb2278d53f7bad8530d3a321` | n/a |
| `corpus/gcd-stratified-manifest-v9.json` | `521937e0b3288d50f51e77d162e18d6ef1515ec8b644034c71f2d2ea68522bc4` | 17832 bytes |
| `config/execution-policy.json` | `bd518ab8792604352b1986b3e293c9daf2fbcc5e9f455aaeb162bec1d6cf5e76` | 696 bytes |
| `tla2tools-1.8.0.jar` | `eabd140a70f49eb9305a3bd3f3df944eddf87e5a90d329789085f8953a80533a` | 4487757 bytes |
| Application JAR | `76114011b9d1d63f706879a4d7dbe68d6a8f991c5335963adc7075b38d60b36a` | 3015208 bytes |
| `docs/experiments/gcd-stratified-census-v9.json` | `cd714aecfec926c3250922ec52e9dc6caef944c1300ff2cafaf9911c2f7a66cb` | 24623 bytes |

The census was generated twice into distinct write-once paths. The files were byte-identical. All 15 entries completed and exposed 51 GCD sites. The derived SimpleAllocator entry exposed exactly the Request, Allocate, and Return sites identified in the design.

## Primary Hypothesis

The SimpleAllocator Request mutant at census ID `gcd-94424c8f20df1b20ffb7` will be killed by the full oracle, pass the type-only oracle, pass the legacy oracle while expanding reachable states, and therefore be classified as `adopted-target-invariant-kill`.

## Secondary Checks

- The derived SimpleAllocator baseline completes under `TypeInvariant`, `ResourceMutex`, and `NoRequestAllocationOverlap`.
- At least one SimpleAllocator mutant, expected to be Allocate, is classified as `type-invariant-kill`.
- The Return mutant is not killed and receives no attribution rerun.
- Attribution buckets conserve the generated and evaluated mutant denominators.
- Two complete experiment executions have semantically identical outcomes.

## Stopping and Interpretation Rules

Any baseline failure, identity mismatch, census mismatch, unresolved attribution, sandbox failure, timeout, or tool error is reported as such and is not converted into evidence for an invariant kill. The mutation experiment may start only after this preregistration and the census are committed. The conclusion must distinguish post-selection closed-loop confirmation from independent generalization.
