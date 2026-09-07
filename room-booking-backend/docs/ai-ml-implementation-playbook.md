# Self-hosted AI/ML implementation playbook

## Purpose

This playbook turns the target contracts in the
[data, experimentation, and machine-learning platform design](features/data-experimentation-and-ml-platform.md)
into a concrete implementation path for a backend engineer who is new to machine learning. It
selects a deliberately small self-hosted stack, assigns responsibilities, defines the boundary
between Spring Boot and Python, and gives an evidence-led order for building the four production
model families required by the target release.

This document is an implementation companion, not a replacement for the feature designs. The
feature documents remain authoritative for domain invariants, product behavior, model quality,
privacy, fairness, and release gates. Where this playbook and a feature document appear to differ,
follow the feature document and update this playbook.

The intended reader can operate a Java backend and PostgreSQL but is not expected to know model
training terminology. The glossary in the platform design defines feature, label, training set,
prediction, decision, shadow, canary, and other governed terms used below.

## Status and target

This is a target implementation plan. The repository does not currently contain the ML workspace,
durable analytical event path, MLflow deployment, model services, trained model artifacts,
prediction log, or release workflow described here. Future changes must not present a planned
component as implemented until its tests and completion gate pass.

The target release requires production ML for:

1. personalized discovery and learning-to-rank;
2. review aspect and sentiment intelligence;
3. bounded host pricing recommendations; and
4. fraud, trust, safety, and content moderation.

The order in this document is an implementation dependency order. It does not define smaller
product releases. The target release remains incomplete until all four families pass their
respective feature completion gates.

## Non-negotiable decisions

### All AI/ML execution is self-hosted

Room Booking must not call an externally hosted AI or ML API for labeling, training, evaluation,
embedding, moderation, or production inference. This prohibits runtime and offline use of services
such as hosted LLM, hosted embedding, hosted model-training, and hosted inference APIs.

Open-source packages and pretrained model weights may be admitted as supply-chain inputs when all of
the following are true:

- the license and intended commercial use have been reviewed and recorded;
- the exact source revision, files, sizes, and cryptographic checksums are recorded;
- packages and weights are scanned before admission;
- approved copies are mirrored into Room Booking-controlled package and object storage;
- training and runtime resolve only from those internal mirrors; and
- the system remains functional when public package and model hosts are unreachable.

Downloading an unpinned model from a public model hub at service startup is prohibited. Enabling a
library option that uploads telemetry, examples, traces, prompts, features, or model outputs is also
prohibited.

### CPU-only target

Target-release training jobs and inference services must have a supported CPU-only path. Model size,
input length, batch size, quantization, and request deadlines must be selected from measurements on
the production-equivalent CPU class. GPU acceleration may be evaluated later, but it is not a hidden
release dependency.

Large generative models are not part of the initial production path. Review intelligence and content
moderation use bounded classifiers or extractors with schema-validated outputs. A future generative
capability requires a separate approval, evaluation set, capacity plan, and update to this playbook.

### Models advise; domains decide

No model service owns availability, booking, price, tax, ledger, payment, payout, publication,
restriction, appeal, support remedy, or other authoritative state. A model returns a versioned
prediction. Spring Boot applies the owning domain's deterministic eligibility, policy, safety, and
fallback rules and persists the final decision separately.

Every production route therefore requires:

- a named deterministic or human-operable fallback;
- a total deadline that is shorter than the consuming journey's deadline;
- an independently operable kill switch;
- a stored model, feature, policy, prediction, and decision version trail;
- shadow and canary evidence before active use; and
- a tested rollback target.

## Minimal target architecture

