# Scala Workflow Engine

A workflow engine built from scratch in Scala 3 — supporting DAG-based execution, parallel processing, retry logic, conditional branching, crash recovery, JSON workflow definitions, and a REST API.

Built as a learning project to understand both workflow engine architecture and the Scala programming language.

```
         ┌──→ [Validate] ───────┐
[Fetch] ─┤                      ├──→ [Process] ──→ [Notify]
         └──→ [CheckInventory] ─┘
          (parallel)
```

---

## Table of Contents

- [Features](#features)
- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [Running the Demos](#running-the-demos)
- [Running the Server](#running-the-server)
- [API Reference](#api-reference)
- [Creating Custom Steps](#creating-custom-steps)
- [Defining Workflows in JSON](#defining-workflows-in-json)
- [Project Structure](#project-structure)
- [Phases Completed](#phases-completed)
- [Roadmap](#roadmap)
- [Scala Concepts Covered](#scala-concepts-covered)

---

## Features

### Core Engine
- **Sequential execution** — run steps in order, passing context between them
- **DAG execution** — define dependencies between steps, engine computes execution order via topological sort
- **True parallel execution** — independent steps run simultaneously on separate threads using Scala `Future`s
- **Type-safe context** — `TypedKey[T]` system prevents type mismatches at compile time

### Resilience
- **Retry with exponential backoff** — configurable per-step retry policy (max retries, initial delay, backoff multiplier)
- **Crash recovery** — checkpoints saved after each level; engine resumes from last checkpoint on restart
- **Cycle detection** — validates DAGs before execution to catch circular dependencies
- **Dependency validation** — catches missing/dangling dependency references before execution

### Flow Control
- **If/else branching** — `IfElseStep` routes execution based on a context predicate
- **Multi-way branching** — `BranchStep` routes to one of N paths based on a context function
- **Step composition** — `SequenceStep` groups multiple steps into one, enabling branches containing sub-workflows
- **Arbitrary nesting** — branches inside sequences inside branches, to any depth

### Operations
- **Structured logging** — every step produces a `StepLog` with timing, status, and thread info
- **Workflow logs** — complete execution summaries with duration breakdown and bottleneck identification
- **Pluggable loggers** — `ConsoleLogger`, `SilentLogger`, `BufferedLogger`, or implement your own
- **JSON workflow definitions** — define workflows in JSON files, no recompilation needed
- **Step registry** — map step type names to factory functions, extensible with one line

### API
- **REST API** — submit, run, monitor, and delete workflows over HTTP
- **JSON responses** — all endpoints return structured JSON
- **Error handling** — clear error messages for invalid JSON, unknown step types, missing workflows, cycles

---

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                        REST API (cask)                       │
│  POST /workflows/:id    POST /workflows/:id/run              │
│  GET  /workflows/:id/status    GET /workflows/:id/log        │
└────────────────────────────┬─────────────────────────────────┘
                             │
┌────────────────────────────▼─────────────────────────────────┐
│                     WorkflowManager                          │
│  Manages lifecycle: submit → validate → run → monitor        │
└────────────────────────────┬─────────────────────────────────┘
                             │
┌────────────────────────────▼─────────────────────────────────┐
│                   DurableDAGEngine                           │
│  1. Load checkpoint (if resuming)                            │
│  2. Compute execution levels (topological sort)              │
│  3. For each level:                                          │
│     a. Skip already-completed steps                          │
│     b. Run remaining steps (parallel if multiple)            │
│     c. Save checkpoint                                       │
│  4. Return result + structured log                           │
└──────┬──────────────────┬────────────────────┬───────────────┘
       │                  │                    │
┌──────▼──────┐  ┌────────▼────────┐   ┌───────▼───────┐
│  StateStore │  │   LogCollector  │   │    Retrier    │
│  (persist)  │  │   (observe)     │   │   (resilience)│
│             │  │                 │   │               │
│ FileStore   │  │ StepLog         │   │ RetryPolicy   │
│ MemoryStore │  │ WorkflowLog     │   │ RetryResult   │
└─────────────┘  └─────────────────┘   └───────────────┘

┌──────────────────────────────────────────────────────────────┐
│                     Workflow Definition                      │
│                                                              │
│  Workflow ──→ Map[String, StepNode]                          │
│  StepNode ──→ id + WorkflowStep + dependencies: Set[String]  │
│                                                              │
│  Loaded from: Scala code  OR  JSON files via WorkflowLoader  │
└──────────────────────────────────────────────────────────────┘
```

---

## Prerequisites

- **Java JDK 17+** — Scala runs on the JVM
- **IntelliJ IDEA** (Community or Ultimate) with the **Scala plugin**
  - Install plugin: Settings → Plugins → Marketplace → search "Scala" → Install
- **sbt** — installed automatically by IntelliJ when you open the project

Or if running from terminal:
- **sbt** — install from https://www.scala-sbt.org/download.html

---

## Getting Started

### Clone and Open

```bash
git clone <your-repo-url>
cd workflow-engine
```

**IntelliJ:** File → Open → select the `workflow-engine` folder → Open as Project. Wait for sbt sync to complete (watch the progress bar at the bottom).

**Terminal:**
```bash
sbt compile
```

### Verify It Works

**IntelliJ:** Open `src/main/scala/Main.scala`, click the green ▶ arrow next to `@main def run()`.

**Terminal:**
```bash
sbt "runMain run"
```

You should see workflow execution output with checkmarks (✓) for successful steps.

---

## Running the Demos

The main demo runner exercises all engine features:

```bash
sbt "runMain run"
```

This runs:
1. **Sequential engine** — basic step-by-step execution
2. **Conditional branching** — if/else based on order amount
3. **DAG workflow** — dependency graph with parallel levels
4. **Crash and resume** — simulated failure + checkpoint recovery
5. **In-memory state store** — fast testing without file I/O

---

## Running the Server

Start the REST API server:

**IntelliJ:** Open `src/main/scala/api/Main.scala`, click the green ▶ arrow next to `object WorkflowServerApp`.

**Terminal:**
```bash
sbt "runMain api.WorkflowServerApp"
```

The server starts on `http://localhost:8080`. Test it:

```bash
# Health check
curl.exe http://localhost:8080/

# Submit a workflow from file
curl.exe -X POST http://localhost:8080/workflows/order1 -d @workflows/order_processing.json

# Run it
curl.exe -X POST http://localhost:8080/workflows/order1/run

# Check status
curl.exe http://localhost:8080/workflows/order1/status

# Get execution log
curl.exe http://localhost:8080/workflows/order1/log

# List all workflows
curl.exe http://localhost:8080/workflows

# Delete a workflow
curl.exe -X DELETE http://localhost:8080/workflows/order1
```

### PowerShell (if curl.exe doesn't work)

```powershell
# Submit from file
$json = Get-Content -Raw workflows/order_processing.json
Invoke-RestMethod -Method Post -Uri http://localhost:8080/workflows/order1 -Body $json

# Run
Invoke-RestMethod -Method Post -Uri http://localhost:8080/workflows/order1/run

# Status
Invoke-RestMethod http://localhost:8080/workflows/order1/status

# Log
Invoke-RestMethod http://localhost:8080/workflows/order1/log

# List
Invoke-RestMethod http://localhost:8080/workflows

# Delete
Invoke-RestMethod -Method Delete -Uri http://localhost:8080/workflows/order1
```

---

## API Reference

| Method | Endpoint | Description | Request Body |
|--------|----------|-------------|-------------|
| `GET` | `/` | Health check, lists endpoints | — |
| `POST` | `/workflows/:id` | Submit a workflow definition | Workflow JSON |
| `POST` | `/workflows/:id/run` | Execute a submitted workflow | — |
| `GET` | `/workflows/:id/status` | Get workflow status and progress | — |
| `GET` | `/workflows/:id/log` | Get detailed execution log | — |
| `GET` | `/workflows` | List all submitted workflows | — |
| `DELETE` | `/workflows/:id` | Delete a workflow and its state | — |

### Response Examples

**POST /workflows/order1** (submit):
```json
{
  "id": "order1",
  "name": "OrderProcessing",
  "nodeCount": 5,
  "status": "submitted"
}
```

**POST /workflows/order1/run** (execute):
```json
{
  "id": "order1",
  "success": true,
  "completedSteps": ["fetch", "validate", "inventory", "process", "notify"],
  "durationMs": 520
}
```

**GET /workflows/order1/status**:
```json
{
  "id": "order1",
  "name": "OrderProcessing",
  "status": "completed",
  "completedSteps": ["fetch", "validate", "inventory", "process", "notify"],
  "totalSteps": 5,
  "progressPercent": 100
}
```

**GET /workflows/order1/log**:
```json
{
  "id": "order1",
  "workflowName": "OrderProcessing",
  "totalMs": 520,
  "succeeded": true,
  "steps": [
    {"stepId": "fetch", "stepName": "FetchOrder", "status": "success", "durationMs": 102, "threadName": ""},
    {"stepId": "validate", "stepName": "ValidateOrder", "status": "success", "durationMs": 151, "threadName": "ForkJoinPool-1-worker-1"},
    {"stepId": "inventory", "stepName": "CheckInventory", "status": "success", "durationMs": 201, "threadName": "ForkJoinPool-1-worker-2"},
    {"stepId": "process", "stepName": "ProcessOrder", "status": "success", "durationMs": 103, "threadName": ""},
    {"stepId": "notify", "stepName": "NotifyCustomer", "status": "success", "durationMs": 1, "threadName": ""}
  ]
}
```

**Error response:**
```json
{
  "error": "Unknown step type: 'DoesNotExist'. Available: CheckInventory, FetchData, FetchOrder, ..."
}
```

---

## Creating Custom Steps

### 1. Implement the WorkflowStep trait

```scala
package steps

import engine.{WorkflowContext, WorkflowStep, TypedKey}

// Define typed keys for your step's inputs and outputs
object MyKeys {
  val InputUrl  = TypedKey[String]("inputUrl")
  val OutputData = TypedKey[String]("outputData")
}

class MyCustomStep extends WorkflowStep {
  val name = "MyCustomStep"

  // Optional: add retry policy
  // override def retryPolicy: Option[RetryPolicy] =
  //   Some(RetryPolicy(maxRetries = 3, initialDelayMs = 200))

  def execute(ctx: WorkflowContext): Either[String, WorkflowContext] = {
    // Read from context
    ctx.get(MyKeys.InputUrl) match {
      case Some(url) =>
        // Do your work here
        val result = s"Processed: $url"
        // Write to context and return success
        Right(ctx.set(MyKeys.OutputData, result))
      case None =>
        // Return failure
        Left("InputUrl not found in context")
    }
  }
}
```

### 2. Register it in DefaultRegistry

```scala
// In config/DefaultRegistry.scala, add:
.register("MyCustomStep") { cfg =>
  new MyCustomStep()
}
```

### 3. Use it in a workflow

**In Scala code:**
```scala
val workflow = Workflow(
  name = "MyWorkflow",
  nodes = Map(
    "step1" -> StepNode("step1", new MyCustomStep()),
    "step2" -> StepNode("step2", new AnotherStep(), dependencies = Set("step1"))
  )
)
```

**In JSON:**
```json
{
  "name": "MyWorkflow",
  "nodes": [
    {"id": "step1", "stepType": "MyCustomStep", "config": {}},
    {"id": "step2", "stepType": "AnotherStep", "dependsOn": ["step1"], "config": {}}
  ]
}
```

---

## Defining Workflows in JSON

### Schema

```json
{
  "name": "WorkflowName",
  "nodes": [
    {
      "id": "unique_step_id",
      "stepType": "RegisteredStepTypeName",
      "dependsOn": ["other_step_id"],
      "config": {
        "key": "value"
      }
    }
  ]
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `name` | Yes | Human-readable workflow name |
| `nodes` | Yes | Array of step definitions |
| `nodes[].id` | Yes | Unique identifier for this step |
| `nodes[].stepType` | Yes | Must match a registered step type |
| `nodes[].dependsOn` | No | Array of step IDs that must complete first |
| `nodes[].config` | No | Key-value config passed to the step factory |

### Available Step Types

| Step Type | Config Keys | Description |
|-----------|-------------|-------------|
| `FetchData` | `url` | Simulates fetching data from a URL |
| `TransformData` | — | Transforms raw data (uppercase) |
| `SaveResult` | — | Simulates saving data |
| `FetchOrder` | — | Simulates fetching an order |
| `ValidateOrder` | — | Simulates order validation |
| `CheckInventory` | — | Simulates inventory check |
| `ProcessOrder` | — | Simulates order processing |
| `NotifyCustomer` | — | Simulates sending notification |
| `Timed` | `name`, `durationMs`, `outputKey`, `outputValue` | Sleeps for specified duration (for benchmarking) |

### Example: Parallel Pipeline

```json
{
  "name": "ParallelPipeline",
  "nodes": [
    {
      "id": "start",
      "stepType": "Timed",
      "config": {"name": "Initialize", "durationMs": "100", "outputKey": "init", "outputValue": "done"}
    },
    {
      "id": "branchA",
      "stepType": "Timed",
      "dependsOn": ["start"],
      "config": {"name": "BranchA", "durationMs": "300", "outputKey": "a", "outputValue": "done"}
    },
    {
      "id": "branchB",
      "stepType": "Timed",
      "dependsOn": ["start"],
      "config": {"name": "BranchB", "durationMs": "200", "outputKey": "b", "outputValue": "done"}
    },
    {
      "id": "merge",
      "stepType": "Timed",
      "dependsOn": ["branchA", "branchB"],
      "config": {"name": "Merge", "durationMs": "50", "outputKey": "result", "outputValue": "complete"}
    }
  ]
}
```

Execution plan: `start → [branchA | branchB] → merge`

`branchA` and `branchB` run in parallel. `merge` waits for both.

---

## Project Structure

```
workflow-engine/
├── build.sbt                              # Project config + dependencies
├── README.md                              # This file
├── checkpoints/                           # Workflow checkpoints (created at runtime)
├── workflows/                             # JSON workflow definitions
│   ├── order_processing.json
│   └── parallel_demo.json
├── project/
│   └── build.properties                   # sbt version
└── src/
    ├── main/scala/
    │   ├── Main.scala                     # Demo runner entry point
    │   │
    │   ├── engine/                        # Core engine
    │   │   ├── WorkflowStep.scala         # Step trait + WorkflowResult
    │   │   ├── WorkflowContext.scala       # Type-safe shared context
    │   │   ├── TypedKey.scala             # Generic typed keys for context
    │   │   ├── ContextKeys.scala          # Pre-defined context keys
    │   │   ├── Workflow.scala             # DAG definition + topological sort + validation
    │   │   ├── WorkflowEngine.scala       # Sequential engine (Phase 1)
    │   │   ├── DAGEngine.scala            # DAG engine, sequential levels (Phase 5)
    │   │   ├── ParallelDAGEngine.scala    # DAG engine, parallel levels (Phase 6)
    │   │   ├── LoggedParallelDAGEngine.scala  # + structured logging (Phase 7)
    │   │   ├── DurableDAGEngine.scala     # + crash recovery (Phase 9)
    │   │   ├── BranchStep.scala           # Multi-way conditional branching
    │   │   ├── IfElseStep.scala           # Binary conditional branching
    │   │   └── SequenceStep.scala         # Composite step (group of steps)
    │   │
    │   ├── steps/                         # Step implementations
    │   │   ├── FetchDataStep.scala        # Simulated data fetch
    │   │   ├── TransformDataStep.scala    # Data transformation
    │   │   ├── SaveResultStep.scala       # Simulated save
    │   │   ├── FlakyApiStep.scala         # Fails N times then succeeds (retry demo)
    │   │   ├── OrderSteps.scala           # Order processing steps (6 steps)
    │   │   ├── DAGDemoSteps.scala         # DAG demo steps + keys
    │   │   ├── ParallelDemoSteps.scala    # Configurable timed step
    │   │   ├── CrashingStep.scala         # Simulated crash (recovery demo)
    │   │   └── CrashDemoKeys.scala        # Keys for crash demo
    │   │
    │   ├── retry/                         # Retry logic
    │   │   ├── RetryPolicy.scala          # Config: max retries, delay, backoff
    │   │   ├── RetryResult.scala          # Enum: Success | Failure
    │   │   └── Retrier.scala             # Retry loop with exponential backoff
    │   │
    │   ├── logging/                       # Structured logging
    │   │   ├── StepLog.scala              # Per-step execution record
    │   │   ├── WorkflowLog.scala          # Complete workflow execution record
    │   │   ├── LogCollector.scala         # Thread-safe log accumulator
    │   │   └── Logger.scala               # Logger trait + Console/Silent/Buffered
    │   │
    │   ├── config/                        # JSON workflow loading
    │   │   ├── WorkflowConfig.scala       # JSON data models (derives ReadWriter)
    │   │   ├── StepRegistry.scala         # Step type → factory function mapping
    │   │   ├── WorkflowLoader.scala       # JSON string/file → Workflow
    │   │   └── DefaultRegistry.scala      # Pre-built registry with all step types
    │   │
    │   ├── persistence/                   # State persistence
    │   │   ├── WorkflowState.scala        # Checkpoint data model
    │   │   ├── ContextSerializer.scala    # WorkflowContext ↔ serializable map
    │   │   ├── StateStore.scala           # Storage trait
    │   │   ├── FileStateStore.scala       # JSON files on disk
    │   │   └── InMemoryStateStore.scala   # In-memory (for tests)
    │   │
    │   └── api/                           # REST API
    │       ├── Main.scala                 # Server entry point
    │       ├── ApiModels.scala            # Response case classes
    │       ├── WorkflowManager.scala      # Business logic layer
    │       └── WorkflowServer.scala       # HTTP routes (cask)
    │
    └── test/scala/                        # Tests (to be implemented)
```

---

## Phases Completed

| Phase | Feature | Key Scala Concepts |
|-------|---------|-------------------|
| 1 | Sequential engine | `case class`, `trait`, `Either`, `foldLeft`, pattern matching |
| 2 | Type-safe context | Generics (`TypedKey[T]`), `object` singletons |
| 3 | Retry logic | `enum`, `@tailrec`, higher-order functions |
| 4 | Conditional branching | Functions as values, composite pattern, `.getOrElse` |
| 5 | DAG execution | `var` vs `val`, Set operations, `require()`, graph algorithms |
| 6 | Parallel execution | `Future`, `ExecutionContext`, `given`/`using`, `Duration` |
| 7 | Structured logging | `java.time`, `synchronized`, trait polymorphism, format strings |
| 8 | JSON workflows | Library deps in sbt, `derives ReadWriter`, for-comprehensions over Either |
| 9 | Crash recovery | Custom serialization, `scala.jdk.CollectionConverters`, file I/O |
| 10 | REST API | HTTP routing, annotations, request/response, concurrent state |

---

## Roadmap

```
COMPLETED
══════════════════════════════════════════════════════════
Phase 1:  Sequential engine                           ✅
Phase 2:  Type-safe context                           ✅
Phase 3:  Retry with exponential backoff              ✅
Phase 4:  Conditional branching                       ✅
Phase 5:  DAG workflows + topological sort            ✅
Phase 6:  True parallel execution                     ✅
Phase 7:  Structured logging and timing               ✅
Phase 8:  JSON workflow definitions                   ✅
Phase 9:  Crash recovery with checkpointing           ✅
Phase 10: REST API                                    ✅


MILESTONE 2: HARDENING (make it reliable)
══════════════════════════════════════════════════════════
Build confidence that what you have actually works
correctly, handles edge cases, and fails gracefully.

Phase 11: Unit Tests
- Set up munit test structure in src/test/scala/
- Test WorkflowContext (set, get, type safety)
- Test Retrier (success, failure, max retries)
- Test Workflow validation (cycles, missing deps)
- Test topological sort (correct levels)
- Test ContextSerializer (round-trip serialization)
- Test BranchStep and IfElseStep
  ┌──────────────────────────────────────────────┐
  │ Scala concepts:                              │
  │   munit framework, assertions, test suites,  │
  │   src/test/scala/ structure, sbt test runner │
  └──────────────────────────────────────────────┘

Phase 12: Integration Tests
- Test full workflow execution end-to-end
- Test crash + resume with InMemoryStateStore
- Test parallel execution produces correct results
- Test WorkflowLoader with valid/invalid JSON
- Test API endpoints with simulated HTTP requests
  ┌──────────────────────────────────────────────┐
  │ Scala concepts:                              │
  │   Test fixtures, setup/teardown, testing     │
  │   async code, testing side effects           │
  └──────────────────────────────────────────────┘

Phase 13: Property-Based Tests
- Generate random DAGs, verify topological sort is valid
- Generate random context data, verify serialization round-trips
- Generate random step sequences, verify engine invariants
  ┌──────────────────────────────────────────────┐
  │ Scala concepts:                              │
  │   ScalaCheck, generators, Arbitrary,         │
  │   forAll, shrinking, property specifications │
  └──────────────────────────────────────────────┘

Phase 14: Step Timeouts
- Add timeout config to WorkflowStep trait
- Wrap step execution in Future + Await with deadline
- Handle TimeoutException gracefully
- Timed-out step = failed step (trigger retry if configured)
  ┌──────────────────────────────────────────────┐
  │ Scala concepts:                              │
  │   Future racing, TimeoutException,           │
  │   combining timeout + retry policies         │
  └──────────────────────────────────────────────┘

Phase 15: Retry in DAG Engine
- Integrate Retrier into DurableDAGEngine
- Retry individual steps within a level
- Checkpoint AFTER successful retry (not between attempts)
- Log each retry attempt in the WorkflowLog
  ┌──────────────────────────────────────────────┐
  │ Scala concepts:                              │
  │   Composing existing abstractions,           │
  │   combining retry + parallel + persistence   │
  └──────────────────────────────────────────────┘

Phase 16: Input Validation
- Validate step configs at submit time (not at run time)
- Each step type declares required config keys
- StepRegistry checks config before creating step
- Clear error messages: "Step 'FetchData' requires 'url' in config"
  ┌──────────────────────────────────────────────┐
  │ Scala concepts:                              │
  │   Validation accumulation (collect ALL       │
  │   errors, not just first), Either + List     │
  └──────────────────────────────────────────────┘

Phase 17: Graceful Shutdown
- Register JVM shutdown hook with Runtime.addShutdownHook
- On Ctrl+C: save checkpoint for any running workflow
- Mark running workflows as "paused" in state store
- On restart: detect paused workflows, offer to resume
  ┌──────────────────────────────────────────────┐
  │ Scala concepts:                              │
  │   JVM lifecycle, shutdown hooks,             │
  │   AtomicReference for thread-safe state      │
  └──────────────────────────────────────────────┘

Phase 18: Rate Limiting
- Limit concurrent workflow runs (e.g., max 5)
- Queue excess requests, execute when slots free up
- API returns "queued" status for waiting workflows
  ┌──────────────────────────────────────────────┐
  │ Scala concepts:                              │
  │   Semaphore, bounded thread pools,           │
  │   java.util.concurrent primitives            │
  └──────────────────────────────────────────────┘


MILESTONE 3: REAL-WORLD FEATURES (make it useful)
══════════════════════════════════════════════════════════
Add capabilities that make the engine actually usable
for real tasks, not just simulated steps.

Phase 19: HTTP Steps (Real API Calls)
- Add sttp or requests-scala library
- Create HttpGetStep, HttpPostStep
- Configurable URL, headers, body from context
- Parse JSON responses back into context
- Works with retry (real network calls fail!)
  ┌──────────────────────────────────────────────┐
  │ Scala concepts:                              │
  │   HTTP client libraries, adding deps to sbt, │
  │   real I/O, error handling for network calls │
  └──────────────────────────────────────────────┘

Phase 20: Script Steps (Run Arbitrary Code)
- Create ScriptStep that executes a shell command
- Capture stdout/stderr into context
- Configurable working directory, environment vars
- Timeout support (kill long-running processes)
  ┌──────────────────────────────────────────────┐
  │ Scala concepts:                              │
  │   java.lang.ProcessBuilder, process I/O,     │
  │   stream reading, process lifecycle          │
  └──────────────────────────────────────────────┘

Phase 21: Scheduled Workflows
- "Run this workflow every 5 minutes"
- "Run this workflow at 2am daily"
- Cron expression parsing
- ScheduledExecutorService for timing
- API: POST /workflows/{id}/schedule
  ┌──────────────────────────────────────────────┐
  │ Scala concepts:                              │
  │   ScheduledExecutorService, cron parsing,    │
  │   daemon threads, periodic tasks             │
  └──────────────────────────────────────────────┘

Phase 22: Sub-Workflows
- A step that triggers another workflow
- Parent workflow waits for child to complete
- Child context can be merged back into parent
- Nested crash recovery (child checkpointed independently)
  ┌──────────────────────────────────────────────┐
  │ Scala concepts:                              │
  │   Recursive engine invocation, workflow      │
  │   composition, context scoping               │
  └──────────────────────────────────────────────┘

Phase 23: Workflow Parameterization
- Pass parameters when running a workflow
- "Run the order workflow with orderId=12345"
- Parameters injected into initial context
- API: POST /workflows/{id}/run with JSON body
  ┌──────────────────────────────────────────────┐
  │ Scala concepts:                              │
  │   JSON body parsing in HTTP handlers,        │
  │   context initialization, template patterns  │
  └──────────────────────────────────────────────┘

Phase 24: Notification Steps
- Email step (SMTP or API like SendGrid)
- Slack webhook step
- Generic webhook step (POST to any URL on completion/failure)
- Workflow-level hooks: "notify on failure"
  ┌──────────────────────────────────────────────┐
  │ Scala concepts:                              │
  │   SMTP libraries, webhook patterns,          │
  │   event hooks / callback design              │
  └──────────────────────────────────────────────┘


MILESTONE 4: OBSERVABILITY (make it visible)
══════════════════════════════════════════════════════════
Make it easy to understand what's happening inside
the engine, in real time and historically.

Phase 25: Web Dashboard
- Simple HTML page served by cask
- List all workflows with status
- Click a workflow to see its DAG + step statuses
- Auto-refresh or manual refresh button
  ┌──────────────────────────────────────────────┐
  │ Scala concepts:                              │
  │   Serving static HTML from cask, string      │
  │   templating, basic HTML/CSS/JS              │
  └──────────────────────────────────────────────┘

Phase 26: Visual DAG Renderer
- Generate SVG or HTML visualization of workflow graph
- Color-code nodes: green=done, yellow=running, red=failed
- Show step durations on edges
- Embed in web dashboard
  ┌──────────────────────────────────────────────┐
  │ Scala concepts:                              │
  │   Graph layout algorithms, SVG generation,   │
  │   string templating for markup               │
  └──────────────────────────────────────────────┘

Phase 27: WebSocket Live Updates
- Push step completions to browser in real time
- No polling — server pushes events as they happen
- Dashboard updates live as workflow executes
  ┌──────────────────────────────────────────────┐
  │ Scala concepts:                              │
  │   cask WebSocket support, event publishing,  │
  │   observer pattern, concurrent collections   │
  └──────────────────────────────────────────────┘

Phase 28: Metrics and History
- Track execution history (last N runs per workflow)
- Average step durations over time
- Success/failure rates
- API: GET /workflows/{id}/history
- Identify bottleneck steps across runs
  ┌──────────────────────────────────────────────┐
  │ Scala concepts:                              │
  │   Time-series data, aggregation functions,   │
  │   sliding windows, basic statistics          │
  └──────────────────────────────────────────────┘


MILESTONE 5: PERSISTENCE + STORAGE (make it durable)
══════════════════════════════════════════════════════════
Move beyond file-based storage to real databases.

Phase 29: Database State Store
- Replace FileStateStore with a database backend
- SQLite for single-machine (simple, no server needed)
- Use doobie or Slick for type-safe database access
- Migrate existing checkpoints to database
  ┌──────────────────────────────────────────────┐
  │ Scala concepts:                              │
  │   JDBC, database libraries (doobie/Slick),   │
  │   SQL, connection pools, transactions        │
  └──────────────────────────────────────────────┘

Phase 30: Workflow Versioning
- Version workflow definitions (v1, v2, v3...)
- Running instances keep using the version they started with
- New runs use the latest version
- API: GET /workflows/{id}/versions
  ┌──────────────────────────────────────────────┐
  │ Scala concepts:                              │
  │   Schema evolution, immutable versioned data,│
  │   backward compatibility patterns            │
  └──────────────────────────────────────────────┘

Phase 31: Event Sourcing
- Instead of saving "current state," save every event
- Events: StepStarted, StepCompleted, StepFailed, etc.
- Rebuild state by replaying events
- Full audit trail of everything that happened
  ┌──────────────────────────────────────────────┐
  │ Scala concepts:                              │
  │   Event sourcing pattern, event replay,      │
  │   sealed trait event hierarchies,            │
  │   append-only storage                        │
  └──────────────────────────────────────────────┘


MILESTONE 6: ADVANCED SCALA (make it elegant)
══════════════════════════════════════════════════════════
Refactor the engine using idiomatic Scala patterns.
This is about deepening your Scala skills.

Phase 32: Eliminate All `Any` Types
- Replace Map[String, Any] with fully type-safe heterogeneous map
- Every context read/write checked at compile time
- No asInstanceOf anywhere in the codebase
  ┌──────────────────────────────────────────────┐
  │ Scala concepts:                              │
  │   Typelevel programming, TypeTag/ClassTag,   │
  │   heterogeneous maps, type-safe builders     │
  └──────────────────────────────────────────────┘

Phase 33: Effect System with cats-effect IO
- Replace Future with IO from cats-effect
- All side effects tracked in the type system
- Cancelable, composable, stack-safe
- Resource management with Resource monad
  ┌──────────────────────────────────────────────┐
  │ Scala concepts:                              │
  │   IO monad, cats-effect, referential         │
  │   transparency, Resource, functional I/O     │
  └──────────────────────────────────────────────┘

Phase 34: Tagless Final
- Abstract over the effect type (IO, Future, Id)
- Engine works with ANY effect system
- Steps define effects abstractly: F[WorkflowContext]
- Choose effect at the "edge" (main method)
  ┌──────────────────────────────────────────────┐
  │ Scala concepts:                              │
  │   Higher-kinded types F[_], typeclasses,     │
  │   tagless final pattern, polymorphism over   │
  │   effect types                               │
  └──────────────────────────────────────────────┘

Phase 35: Akka/Pekko Actors
- Replace thread-based parallelism with actor model
- Each step runs in its own actor
- Supervisor actor manages failures and restarts
- Built-in mailbox queuing and backpressure
  ┌──────────────────────────────────────────────┐
  │ Scala concepts:                              │
  │   Actor model, message passing, supervision  │
  │   strategies, Akka/Pekko toolkit             │
  └──────────────────────────────────────────────┘


MILESTONE 7: DISTRIBUTED (make it scale)
══════════════════════════════════════════════════════════
The final frontier — running across multiple machines.

Phase 36: Message Queue Integration
- Steps can be dispatched to external workers via message queue
- RabbitMQ or Redis as the transport
- Engine publishes "work items," workers consume and report back
  ┌──────────────────────────────────────────────┐
  │ Scala concepts:                              │
  │   Message queue clients, serialization for   │
  │   wire transport, async request-reply        │
  └──────────────────────────────────────────────┘

Phase 37: Worker Nodes
- Separate "orchestrator" from "worker" processes
- Workers can run on different machines
- Workers register their capabilities (step types they support)
- Orchestrator routes steps to capable workers
  ┌──────────────────────────────────────────────┐
  │ Scala concepts:                              │
  │   Service discovery, heartbeats, distributed │
  │   state, network programming                 │
  └──────────────────────────────────────────────┘

Phase 38: Horizontal Scaling
- Multiple orchestrator instances behind a load balancer
- Shared database for state (from Phase 29)
- Distributed locking to prevent duplicate execution
- Leader election for scheduled workflows
  ┌──────────────────────────────────────────────┐
  │ Scala concepts:                              │
  │   Distributed systems fundamentals, CAP      │
  │   theorem trade-offs, distributed locks,     │
  │   consensus, ZooKeeper or etcd               │
  └──────────────────────────────────────────────┘
```

### Milestone 2: Hardening

| Phase | Feature | Status |
|-------|---------|--------|
| 11 | Unit tests (munit) | Planned |
| 12 | Integration tests | Planned |
| 13 | Property-based tests (ScalaCheck) | Planned |
| 14 | Step timeouts | Planned |
| 15 | Retry in DAG engine | Planned |
| 16 | Input validation | Planned |
| 17 | Graceful shutdown | Planned |
| 18 | Rate limiting | Planned |

### Milestone 3: Real-World Features

| Phase | Feature | Status |
|-------|---------|--------|
| 19 | HTTP steps (real API calls) | Planned |
| 20 | Script steps (shell commands) | Planned |
| 21 | Scheduled workflows (cron) | Planned |
| 22 | Sub-workflows | Planned |
| 23 | Workflow parameterization | Planned |
| 24 | Notification steps (email, Slack) | Planned |

### Milestone 4: Observability

| Phase | Feature | Status |
|-------|---------|--------|
| 25 | Web dashboard | Planned |
| 26 | Visual DAG renderer | Planned |
| 27 | WebSocket live updates | Planned |
| 28 | Metrics and history | Planned |

### Milestone 5: Persistence + Storage

| Phase | Feature | Status |
|-------|---------|--------|
| 29 | Database state store (SQLite) | Planned |
| 30 | Workflow versioning | Planned |
| 31 | Event sourcing | Planned |

### Milestone 6: Advanced Scala

| Phase | Feature | Status |
|-------|---------|--------|
| 32 | Eliminate all `Any` types | Planned |
| 33 | Effect system (cats-effect IO) | Planned |
| 34 | Tagless final | Planned |
| 35 | Akka/Pekko actors | Planned |

### Milestone 7: Distributed

| Phase | Feature | Status |
|-------|---------|--------|
| 36 | Message queue integration | Planned |
| 37 | Worker nodes | Planned |
| 38 | Horizontal scaling | Planned |

---

## Scala Concepts Covered

A reference of every Scala concept introduced in this project, organized by phase:

### Fundamentals (Phases 1–2)
- `case class` — immutable data containers with auto-generated `copy`, `toString`, `equals`
- `trait` — interfaces that can include default implementations
- `object` — singletons (one instance, like static classes)
- `val` vs `var` — immutable vs mutable bindings
- `Map`, `List`, `Set`, `Option`, `Either` — core collection types
- Pattern matching (`match { case ... }`) — destructuring and control flow
- String interpolation (`s"hello $name"`, `f"${value}%4d"`)
- Generics (`TypedKey[T]`) — parameterized types

### Functional Programming (Phases 3–4)
- `foldLeft` — reduce a collection to a single value with an accumulator
- `map`, `filter`, `forall`, `exists`, `collect` — collection transformations
- `flatMap` — chaining operations that return wrapped values
- Higher-order functions — passing functions as arguments
- Functions as values — storing functions in fields and collections
- `@tailrec` — compiler-verified tail recursion optimization
- `enum` (Scala 3) — algebraic data types with fixed variants
- For-comprehensions over `Either` — chaining fallible operations

### Concurrency (Phases 5–6)
- `Future` — asynchronous computation on another thread
- `ExecutionContext` — thread pool management
- `Await.result` — blocking until a Future completes
- `given` / `using` (Scala 3) — context parameters (dependency injection)
- `synchronized` — thread-safe access to shared mutable state
- `Duration` — type-safe time durations (`60.seconds`)
- `System.nanoTime` — high-precision timing

### JVM Integration (Phases 7–9)
- `java.time.Instant` / `Duration` — timestamps and time math
- `java.nio.file` — file I/O (`Files.readString`, `Files.writeString`)
- `scala.jdk.CollectionConverters` — Java ↔ Scala collection conversion
- `scala.util.Try` — exception catching as values (`Success` / `Failure`)
- `scala.util.Using` — safe resource management

### Libraries and Build (Phases 8–10)
- `build.sbt` — project configuration, dependency management
- `upickle` — JSON serialization/deserialization
- `derives ReadWriter` (Scala 3) — auto-generated JSON codecs
- `ujson` — raw JSON manipulation
- `cask` — HTTP server framework with annotation-based routing
- `type` aliases — shorthand for complex type signatures

---

## Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| Scala | 3.6.4 | Language |
| sbt | 1.x | Build tool |
| upickle | 4.1.0 | JSON serialization |
| cask | 0.10.2 | HTTP server |
| munit | 1.0.0 | Testing (test scope) |

