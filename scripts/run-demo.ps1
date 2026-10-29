<#
.SYNOPSIS
    Demonstration and verification script for SOA Team 8 Hazardous Cargo Freight Planning Platform.
.DESCRIPTION
    Executes Scenario A (Valid Candidate & Planner Approval) and Scenario B (Rejection & Event Projection).
    Produces timestamped evidence, redacts sensitive tokens, and verifies end-to-end provenance.
.EXAMPLE
    .\scripts\run-demo.ps1
#>

[CmdletBinding()]
param(
    [string]$GatewayBaseUrl = $env:GATEWAY_URL,
    [string]$EvidenceDir = "",
    [switch]$SkipReadinessCheck
)

$ErrorActionPreference = "Stop"

if (-not $GatewayBaseUrl) {
    $GatewayBaseUrl = "http://localhost:8080"
}

$Timestamp = (Get-Date).ToString("yyyyMMdd-HHmmss")
if (-not $EvidenceDir) {
    $EvidenceDir = Join-Path -Path $PSScriptRoot -ChildPath "..\evidence\demo-$Timestamp"
}

if (-not (Test-Path $EvidenceDir)) {
    New-Item -ItemType Directory -Path $EvidenceDir -Force | Out-Null
}

$LogFile = Join-Path $EvidenceDir "demo-execution.log"
function Log-Info([string]$msg) {
    $formatted = "[$(Get-Date -Format 'HH:mm:ss.fff')] [INFO] $msg"
    Write-Host $formatted -ForegroundColor Cyan
    Add-Content -Path $LogFile -Value $formatted
}

function Log-Success([string]$msg) {
    $formatted = "[$(Get-Date -Format 'HH:mm:ss.fff')] [PASS] $msg"
    Write-Host $formatted -ForegroundColor Green
    Add-Content -Path $LogFile -Value $formatted
}

function Log-Error([string]$msg) {
    $formatted = "[$(Get-Date -Format 'HH:mm:ss.fff')] [FAIL] $msg"
    Write-Host $formatted -ForegroundColor Red
    Add-Content -Path $LogFile -Value $formatted
}

function Save-Evidence([string]$filename, [object]$data) {
    $path = Join-Path $EvidenceDir $filename
    if ($data -is [string]) {
        Set-Content -Path $path -Value $data -Encoding UTF8
    } else {
        $json = $data | ConvertTo-Json -Depth 10
        Set-Content -Path $path -Value $json -Encoding UTF8
    }
}

Log-Info "Starting SOA Team 8 End-to-End Demo Verification"
Log-Info "Target Gateway: $GatewayBaseUrl"
Log-Info "Evidence Directory: $EvidenceDir"

# ==============================================================================
# 0. Infrastructure & Service Health Check
# ==============================================================================
if (-not $SkipReadinessCheck) {
    Log-Info "Checking service readiness..."
    $serviceEndpoints = @{
        "Eureka Server" = "http://localhost:8761/eureka/apps"
        "Gateway Route (Auth)" = "$GatewayBaseUrl/api/v1/auth/health"
    }

    try {
        $eureka = Invoke-RestMethod -Uri "http://localhost:8761/eureka/apps" -Headers @{ "Accept" = "application/json" } -TimeoutSec 5
        Log-Success "Service Discovery (Eureka) is reachable."
    } catch {
        Log-Error "Eureka is unreachable at http://localhost:8761. Ensure discovery service is running."
        exit 1
    }
}

# Credentials from environment or defaults
$ShipperUser = if ($env:SOA8_SHIPPER_USER) { $env:SOA8_SHIPPER_USER } else { "shipper@soa8.org" }
$ShipperPass = if ($env:SOA8_SHIPPER_PASS) { $env:SOA8_SHIPPER_PASS } else { "ShipperPass123!" }
$PlannerUser = if ($env:SOA8_PLANNER_USER) { $env:SOA8_PLANNER_USER } else { "planner@soa8.org" }
$PlannerPass = if ($env:SOA8_PLANNER_PASS) { $env:SOA8_PLANNER_PASS } else { "PlannerPass123!" }

# ==============================================================================
# 1. Authentication
# ==============================================================================
Log-Info "Authenticating users..."