```text
authoritative Spring Boot domains
        |
        | committed facts + validated observations
        v
transactional outbox -> scheduled export -> MinIO raw/conformed data
                                                |
                                                v
                                      room-booking-ml CLI jobs
                                   feature/label build, train, evaluate
                                                |
                              +-----------------+-----------------+
                              |                                   |
                              v                                   v
                    self-hosted MLflow                    MinIO artifacts
                    metadata in PostgreSQL               immutable/checksummed
                              |                                   |
                              +-----------------+-----------------+
                                                |
                                          approved versions
                                                |
             +------------------+---------------+---------------+------------------+
             |                  |                               |                  |
             v                  v                               v                  v
       discovery-ml         review-ml                       pricing-ml          trust-ml
       Python service       Python service                  Python service      Python service
             |                  |                               |                  |
             +------------------+---------------+---------------+------------------+
                                                |
                                      private inference contract
                                                |
                                                v
                         Spring Boot prediction record + deterministic decision
                                                |
                                                v
                                 outcome event and model evaluation
```

This topology deliberately uses one service per model family. A family service may load multiple
approved artifacts, such as demand and booking-pace models inside `pricing-ml`, without creating one
deployment per artifact.

### Component responsibilities

| Component | Owns | Must not own |
| --- | --- | --- |
| Spring Boot domains | Facts, policy, feature authorization, route selection, deadline, fallback, final decision, audit | Model training or arbitrary artifact execution |
| `room-booking-ml` | Reproducible dataset, training, evaluation, packaging, and batch-scoring commands | Model approval or production route mutation |
| MLflow | Internally hosted run metadata, parameters, metrics, model cards, and artifact lineage | Final domain authority or the only copy of approval evidence |
| MinIO | Immutable raw/conformed snapshots, training manifests, admitted base weights, and trained artifacts | Searchable lifecycle state or route selection |
| PostgreSQL | Event/registry metadata, model actions, release routes, predictions, decisions, quality, and audit | Large training matrices or unrestricted raw text blobs |
| Family model service | Validate the internal request, load an approved artifact, execute bounded inference, report status | Choosing business action, applying a fallback action, or writing domain state |

MLflow tracking and model-registry metadata use an internal PostgreSQL database, while MLflow
artifacts use an internal MinIO bucket. Spring Boot remains the authority for model approval actions
and active release routes. An MLflow tag or stage alone cannot activate a production model.
The launch deployment may share a PostgreSQL cluster, but MLflow uses a separate database and role
so training or registry work cannot acquire application-table privileges.

## Planned repository shape

Keep the Java application and ML lifecycle separate while preserving one workspace:

```text
room-booking/
  room-booking-backend/
    docs/
    src/
  room-booking-ml/                 # planned; does not exist yet
    pyproject.toml
    uv.lock
    src/room_booking_ml/
      common/                      # manifests, validation, storage, metrics
      discovery/                   # dataset, training, evaluation, service adapter
      review/
      pricing/
      trust/
    services/
      discovery/
      review/
      pricing/
      trust/
    tests/
    model_cards/
```

Use a single locked Python environment initially. Split environments only after conflicting native
dependencies or materially different deployment needs are demonstrated. Notebooks may explore data,
but every dataset and artifact admitted to the registry must be reproducible through a reviewed CLI
command under `src/room_booking_ml`; a notebook is never the production build definition.

Release builds install only from an internal Python package mirror or a checksummed wheelhouse. The
lock file, container base image digest, operating-system packages, and native-library versions are
part of the artifact provenance.

## Technology baseline

| Need | Initial choice | Reason and boundary |
| --- | --- | --- |
| Tabular ranking/risk/propensity | LightGBM | CPU-friendly, interpretable first choice; retain native model artifact initially |
| Forecasting | StatsForecast plus simple seasonal baselines | Rolling-origin evaluation; do not hide a weak model behind a complex pipeline |
| Text classification/extraction | Small multilingual transformer, fine-tuned locally | Candidate must pass Vietnamese, license, memory, latency, and evidence-span evaluation |
| Image classification | Small locally hosted classifier | Candidate must match the approved moderation taxonomy and abstain outside validated scope |
| Training/inference framework | Python with locked packages | Avoids cross-language export work while the service boundary already isolates Python |
| Service API | FastAPI with Pydantic schemas | One private, schema-first REST contract per family service |
| Experiment/model tracking | Self-hosted MLflow | Use internal PostgreSQL metadata and MinIO artifact storage |
| Data interchange | Versioned JSON for bounded requests; Parquet for datasets | Do not move bulk training data through synchronous REST |
| Scheduling | Reproducible CLI plus an internal scheduler | Add a workflow orchestrator only after measured retry, dependency, or scale needs |

