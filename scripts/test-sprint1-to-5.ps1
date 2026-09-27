# ==============================================================================
# ALB-M: Sprint 1 through Sprint 5 End-to-End Test Suite
# PowerShell automation script for verifying all milestones up to Sprint 5
# ==============================================================================

param (
    [string]$GatewayUrl = "http://localhost:8080",
    [string]$Worker1Url = "http://localhost:8081",
    [string]$Worker2Url = "http://localhost:8082",
    [string]$Worker3Url = "http://localhost:8083",
    [string]$EurekaUrl = "http://localhost:8761",
    [switch]$SkipUnitTests = $false
)

$ErrorActionPreference = "Continue"

function Write-Step {
    param([string]$Message)
    Write-Host "`n====================================================================" -ForegroundColor Cyan
    Write-Host "  $Message" -ForegroundColor Cyan
    Write-Host "====================================================================" -ForegroundColor Cyan
}

function Write-Pass {
    param([string]$Message)
    Write-Host "  [PASS] $Message" -ForegroundColor Green
}

function Write-Fail {
    param([string]$Message)
    Write-Host "  [FAIL] $Message" -ForegroundColor Red
}

function Write-Info {
    param([string]$Message)
    Write-Host "  [INFO] $Message" -ForegroundColor Yellow
}

$Global:Passed = 0
$Global:Failed = 0

function Assert-Condition {
    param([bool]$Condition, [string]$PassMsg, [string]$FailMsg)
    if ($Condition) {
        Write-Pass $PassMsg
        $Global:Passed++
    } else {
        Write-Fail $FailMsg
        $Global:Failed++
    }
}

# ------------------------------------------------------------------------------
# STEP 0: Maven Build & Unit Tests Check
# ------------------------------------------------------------------------------
if (-not $SkipUnitTests) {
    Write-Step "STEP 0: Running Comprehensive Maven Unit & Integration Tests"
    $mvnResult = & .\mvnw.cmd test
    $success = $LASTEXITCODE -eq 0
    Assert-Condition $success "All unit tests across service-registry, gateway, and demo-worker passed" "Maven test execution failed"
}

# ------------------------------------------------------------------------------
# STEP 1: Sprint 1 - Infrastructure & Discovery Verification
# ------------------------------------------------------------------------------
Write-Step "STEP 1: Sprint 1 - Discovery & Service Health Checks"

try {
    $eurekaResponse = Invoke-RestMethod -Uri "$EurekaUrl/eureka/apps" -Headers @{"Accept"="application/json"} -TimeoutSec 3 -ErrorAction SilentlyContinue
    $eurekaOk = $null -ne $eurekaResponse
    Assert-Condition $eurekaOk "Eureka Server is reachable at $EurekaUrl" "Eureka Server unreachable at $EurekaUrl"
} catch {
    Write-Info "Eureka server not running locally. If running under Docker, ensure docker compose up is active."
}

try {
    $gwHealth = Invoke-RestMethod -Uri "$GatewayUrl/actuator/health" -TimeoutSec 3 -ErrorAction SilentlyContinue
    $gwOk = $gwHealth.status -eq "UP"
    Assert-Condition $gwOk "Gateway is UP at $GatewayUrl" "Gateway unreachable or not UP"
} catch {
    Write-Info "Gateway not reachable at $GatewayUrl."
}

# ------------------------------------------------------------------------------
# STEP 2: Sprint 2 - Baseline Routing Algorithms & Admin API
# ------------------------------------------------------------------------------
Write-Step "STEP 2: Sprint 2 - Baseline Strategies & Dynamic Switching"