$shipperAuthReq = @{ username = $ShipperUser; password = $ShipperPass } | ConvertTo-Json
$shipperAuth = Invoke-RestMethod -Uri "$GatewayBaseUrl/api/v1/auth/login" -Method Post -ContentType "application/json" -Body $shipperAuthReq
$shipperToken = $shipperAuth.token
$shipperHeaders = @{ "Authorization" = "Bearer $shipperToken" }
Log-Success "Shipper authenticated (User ID: $($shipperAuth.userId), Roles: $($shipperAuth.roles -join ', '))"

$plannerAuthReq = @{ username = $PlannerUser; password = $PlannerPass } | ConvertTo-Json
$plannerAuth = Invoke-RestMethod -Uri "$GatewayBaseUrl/api/v1/auth/login" -Method Post -ContentType "application/json" -Body $plannerAuthReq
$plannerToken = $plannerAuth.token
$plannerHeaders = @{ "Authorization" = "Bearer $plannerToken" }
Log-Success "Load Planner authenticated (User ID: $($plannerAuth.userId), Roles: $($plannerAuth.roles -join ', '))"

# Redacted Auth Evidence
Save-Evidence "auth-shipper.json" @{ userId = $shipperAuth.userId; username = $shipperAuth.username; roles = $shipperAuth.roles; token = "[REDACTED]" }
Save-Evidence "auth-planner.json" @{ userId = $plannerAuth.userId; username = $plannerAuth.username; roles = $plannerAuth.roles; token = "[REDACTED]" }

# Verify Pinned Rule Snapshot
$snapshotId = "snap-2026-q4-v1"
Log-Info "Fetching pinned rule snapshot: $snapshotId..."
$snapshot = Invoke-RestMethod -Uri "$GatewayBaseUrl/api/v1/rule-snapshots/$snapshotId" -Headers $shipperHeaders
Save-Evidence "rule-snapshot.json" $snapshot
Log-Success "Rule snapshot verified: ID=$($snapshot.snapshotId), Jurisdiction=$($snapshot.jurisdiction), Digest=$($snapshot.snapshotDigest), RulesCount=$($snapshot.rules.Count)"

# ==============================================================================
# SCENARIO A: Valid Candidate Layout & Planner Approval Flow
# ==============================================================================
Log-Info "--- [SCENARIO A: VALID CANDIDATE & PLANNER APPROVAL] ---"

# Step A.1: Create Declaration
$declRefA = "DECL-DEMO-A-$Timestamp"
$declPayloadA = @{
    referenceNumber = $declRefA
    shipperId = $shipperAuth.userId
    origin = "Hazmat Facility Terminal 1"
    destination = "Regional Logistics Hub"
    items = @(
        @{
            cargoItemId = "item-class3-solv"
            hazardClass = "3"
            unNumber = "UN1993"
            description = "Industrial Solvent"
            quantity = 1
            lengthMm = 1200
            widthMm = 800
            heightMm = 1000
            weightKg = 400.0
            stackable = $true
            maxStackWeightKg = 1200.0
        },
        @{
            cargoItemId = "item-class8-acid"
            hazardClass = "8"
            unNumber = "UN1789"
            description = "Hydrochloric Acid Solution"
            quantity = 1
            lengthMm = 1000
            widthMm = 800
            heightMm = 900
            weightKg = 350.0
            stackable = $false
            maxStackWeightKg = 0.0
        }
    )
} | ConvertTo-Json -Depth 5

$declA = Invoke-RestMethod -Uri "$GatewayBaseUrl/api/v1/declarations" -Method Post -ContentType "application/json" -Headers $shipperHeaders -Body $declPayloadA
Save-Evidence "scenario-a-01-declaration.json" $declA
Log-Success "Step A.1: Declaration created: ID=$($declA.id), Version=$($declA.version), Status=$($declA.status)"

# Step A.2: Initialize Compatibility Assessment
$compTrailer = @{
    compartmentId = "comp-trailer-01"
    type = "Standard Trailer"
    lengthMm = 12000
    widthMm = 2400
    heightMm = 2600
    maxPayloadKg = 25000.0
}
$assessPayloadA = @{
    declarationId = $declA.id
    declarationVersion = $declA.version
    ruleSnapshotId = $snapshot.snapshotId
    compartment = $compTrailer
} | ConvertTo-Json -Depth 5

$assessmentA = Invoke-RestMethod -Uri "$GatewayBaseUrl/api/v1/assessments" -Method Post -ContentType "application/json" -Headers $plannerHeaders -Body $assessPayloadA
Save-Evidence "scenario-a-02-assessment-draft.json" $assessmentA
Log-Success "Step A.2: Assessment created: ID=$($assessmentA.id), Status=$($assessmentA.workflowStatus), Snapshot=$($assessmentA.ruleSnapshotId)"

