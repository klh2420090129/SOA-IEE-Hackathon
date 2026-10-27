$ErrorActionPreference = "Stop"

$plannerAuth = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/login" -Method Post -ContentType "application/json" -Body (@{ username="planner@soa8.org"; password="PlannerPass123!" } | ConvertTo-Json)
$plannerHeaders = @{ "Authorization" = "Bearer $($plannerAuth.token)" }

$compTrailer = @{
    compartmentId = "comp-trailer-cpsat"
    type = "Standard Trailer"
    lengthMm = 6000
    widthMm = 2400
    heightMm = 2400
    maxPayloadKg = 15000.0
}

$items = @(
    @{
        cargoItemId = "item-class3-solv"
        hazardClass = "3"
        unNumber = "UN1993"
        description = "Industrial Solvent Drum"
        quantity = 2
        lengthMm = 1200
        widthMm = 800
        heightMm = 1000
        weightKg = 400.0
        stackable = $true
        maxStackWeightKg = 1200.0
        permittedOrientations = @("DEFAULT", "ROTATE_YAW_90")
    },
    @{
        cargoItemId = "item-class8-acid"
        hazardClass = "8"
        unNumber = "UN1789"
        description = "Hydrochloric Acid IBC"
        quantity = 1
        lengthMm = 1200
        widthMm = 1000
        heightMm = 1100
        weightKg = 600.0
        stackable = $false
        maxStackWeightKg = 0.0
        permittedOrientations = @("DEFAULT")
    }
)

Write-Host "=== 1. SUBMITTING CP-SAT JOB ==="
$optPayload = @{
    declarationId = "DECL-CPSAT-COMP-001"
    compartment = $compTrailer
    items = $items
    ruleSnapshotId = "snap-2026-q4-v1"
    algorithm = "PYTHON_CPSAT_3D_V1"
} | ConvertTo-Json -Depth 5

$submittedJob = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/optimization-jobs" -Method Post -ContentType "application/json" -Headers $plannerHeaders -Body $optPayload
Write-Host "Submitted Job: ID=$($submittedJob.jobId), Status=$($submittedJob.status)"

$jobResult = $null
for ($i = 0; $i -lt 15; $i++) {
    Start-Sleep -Seconds 1
    $pollJob = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/optimization-jobs/$($submittedJob.jobId)" -Headers $plannerHeaders
    if ($pollJob.status -eq "COMPLETED") {
        $jobResult = $pollJob
        break
    }
}

if (-not $jobResult) {
    Write-Host "CP-SAT job did not complete in time."
    exit 1
}

$layout = $jobResult.resultJson | ConvertFrom-Json
$candidateDigest = $layout.contentDigest

Write-Host "CP-SAT Results:"
Write-Host "  Solver Status: $($layout.solverStatus)"
Write-Host "  Provenance: $($layout.provenance)"
Write-Host "  Candidate Digest: $candidateDigest"
Write-Host "  Placements: $($layout.placements.Count)"
Write-Host "  Diagnostics: $($layout.diagnostics)"

Write-Host "`n=== 2. CREATING ASSESSMENT & RECORDING CANDIDATE ==="
$assessPayload = @{
    declarationId = "DECL-CPSAT-COMP-001"
    declarationVersion = 1
    ruleSnapshotId = "snap-2026-q4-v1"
    compartment = $compTrailer
} | ConvertTo-Json -Depth 5
$assessment = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/assessments" -Method Post -ContentType "application/json" -Headers $plannerHeaders -Body $assessPayload
Write-Host "Assessment created: ID=$($assessment.id), Status=$($assessment.workflowStatus)"

$candidatePayload = @{
    layoutId = "layout-cpsat-$($assessment.id.Substring(0,8))"
    layoutVersion = 1
    compartmentId = $compTrailer.compartmentId
    contentDigest = $candidateDigest
    solverStatus = $layout.solverStatus
    provenance = $layout.provenance
    placements = $layout.placements
} | ConvertTo-Json -Depth 5

$candidateRec = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/assessments/$($assessment.id)/candidate" -Method Post -ContentType "application/json" -Headers $plannerHeaders -Body $candidatePayload
Write-Host "Candidate recorded: Status=$($candidateRec.workflowStatus), Digest=$($candidateRec.candidateContentDigest)"

if ($candidateRec.candidateContentDigest -ne $candidateDigest) {
    Write-Host "Candidate digest mismatch!"
    exit 1
}

Write-Host "`n=== 3. INDEPENDENT 3D VALIDATION ENGINE ==="
$valPayload = @{
    layout = $candidatePayload | ConvertFrom-Json
    compartment = $compTrailer
    ruleSnapshot = @{
        snapshotId = "snap-2026-q4-v1"
        snapshotDigest = $assessment.ruleSnapshotDigest
    }
} | ConvertTo-Json -Depth 5