Native LightGBM artifacts and framework-native transformer weights are the default because Python is
also the serving runtime. ONNX is an optimization option, not an initial platform requirement. Adopt
ONNX for an artifact only when a reproducible benchmark demonstrates a material latency, memory, or
portability benefit and prediction-parity tests pass.

Do not select a pretrained checkpoint because of a public leaderboard alone. The model-intake record
must cover license, provenance, architecture, parameter count, tokenizer/preprocessing, supported
languages, input bounds, known limitations, security scan, checksum, CPU benchmark, and an approved
evaluation result on Room Booking reference cases.

## Model-family implementation profiles

### Discovery service

`discovery-ml` scores an already eligible candidate set. Spring Boot performs destination,
publication, capacity, date, guest-count, price, policy, and other hard filtering before inference.

Initial learned artifact:

- LightGBM learning-to-rank model;
- target defined by the discovery feature design, with completed-stay and satisfaction guardrails;
- point-in-time guest, listing, trip, market, price, quality, and exposure-context features;
- temporal and entity-aware split, with position-bias analysis; and
- score plus bounded reason codes, never a bookability decision.

The deterministic normalized ranking policy is both the baseline and runtime fallback. A model must
beat or meaningfully complement that baseline under the approved offline and online decision rule.
Semantic embedding retrieval, two-tower retrieval, vector storage, and contextual bandits remain
measured-scale capabilities and are not prerequisites for the initial learning-to-rank artifact.

### Review-intelligence service

`review-ml` processes a specific immutable, published review revision asynchronously. It returns
zero or more schema-constrained mentions containing approved aspect, target, sentiment, evidence
span, and confidence. It does not rewrite, summarize as fact, publish, remove, or change the original
review.

Initial learned artifact:

- a small multilingual encoder fine-tuned locally for the approved aspect taxonomy;
- deterministic category-to-aspect evidence retained as baseline;
- Vietnamese as a mandatory evaluated launch slice;
- explicit negation, contrast, multiple-aspect, non-listing-target, and unsupported-language cases;
- confidence thresholds selected on an adjudicated evaluation set; and
- deterministic abstention when the language, taxonomy, confidence, input length, or service state is
  unsupported.

Labels come from approved internal review data, deterministic weak-labeling rules where appropriate,
and human adjudication. No review is sent to an external labeling or model API. A locally hosted base
model may propose labels, but proposed labels remain separate from adjudicated ground truth and must
carry their producing model and configuration version.

### Pricing service

`pricing-ml` produces forecasts and probabilities consumed by the deterministic pricing optimizer.
It never returns the authoritative quote or changes a host's calendar price.

Initial artifact set:

- market demand forecast using a seasonal baseline and an evaluated StatsForecast candidate;
- listing booking-pace forecast when each supported cohort has sufficient history;
- calibrated booking-propensity model using logistic regression or LightGBM;
- cancellation/risk estimate where it improves the approved pricing objective; and
- intervals, freshness, supported scope, and uncertainty with every output.

Spring Boot generates currency-valid price candidates, evaluates each candidate through the real
quote and settlement path, applies host floors/ceilings and volatility controls, and chooses the
bounded recommendation. Historical correlation is not sufficient evidence for price elasticity.
Elasticity enters production only after approved randomized price variation supplies causal evidence.
Until then, the optimizer uses demand/pace/propensity evidence and deterministic market-aware rules.

