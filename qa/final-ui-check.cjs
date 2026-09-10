const { chromium, request } = require('playwright');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const base = process.env.ARENA_QA_URL || 'http://localhost:5174';
const report = { at: new Date().toISOString(), checks: [], errors: [] };
const save = () => fs.writeFileSync(path.join(__dirname, 'final-ui-check.json'), JSON.stringify(report, null, 2));

(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe' });
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, locale: 'pt-BR' });
    page.on('pageerror', e => report.errors.push(e.message));
    page.on('console', m => { if (m.type() === 'error') report.errors.push({ message: m.text(), resource: m.location().url }); });
    page.on('response', r => { if (r.url().startsWith(base + '/api/') && r.status() >= 400) report.errors.push(r.status() + ' ' + r.url()); });
    const auth = await login(page, 'participante');
    const api = await request.newContext({ baseURL: base, extraHTTPHeaders: { Authorization: 'Bearer ' + auth.token } });
    const events = await (await api.get('/api/events')).json();
    const cancelled = events.find(e => e.status === 'CANCELLED');
    const awaiting = events.find(e => e.title === 'Rodada demonstrativa · aguardando liquidação');
    assert.equal(cancelled.status, 'CANCELLED'); assert.equal(cancelled.availableMarketCount, 0);
    assert.equal(awaiting.status, 'FINISHED'); assert.equal(awaiting.availableMarketCount, 0);
    assert.ok(events.slice(0,2).every(e => e.status === 'LIVE'));
    report.checks.push({ name: 'Demo terminal states and live-first ordering', passed: true });
    const refunded = (await (await api.get('/api/predictions')).json()).find(p => p.id === 111);
    assert.equal(refunded.status, 'REFUNDED');
    assert.equal((await (await api.get('/api/wallet')).json()).balance, 7158);
    report.checks.push({ name: 'Live refund persists after final rebuild', passed: true });

    for (const theme of ['light', 'dark']) {
      await page.goto(base + '/account');
      await page.getByRole('tab', { name: 'Preferências', exact: true }).click();
      await page.getByRole('button', { name: theme === 'light' ? 'Claro' : 'Escuro', exact: true }).click();
      await page.goto(base + '/events');
      const select = page.getByRole('combobox', { name: 'Filtrar modalidade' });
      await select.waitFor();
      await select.focus();
      const style = await select.evaluate(e => ({ background: getComputedStyle(e).backgroundColor, color: getComputedStyle(e).color, option: getComputedStyle(e.options[0]).backgroundColor, fontSize: getComputedStyle(e).fontSize, focusShadow: getComputedStyle(e).boxShadow, theme: document.documentElement.dataset.theme }));
      assert.equal(style.theme, theme); assert.notEqual(style.focusShadow, 'none');
      assert.ok(parseFloat(style.fontSize) >= 13);
      assert.equal(style.background, style.option);
      await select.press('Alt+ArrowDown'); await select.press('ArrowDown'); await select.press('Enter');
      await select.selectOption('BASKETBALL');
      await page.waitForURL(/sport=BASKETBALL/);
      await page.waitForFunction(() => {
        const chips = Array.from(document.querySelectorAll('.event-card .sport-chip'));
        return chips.length > 0 && chips.every(e => e.textContent === 'Basquete');
      });
      assert.ok((await page.locator('.event-card .sport-chip').allTextContents()).every(t => t === 'Basquete'));
      report.checks.push({ name: 'Theme-aware native select and sport filter', theme, style, passed: true });
      await page.screenshot({ path: path.join(__dirname, `final-filter-${theme}.png`) });
    }
    await page.goto(base + '/account');
    await page.getByRole('tab', { name: 'Preferências', exact: true }).click();
    await page.getByRole('button', { name: 'Claro', exact: true }).click();
    await page.goto(base + '/events/3');
    await page.getByRole('button', { name: /^Pontos/ }).click();
    const option = page.locator('.market-block button:enabled').first();
    await option.focus(); await page.keyboard.press('Enter');
    const dialog = page.getByRole('dialog'); await dialog.waitFor();
    const stake = page.locator('#prediction-stake'); await stake.focus();
    assert.equal(await stake.evaluate(e => getComputedStyle(e).backgroundColor), 'rgb(255, 255, 255)');
    await stake.fill('8000');
    assert.equal(await page.getByRole('button', { name: 'Confirmar palpite', exact: true }).isDisabled(), true);
    assert.match(await page.locator('#prediction-stake-error').innerText(), /insuficiente/i);
    await stake.fill('10');
    await page.setViewportSize({ width: 360, height: 800 });
    await page.screenshot({ path: path.join(__dirname, 'final-slip-360.png') });
    await page.keyboard.press('Escape'); await dialog.waitFor({ state: 'hidden' });
    assert.equal(await option.evaluate(e => document.activeElement === e), true);
    report.checks.push({ name: 'Slip focus, insufficient balance, Escape restores option', passed: true });

    const admin = await browser.newPage({ viewport: { width: 360, height: 800 }, locale: 'pt-BR' });
    admin.on('pageerror', e => report.errors.push(e.message));
    await login(admin, 'administrador');
    await admin.goto(base + '/admin/results');
    const searched = admin.waitForResponse(r => r.url().includes('/api/admin/events?') && r.url().includes('search='));
    await admin.getByRole('textbox', { name: 'Buscar em Resultados' }).fill('Validação E2E');
    await searched;
    const opener = admin.getByRole('row').filter({ has: admin.getByText('Validação E2E · Automobilismo', { exact: true }) }).getByRole('button');
    await opener.click(); await admin.getByRole('dialog').waitFor();
    const position = admin.locator('[id^=classification-position]').first(); await position.focus();
    const positionStyle = await position.evaluate(e => ({ inputBackground: getComputedStyle(e).backgroundColor, rowBackground: getComputedStyle(e.closest('.admin-builder__row')).backgroundColor }));
    assert.equal(positionStyle.inputBackground, 'rgb(255, 255, 255)');
    assert.equal(positionStyle.rowBackground, 'rgb(238, 241, 248)');
    await admin.screenshot({ path: path.join(__dirname, 'final-f1-form-360.png') });
    await admin.keyboard.press('Escape'); await admin.getByRole('dialog').waitFor({ state: 'hidden' });
    assert.equal(await opener.evaluate(e => document.activeElement === e), true);
    report.checks.push({ name: 'Admin F1 theme surfaces and Escape focus restoration', ...positionStyle, passed: true });
    await admin.goto(base + '/admin/markets');
    await admin.getByRole('textbox', { name: 'Buscar em Mercados' }).fill('Resultado ao vivo');
    await admin.getByText('Futebol · Principais · Regra da modalidade', { exact: true }).first().waitFor();
    report.checks.push({ name: 'Admin displays actual market sport', passed: true });
    report.passed = report.errors.length === 0; save(); console.log(JSON.stringify(report, null, 2));
  } catch (e) { report.fatal = String(e.stack || e); report.passed = false; save(); throw e; }
  finally { await browser.close(); }

  async function login(page, role) {
    await page.goto(base + '/login');
    const response = page.waitForResponse(r => r.url().endsWith('/auth/demo'));
    await page.getByRole('button', { name: 'Entrar na demonstração como ' + role, exact: true }).click();
    return (await response).json();
  }
})().catch(e => { console.error(e); process.exitCode = 1; });
