# PowerShell script to send test data to monitoring server
# Usage: .\send_test_data.ps1

$data = @(
    "0.0,25.0,0.0",
    "1.0,45.0,0.0",
    "2.0,65.0,10.0",
    "3.0,75.0,20.0",
    "4.0,85.0,40.0",
    "5.0,90.0,60.0",
    "6.0,85.0,70.0",
    "7.0,75.0,65.0",
    "8.0,65.0,50.0",
    "9.0,55.0,30.0",
    "10.0,45.0,10.0"
)

Write-Host "================================" -ForegroundColor Cyan
Write-Host "  Monitoring Server Data Sender" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

try {
    Write-Host "Connecting to localhost:9999..." -ForegroundColor Yellow
    $client = New-Object System.Net.Sockets.TcpClient("localhost", 9999)
    $stream = $client.GetStream()
    $writer = New-Object System.IO.StreamWriter($stream)
    $writer.AutoFlush = $true

    Write-Host "Connected successfully!" -ForegroundColor Green
    Write-Host ""
    Write-Host "Sending $($data.Length) data points..." -ForegroundColor Yellow
    Write-Host ""

    for ($i = 0; $i -lt $data.Length; $i++) {
        $line = $data[$i]
        $progress = [math]::Round(($i + 1) / $data.Length * 100)
        Write-Host "[$($i+1)/$($data.Length)] ($progress%) " -NoNewline -ForegroundColor Cyan
        Write-Host "$line" -ForegroundColor White

        $writer.WriteLine($line)
        $writer.Flush()
        Start-Sleep -Milliseconds 500
    }

    Write-Host ""
    Write-Host "All data sent successfully!" -ForegroundColor Green
    Write-Host "Waiting for server to process the last data point..." -ForegroundColor Yellow

    Start-Sleep -Seconds 2

    Write-Host "Closing connection..." -ForegroundColor Yellow
    $writer.Close()
    $stream.Close()
    $client.Close()

    Write-Host ""
    Write-Host "================================" -ForegroundColor Green
    Write-Host "  Done! Check the graph." -ForegroundColor Green
    Write-Host "================================" -ForegroundColor Green

} catch {
    Write-Host ""
    Write-Host "Error: $_" -ForegroundColor Red
    Write-Host "Make sure the server is running on port 9999." -ForegroundColor Red
}

Write-Host ""