### Trust and moderation service

`trust-ml` contains separate artifacts under one family service for calibrated risk and bounded
content classification. A score is evidence for the trust policy; it is never an accusation or a
restriction command.

Initial artifact set:

- LightGBM risk models for explicitly scoped account, booking, payment, payout, promotion, or
  review-abuse targets only when mature adjudicated labels exist;
- a small multilingual text classifier for approved spam, phishing, harassment, contact-sharing, or
  other policy categories;
- a small image classifier for the approved launch moderation taxonomy; and
- deterministic rules, secure attachment scanning, quarantine, and human queues as the safety floor.

Each risk target has its own horizon, calibration, cost matrix, threshold review, feature allowlist,
and false-positive/appeal analysis. Sharing a service or feature library does not create a universal
actor risk score. Unknown, unavailable, low-confidence, and unsupported content remain distinct from
safe content and route according to deterministic policy.

## Data and training flow

### Source evidence

Only authoritative domain facts and validated observations enter training. The first complete
journey covers search, impression, click, quote, booking, payment, confirmation, stay completion,
cancellation/refund where applicable, and review. Producers commit a durable outbox record in the
same transaction as the source fact. At-least-once export and consumption converge through stable
event IDs and inbox deduplication.

Never backfill impressions, experiment exposure, or user intent that was not actually observed.
Historical source-table exports use synthetic event IDs with explicit `BACKFILL` provenance and do
not pretend to be original event emissions.

### Storage layers

At launch scale, scheduled jobs may export immutable partitions from PostgreSQL to MinIO without a
broker. Keep three logical layers even when they share one object store:

- `raw`: accepted event envelopes and source snapshots, append-only;
- `conformed`: validated, deduplicated, privacy-applied domain tables with historical dimensions; and
- `semantic`: versioned metrics, feature inputs, mature labels, and frozen training sets.

Every published partition includes schema version, row count, checksum, time range, source watermark,
quality status, pipeline run ID, and deletion watermark. A failed or partial build never replaces the
current qualified partition.

### Point-in-time training set

For each example, choose a `predictionAt` and join only feature values whose source was available at
or before that instant. Outcomes observed later may become labels only according to the label's
declared horizon and maturity rule. Use temporal splits and entity grouping; do not randomly place
the same booking, listing, host, guest, or near-duplicate content across train and test partitions.

Each training run writes an immutable manifest containing:

- dataset and model-family identity;
- source snapshots, watermarks, privacy query, and deletion watermark;
- population, sampling, split, target, horizon, and label version;
- feature definitions, as-of rules, missing-value semantics, and code/config hashes;
- dependency lock, container digest, random seed, and admitted base-model checksum;
- row counts and relevant market/language/entity distributions;
- leakage, quality, and known-limitation results; and
- final dataset location and checksum.

### Labeling without external services

Use the least complex valid source in this order:

1. authoritative outcomes, such as completed stay, cancellation, chargeback, or upheld appeal;
2. deterministic derivation from versioned business facts;
3. human adjudication under a versioned rubric;
4. weak labels proposed by rules or a locally hosted model, stored with provenance; and
5. active-learning sampling to send uncertain or representative cases to human reviewers.

Weak or model-proposed labels cannot be silently promoted to ground truth. Keep an independently
human-adjudicated test set that no label-proposal model trained on. Sensitive support, message,
payment, identity, or safety content is never repurposed as general training data.

## Registry, approval, and artifact promotion

MLflow records training runs and artifact lineage, but Room Booking's governed records authorize
production use. A trained model progresses through:

```text
DRAFT -> TRAINED -> VALIDATED -> APPROVED -> SHADOW -> CANARY -> ACTIVE
                    |              |           |         |
                    +-> REJECTED   +-> RETIRED +-> ROLLED_BACK
```

The registered version includes the training manifest, model card, feature/label versions, artifact
URI and checksum, evaluation results, CPU resource measurements, supported scope, prohibited uses,
fallback, monitoring thresholds, owner, approvers, and rollback target.

