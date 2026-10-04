// UI end-to-end against the local PostgreSQL QA stack. Changes only the shared Demo round.
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const base = process.env.ARENA_QA_URL || 'http://localhost:5176';
assert(['localhost', '127.0.0.1'].includes(new URL(base).hostname), 'Demo QA requires loopback');
const report = { at: new Date().toISOString(), base, steps: [], consoleErrors: [], pageErrors: [], apiErrors: [] };
const save = () => fs.writeFileSync(path.join(__dirname, 'consolidation-product-flow.json'), JSON.stringify(report, null, 2));
(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe' });
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, locale: 'pt-BR' });
    page.on('console', item => { if (item.type() === 'error') report.consoleErrors.push(item.text()); });
    page.on('pageerror', error => report.pageErrors.push(error.message));
    page.on('response', response => { if (response.url().startsWith(base + '/api/') && response.status() >= 400) report.apiErrors.push({ path: new URL(response.url()).pathname, status: response.status() }); });
    const ready = async () => { await page.locator('#main-content h1').waitFor(); };
    const step = name => { report.steps.push({ name, passed: true }); save(); console.log(name); };
    const snapshot = async () => {
      const response = page.waitForResponse(item => item.url() === base + '/api/demo/scenario' && item.status() === 200);
      await page.goto(base + '/demo', { waitUntil: 'domcontentloaded' }); await ready();
      return await (await response).json();
    };
    await page.goto(base + '/login');
    await page.getByRole('button', { name: 'Entrar na demonstração como administrador' }).click();
    await page.waitForURL(base + '/admin'); await ready(); step('Admin Demo opens full dashboard');
    await snapshot();
    await page.getByRole('button', { name: 'Começar nova rodada', exact: true }).click();
    const resetResponse = page.waitForResponse(item => item.url() === base + '/api/demo/reset' && item.status() === 200);
    await page.getByRole('button', { name: 'Confirmar nova rodada', exact: true }).click();
    const fresh = await (await resetResponse).json(); step('Reset only controlled Demo round');
    await page.getByRole('button', { name: 'Participante Demo', exact: true }).click();
    await page.getByRole('heading', { name: 'Participante Demo', exact: true }).waitFor();
    await page.goto(base + '/app'); await ready(); step('Participant dashboard');
    await page.goto(base + '/events'); await ready();
    const detailLink = page.locator('a[href="/events/' + fresh.event.id + '"]').first();
    await detailLink.click(); await page.waitForURL(base + '/events/' + fresh.event.id); await ready(); step('Events to full detail');
    const scoreMarket = fresh.event.markets.find(market => market.templateCode === 'SERIES_SCORE');
    await page.getByRole('group', { name: 'Selecionar categoria de mercado' }).getByRole('button').filter({ hasText: scoreMarket.category }).click();
    const option = scoreMarket.options.find(item => item.key === '2_1');
    const optionButton = page.getByRole('button').filter({ hasText: option.label });
    await optionButton.first().click();
    const dialog = page.getByRole('dialog', { name: 'Confirmar palpite', exact: true });
    await dialog.waitFor(); await dialog.getByRole('spinbutton').fill('10');
    const predictionResponse = page.waitForResponse(item => item.url() === base + '/api/predictions' && item.request().method() === 'POST' && item.status() === 201);
    await dialog.getByRole('button', { name: 'Confirmar palpite', exact: true }).click();
    const prediction = await (await predictionResponse).json();
    await page.getByText('Sua leitura está registrada', { exact: true }).waitFor();
    assert.equal(prediction.eventId, fresh.event.id); assert.equal(prediction.stakePoints, 10);
    report.prediction = { id: prediction.id, eventId: prediction.eventId, multiplier: prediction.multiplierSnapshot ?? prediction.multiplier, potentialPoints: prediction.potentialPoints };
    await page.screenshot({ path: path.join(__dirname, 'consolidation-prediction-receipt.png') });
    await page.getByRole('button', { name: 'Continuar na Arena', exact: true }).click(); step('Prediction submitted from ordinary event detail');
    await page.goto(base + '/predictions'); await ready();
    await page.getByText(option.label, { exact: true }).first().waitFor(); step('Persisted prediction in My predictions');
    await snapshot();
    await page.getByRole('button', { name: 'Administrador Demo', exact: true }).click();
    await page.getByRole('heading', { name: 'Administrador Demo', exact: true }).waitFor();
    await page.getByRole('button', { name: 'Iniciar partida Demo', exact: true }).click();
    await page.getByRole('button', { name: 'Confirmar início', exact: true }).click();
    await page.getByRole('button', { name: 'Simular resultado', exact: true }).waitFor(); step('Controlled event started');
    await page.getByRole('button', { name: 'Simular resultado', exact: true }).click();
    await page.getByLabel('Placar final simulado · BO3').selectOption('2:1');
    await page.getByRole('button', { name: 'Confirmar resultado', exact: true }).click();
    await page.getByRole('heading', { name: 'Rodada concluída' }).waitFor(); step('Demo result uses shared settlement');
    await page.getByRole('button', { name: 'Participante Demo', exact: true }).click();
    await page.getByRole('heading', { name: 'Participante Demo', exact: true }).waitFor();
    const settled = await snapshot();
    const own = settled.predictions.find(item => item.id === prediction.id);
    assert.equal(own.status, 'WON'); assert.equal(own.rewardedPoints, prediction.potentialPoints);
    assert(settled.ranking.some(item => item.currentUser && item.points >= own.rewardedPoints));
    report.settlement = { status: own.status, rewardedPoints: own.rewardedPoints, processedAt: settled.event.resultProcessedAt };
    step('Persisted reward equals saved potential and ranking reflects activity');
    for (const route of ['/rankings', '/statistics', '/challenges', '/achievements', '/pools', '/leagues', '/community', '/account']) {
      await page.goto(base + route); await ready(); step('Participant module: ' + route);
    }
    assert.equal(report.consoleErrors.length + report.pageErrors.length + report.apiErrors.length, 0);
    report.result = 'PASS'; save();
  } catch (error) { report.result = 'FAIL'; report.failure = error.message; save(); throw error; }
  finally { await browser.close(); }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
