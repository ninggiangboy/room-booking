# CI/CD

Four GitHub Actions workflows plus one config file under `.github/`, monorepo-aware through
`paths:` filters so a change to one service never rebuilds or redeploys another. All four workflows
pass `actionlint` with no findings (verified before this document was written, not assumed).

## `ci.yml` — every pull request and every push

Four stages, all required: **Build**, **Unit Test**, **Code Quality**, **Security Scan**. The first
three run in one job (`build-test-quality`) since they share the same Gradle build; Security Scan
runs as an independent job so a secret-scan or dependency-scan failure is never confused with a
compile or test failure in the same log.

### Build, Unit Test, Code Quality (`build-test-quality` job)

Runs on `paths: ['room-booking-backend/**']` (extended with each new service's path as it joins).
Executes the commands `room-booking-backend/docs/conventions/07-workflow-and-commits.md` documents
as the definition of done, plus the two code-quality tasks:

```bash
git diff --check      # pull requests only -- needs the PR's base commit
./gradlew build        # Build -- also runs test, spotbugsMain, pmdMain, etc. via `check`
./gradlew test          # Unit Test -- named separately so a failure reads as a test failure,
                         # not a generic build failure, even though `build` above already ran it
./gradlew spotbugsMain pmdMain   # Code Quality -- ditto, named separately for the same reason
./gradlew javadoc
```

This is deliberate: CI enforcing the same commands the conventions already require means there is
one definition of done, read by both a person before opening a pull request and by the machine that
gates merging it — not two definitions that can drift apart. The Gradle home is cached between runs
to keep them short.

**Code Quality** is SpotBugs (bytecode-level bug detection: null derefs, resource leaks,
concurrency bugs) plus its `find-sec-bugs` plugin (security-focused rules: injection, weak crypto,
hardcoded secrets) and PMD (source-level smells: excess complexity, empty catch blocks, generic
exception catching), configured in `build.gradle` and `config/pmd/ruleset.xml`. Findings do not fail
the build yet (`ignoreFailures = true` on both, in `build.gradle`) — they're a baseline being
surfaced, not a merge gate, until the team decides the baseline is clean enough to tighten. First run
against this codebase found 28 SpotBugs findings and 23 PMD findings after tuning the PMD ruleset
down from an initial 112 (excluding rules that fought established conventions rather than finding
real bugs — see the ruleset file's comments for exactly which, and why). Reports upload as build
artifacts every run (`quality-and-test-reports`, 14-day retention) and, best-effort, to GitHub code
scanning via SARIF — that upload is allowed to fail silently (`continue-on-error: true`) since code
scanning needs GitHub Advanced Security on a private repository, which isn't assumed to exist.

### Security Scan (`security` job)

Independent of the build — doesn't need Java, runs in parallel:

1. **Secret scan** (`gitleaks/gitleaks-action`) — scans the diff (pull requests) or the pushed
   commits for accidentally committed credentials.
2. **Dependency vulnerability scan** (`aquasecurity/trivy-action`, filesystem mode against
   `room-booking-backend/gradle.lockfile`) — **fails the job** on HIGH/CRITICAL findings, unlike
   Code Quality above; a known-vulnerable dependency is a hard gate from day one, not a baseline to
   tighten into later.

This step depends on `build.gradle`'s `dependencyLocking { lockAllConfigurations() }` and the
committed `gradle.lockfile` — confirmed empirically that Trivy's filesystem scanner cannot resolve a
plain `build.gradle`'s transitive dependency graph without one (it reports "Number of
language-specific files num=0" and scans nothing, which would silently look like a clean scan
instead of a scan that never ran). Regenerate the lockfile after any dependency change:
`./gradlew dependencies --write-locks`, and commit the result.

This exact setup already found a real issue: Spring Boot 4.1.1's own BOM pinned an embedded Tomcat
version (11.0.24) vulnerable to three CRITICAL CVEs (a security-constraint bypass, a DIGEST-auth
replay bypass, and a FORM-auth resource-access bypass). Fixed with an explicit
`dependencyManagement.dependencies.dependencySet` override to 11.0.25 in `build.gradle` — see that
file's comment for the CVE IDs and the note to revisit once Boot's own BOM catches up.

A pull request cannot merge while `ci.yml` is failing.

## `release.yml` — on merge to `main`

1. `./gradlew bootBuildImage --imageName=ghcr.io/<org>/room-booking-backend:<sha>`. Verified working
   (`docs/observability.md`'s Phase 0 spike): without `--imageName`, Buildpacks names the image from
   the Gradle project name and `version` in `build.gradle`
   (`docker.io/library/backend:0.0.1-SNAPSHOT`) — CI must pass `--imageName` explicitly rather than
   relying on that default, both for the registry path and because the release identity is the
   commit SHA (`docs/release-and-rollback.md`), not the static `version` string.
2. **Security Scan, container stage**: Trivy scans the built image itself (the base image's OS
   packages, not just the JVM dependency graph `ci.yml` already covers) and **fails the workflow**
   on HIGH/CRITICAL, before the image is pushed anywhere — verified locally against a real built
   image (clean, 0 findings, confirming the fixed Tomcat version above actually made it into the
   shipped artifact).
3. Push to GHCR, authenticated with the automatic `GITHUB_TOKEN` — no separate registry account
   needed.
4. Deploy that image to `dev` automatically: SSH to the `dev` host (`appleboy/ssh-action`), update
   `IMAGE_TAG` in `room-booking-infra/deploy/.env`, `docker compose -f compose.deploy.yaml up -d`.
5. Run smoke tests against the deployed `dev` instance over its real public hostname: a health check
   and a real register/login round trip. A failing smoke test fails the workflow — this is what
   makes `dev` an integration signal instead of a place a broken merge quietly sits.

## `promote.yml` — manually dispatched with a commit SHA

1. Confirms the image actually exists in GHCR for that SHA (`docker manifest inspect`) before doing
   anything else — a typo'd or never-built SHA fails immediately with a clear error instead of
   partway through a deploy.
2. Deploys to `staging` the same way `release.yml` deploys to `dev`, then smoke-tests it.
3. Deploys to `production` the same way, but only after the `production` GitHub Environment's
   required reviewer approves the run — a free, fully audited manual gate, no separate approval
   tooling needed.

Nothing is ever rebuilt in this workflow, and nothing is re-scanned — the image was already scanned
once in `release.yml` before it reached GHCR, and rule 4 in `docs/conventions.md` says one artifact
is promoted, not rebuilt (or re-validated from scratch) per environment.

## `dependabot.yml` — the ongoing half of Security Scan

`ci.yml`'s Trivy step catches what's already vulnerable in a given commit; Dependabot
(`.github/dependabot.yml`) catches what's about to fall behind, opening a pull request weekly for
outdated Gradle dependencies and GitHub Actions versions. Native to GitHub, free on every plan, no
separate service to run.

## What each environment's deploy step actually needs (not yet provisioned)

Both `release.yml` and `promote.yml` SSH into a host and run a fixed script; they carry no
environment-specific logic beyond which secrets they read. Until the following exist, the deploy
jobs fail (loudly, by design — a missing secret should never look like a silent skip):

- A GitHub Environment named `dev`, `staging`, and `production` (Settings → Environments), each
  with secrets `*_SSH_HOST`, `*_SSH_USER`, `*_SSH_KEY` (an SSH key authorized on that host, scoped to
  only what the deploy script needs) and a repository variable `*_APP_HOSTNAME` for the smoke test.
  `production`'s Environment additionally needs a required reviewer configured.
- Each host provisioned per `docs/vps-setup-guide.md`, with `/opt/room-booking-infra/deploy/`
  containing `compose.deploy.yaml`, `caddy/Caddyfile`, and a filled-in `.env` (from
  `deploy/.env.example`) — the deploy script only ever edits `IMAGE_TAG` in that `.env`, nothing
  else.
- The central observability host (`docs/observability.md`) reachable, since every environment's
  `.env` points `OTLP_ENDPOINT` / `LOKI_PUSH_URL` at it.

## What this pipeline does not do (yet)

No image build or deploy runs on a pull request — only `ci.yml`'s checks. No per-pull-request
preview environment exists (see "what is explicitly not covered yet" in
`docs/platform-architecture.md`). Code Quality findings don't fail the build yet (see above) — that's
a deliberate first step, not the end state. No DAST (dynamic scanning against a running instance) —
only SAST-style static analysis and dependency/image scanning exist today. No frontend or other
service has a workflow yet; when one joins, it gets its own `paths:`-filtered job in each workflow,
following this same shape rather than a bespoke one.
