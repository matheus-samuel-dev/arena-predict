param([string]$BaseUrl = 'http://127.0.0.1:5176/api')
$ErrorActionPreference = 'Stop'
if (-not ([uri]$BaseUrl).IsLoopback) { throw 'Este roteiro altera a rodada compartilhada e aceita somente um ambiente local de QA.' }
$requests = [System.Collections.Generic.List[object]]::new()
$sessions = @{}
function Check($condition, [string]$message) { if (-not $condition) { throw $message } }
function Api([string]$method, [string]$path, $body = $null, [string]$profile = 'PARTICIPANT', [int]$expected = 200) {
    $headers = @{}
    if ($sessions.ContainsKey($profile)) { $headers.Authorization = "Bearer $($sessions[$profile].token)" }
    $params = @{ Method=$method; Uri="$BaseUrl$path"; Headers=$headers; ContentType='application/json'; TimeoutSec=40; SkipHttpErrorCheck=$true }
    if ($null -ne $body) { $params.Body = $body | ConvertTo-Json -Depth 8 -Compress }
    $response = Invoke-WebRequest @params
    $requests.Add(@{ method=$method; path=$path; status=[int]$response.StatusCode; expected=$expected })
    Check ($response.StatusCode -eq $expected) "HTTP inesperado: $method $path retornou $($response.StatusCode), esperado $expected."
    if ($response.Content) { return $response.Content | ConvertFrom-Json }
}
function Place($event) {
    $market = $event.markets | Where-Object templateCode -eq 'SERIES_SCORE' | Select-Object -First 1
    $option = $market.options | Where-Object key -eq '2_1' | Select-Object -First 1
    return Api 'POST' '/predictions' @{eventId=$event.id; marketId=$market.id; optionId=$option.id; stakePoints=10; idempotencyKey="demo-qa-$([guid]::NewGuid().ToString('N'))"} 'PARTICIPANT' 201
}

