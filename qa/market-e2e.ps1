param([ValidateSet('prepare','exercise','advance-football','verify-football','verify-persistence')][string]$Phase='prepare')
$ErrorActionPreference='Stop'
$base='http://localhost:5174/api'
function Api($method,$path,$body=$null,$role='ADMIN') {
    $headers=if($role -eq 'ADMIN'){$adminHeaders}else{$participantHeaders}
    $args=@{Method=$method;Uri="$base$path";Headers=$headers;TimeoutSec=90}
    if($null -ne $body){$args.ContentType='application/json; charset=utf-8';$args.Body=ConvertTo-Json -InputObject $body -Depth 20 -Compress}
    $response=Invoke-RestMethod @args
    return $response
}
function Check($condition,$message) {if(-not $condition){throw $message}}
function State($id,$h,$a,$clock,$data) {
    $safeData=$data.Replace("'","''");$safeClock=$clock.Replace("'","''")
    $sql="UPDATE arena_events SET status='LIVE',home_score=$h,away_score=$a,clock='$safeClock',live_data='$safeData',version=version+1 WHERE id=$([long]$id) AND external_key LIKE 'qa-market-%';"
    $output=docker exec arenapredict-finalization-postgres psql -v ON_ERROR_STOP=1 -U bolao -d bolao_copa -c $sql
    Check ($LASTEXITCODE -eq 0 -and ($output -join '') -match 'UPDATE 1') 'Falha ao atualizar estado do fixture'
}
$admin=Invoke-RestMethod -Method Post -Uri "$base/auth/demo" -ContentType 'application/json' -Body '{"profile":"ADMIN"}'
$participant=Invoke-RestMethod -Method Post -Uri "$base/auth/demo" -ContentType 'application/json' -Body '{"profile":"PARTICIPANT"}'
$adminHeaders=@{Authorization="Bearer $($admin.token)"};$participantHeaders=@{Authorization="Bearer $($participant.token)"}
$file=Join-Path $PSScriptRoot 'market-e2e-fixtures.json'
if($Phase -eq 'prepare') {
    if(Test-Path $file) {
        $cleanup=@()
        foreach($old in (Get-Content $file -Raw | ConvertFrom-Json)) {
            $event=Api GET "/events/$($old.eventId)"
            Check ($event.externalKey.StartsWith('qa-market-')) 'Recusa de cancelamento fora dos fixtures QA'
            if($event.status -in @('FINISHED','CANCELLED')) { continue }
            $active=@(Api GET '/predictions' $null 'PARTICIPANT' | Where-Object {$_.eventId -eq $event.id -and $_.status -eq 'ACTIVE'})
            $before=(Api GET '/wallet' $null 'PARTICIPANT').balance
            $first=Api POST "/admin/events/$($event.id)/cancel"
            $second=Api POST "/admin/events/$($event.id)/cancel"
            $after=(Api GET '/wallet' $null 'PARTICIPANT').balance
            $expected=[long]($active | Measure-Object stakePoints -Sum).Sum
            Check ($after-$before -eq $expected -and $second.refundedPredictions -eq 0) "Reembolso divergente: esperado=$expected, antes=$before, depois=$after, repetição=$($second.refundedPredictions)"
            $cleanup+=@{eventId=$event.id;refund=$expected;first=$first;second=$second;balanceBefore=$before;balanceAfter=$after}
        }
        $cleanup | ConvertTo-Json -Depth 8 | Set-Content -Encoding utf8 (Join-Path $PSScriptRoot 'market-cancellation-evidence.json')
    }
    $all=Api GET '/events';$fixtures=@()
    foreach($key in @('football-open','nba-open','tennis-open','cs2-open','vct-open','lol-open','f1-open')) {
        $source=$all | Where-Object externalKey -eq "demo-$key" | Select-Object -First 1
        Check ($null -ne $source) "Fixture fonte ausente: $key"
        $body=@{externalKey="qa-market-$key-$([guid]::NewGuid().ToString('N'))";championshipId=$source.championshipId;homeCompetitorId=$source.homeCompetitor.id;awayCompetitorId=$source.awayCompetitor.id;title="QA mercados · $($source.title)";startsAt=[DateTime]::UtcNow.AddHours(1).ToString('o');predictionClosesAt=[DateTime]::UtcNow.AddMinutes(55).ToString('o');status='SCHEDULED';format=$source.format;bestOf=$source.bestOf;demo=$false;featured=$false;participants=@($source.participants | ForEach-Object {@{competitorId=$_.competitor.id;displayOrder=$_.displayOrder}})}
        $event=Api POST '/admin/events' $body
        $event=Api POST "/admin/events/$($event.id)/markets/generate"
        switch($key) {
            'football-open' {State $event.id 3 1 '20:00' '{"addedTimeMinutes":5,"corners":[5,4]}' ;$code='LIVE_RESULT'}
            'nba-open' {State $event.id 20 10 '08:00' '{"quarter":1,"quarterMinutes":12}';$code='WINNER'}
            'tennis-open' {State $event.id 0 0 '' '{"currentGames":[0,0]}';$code='MATCH_WINNER'}
            'cs2-open' {State $event.id 0 0 '' '{"currentRounds":[5,3]}';$code='SERIES_WINNER'}
            'vct-open' {State $event.id 0 0 '' '{"currentRounds":[5,3]}';$code='SERIES_WINNER'}
            'lol-open' {State $event.id 0 0 '' '{}';$code='SERIES_WINNER'}
            'f1-open' {$code='RACE_WINNER'}
        }
        $fixtures+=@{source=$key;eventId=$event.id;code=$code}
    }
    $fixtures | ConvertTo-Json -Depth 10 | Set-Content -Encoding utf8 $file
    $fixtures | Format-Table
    exit
}
$fixtures=Get-Content $file -Raw | ConvertFrom-Json
if($Phase -eq 'verify-persistence') {
    $expected=Get-Content (Join-Path $PSScriptRoot 'market-api-e2e.json') -Raw | ConvertFrom-Json
    $actual=@(Api GET '/predictions' $null 'PARTICIPANT')
    $ledger=@(Api GET '/wallet/transactions' $null 'PARTICIPANT')
    foreach($sport in $expected) {
        foreach($saved in $sport.predictions) {
            $persisted=$actual | Where-Object id -eq $saved.id
            Check ($persisted.status -eq $saved.status -and $persisted.multiplier -eq $saved.multiplier -and $persisted.potentialPoints -eq $saved.potentialPoints -and $persisted.rewardedPoints -eq $saved.rewardedPoints) "Persistência divergente no palpite $($saved.id)"
            if($persisted.status -eq 'WON') {
                $credits=@($ledger | Where-Object {$_.type -eq 'PREDICTION_WON' -and $_.referenceId -eq [string]$saved.id})
                Check ($credits.Count -eq 1) "Crédito repetido após reinício: $($saved.id)"
            }
        }
    }
    @{checkedAt=[DateTime]::UtcNow.ToString('o');sports=$expected.Count;predictions=($expected.predictions | Measure-Object).Count;persisted=$true;singleReward=$true} | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $PSScriptRoot 'market-persistence.json')
    Write-Output 'Persistência após reinício: snapshots, resultados e créditos únicos confirmados.'
    exit
}
if($Phase -eq 'advance-football') {
    $fixture=$fixtures | Where-Object source -eq 'football-open'
    State $fixture.eventId 3 1 '83:00' '{"addedTimeMinutes":5,"corners":[5,4]}'
    $event=Api GET "/events/$($fixture.eventId)"; $market=$event.markets | Where-Object templateCode -eq 'LIVE_RESULT'
    $bets=@(Api GET '/predictions' $null 'PARTICIPANT' | Where-Object eventId -eq $event.id)
    Check ($bets.Count -eq 2) 'Devem existir exatamente dois palpites, mesmo após repetir a confirmação'
    $ledger=@(Api GET '/wallet/transactions' $null 'PARTICIPANT')
    $debits=@($ledger | Where-Object {$_.type -eq 'PREDICTION_PLACED' -and $_.referenceId -in @($bets | ForEach-Object {[string]$_.id})})
    Check ($debits.Count -eq 2 -and ($debits | Measure-Object amount -Sum).Sum -eq -70) 'Débito duplicado ou divergente na confirmação pela UI'
    foreach($bet in $bets) {
        Check ($bet.multiplier -in @(1.16,23.45)) 'Snapshot original do futebol foi alterado'
    }
    $proof=@{eventId=$event.id;market=$market;predictions=$bets;debits=$debits;balance=(Api GET '/wallet' $null 'PARTICIPANT').balance}
    $proof | ConvertTo-Json -Depth 20 | Set-Content -Encoding utf8 (Join-Path $PSScriptRoot 'market-browser-before-settlement.json')
    $proof | ConvertTo-Json -Depth 5
    exit
}
function ResultData($source) {
    switch($source) {
        'football-open' {return @{firstHalfHome='1';firstHalfAway='0';cornersHome='7';cornersAway='4';cardsHome='3';cardsAway='2'}}
        'nba-open' {return @{firstHalfHome='55';firstHalfAway='50';quarter1Home='28';quarter1Away='25'}}
        'tennis-open' {return @{set1Home='6';set1Away='4';gamesHome='17';gamesAway='14';tieBreak='NO'}}
        'cs2-open' {return @{map1Home='13';map1Away='10';map1Half1Home='7';map1Half1Away='5';map1Half2Home='6';map1Half2Away='5';pistol1='HOME'}}
        'vct-open' {return @{map1Home='13';map1Away='10';pistol1='HOME'}}
        'lol-open' {return @{map1Home='1';map1Away='0';firstBlood='HOME';firstTower='AWAY';firstDragon='HOME';firstBaron='NONE';killsHome='20';killsAway='10'}}
    }
}
if($Phase -eq 'verify-football') {
    $proof=Get-Content (Join-Path $PSScriptRoot 'market-browser-before-settlement.json') -Raw | ConvertFrom-Json
    $bets=@(Api GET '/predictions' $null 'PARTICIPANT' | Where-Object eventId -eq $proof.eventId)
    $win=$bets | Where-Object status -eq 'WON';$lose=$bets | Where-Object status -eq 'LOST'
    Check ($win.Count -eq 1 -and $lose.Count -eq 1) 'Vencedor e perdedor não liquidados'
    Check ($win.rewardedPoints -eq $win.potentialPoints) 'Recompensa não usa snapshot'
    $after=(Api GET '/wallet' $null 'PARTICIPANT').balance
    Check ($after -eq $proof.balance+$win.potentialPoints) 'Saldo incorreto após liquidação pela UI'
    $result=@{homeScore=3;awayScore=1;finishEvent=$true;settleMarkets=$true;resultData=(ResultData 'football-open')}
    $null=Api PUT "/admin/events/$($proof.eventId)/result" $result
    $null=Api PUT "/admin/events/$($proof.eventId)/result" $result
    Check ((Api GET '/wallet' $null 'PARTICIPANT').balance -eq $after) 'Liquidação repetida duplicou recompensa'
    $transactions=@(Api GET '/wallet/transactions' $null 'PARTICIPANT' | Where-Object { $_.referenceId -eq [string]$win.id -and $_.type -eq 'PREDICTION_WON' })
    Check ($transactions.Count -eq 1) 'Mais de uma transação de recompensa'
    @{eventId=$proof.eventId;predictions=$bets;balanceBeforeSettlement=$proof.balance;balanceAfterSettlement=$after;rewardTransactions=$transactions;idempotent=$true} | ConvertTo-Json -Depth 20 | Set-Content -Encoding utf8 (Join-Path $PSScriptRoot 'market-browser-settlement.json')
    Write-Output 'Browser E2E: vencedor, perdedor, snapshot, saldo e idempotência confirmados.'
    exit
}
$evidence=@()
foreach($f in $fixtures | Where-Object source -ne 'football-open') {
    $event=Api GET "/events/$($f.eventId)";$market=$event.markets | Where-Object templateCode -eq $f.code
    Check ($market.availability.allowed) "Mercado bloqueado: $($f.source)"
    $winner=if($f.source -eq 'f1-open'){$market.options[0]}else{$market.options | Where-Object key -eq 'HOME'}
    $loser=if($f.source -eq 'f1-open'){$market.options[-1]}else{$market.options | Where-Object key -eq 'AWAY'}
    $existing=@(Api GET '/predictions' $null 'PARTICIPANT' | Where-Object eventId -eq $event.id)
    if($existing.Count -eq 2) {
        $win=$existing | Where-Object optionId -eq $winner.id;$lose=$existing | Where-Object optionId -eq $loser.id;$payload=$null
    } else {
        $payload=@{eventId=$event.id;marketId=$market.id;optionId=$winner.id;stakePoints=40;expectedMultiplier=$winner.multiplier;idempotencyKey=[guid]::NewGuid().ToString()}
        $win=Api POST '/predictions' $payload 'PARTICIPANT'
        $lose=Api POST '/predictions' @{eventId=$event.id;marketId=$market.id;optionId=$loser.id;stakePoints=30;expectedMultiplier=$loser.multiplier;idempotencyKey=[guid]::NewGuid().ToString()} 'PARTICIPANT'
    }
    $ledger=@(Api GET '/wallet/transactions' $null 'PARTICIPANT')
    $debits=@($ledger | Where-Object {$_.type -eq 'PREDICTION_PLACED' -and $_.referenceId -in @([string]$win.id,[string]$lose.id)} | Sort-Object id)
    Check ($debits.Count -eq 2 -and ($debits.amount | Measure-Object -Sum).Sum -eq -70) 'Débito incorreto'
    $firstDebit=$debits[0];$before=$firstDebit.balanceAfter-$firstDebit.amount
    switch($f.source) {
        'nba-open' {State $event.id 110 100 '00:30' '{"quarter":4,"quarterMinutes":12}'}
        'tennis-open' {State $event.id 1 0 '' '{"currentGames":[5,3]}'}
        'cs2-open' {State $event.id 1 0 '' '{"currentRounds":[12,3]}'}
        'vct-open' {State $event.id 1 0 '' '{"currentRounds":[12,3]}'}
        'lol-open' {State $event.id 1 0 '' '{}'}
        'f1-open' {State $event.id 0 0 '' '{}'}
    }
    $changed=Api GET "/events/$($event.id)";$newMarket=$changed.markets | Where-Object id -eq $market.id
    $newOption=$newMarket.options | Where-Object id -eq $winner.id
    if($f.source -ne 'f1-open'){Check ($newOption.multiplier -lt $win.multiplier) 'Líder não ficou mais provável'}
    else {Check ($newOption.multiplier -eq $win.multiplier) 'Corrida inventou multiplicador dinâmico'}
    $saved=Api GET '/predictions' $null 'PARTICIPANT' | Where-Object id -eq $win.id
    Check ($saved.multiplier -eq $win.multiplier) 'Snapshot mudou'
    if($payload){$replay=Api POST '/predictions' $payload 'PARTICIPANT';Check ($replay.id -eq $win.id) 'Retry duplicou palpite'}
    if($f.source -eq 'f1-open') {
        $positions=@($event.participants | Sort-Object displayOrder | ForEach-Object -Begin {$i=0} -Process {@{competitorId=$_.competitor.id;displayOrder=$i;position=++$i}})
        $result=@{participants=$positions;finishEvent=$true;settleMarkets=$true;resultData=@{fastestLap=$winner.key;safetyCar='YES';driverClassified='YES'}}
        $path="/admin/events/$($event.id)/classification"
    } else {
        $h=if($f.source -eq 'nba-open'){115}else{2};$a=if($f.source -eq 'nba-open'){101}else{1}
        $result=@{homeScore=$h;awayScore=$a;finishEvent=$true;settleMarkets=$true;resultData=(ResultData $f.source)}
        $path="/admin/events/$($event.id)/result"
    }
    $null=Api PUT $path $result
    $saved=@(Api GET '/predictions' $null 'PARTICIPANT' | Where-Object eventId -eq $event.id)
    Check (($saved | Where-Object id -eq $win.id).status -eq 'WON') 'Vencedor incorreto'
    Check (($saved | Where-Object id -eq $lose.id).status -eq 'LOST') 'Perdedor incorreto'
    $after=(Api GET '/wallet' $null 'PARTICIPANT').balance
    $ledger=@(Api GET '/wallet/transactions' $null 'PARTICIPANT')
    $otherCredits=($ledger | Where-Object {$_.id -ge $firstDebit.id -and $_.referenceId -notin @([string]$win.id,[string]$lose.id)} | Measure-Object amount -Sum).Sum
    Check ($after -eq $before-70+$win.potentialPoints+$otherCredits) 'Saldo final incorreto'
    $null=Api PUT $path $result;$null=Api PUT $path $result
    Check ((Api GET '/wallet' $null 'PARTICIPANT').balance -eq $after) 'Recompensa duplicada'
    $credits=@(Api GET '/wallet/transactions' $null 'PARTICIPANT' | Where-Object {$_.referenceId -eq [string]$win.id -and $_.type -eq 'PREDICTION_WON'})
    Check ($credits.Count -eq 1) 'Crédito duplicado no ledger'
    $evidence+=@{sport=$f.source;eventId=$event.id;beforeMultiplier=$win.multiplier;afterMultiplier=$newOption.multiplier;predictions=$saved;balanceBefore=$before;balanceAfter=$after;otherCredits=$otherCredits;singleReward=$true;idempotent=$true}
    $evidence | ConvertTo-Json -Depth 20 | Set-Content -Encoding utf8 (Join-Path $PSScriptRoot 'market-api-e2e.json')
    Write-Output "$($f.source): snapshot, débito, resultado, saldo e idempotência OK"
}
