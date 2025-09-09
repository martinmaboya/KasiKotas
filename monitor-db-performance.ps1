# Database Performance Monitoring Script for KasiKotas
# This script helps monitor database connection pool and cache performance

Write-Host "KasiKotas Database Performance Monitor" -ForegroundColor Green
Write-Host "=====================================" -ForegroundColor Green

# Check if application is running
$appRunning = netstat -an | Select-String ":8080.*LISTENING"
if ($appRunning) {
    Write-Host "✓ Application is running on port 8080" -ForegroundColor Green
} else {
    Write-Host "✗ Application is not running on port 8080" -ForegroundColor Red
    Write-Host "Start your application first: mvn spring-boot:run" -ForegroundColor Yellow
    exit 1
}

Write-Host "`nTesting high-traffic endpoints..." -ForegroundColor Yellow

# Function to test endpoint performance
function Test-Endpoint {
    param (
        [string]$Url,
        [string]$Description,
        [int]$Iterations = 5
    )
    
    Write-Host "`nTesting: $Description" -ForegroundColor Cyan
    Write-Host "URL: $Url"
    
    $times = @()
    
    for ($i = 1; $i -le $Iterations; $i++) {
        try {
            $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
            $response = Invoke-WebRequest -Uri $Url -Method GET -UseBasicParsing -TimeoutSec 10
            $stopwatch.Stop()
            
            $responseTime = $stopwatch.ElapsedMilliseconds
            $times += $responseTime
            
            $status = if ($response.StatusCode -eq 200) { "✓" } else { "✗" }
            Write-Host "  Request $i`: $status $($response.StatusCode) - $responseTime ms"
            
            # Small delay between requests
            Start-Sleep -Milliseconds 100
        }
        catch {
            Write-Host "  Request $i`: ✗ ERROR - $($_.Exception.Message)" -ForegroundColor Red
        }
    }
    
    if ($times.Count -gt 0) {
        $avgTime = ($times | Measure-Object -Average).Average
        $minTime = ($times | Measure-Object -Minimum).Minimum
        $maxTime = ($times | Measure-Object -Maximum).Maximum
        
        Write-Host "  Average: $([math]::Round($avgTime, 2)) ms" -ForegroundColor Green
        Write-Host "  Min: $minTime ms, Max: $maxTime ms"
    }
}

# Test cached endpoint (should be fast after first request)
Test-Endpoint -Url "http://localhost:8080/api/orders/user/83" -Description "Orders for User 83 (Cached)" -Iterations 3

# Test another endpoint to verify it's not cache-specific
Test-Endpoint -Url "http://localhost:8080/api/orders/count" -Description "Total Order Count" -Iterations 3

Write-Host "`nPerformance Tips:" -ForegroundColor Yellow
Write-Host "- First request may be slower (cache miss)"
Write-Host "- Subsequent requests should be faster (cache hit)"
Write-Host "- If times are consistently high (>2000ms), check database connectivity"
Write-Host "- Monitor application logs for HikariPool connection messages"

Write-Host "`nTo monitor in real-time:" -ForegroundColor Cyan
Write-Host "- Check application logs: tail -f nohup.out (Linux) or check console"
Write-Host "- Watch for 'cache miss' vs 'cache hit' messages"
Write-Host "- Look for any HikariPool timeout warnings"

Write-Host "`nMonitoring complete!" -ForegroundColor Green