foreach ($profile in @('PARTICIPANT','ADMIN')) {
    $sessions[$profile] = Api 'POST' '/auth/demo' @{profile=$profile} $profile
    Check ($sessions[$profile].demoProfile -eq $profile) 'Perfil Demo não retornado pela autenticação.'
}
$initial = Api 'GET' '/demo/scenario'
$scenario = Api 'POST' '/demo/reset' @{expectedGeneration=$initial.generation} 'ADMIN'
$officialBefore = @(Api 'GET' '/events' | Where-Object externalProvider)
$id = $scenario.event.id
Check ($scenario.event.demoManaged -and $scenario.event.demo -and -not $scenario.event.externalProvider) 'Escopo do evento Demo inválido.'
$null = Api 'GET' '/admin/dashboard' $null 'ADMIN' 403
$null = Api 'POST' "/demo/events/$id/start" $null 'PARTICIPANT' 403
$null = Api 'POST' '/demo/reset' @{expectedGeneration=$scenario.generation} 'PARTICIPANT' 403
$null = Api 'POST' "/demo/events/$id/result" @{homeScore=2; awayScore=2} 'ADMIN' 422
$afterInvalid = Api 'GET' '/demo/scenario'
Check ($afterInvalid.event.status -eq 'OPEN_FOR_PREDICTIONS' -and $null -eq $afterInvalid.event.homeScore) 'Resultado inválido persistiu alteração parcial.'
$prediction = Place $scenario.event
$reloaded = Api 'GET' '/demo/scenario'
Check (@($reloaded.predictions | Where-Object id -eq $prediction.id).Count -eq 1) 'Palpite não persistiu após nova requisição.'
$running = Api 'POST' "/demo/events/$id/start" $null 'ADMIN'
Check ($running.event.status -eq 'LIVE' -and $null -eq $running.event.homeScore) 'Estado LIVE ou placar ausente incorreto.'
$null = Api 'POST' "/predictions/$($prediction.id)/cancel" $null 'PARTICIPANT' 422
$closedMarket = $running.event.markets | Where-Object templateCode -eq 'SERIES_SCORE' | Select-Object -First 1
$null = Api 'POST' '/predictions' @{eventId=$id; marketId=$closedMarket.id; optionId=$closedMarket.options[0].id; stakePoints=10; idempotencyKey="closed-$([guid]::NewGuid().ToString('N'))"} 'PARTICIPANT' 422
$finished = Api 'POST' "/demo/events/$id/result" @{homeScore=2; awayScore=1} 'ADMIN'
$participant = Api 'GET' '/demo/scenario'
$won = $participant.predictions | Where-Object id -eq $prediction.id | Select-Object -First 1
Check ($finished.event.status -eq 'FINISHED' -and $finished.event.resultProcessedAt) 'Resultado final não foi processado.'
Check ($won.status -eq 'WON' -and $won.rewardedPoints -eq $prediction.potentialPoints) 'Pontuação persistida incorreta.'
$ranking = $participant.ranking | Where-Object currentUser | Select-Object -First 1
Check ($ranking.points -ge $won.rewardedPoints -and $ranking.position -eq 1) 'Ranking não refletiu o acerto.'
$wallet = Api 'GET' '/wallet'
$ledger = Api 'GET' '/wallet/transactions'
$null = Api 'POST' "/demo/events/$id/result" @{homeScore=2; awayScore=1} 'ADMIN'
Check ((Api 'GET' '/wallet').balance -eq $wallet.balance) 'Resultado repetido creditou pontos novamente.'
Check (((Api 'GET' '/wallet/transactions') | ConvertTo-Json -Depth 6 -Compress) -eq ($ledger | ConvertTo-Json -Depth 6 -Compress)) 'Resultado repetido modificou ledger.'
$null = Api 'POST' "/demo/events/$id/result" @{homeScore=2; awayScore=0} 'ADMIN' 409
$next = Api 'POST' '/demo/reset' @{expectedGeneration=$scenario.generation} 'ADMIN'
$retry = Api 'POST' '/demo/reset' @{expectedGeneration=$scenario.generation} 'ADMIN'
Check ($next.event.id -ne $id -and $retry.event.id -eq $next.event.id) 'Reset não foi idempotente.'
$null = Api 'POST' "/demo/events/$id/result" @{homeScore=2; awayScore=1} 'ADMIN' 409
$newPrediction = Place $next.event
$final = Api 'POST' '/demo/reset' @{expectedGeneration=$next.generation} 'ADMIN'
$archivedPrediction = Api 'GET' '/predictions' | Where-Object id -eq $newPrediction.id | Select-Object -First 1
Check ($archivedPrediction.status -eq 'REFUNDED') 'Reset de rodada aberta não reembolsou o palpite.'
$officialAfter = @(Api 'GET' '/events' | Where-Object externalProvider)
Check (($officialBefore | ConvertTo-Json -Depth 20 -Compress) -eq ($officialAfter | ConvertTo-Json -Depth 20 -Compress)) 'Reset modificou dados externos.'
$report = [ordered]@{
    checkedAt=[DateTime]::UtcNow.ToString('o'); baseUrl=$BaseUrl; result='PASS';
    completedEventId=$id; predictionId=$prediction.id; score='2-1'; reward=$won.rewardedPoints;
    rankingPosition=$ranking.position; newRoundEventId=$final.event.id; refundedPredictionId=$newPrediction.id;
    officialEventsCompared=$officialBefore.Count;
    realProviderCredentialValidated=$false; requests=$requests
}
$report | ConvertTo-Json -Depth 8 | Set-Content -Encoding utf8 (Join-Path $PSScriptRoot 'demo-api-e2e.json')
Write-Output "PASS: palpite persistido, LIVE, resultado, ranking, idempotência, autorização e reset; $($requests.Count) requisições."