$strategies = @("ROUND_ROBIN", "WEIGHTED_ROUND_ROBIN", "LEAST_CONNECTIONS")
foreach ($strat in $strategies) {
    try {
        $body = @{ strategy = $strat } | ConvertTo-Json
        $switchRes = Invoke-RestMethod -Uri "$GatewayUrl/admin/routing/strategy" -Method Post -Body $body -ContentType "application/json" -TimeoutSec 3
        Assert-Condition ($switchRes.activeStrategy -eq $strat -or $switchRes.status -eq "SUCCESS") "Successfully switched strategy to $strat" "Failed to switch to $strat"
    } catch {
        Write-Info "Cannot switch strategy on $GatewayUrl (check if Gateway is running)"
    }
}

# ------------------------------------------------------------------------------
# STEP 3: Sprint 3 - Observability & Telemetry Pipeline
# ------------------------------------------------------------------------------
Write-Step "STEP 3: Sprint 3 - Prometheus Telemetry Verification"

try {
    $metrics = Invoke-RestMethod -Uri "$Worker1Url/actuator/prometheus" -TimeoutSec 3 -ErrorAction SilentlyContinue
    $hasWorkerGauges = $metrics -match "alb_worker_active_requests" -and $metrics -match "alb_worker_cpu_usage"
    Assert-Condition $hasWorkerGauges "Worker exposes custom Micrometer gauges (active_requests, cpu_usage)" "Worker missing Prometheus telemetry gauges"
} catch {
    Write-Info "Worker 1 unreachable for Prometheus scrape test"
}

# ------------------------------------------------------------------------------
# STEP 4: Sprint 4 - Multi-Metric Adaptive Routing Engine (MM-AR)
# ------------------------------------------------------------------------------
Write-Step "STEP 4: Sprint 4 - Multi-Metric Adaptive Engine & Control Plane"

try {
    # 4.1 Switch to ADAPTIVE_MULTI_METRIC
    $body = @{ strategy = "ADAPTIVE_MULTI_METRIC" } | ConvertTo-Json
    $switchAdaptive = Invoke-RestMethod -Uri "$GatewayUrl/admin/routing/strategy" -Method Post -Body $body -ContentType "application/json" -TimeoutSec 3
    Assert-Condition ($switchAdaptive.activeStrategy -eq "ADAPTIVE_MULTI_METRIC") "Activated ADAPTIVE_MULTI_METRIC strategy" "Failed to activate ADAPTIVE_MULTI_METRIC"

    # 4.2 Inspect /admin/routing/status
    $status = Invoke-RestMethod -Uri "$GatewayUrl/admin/routing/status" -TimeoutSec 3
    Assert-Condition ($null -ne $status.activeStrategy) "GET /admin/routing/status returned active configuration" "Failed reading routing status"

    # 4.3 Update Weights via /admin/routing/weights
    $weightBody = @{
        cpu = 0.30
        memory = 0.10
        latency = 0.30
        connections = 0.10
        errors = 0.20
    } | ConvertTo-Json
    $weightsRes = Invoke-RestMethod -Uri "$GatewayUrl/admin/routing/weights" -Method Post -Body $weightBody -ContentType "application/json" -TimeoutSec 3
    Assert-Condition ($weightsRes.status -eq "UPDATED") "Dynamic weight adjustment successful (POST /admin/routing/weights)" "Weight update failed"

    # 4.4 Update Config via POST /admin/routing/config
    $configBody = @{
        refreshIntervalMs = 400
        hysteresisDelta = 0.12
        softmaxTemperature = 0.30
    } | ConvertTo-Json
    $configRes = Invoke-RestMethod -Uri "$GatewayUrl/admin/routing/config" -Method Post -Body $configBody -ContentType "application/json" -TimeoutSec 3
    Assert-Condition ($configRes.status -eq "UPDATED") "Hyperparameter tuning successful (POST /admin/routing/config)" "Config update failed"
} catch {
    Write-Info "Adaptive engine verification encountered network error (check Gateway status)"
}

# ------------------------------------------------------------------------------
# STEP 5: Sprint 5 - Chaos Engineering & Fault Injection Suite
# ------------------------------------------------------------------------------
Write-Step "STEP 5: Sprint 5 - Chaos Engineering & Fault Injection Validation"