Promotion follows this order:

1. the training CLI uploads an immutable artifact and manifest to internal MinIO;
2. MLflow records the run and links the immutable artifact;
3. validation jobs reproduce required metrics and compare the deterministic baseline;
4. authorized reviewers record approval in the Room Booking model-action audit;
5. an explicit release route names the exact checksum, consumer, market, and traffic share;
6. the family service downloads from internal MinIO during deployment or controlled reload and
   verifies the checksum before becoming ready; and
7. Spring Boot begins shadow or canary routing only after readiness and route-version checks pass.

Changing weights, tokenizer, preprocessing, taxonomy, feature order, thresholds, or output mapping
creates a new version. Do not overwrite an artifact or mutate historical run evidence.

## Inference contracts

### Public and internal boundaries

Clients use the Spring Boot product APIs. They never call a Python service and never choose a model,
version, feature set, threshold, or experiment variant.

The governed Spring endpoint remains the contract described by the platform design:

```text
POST /api/v1/internal/predictions
GET  /api/v1/internal/predictions/{predictionId}
```

Spring Boot resolves the registered route and authorized feature set, persists idempotent prediction
evidence, applies the deadline and fallback, and calls exactly one private family service. Each
family service exposes the same network-isolated runtime shape:

```text
GET  /health/live
GET  /health/ready
POST /internal/v1/predictions
POST /internal/v1/predictions:batch
```

The batch endpoint is bounded and intended for asynchronous enrichment, not bulk training data.
Large batch inference uses a manifest-based CLI job over MinIO partitions.

### Family-service request

The caller supplies a server-selected artifact and typed input; it cannot supply a filesystem path
or public URL:

```json
{
  "requestId": "72c138cf-2b6c-46a1-bf5a-9dc06033ccb5",
  "idempotencyKey": "ranking:search-context-id:route-epoch-7",
  "consumer": "DISCOVERY_RANKING",
  "market": "VN",
  "predictionAt": "2026-09-07T10:00:00Z",
  "deadlineAt": "2026-09-07T10:00:00.080Z",
  "modelKey": "discovery-ltr",
  "modelVersion": "discovery-ltr-2026-09-07.1",
  "artifactChecksum": "sha256:opaque-approved-checksum",
  "featureSetVersion": "discovery-ranking-v1",
  "inputSchemaVersion": "discovery-ranking-input-v1",
  "featureAsOf": "2026-09-07T09:59:00Z",
  "contextId": "opaque-server-context-id",
  "inputs": {
    "candidates": []
  }
}
```

`inputs` is a bounded family-specific schema. Essential features are typed fields in the versioned
schema, not arbitrary client-supplied names. The service rejects a model/schema/checksum combination
that was not loaded together.

### Family-service response

```json
{
  "requestId": "72c138cf-2b6c-46a1-bf5a-9dc06033ccb5",
  "modelKey": "discovery-ltr",
  "modelVersion": "discovery-ltr-2026-09-07.1",
  "artifactChecksum": "sha256:opaque-approved-checksum",
  "featureSetVersion": "discovery-ranking-v1",
  "predictedAt": "2026-09-07T10:00:00.021Z",
  "outputs": {
    "candidateScores": []
  },
  "uncertainty": {
    "status": "WITHIN_VALIDATED_RANGE"
  },
  "reasonCodes": [],
  "status": "PREDICTED",
  "fallbackReason": null,
  "durationMillis": 17
}
```

Expected non-prediction states include `FEATURE_MISSING`, `FEATURE_STALE`, `MODEL_UNAVAILABLE`,
`MODEL_NOT_LOADED`, `MODEL_OUT_OF_SCOPE`, `UNSUPPORTED_INPUT`, `LOW_CONFIDENCE`, and `TIMED_OUT`.
The service reports the state; Spring Boot selects and records the owning domain's fallback action.
An expected abstention is not rewritten as a successful prediction.

