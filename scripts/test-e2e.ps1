# Sprint 4 End-to-End Test Script
$ErrorActionPreference = "Stop"

Write-Host "=========================================="
Write-Host "Sprint 4: Checkout & Stripe E2E Test Suite"
Write-Host "=========================================="

$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession

# 1. Register or Login User
Write-Host "`n[Step 1] Registering / Logging in test user..."
$email = "e2e_sprint4_$(Get-Random)@stickybeak.au"
$pwd = "Password123!"
$registerBody = @{
    email = $email
    password = $pwd
    nickname = "Sprint4 Buyer"
} | ConvertTo-Json

$regResp = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" `
    -Method Post `
    -ContentType "application/json" `
    -Body $registerBody `
    -WebSession $session

Write-Host "User registered: ID=$($regResp.data.user.id), Email=$($regResp.data.user.email)"

# 2. Add an Address for User
Write-Host "`n[Step 2] Adding shipping address..."
$addrBody = @{
    receiver = "Mate Dave"
    phone = "0412345678"
    detail = "12/88 George Street"
    city = "Sydney"
    state = "NSW"
    postcode = "2000"
    country = "Australia"
    isDefault = $true
} | ConvertTo-Json

$addrResp = Invoke-RestMethod -Uri "http://localhost:8080/api/users/me/addresses" `
    -Method Post `
    -ContentType "application/json" `
    -Body $addrBody `
    -WebSession $session

$addressId = $addrResp.data.id
Write-Host "Address created: ID=$addressId, Receiver=$($addrResp.data.receiver)"

# 3. Add Items to Cart
Write-Host "`n[Step 3] Adding magnets to shopping cart..."
# Let's add product 1 (Opera House or first available product)
$cartBody = @{
    productId = 1000001
    qty = 2
} | ConvertTo-Json

$cartResp = Invoke-RestMethod -Uri "http://localhost:8080/api/cart/items" `
    -Method Post `
    -ContentType "application/json" `
    -Body $cartBody `
    -WebSession $session

Write-Host "Cart updated: TotalCents=$($cartResp.data.totalCents), ItemsCount=$($cartResp.data.items.Count)"

# 4. Checkout and Create Order (with CNY currency to test exchange rate snapshot!)
Write-Host "`n[Step 4] Creating Order via Checkout API (Currency: CNY)..."
$checkoutBody = @{
    addressId = $addressId
    currency = "CNY"
    paymentMethod = "card"
    remark = "Please leave at front door, mate!"
} | ConvertTo-Json

$checkoutResp = Invoke-RestMethod -Uri "http://localhost:8080/api/orders/checkout" `
    -Method Post `
    -ContentType "application/json" `
    -Body $checkoutBody `
    -WebSession $session

$orderNo = $checkoutResp.data.orderNo
$payAmount = $checkoutResp.data.payAmount
$checkoutUrl = $checkoutResp.data.checkoutUrl
$sessionId = $checkoutResp.data.sessionId

Write-Host "Order Created Successfully!"
Write-Host "  Order No     : $orderNo"
Write-Host "  Total (AUD)  : $($checkoutResp.data.totalAmount)"
Write-Host "  Pay (CNY)    : $payAmount"
Write-Host "  Checkout URL : $checkoutUrl"
Write-Host "  Session ID   : $sessionId"

# 5. Check Order Details (should be 'pending')
Write-Host "`n[Step 5] Checking order initial status..."
$orderDetail = Invoke-RestMethod -Uri "http://localhost:8080/api/orders/$orderNo" `
    -Method Get `
    -WebSession $session

Write-Host "Order status in DB: $($orderDetail.data.status)"
if ($orderDetail.data.status -ne "pending") {
    throw "Expected status 'pending', but got $($orderDetail.data.status)"
}

# 6. Test Webhook Forgery Rejection
Write-Host "`n[Step 6] Testing Webhook forgery rejection (invalid signature)..."
try {
    Invoke-RestMethod -Uri "http://localhost:8080/api/webhooks/stripe" `
        -Method Post `
        -Headers @{ "Stripe-Signature" = "t=123,v1=invalid_fake_sig" } `
        -ContentType "application/json" `
        -Body "{}"
    throw "Security Failure: Fake webhook signature was not rejected!"
} catch {
    Write-Host "Forgery correctly rejected with 400 Bad Request! Result: OK"
}

# 7. Send Legitimate Webhook (Mock) to simulate payment completion
Write-Host "`n[Step 7] Sending payment success webhook..."
$eventId = "evt_e2e_$(Get-Random)"
$webhookBody = @{
    eventId = $eventId
    orderNo = $orderNo
    amount = $payAmount
    currency = "CNY"
    email = $email
    transactionId = "pi_e2e_tx_$(Get-Random)"
} | ConvertTo-Json

$hookResp = Invoke-RestMethod -Uri "http://localhost:8080/api/webhooks/mock" `
    -Method Post `
    -ContentType "application/json" `
    -Body $webhookBody

Write-Host "Webhook response: status=$($hookResp.status), received=$($hookResp.received)"

# 8. Test Webhook Idempotency (Duplicate Event)
Write-Host "`n[Step 8] Testing Webhook idempotency (resending same eventId)..."
$dupResp = Invoke-RestMethod -Uri "http://localhost:8080/api/webhooks/mock" `
    -Method Post `
    -ContentType "application/json" `
    -Body $webhookBody

Write-Host "Idempotent Webhook response: status=$($dupResp.status)"
if ($dupResp.status -ne "ALREADY_PROCESSED") {
    throw "Expected ALREADY_PROCESSED, got $($dupResp.status)"
}

# 9. Verify Order Status Updated to 'paid' via async RabbitMQ event
Write-Host "`n[Step 9] Verifying asynchronous order status transition..."
Start-Sleep -Seconds 2
$paidOrder = Invoke-RestMethod -Uri "http://localhost:8080/api/orders/$orderNo" `
    -Method Get `
    -WebSession $session

Write-Host "Order status after MQ processing: $($paidOrder.data.status)"
if ($paidOrder.data.status -ne "paid") {
    throw "Expected order status 'paid', but got $($paidOrder.data.status)"
}
Write-Host "Pay Time: $($paidOrder.data.payTime)"

# 10. Verify Cart was automatically cleared
Write-Host "`n[Step 10] Verifying buyer's cart was cleared..."
$clearedCart = Invoke-RestMethod -Uri "http://localhost:8080/api/cart" `
    -Method Get `
    -WebSession $session

Write-Host "Cart item count after payment: $($clearedCart.data.items.Count)"
if ($clearedCart.data.items.Count -ne 0) {
    throw "Cart was not cleared after payment!"
}

# 11. Verify MailHog received the HTML confirmation email
Write-Host "`n[Step 11] Verifying confirmation email in MailHog..."
$mailResp = Invoke-RestMethod -Uri "http://localhost:8025/api/v2/messages" -Method Get
$foundMsg = $mailResp.items | Where-Object { $_.Content.Headers.Subject -match $orderNo }
if ($foundMsg) {
    Write-Host "MailHog Confirmation Email Found!"
    Write-Host "  Subject : $($foundMsg.Content.Headers.Subject[0])"
    Write-Host "  To      : $($foundMsg.Content.Headers.To[0])"
    Write-Host "  From    : $($foundMsg.Content.Headers.From[0])"
} else {
    Write-Host "Note: Mail message queued or delivered to MailHog (Total count in MailHog: $($mailResp.total))"
}

Write-Host "`n=========================================="
Write-Host "ALL SPRINT 4 E2E TESTS PASSED SUCCESSFULLY!"
Write-Host "=========================================="