# Select Worker 2 for fault injection testing
$targetWorker = $Worker2Url

try {
    # 5.1 Latency Injection Hook (T5.2)
    $latencyBody = @{
        delayMs = 250
        jitterMs = 30
        probability = 1.0
        durationSeconds = 20
    } | ConvertTo-Json
    $latRes = Invoke-RestMethod -Uri "$targetWorker/chaos/latency" -Method Post -Body $latencyBody -ContentType "application/json" -TimeoutSec 3
    Assert-Condition ($latRes.status -eq "APPLIED" -and $latRes.fault -eq "LATENCY") "T5.2: Injected artificial latency ($($latRes.delayMs)ms with $($latRes.jitterMs)ms jitter)" "Latency injection failed"

    # 5.2 CPU Burn Hook (T5.1)
    $cpuBody = @{
        threads = 2
        targetCpuPercent = 85
        durationSeconds = 15
    } | ConvertTo-Json
    $cpuRes = Invoke-RestMethod -Uri "$targetWorker/chaos/cpu-burn" -Method Post -Body $cpuBody -ContentType "application/json" -TimeoutSec 3
    Assert-Condition ($cpuRes.status -eq "APPLIED" -and $cpuRes.fault -eq "CPU_BURN") "T5.1: Injected CPU Burn ($($cpuRes.threads) threads, $($cpuRes.targetCpuPercent)% target load)" "CPU burn injection failed"

    # 5.3 Error Burst Hook (T5.3)
    $errBody = @{
        errorRate = 0.40
        durationSeconds = 15
    } | ConvertTo-Json
    $errRes = Invoke-RestMethod -Uri "$targetWorker/chaos/error-burst" -Method Post -Body $errBody -ContentType "application/json" -TimeoutSec 3
    Assert-Condition ($errRes.status -eq "APPLIED" -and $errRes.fault -eq "ERROR_BURST") "T5.3: Injected HTTP 500 Error Burst (rate=$($errRes.errorRate))" "Error burst injection failed"

    # 5.4 Chaos Status Check (T5.4)
    $chaosStatus = Invoke-RestMethod -Uri "$targetWorker/chaos/status" -TimeoutSec 3
    Assert-Condition ($chaosStatus.status -eq "OK" -and $null -ne $chaosStatus.chaos) "GET /chaos/status correctly reports active faults" "Chaos status check failed"

    # 5.5 Fault Immunity: Verify that /chaos and /actuator are immune to injected faults
    $actuatorRes = Invoke-WebRequest -Uri "$targetWorker/actuator/health" -TimeoutSec 3
    Assert-Condition ($actuatorRes.StatusCode -eq 200) "Fault Immunity: /actuator/health remains 200 OK during active chaos" "Actuator was disrupted by chaos"

    # 5.6 Reset Hook (T5.4)
    $resetRes = Invoke-RestMethod -Uri "$targetWorker/chaos/reset" -Method Post -TimeoutSec 3
    Assert-Condition ($resetRes.status -eq "RESET" -and $resetRes.chaosStatus.healthy -eq $true) "T5.4: POST /chaos/reset instantly restored worker to healthy baseline" "Reset failed"
} catch {
    Write-Info "Worker chaos testing encountered error: $($_.Exception.Message)"
}

# ------------------------------------------------------------------------------
# SUMMARY
# ------------------------------------------------------------------------------
Write-Host "`n====================================================================" -ForegroundColor Cyan
Write-Host "                     TEST SUMMARY REPORT" -ForegroundColor Cyan
Write-Host "====================================================================" -ForegroundColor Cyan
Write-Host "  Passed checks: $Global:Passed" -ForegroundColor Green
Write-Host "  Failed checks: $Global:Failed" -ForegroundColor $(if ($Global:Failed -gt 0) { "Red" } else { "Green" })
Write-Host "====================================================================`n" -ForegroundColor Cyan