Each family replaces `inputs` and `outputs` with an explicitly versioned schema:

| Family | Minimum input grain | Minimum output shape |
| --- | --- | --- |
| Discovery | One search context plus an ordered eligible-candidate list and point-in-time features | Candidate ID, score, uncertainty state, and approved reason codes for every scored candidate |
| Review | One immutable review-revision ID/digest, locale, bounded text, and taxonomy version | Zero or more aspect, target, sentiment, start/end evidence offsets, and confidence records |
| Pricing | One market/listing/stay-date context, forecast horizon, as-of time, and typed feature vector | Named forecast/probability, point estimate, interval, valid period, and uncertainty state |
| Trust | One declared risk target or immutable content revision plus target-specific typed features | Target/horizon score or category probabilities, confidence, uncertainty, and internal reason codes |

Do not reuse one untyped `score` field across families. Probabilities are bounded to `[0, 1]`,
forecasts carry their units, evidence offsets refer to the exact submitted revision, and candidate
IDs must match the eligible set supplied by Spring Boot.

### Transport and authentication

Production traffic uses TLS on the private service network. Spring Boot sends a short-lived,
asymmetrically signed workload token whose issuer, subject, exact service audience, permitted
`ml:predict` scope, issued time, and expiry are validated by the family service. User access tokens
are not accepted by model services. Signing and verification keys come from the internal secret
mechanism and support overlap during rotation.

The service returns `200` for a prediction or a typed expected abstention, `400` for an invalid input
schema, `409` for an unloaded or incompatible model/schema/checksum combination, `413` for a bounded
batch violation, `503` while no approved artifact is ready, and `504` when its own deadline is
exhausted. Spring Boot translates every non-prediction path to the registered fallback and persists
the stable internal reason without exposing model internals to a public client.

### Idempotency and deadlines

Spring Boot owns the canonical idempotency record. Repeating an accepted request returns the stored
prediction identity even if the active route has changed. A caller requesting a genuinely new
prediction uses a new canonical context or route epoch.

The family service checks `deadlineAt` before preprocessing and inference. It does not start work
that cannot complete safely within the remaining budget, does not perform unbounded synchronous
retries, and does not call another uncontrolled network dependency. Spring Boot times out slightly
after the declared internal deadline so it can record a stable fallback reason.

## Reproducible command flow

The exact CLI is implemented with the ML workspace. These command names are the target operator
contract; replacing the implementation requires preserving equivalent behavior and manifests.

```text
uv sync --frozen --no-index --find-links /opt/room-booking/wheelhouse

uv run rbml data build \
  --family review \
  --dataset review-aspect-v1 \
  --as-of 2026-09-07T00:00:00Z

uv run rbml train \
  --family review \
  --dataset-manifest s3://ml-datasets/review-aspect-v1/manifest.json

uv run rbml evaluate \
  --model-run <internal-mlflow-run-id> \
  --evaluation-set review-aspect-v1-vietnamese

uv run rbml package --model-run <internal-mlflow-run-id>
uv run rbml validate-artifact --manifest <internal-minio-manifest-uri>
uv run rbml batch-predict --manifest <approved-batch-manifest-uri>
```

Commands fail closed if a source snapshot, schema, license record, checksum, deletion watermark,
evaluation set, or approved internal storage destination is missing. Secrets come from the runtime
secret mechanism and never appear in command history, manifests, source control, or model metadata.

Scheduled retraining invokes the same commands with immutable inputs. It may produce a `TRAINED`
candidate but cannot approve, promote, or alter production traffic automatically.

## Implementation order

### 0. Governance and reference contracts

Approve owners, data classes, retention/deletion rules, model-risk tiers, feature and label templates,
model card, license intake, approval roles, event envelope, prediction contract, fallback catalog,
initial SLOs, and golden reference cases. Record unresolved consequential choices as ADRs.

