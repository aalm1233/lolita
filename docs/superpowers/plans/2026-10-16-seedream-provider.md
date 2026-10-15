# Seedream provider implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement task-by-task. Steps use checkbox syntax.

**Goal:** Add the user-selected official Seedream 5.0 Pro API to both existing generation flows, preserving GPT and confirmation before attaching mannequin images.
**Architecture:** An immutable provider/key snapshot travels through each existing generator. A shared API router sends exactly one request to the chosen official endpoint. Existing Keystore settings keep separate credentials and notify both screens of configuration changes.
**Tech Stack:** Kotlin, Compose Material3, Android Keystore, HttpURLConnection, Gson, existing Room27/backup7.
**Spec:** Existing approved virtual try-on and mannequin design; user selected Seedream and supplied official curl on 2026-10-16. Scope and decisions are fixed below.

## Global Constraints
- Official model doubao-seedream-5-0-pro-260628, POST https://ark.cn-beijing.volces.com/api/v3/images/generations.
- Ordered JPEG data URLs, prompt, size 1024x1536, output_format png, response_format b64_json, stream false, watermark true; 2–9 images; existing 10MiB/reference and 40MiB total limits.
- No automatic paid retry, redirects or remote-URL result fetching. Bounded response and existing full PNG validation.
- Keep existing OpenAI endpoint and model. Default new installs to Seedream; retain OpenAI for installations with the legacy credential. Selection is local and both providers have independent encrypted keys; no secret refill, logs, source or backup.
- Provider and key are captured atomically once per request; result history records the model that actually ran. Provider switching/configuration is disabled during generation and pending mannequin preview.
- Reuse existing seven-skin components; Chinese text must name the actual recipient. No database/backup schema changes. Preserve originals and append only after confirmation.
- User-supplied credential is temporary test input outside Git. One paid mannequin smoke test, no automatic retry; report inability honestly.
- Follow AGENTS default commit/push/full signed dual-platform release (2.50.0/108), independent AVD only.

## Review Focus
- Switching provider after key read cannot mix credentials, endpoint or persisted model.
- Legacy OpenAI credential survives changing service and clearing the other service key.
- Two retained screen ViewModels observe the same latest service configuration.
- Seedream success HTTP with error/empty/URL-only/malformed data must fail without a second call or saved image.
- Configuration changes while a preview/generation is active must not change the recipient disclosed for that request.

### Task 1: Provider routing and encrypted settings
**Files:** domain/model/ImageGenerationProvider.kt; data/remote/SeedreamImageApi.kt; data/remote/ImageGenerationApi.kt; data/tryon/TryOnSettings.kt; both generators; di/AppModule.kt; matching JVM and Android infrastructure tests.
**Interfaces:** ImageGenerationCredentials(provider,key); TryOnSettings.readCredentials/readProvider/selectProvider and changes StateFlow; ImageGenerationApi.generate(credentials,images,prompt); existing generators consume immutable credentials.
- [x] Write failing routing/request/error/cancellation tests and a generator snapshot/model provenance test; add settings preservation test.
- [x] Run targeted JVM tests, expect missing interfaces before implementation.
- [x] Implement bounded official JSON adapter, routing, per-provider encryption and atomic snapshot; adapt generator fixtures.
- [x] Run testDebugUnitTest; expected all tests pass.
- [x] Commit task with validation evidence.

### Task 2: Shared provider configuration UI
**Files:** both ViewModels/screens; common/ApiKeyDialog.kt; common/ImageServiceSelector.kt; relevant Android tests.
**Interfaces:** both UiStates expose provider; ViewModels observe settings changes and switch provider with busy guards; dialog describes and saves only current service.
- [x] Add Android tests for provider switch, separate keys, masking, shared configuration, busy/preview guard.
- [x] Run new tests or compile to observe missing behavior.
- [x] Implement provider selection and correct recipient/credential text, preserving current test injection boundaries.
- [x] Run independent AVD targeted tests, assembleDebug and whole JVM suite; expected pass.
- [x] Commit task and update AGENTS/version/docs.

### Task 3: Live verification and release
**Files:** docs/seedream-provider-2026-10-16.md; docs/release-2.50.0.md; docs/release-2.50.0-verification.json; local artifacts outside Git.
- [x] Inspect the single live result and record model, timing, dimensions, hashes and limitations without secrets.
- [x] Fresh whole-branch code review; resolve important findings with failing regression first.
- [x] Integrate source, clean assembleRelease; verify original signature, version, non-debuggable APK and preserved upgrade data.
- [x] Publish same signed APK and SHA256SUMS on GitHub/Gitee; anonymous complete downloads, hashes and update endpoint must pass.
- [x] Clean temporary credentials, attach artifacts and report actual outcome.