# Step A.3: Optimization Job Dispatch
$optPayloadA = @{
    declarationId = $declA.id
    compartment = $compTrailer
    items = $declPayloadA | ConvertFrom-Json | Select-Object -ExpandProperty items
    ruleSnapshotId = $snapshot.snapshotId
    algorithm = "HEURISTIC_EP_3D_V1"
} | ConvertTo-Json -Depth 5

$optJobA = Invoke-RestMethod -Uri "$GatewayBaseUrl/api/v1/optimization-jobs" -Method Post -ContentType "application/json" -Headers $plannerHeaders -Body $optPayloadA
Save-Evidence "scenario-a-03-opt-job-response.json" $optJobA
$layoutA = $optJobA.resultJson | ConvertFrom-Json
Save-Evidence "scenario-a-03-layout-result.json" $layoutA

$effectiveSolverStatus = $layoutA.solverStatus
$effectiveProvenance = $layoutA.provenance
$candidateDigestA = $layoutA.contentDigest

Log-Success "Step A.3: Optimization completed: JobID=$($optJobA.jobId), EffectiveSolverStatus=$effectiveSolverStatus, EffectiveProvenance=$effectiveProvenance, Digest=$candidateDigestA, PlacementsCount=$($layoutA.placements.Count)"

if ($effectiveSolverStatus -ne "FEASIBLE" -and $effectiveSolverStatus -ne "OPTIMAL") {
    Log-Error "Solver did not return FEASIBLE or OPTIMAL for Scenario A. Returned: $effectiveSolverStatus"
    exit 1
}

# Step A.4: Record Candidate Layout
$candidatePayloadA = @{
    layoutId = "layout-a-$($assessmentA.id.Substring(0,8))"
    layoutVersion = 1
    compartmentId = $compTrailer.compartmentId
    contentDigest = $candidateDigestA
    solverStatus = $effectiveSolverStatus
    provenance = $effectiveProvenance
    placements = $layoutA.placements
} | ConvertTo-Json -Depth 5

$candidateRecA = Invoke-RestMethod -Uri "$GatewayBaseUrl/api/v1/assessments/$($assessmentA.id)/candidate" -Method Post -ContentType "application/json" -Headers $plannerHeaders -Body $candidatePayloadA
Save-Evidence "scenario-a-04-candidate-recorded.json" $candidateRecA
Log-Success "Step A.4: Candidate recorded: Status=$($candidateRecA.workflowStatus), PersistedDigest=$($candidateRecA.candidateContentDigest)"

if ($candidateRecA.candidateContentDigest -ne $candidateDigestA) {
    Log-Error "Candidate digest mismatch! Optimizer=$candidateDigestA vs Persisted=$($candidateRecA.candidateContentDigest)"
    exit 1
}

# Step A.5: Independent 3D Validation Engine Execution
$valPayloadA = @{
    layout = $candidatePayloadA | ConvertFrom-Json
    compartment = $compTrailer
    ruleSnapshot = @{
        snapshotId = $snapshot.snapshotId
        snapshotDigest = $assessmentA.ruleSnapshotDigest
    }
} | ConvertTo-Json -Depth 5

$valResA = Invoke-RestMethod -Uri "$GatewayBaseUrl/api/v1/validations" -Method Post -ContentType "application/json" -Headers $plannerHeaders -Body $valPayloadA
Save-Evidence "scenario-a-05-validation-result.json" $valResA
Log-Success "Step A.5: Independent validation executed: ID=$($valResA.validationId), Passed=$($valResA.passed), ValidatorVersion=$($valResA.validatorVersion)"

if (-not $valResA.passed) {
    Log-Error "Independent validation failed unexpectedly for Scenario A: $($valResA.findings | ConvertTo-Json)"
    exit 1
}

# Step A.6: Record Authoritative Validation Outcome
$valOutcomePayloadA = @{
    validationId = $valResA.validationId
    passed = $true
} | ConvertTo-Json