Exit when the source-of-truth matrix, self-hosted boundary, prohibited model authority, first four
family targets, and deterministic baselines have accountable approval.

### 1. Durable evidence and deterministic baselines

Implement the outbox/inbox foundation and instrument a complete marketplace journey. Establish
versioned raw/conformed exports, reconciliation, privacy/deletion propagation, and data-quality
blocking. Implement the non-ML ranking, review/category, pricing, and trust/moderation paths first.

Exit when a backlog or analytical outage cannot break the transactional journey and every future ML
consumer has a tested fallback.

### 2. ML foundation and service skeletons

Create `room-booking-ml`, locked offline builds, internal package/model mirrors, MinIO bucket policy,
self-hosted MLflow, dataset manifests, model cards, Spring prediction records, release routes, and
the four services with health/readiness and contract tests. Deploy a deterministic fixture model in
non-production to prove checksum loading, schema validation, deadline, logging, and kill switch.

Exit when one fixture prediction is traceable from source snapshot through artifact and decision,
and all four services fail safely when the artifact store or service is unavailable.

### 3. Review intelligence

Approve taxonomy and an adjudicated multilingual evaluation set, then build the local extraction
dataset and model. Run offline, shadow, and replay evaluation before a high-confidence host-insight
canary. Enable public or ranking influence only through the review feature's evidence thresholds and
correction path.

### 4. Discovery ranking

Build point-in-time search examples, validate exposure/position-bias handling, train LightGBM ranker,
and compare it with deterministic normalized ranking. Shadow full candidate sets, canary inside an
already eligible set, then ramp by approved experiment gates while monitoring completed-stay,
satisfaction, concentration, cancellation, fairness, and latency guardrails.

### 5. Trust and moderation

Start content classifiers and scoped risk candidates only after policy categories, adjudicated
labels, appeals, reviewer capacity, and deterministic protections exist. Calibrate per target and
traffic slice. Move from shadow to reviewer assistance before any bounded automation. Drill false-
positive restoration and the all-models-disabled safety floor.

### 6. Predictive pricing

Build rolling-origin demand and pace datasets, then propensity, cancellation, and risk baselines.
Return predictions to the real deterministic candidate-price optimizer in shadow. Canary bounded
host recommendations only after quote, settlement, host-control, volatility, fairness, and rollback
tests pass. Add elasticity only after a separately approved causal experiment.

### 7. Target-release acceptance

Rehearse simultaneous outage, fallback, kill switch, route rollback, artifact corruption, delayed
labels, deletion, drift, and restore/replay. The release gate requires every family to retain its
deterministic or human path and to pass the metrics and completion criteria in its authoritative
feature document.

## Observability and operations

Each family dashboard separates model behavior from final domain action and includes:

- request rate, success, abstention, timeout, error, and fallback reason;
- total and inference-only latency by model version and input-size band;
- CPU, memory, worker saturation, queue age, artifact load, and readiness;
- feature missingness, staleness, schema mismatch, and distribution drift;
- score/output distribution, calibration, mature-outcome quality, and approved slices;
- deterministic-baseline comparison and model-versus-policy override rate;
- canary traffic, kill-switch state, active route, rollback target, and route changes; and
- dataset, artifact, license, privacy, retention, and deletion-propagation failures.

Page only on conditions that require immediate action, such as unsafe routing, invalid artifact,
fallback failure, severe quality breach, or target SLO exhaustion. Slow drift, retraining need, and
evaluation-set maintenance create owned tickets unless a pre-approved threshold requires automatic
pause. Automatic retraining never means automatic promotion.

Runbooks cover at least model-service outage, MLflow outage, MinIO outage, checksum failure, corrupt
or incompatible tokenizer, schema mismatch, latency saturation, prediction spike, adverse-action
spike, false-positive incident, privacy deletion failure, rollback, and full rebuild from manifests.

## Security, privacy, and supply chain

