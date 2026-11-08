# Haze 1.6.9 lifecycle backport

Source: https://github.com/chrisbanes/haze/tree/1.6.9
Upstream commit: `b01a57b5c2c0c27efddbba888d13f1ef5f79de12`.
Copyright belongs to Christopher Banes and the Haze project contributors;
see LICENSE (Apache-2.0) and the original source headers.

Only commonMain Kotlin, androidMain Kotlin and Android resources are vendored.
`upstream-sha256.json` records their unmodified upstream hashes. The local
Gradle build keeps Kotlin 2.1.0, Poko 0.18.7, and the application's previously
resolved Compose 1.8.0 / coroutines 1.10.1 / activity 1.10.1 / tracing 1.3.0.

Local modification: `RenderScriptContext.kt` serializes buffer callbacks,
blur computation and destruction on the instance monitor. Destruction is
idempotent. This backports the lifetime protection without migrating Haze 2 APIs.
Do not also include the Maven Haze artifact: it contains the same classes.

The reproduced library defect has not yet been matched to the phone's SIGABRT.
See `docs/plans/2026-11-08-native-race-fix.md` in the application repository.