$valOutcomeRecA = Invoke-RestMethod -Uri "$GatewayBaseUrl/api/v1/assessments/$($assessmentA.id)/validation" -Method Post -ContentType "application/json" -Headers $plannerHeaders -Body $valOutcomePayloadA
Save-Evidence "scenario-a-06-assessment-pending-approval.json" $valOutcomeRecA
Log-Success "Step A.6: Authoritative validation bound: Status=$($valOutcomeRecA.workflowStatus), ValidationPassed=$($valOutcomeRecA.validationPassed)"

if ($valOutcomeRecA.workflowStatus -ne "PENDING_APPROVAL") {
    Log-Error "Assessment did not transition to PENDING_APPROVAL: $($valOutcomeRecA.workflowStatus)"
    exit 1
}

# Step A.7: Adversarial Approval Check (Tampered Digest MUST fail)
Log-Info "Step A.7: Asserting fail-closed on tampered digest approval..."
$tamperedApproveReq = @{
    layoutVersion = 1
    expectedDigest = "0000000000000000000000000000000000000000000000000000000000000000"
    plannerId = $PlannerUser
    notes = "Tampered approval attempt"
} | ConvertTo-Json
$tamperedRejected = $false
try {
    Invoke-RestMethod -Uri "$GatewayBaseUrl/api/v1/assessments/$($assessmentA.id)/approve" -Method Post -ContentType "application/json" -Headers $plannerHeaders -Body $tamperedApproveReq
} catch {
    $tamperedRejected = $true
    Log-Success "Tampered digest approval correctly rejected with HTTP $([int]$_.Exception.Response.StatusCode)."
}
if (-not $tamperedRejected) {
    Log-Error "Security failure: Tampered digest approval was accepted!"
    exit 1
}

# Step A.8: Authorized Load Planner Approval
$approveReqA = @{
    layoutVersion = 1
    expectedDigest = $candidateRecA.candidateContentDigest
    plannerId = $PlannerUser
    notes = "Approved verified candidate layout after authoritative 3D validation"
} | ConvertTo-Json

$approvedA = Invoke-RestMethod -Uri "$GatewayBaseUrl/api/v1/assessments/$($assessmentA.id)/approve" -Method Post -ContentType "application/json" -Headers $plannerHeaders -Body $approveReqA
Save-Evidence "scenario-a-08-assessment-approved.json" $approvedA
Log-Success "Step A.8: Assessment successfully approved: WorkflowStatus=$($approvedA.workflowStatus), Decision=$($approvedA.approvalDecision)"

# Retrieve Final Assessment Record
$finalA = Invoke-RestMethod -Uri "$GatewayBaseUrl/api/v1/assessments/$($assessmentA.id)" -Headers $plannerHeaders
Save-Evidence "scenario-a-09-final-assessment.json" $finalA
Log-Success "Scenario A Complete: Final Workflow Status = $($finalA.workflowStatus)"

# ==============================================================================
# SCENARIO B: Rejection Flow, Transactional Outbox, & Downstream Projection
# ==============================================================================
Log-Info "`n--- [SCENARIO B: REJECTED WORKFLOW & DOWNSTREAM PROJECTION] ---"

# Step B.1: Create Declaration for Rejection
$declRefB = "DECL-DEMO-B-$Timestamp"
$declPayloadB = @{
    referenceNumber = $declRefB
    shipperId = $shipperAuth.userId
    origin = "Hazardous Warehouse Area 4"
    destination = "Inland Depot"
    items = @(
        @{
            cargoItemId = "item-class43-wet"
            hazardClass = "4.3"
            unNumber = "UN1402"
            description = "Calcium Carbide (Water-reactive)"
            quantity = 1
            lengthMm = 1000
            widthMm = 800
            heightMm = 900
            weightKg = 300.0
            stackable = $false
            maxStackWeightKg = 0.0
        }
    )
} | ConvertTo-Json -Depth 5

$declB = Invoke-RestMethod -Uri "$GatewayBaseUrl/api/v1/declarations" -Method Post -ContentType "application/json" -Headers $shipperHeaders -Body $declPayloadB
Save-Evidence "scenario-b-01-declaration.json" $declB
Log-Success "Step B.1: Declaration created: ID=$($declB.id), Status=$($declB.status)"

# Step B.2: Create Assessment
$assessPayloadB = @{
    declarationId = $declB.id
    declarationVersion = $declB.version
    ruleSnapshotId = $snapshot.snapshotId
    compartment = $compTrailer
} | ConvertTo-Json -Depth 5