- Place model services on a private network; authenticate Spring-to-service traffic and deny public
  ingress.
- Deny general Internet egress from release workloads. Permit only explicitly required internal
  PostgreSQL, MinIO, MLflow, monitoring, and secret-service destinations.
- Treat review text, messages, image metadata, and other user content as untrusted input. Bound size,
  validate types, scan files, and test parser and model denial-of-service cases.
- Never place credentials, access tokens, exact private addresses, unrestricted message content, or
  raw payment/identity data in general telemetry, MLflow parameters, model cards, or feature dumps.
- Separate artifact intake from runtime. Intake verifies source, license, checksums, malware scan,
  unsafe serialization formats, and dependency vulnerabilities before mirroring.
- Prefer `safetensors` or documented non-executable formats. Loading a pickle-like artifact from an
  untrusted or mutable location is prohibited.
- Sign or otherwise integrity-protect release manifests and audit every approval, promotion, pause,
  rollback, replay, export, and deletion action.
- Propagate opt-out, correction, and deletion through datasets, features, predictions, caches, and
  future model builds according to the approved retention and legal-hold policy.

## Testing and acceptance

### Shared contract tests

- Every service validates the common envelope and its exact family input/output schema.
- Unknown fields follow the declared compatibility rule; missing essential fields fail safely.
- Artifact checksum, model version, feature set, tokenizer/preprocessor, and schema cannot be mixed.
- Duplicate Spring requests return the original stored prediction identity.
- Deadline exhaustion, cancellation, worker restart, and network interruption produce a recorded
  fallback without an unbounded retry.
- A client cannot choose an artifact, feature value, model route, experiment variant, or threshold.

### Data and model tests

- Golden point-in-time cases prove that future facts cannot enter a feature row.
- Temporal/entity splits prevent booking, listing, host, guest, and duplicate-content leakage.
- Training and evaluation reproduce from a clean environment using only internal mirrors and
  immutable manifests.
- Candidate models are compared with deterministic and simple statistical baselines.
- Vietnamese and every enabled market/language slice pass declared quality, fairness, robustness,
  calibration, latency, memory, and cost gates.
- Malformed, missing, stale, extreme, adversarial, and out-of-scope inputs abstain or fail safely.
- Framework-native and optimized artifacts, when both exist, pass numerical prediction-parity tests.

### Operational acceptance scenarios

1. Disable a family service and prove the consuming journey uses the named fallback.
2. Disable all four services and prove booking, payment, review truth, pricing authority, moderation
   intake, appeals, and urgent safety paths remain correct.
3. Present a wrong checksum or incompatible schema and prove readiness fails before traffic arrives.
4. Expire a feature snapshot and prove no stale prediction is silently treated as current.
5. Roll an active route back while in-flight requests complete and prove prediction history remains
   attributable to the version actually used.
6. Remove or correct source content and prove derived data is invalidated or rebuilt within policy.
7. Restore PostgreSQL and MinIO from backup, replay outbox/inbox data, and prove no duplicate domain
   side effect occurs.
8. Block all Internet access and run build verification from internal mirrors plus production
   inference for every family.

## Explicitly deferred capabilities

Do not add these merely to make the system appear more mature:

- externally hosted AI/ML APIs;
- generative LLM serving;
- Kafka or another broker before scheduled export fails measured requirements;
- Airflow, Prefect, or another orchestrator before CLI scheduling becomes unmanageable;
- a dedicated online feature store before PostgreSQL/materialized projections miss freshness or
  throughput needs;
- vector databases, embedding retrieval, or two-tower retrieval before candidate-scale evidence;
- Kubernetes or one deployment per model artifact;
- automatic promotion or self-modifying model routes; and
- price elasticity, uplift, contextual bandits, or graph ML without the required causal, scale, and
  governance evidence.

Each deferred capability needs an ADR containing the measured trigger, alternatives, ownership,
operating cost, migration path, fallback, and removal or rollback plan.
