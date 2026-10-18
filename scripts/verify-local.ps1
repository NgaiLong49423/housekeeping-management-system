#Requires -Version 7.0
# Creates two clearly local demo bookings: one completed/paid and one cancelled. Does not delete history.
param([long]$ResumeBookingId = 0)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$accounts = Get-Content -Raw -LiteralPath (Join-Path $projectRoot '.runtime\demo-accounts.json') | ConvertFrom-Json
$base = 'http://127.0.0.1:8080'

function Get-Csrf($Page) {
    $token = [regex]::Match($Page.Content, 'name="_csrf"[^>]*value="([^"]+)"').Groups[1].Value
    if (-not $token) { throw 'CSRF token is missing from the rendered form.' }
    return $token
}
function Open-Session([string]$Role) {
    $account = $accounts | Where-Object { $_.role -eq $Role }
    $session = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
    $page = Invoke-WebRequest -Uri "$base/login" -WebSession $session
    $result = Invoke-WebRequest -Uri "$base/login" -Method Post -WebSession $session -Body @{
        email = $account.email; password = $account.password; _csrf = (Get-Csrf $page)
    }
    $expected = '/' + $Role.ToLower() + '/bookings'
    if ($result.BaseResponse.RequestMessage.RequestUri.AbsolutePath -ne $expected) { throw "Login failed for $Role." }
    Write-Host "$Role login: OK"
    return $session
}
function Send-Form($Session, [string]$Path, [hashtable]$Fields, $Page) {
    $Fields['_csrf'] = Get-Csrf $Page
    return Invoke-WebRequest -Uri ($base + $Path) -Method Post -WebSession $Session -Body $Fields
}
function Check([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

$customer = Open-Session 'Customer'
$staff = Open-Session 'Staff'
$admin = Open-Session 'Admin'
$settings = Get-Content -Raw -LiteralPath (Join-Path $projectRoot '.runtime\local-settings.json') | ConvertFrom-Json
Check ($settings.database -eq 'hsf302_housekeeping_local') 'This verification is restricted to the local demo database.'
$rows = sqlcmd -S $settings.sqlInstance -E -C -d $settings.database -b -W -h -1 -s '|' -Q @"
SET NOCOUNT ON;
SELECT TOP (1) s.staff_id,w.work_schedule_id,v.service_id
FROM Staff s JOIN Work_Schedule w ON w.staff_id=s.staff_id CROSS JOIN Services v
WHERE s.email=N'staff@gonnha.local' AND v.duration_minutes=60 ORDER BY w.work_schedule_id;
"@
if ($LASTEXITCODE -ne 0) { throw 'Cannot read the local demo schedule.' }
$ids = ($rows | Where-Object { $_ -match '^\d+\|\d+\|\d+$' } | Select-Object -First 1) -split '\|'
Check ($ids.Count -eq 3) 'Local demo staff, schedule and service are missing.'
$staffId, $scheduleId, $serviceId = $ids
$now = [DateTimeOffset]::UtcNow.ToOffset([TimeSpan]::FromHours(7))
$form = Invoke-WebRequest -Uri "$base/customer/bookings/new" -WebSession $customer
if ($ResumeBookingId -gt 0) {
    $created = Invoke-WebRequest -Uri "$base/customer/bookings/$ResumeBookingId" -WebSession $customer
} else {
    $created = Send-Form $customer '/customer/bookings' @{
        staffId = $staffId; scheduleId = $scheduleId; serviceIds = $serviceId
        startAt = $now.AddMinutes(-5).ToString('yyyy-MM-ddTHH:mm')
    } $form
}
$createdPath = $created.BaseResponse.RequestMessage.RequestUri.AbsolutePath
Check ($createdPath -match '^/customer/bookings/\d+$') 'Booking was not confirmed.'
$bookingId = ($createdPath -split '/')[-1]
Write-Host "Booking #${bookingId}: created CONFIRMED through MVC"

$job = Invoke-WebRequest -Uri "$base/staff/bookings/$bookingId" -WebSession $staff
$detailId = [regex]::Match($job.Content, '/staff/details/(\d+)/evidence').Groups[1].Value
Check ([bool]$detailId) 'Assigned staff cannot access the evidence form.'
$proofPath = Join-Path $projectRoot '.runtime\evidence-smoke.png'
Add-Type -AssemblyName System.Drawing
$bitmap = [System.Drawing.Bitmap]::new(64, 64)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
try {
    $graphics.Clear([System.Drawing.Color]::Teal)
    $bitmap.Save($proofPath, [System.Drawing.Imaging.ImageFormat]::Png)
} finally { $graphics.Dispose(); $bitmap.Dispose() }
$proof = Get-Item -LiteralPath $proofPath
$uploaded = Invoke-WebRequest -Uri "$base/staff/details/$detailId/evidence" -Method Post -WebSession $staff `
    -Form @{ image = $proof; _csrf = (Get-Csrf $job) }
Check ($uploaded.Content -match '/evidence/\d+') 'Evidence was not saved.'
$completed = Send-Form $staff "/staff/bookings/$bookingId/complete" @{} $uploaded
Check ($completed.Content -notmatch "/staff/bookings/$bookingId/complete") 'Booking did not complete.'
Write-Host 'Evidence upload and booking completion: OK'

$invoice = Invoke-WebRequest -Uri "$base/customer/bookings/$bookingId" -WebSession $customer
$chosen = Send-Form $customer "/customer/bookings/$bookingId/payment" @{ method = 'CASH' } $invoice
$review = Invoke-WebRequest -Uri "$base/admin/bookings/$bookingId" -WebSession $admin
$paid = Send-Form $admin "/admin/bookings/$bookingId/paid" @{ receiptVerified = 'true' } $review
Check ($paid.Content -notmatch 'name="receiptVerified"') 'Payment was not marked PAID.'
$feedbackPage = Invoke-WebRequest -Uri "$base/customer/bookings/$bookingId" -WebSession $customer
$feedback = Send-Form $customer "/customer/bookings/$bookingId/feedback" @{ rating = '5'; comment = 'Local setup verification: booking, evidence and payment succeeded.' } $feedbackPage
Check ($feedback.Content -notmatch 'name="rating"') 'Feedback was not saved.'
Write-Host 'Customer payment method, admin receipt verification and feedback: OK'

$people = Invoke-WebRequest -Uri "$base/customer/staff" -WebSession $customer
$favorite = Send-Form $customer "/customer/staff/$staffId/favorite" @{ favorite = 'true' } $people
Check ($favorite.Content -match 'aria-pressed="true"') 'Favorite was not saved.'

$form = Invoke-WebRequest -Uri "$base/customer/bookings/new" -WebSession $customer
$second = Send-Form $customer '/customer/bookings' @{
    staffId = $staffId; scheduleId = $scheduleId; serviceIds = $serviceId
    startAt = $now.AddHours(4).ToString('yyyy-MM-ddTHH:mm')
} $form
$secondPath = $second.BaseResponse.RequestMessage.RequestUri.AbsolutePath
Check ($secondPath -match '^/customer/bookings/\d+$') 'Future cancellation fixture was not created.'
$cancelId = ($secondPath -split '/')[-1]
$cancelled = Send-Form $customer "/customer/bookings/$cancelId/cancel" @{} $second
Check ($cancelled.Content -match 'status-pill cancelled') 'Future booking was not cancelled.'
Write-Host "Favorites and cancellation of booking #${cancelId}: OK"

$forbidden = Invoke-WebRequest -Uri "$base/admin/bookings" -WebSession $customer -SkipHttpErrorCheck
Check ($forbidden.StatusCode -eq 403) 'Customer must not access the admin portal.'
$noCsrf = Invoke-WebRequest -Uri "$base/customer/bookings/$cancelId/cancel" -Method Post -WebSession $customer -SkipHttpErrorCheck
Check ($noCsrf.StatusCode -eq 403) 'Mutation without CSRF must be rejected.'
Write-Host 'Role authorization and CSRF: OK'

$sqlCheck = "SET NOCOUNT ON; SELECT b.booking_id,b.booking_status,p.payment_status,(SELECT COUNT(*) FROM Feedback f WHERE f.booking_id=b.booking_id) AS feedback_count FROM Booking b LEFT JOIN Payment p ON p.booking_id=b.booking_id WHERE b.booking_id IN ($([long]$bookingId),$([long]$cancelId)); SELECT COUNT(*) AS evidence_count FROM Evidences WHERE booking_detail_id=$([long]$detailId);"
sqlcmd -S $settings.sqlInstance -E -C -d $settings.database -b -W -Q $sqlCheck
if ($LASTEXITCODE -ne 0) { throw 'Final SQL verification failed.' }
Write-Host 'Local HTTP-to-SQL Server workflow verification passed.'