$assessmentB = Invoke-RestMethod -Uri "$GatewayBaseUrl/api/v1/assessments" -Method Post -ContentType "application/json" -Headers $plannerHeaders -Body $assessPayloadB
Save-Evidence "scenario-b-02-assessment-draft.json" $assessmentB
Log-Success "Step B.2: Assessment created: ID=$($assessmentB.id), Status=$($assessmentB.workflowStatus)"

# Step B.3: Planner Rejection
$rejectionReason = "OPERATIONAL_CAPACITY_HOLD"
$rejectionNotes = "Planner operational decision: Route hold due to bad weather restrictions on Class 4.3 transport"

$rejectReqB = @{
    layoutVersion = 0
    plannerId = $PlannerUser
    rejectionReasonCode = $rejectionReason
    notes = $rejectionNotes
} | ConvertTo-Json

$rejectedB = Invoke-RestMethod -Uri "$GatewayBaseUrl/api/v1/assessments/$($assessmentB.id)/reject" -Method Post -ContentType "application/json" -Headers $plannerHeaders -Body $rejectReqB
Save-Evidence "scenario-b-03-assessment-rejected.json" $rejectedB
Log-Success "Step B.3: Assessment rejected: Status=$($rejectedB.workflowStatus), Decision=$($rejectedB.approvalDecision), ReasonCode=$($rejectedB.decisionReasonCode)"

# Step B.4: Downstream Event Consumer Projection
Log-Info "Step B.4: Waiting for Transactional Outbox to publish to Kafka (soa.s06.t08.events)..."
$projectionUpdated = $false
for ($i = 0; $i -lt 10; $i++) {
    Start-Sleep -Seconds 1
    $declStatusB = Invoke-RestMethod -Uri "$GatewayBaseUrl/api/v1/declarations/$($declB.id)" -Headers $shipperHeaders
    if ($declStatusB.status -eq "REJECTED") {
        $projectionUpdated = $true
        Save-Evidence "scenario-b-04-declaration-projected.json" $declStatusB
        Log-Success "Downstream projection updated: Declaration [$($declB.id)] status transitioned to REJECTED"
        break
    }
}

if (-not $projectionUpdated) {
    Log-Error "Downstream declaration projection was not updated within timeout. Check outbox publisher and kafka consumer."
    exit 1
}

# Step B.5: Capture Published Kafka Event Evidence
Log-Info "Step B.5: Capturing Kafka CargoLoadRejected event evidence..."
$kafkaEventB = $null
try {
    $rawEvents = docker exec soa8-kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic soa.s06.t08.events --from-beginning --timeout-ms 5000 2>$null
    foreach ($line in $rawEvents) {
        if ($line -match '"declarationId":"' + [regex]::Escape($declB.id) + '"') {
            $kafkaEventB = $line | ConvertFrom-Json
            break
        }
    }
} catch {
    Log-Info "Note: Direct Kafka container query skipped: $($_.Exception.Message)"
}

if ($kafkaEventB) {
    Save-Evidence "scenario-b-05-kafka-event.json" $kafkaEventB
    Log-Success "Captured Kafka CargoLoadRejected event: EventID=$($kafkaEventB.eventId), AggregateID=$($kafkaEventB.aggregateId), CorrelationID=$($kafkaEventB.correlationId)"
} else {
    Log-Info "Kafka event consumed successfully by declaration-service."
}

# ==============================================================================
# Summary
# ==============================================================================
Log-Info "`n=============================================================================="
Log-Info "DEMO EXECUTION SUMMARY"
Log-Info "=============================================================================="
Log-Info "Scenario A (Valid Approval):"
Log-Info "  Declaration ID:       $($declA.id)"
Log-Info "  Assessment ID:        $($assessmentA.id)"
Log-Info "  Optimization Job ID:  $($optJobA.jobId)"
Log-Info "  Candidate Digest:     $candidateDigestA"
Log-Info "  Validation ID:        $($valResA.validationId)"
Log-Info "  Final Status:         $($finalA.workflowStatus)"
Log-Info "Scenario B (Rejection & Event Projection):"
Log-Info "  Declaration ID:       $($declB.id)"
Log-Info "  Assessment ID:        $($assessmentB.id)"
Log-Info "  Rejection Reason:     $($rejectedB.decisionReasonCode)"
Log-Info "  Downstream Status:    $($declStatusB.status)"
Log-Info "Evidence Directory:     $EvidenceDir"
Log-Success "ALL DEMO ASSERTIONS PASSED SUCCESSFULLY"
exit 0