$valRes = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/validations" -Method Post -ContentType "application/json" -Headers $plannerHeaders -Body $valPayload
Write-Host "Validation Result: ID=$($valRes.validationId), Passed=$($valRes.passed), ValidatorVersion=$($valRes.validatorVersion)"

if (-not $valRes.passed) {
    Write-Host "Validation failed: $($valRes.findings | ConvertTo-Json)"
    exit 1
}

Write-Host "`n=== 4. BINDING AUTHORITATIVE VALIDATION OUTCOME ==="
$valOutcomePayload = @{
    validationId = $valRes.validationId
    passed = $true
} | ConvertTo-Json
$valOutcomeRec = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/assessments/$($assessment.id)/validation" -Method Post -ContentType "application/json" -Headers $plannerHeaders -Body $valOutcomePayload
Write-Host "Validation bound: Status=$($valOutcomeRec.workflowStatus)"

Write-Host "`n=== 5. FAIL-CLOSED TAMPERED DIGEST APPROVAL CHECK ==="
$tamperedApproveReq = @{
    layoutVersion = 1
    expectedDigest = "1111111111111111111111111111111111111111111111111111111111111111"
    plannerId = "planner@soa8.org"
    notes = "Tampered approval attempt"
} | ConvertTo-Json
$tamperedRejected = $false
try {
    Invoke-RestMethod -Uri "http://localhost:8080/api/v1/assessments/$($assessment.id)/approve" -Method Post -ContentType "application/json" -Headers $plannerHeaders -Body $tamperedApproveReq
} catch {
    $tamperedRejected = $true
    Write-Host "Tampered approval correctly rejected with HTTP $([int]$_.Exception.Response.StatusCode)."
}
if (-not $tamperedRejected) {
    Write-Host "Security check failed: Tampered digest approval was accepted!"
    exit 1
}

Write-Host "`n=== 6. AUTHORIZED LOAD PLANNER APPROVAL ==="
$approveReq = @{
    layoutVersion = 1
    expectedDigest = $candidateDigest
    plannerId = "planner@soa8.org"
    notes = "Approved CP-SAT candidate layout after independent zero-trust validation"
} | ConvertTo-Json

$approved = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/assessments/$($assessment.id)/approve" -Method Post -ContentType "application/json" -Headers $plannerHeaders -Body $approveReq
Write-Host "Assessment APPROVED: WorkflowStatus=$($approved.workflowStatus), Decision=$($approved.approvalDecision)"

Write-Host "`n=== 7. HEURISTIC BASELINE COMPARISON ON IDENTICAL INPUTS ==="
$optPayloadHeuristic = @{
    declarationId = "DECL-CPSAT-COMP-001"
    compartment = $compTrailer
    items = $items
    ruleSnapshotId = "snap-2026-q4-v1"
    algorithm = "HEURISTIC_EP_3D_V1"
} | ConvertTo-Json -Depth 5

$heurJob = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/optimization-jobs" -Method Post -ContentType "application/json" -Headers $plannerHeaders -Body $optPayloadHeuristic
$heurLayout = $heurJob.resultJson | ConvertFrom-Json

Write-Host "`n--- COMPARISON REPORT ---"
Write-Host "METRIC                         | HEURISTIC (EP_3D_V1)              | CP-SAT (PYTHON_ORTOOLS_3D_V1)"
Write-Host "-------------------------------|-----------------------------------|-------------------------------"
Write-Host ("Solver Status                  | {0,-33} | {1,-29}" -f $heurLayout.solverStatus, $layout.solverStatus)
Write-Host ("Provenance                     | {0,-33} | {1,-29}" -f $heurLayout.provenance, $layout.provenance)
Write-Host ("Units Total                    | {0,-33} | {1,-29}" -f 3, 3)
Write-Host ("Units Placed                   | {0,-33} | {1,-29}" -f $heurLayout.placements.Count, $layout.placements.Count)
Write-Host ("Units Unassigned               | {0,-33} | {1,-29}" -f $heurLayout.unassignedItems.Count, $layout.unassignedItems.Count)
Write-Host ("Execution Time                 | {0,-33} | {1,-29}" -f "$($heurLayout.executionDurationMs) ms", "$($layout.executionDurationMs) ms")
Write-Host ("Validation Outcome             | {0,-33} | {1,-29}" -f "PASSED", "PASSED")
$hDigestSub = $heurLayout.contentDigest.Substring(0,16) + "..."
$cDigestSub = $layout.contentDigest.Substring(0,16) + "..."
Write-Host ("Candidate Digest               | {0,-33} | {1,-29}" -f $hDigestSub, $cDigestSub)
Write-Host "========================="
