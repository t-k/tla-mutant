# Census Classpath Diagnostic Security Review

- Date: 2026-09-01
- Reviewed commit: `84d4236`
- Decision: GO
- Scope: direct census classpath availability guard, packaged-process regression coverage, trusted-host guidance, TLA+ Tools provenance-lock alignment, reproducible application identity, and full-property regression impact.

## Must Fix

None.

## Should Fix

None.

The initial review requested two hardening changes. Both are resolved:

- `TlaToolsClasspathTest` reads `config/tla-tools.lock.json` and requires the diagnostic JAR filename to contain the authoritative pinned version.
- README states that every classpath JAR executes with the current user's host privileges, rejects unverified JARs, and names `verifyTlaTools` and `verifyFrozenApplication` immediately before the direct census invocation.

## Notes

- `TlaToolsClasspath.requireForCensus` runs before derived-edit parsing, work-directory creation, and SANY. The packaged-process regression supplies malformed derived-edit JSON and confirms that the classpath diagnostic wins and no work directory is created.
- The failure text is constant except for the platform classpath separator. It includes no supplied path or environment value, is bounded by unit test, and omits the caught linkage cause.
- The three class probes are an availability heuristic, not an artifact-authenticity boundary. A classpath JAR already executes as trusted host code; verification remains an explicit prerequisite.
- The supported classpath orders the application before TLA+ Tools and is exercised against real SANY.
- Direct census is documented for trusted local input. Public or otherwise untrusted specifications remain behind the existing rootless Podman corpus boundary.
- The application lock, v11 validator-application identity, and frozen assessment digest agree. Two clean no-cache builds produced SHA-256 `80e20e15f832c38e8562c8080962c7b5a7d603d2de0e9acbc2576217a6f189d1`, size `3221295` bytes.
- No full-property production path changed. The initial complete gate passed 212 unit, 30 integration, and 5 sandbox tests with no failures, errors, or process residuals. After both Should Fix changes, the focused classpath test and `verifyFrozenApplication` passed; the final complete gate is run from the recorded reviewed tree before integration.

## Final Decision

GO. There are no open Must Fix or Should Fix findings.
