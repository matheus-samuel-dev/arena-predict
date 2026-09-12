// Real Chromium UI audit. Run against the isolated demo after starting Docker.
// Requires Playwright on NODE_PATH and Chrome locally; never stores auth tokens.
const { chromium, request } = require('playwright');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const baseURL = process.env.ARENA_QA_URL || 'http://localhost:5174';
const out = __dirname;
const report = { startedAt: new Date().toISOString(), baseURL, pages: [], errors: [], responses: [], persistence: {} };
const save = () => {
  const target = path.join(out, 'browser-audit.json');
  const content = JSON.stringify(report, null, 2);
  for (let attempt = 0; attempt < 5; attempt++) {
    try { fs.writeFileSync(target, content); return; }
    catch (error) {
      if (attempt === 4) throw error;
      Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 100 * (attempt + 1));
    }
  }
};

(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe' });
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, locale: 'pt-BR', reducedMotion: 'reduce' });
  const page = await context.newPage();
  page.setDefaultTimeout(20000);
  page.on('pageerror', e => report.errors.push({ kind: 'pageerror', url: page.url(), message: e.message }));
  page.on('console', m => { if (m.type() === 'error') report.errors.push({ kind: 'console', url: page.url(), message: m.text(), resource: m.location().url }); });
  page.on('response', r => { if (r.url().startsWith(baseURL + '/api/')) report.responses.push({ url: r.url().replace(baseURL, ''), status: r.status() }); });
  try {
    await page.goto(baseURL + '/login', { waitUntil: 'domcontentloaded' });
    const loginResponse = page.waitForResponse(r => r.url().endsWith('/auth/demo'));
    await page.getByRole('button', { name: 'Entrar na demonstração como participante', exact: true }).click();
    const auth = await (await loginResponse).json();
    const api = await request.newContext({ baseURL, extraHTTPHeaders: { Authorization: 'Bearer ' + auth.token } });
    const get = async url => { const r = await api.get(url); assert.equal(r.status(), 200, url); return r.json(); };
    const events = await get('/api/events');
    const eventList = Array.isArray(events) ? events : events.content;
    const predictions = await get('/api/predictions');
    const wallet = await get('/api/wallet');
    const transactions = await get('/api/wallet/transactions');
    const predictionList = Array.isArray(predictions) ? predictions : predictions.content;
    const rankingPeriods = {};
    for (const period of ['WEEKLY', 'MONTHLY', 'ALL']) {
      const ranking = await get('/api/rankings?period=' + period);
      rankingPeriods[period] = Array.isArray(ranking) ? ranking : ranking.content;
    }
    report.persistence = {
      verifiedAt: new Date().toISOString(), wallet,
      predictionCount: predictionList.length,
      transactionCount: (Array.isArray(transactions) ? transactions : transactions.content).length,
      eventCount: eventList.length,
      liveEvents: eventList.filter(e => e.status === 'LIVE').length,
      openMarkets: eventList.flatMap(e => e.markets).filter(m => m.availability?.allowed).length,
      rankingRows: Object.fromEntries(Object.entries(rankingPeriods).map(([period, rows]) => [period, rows.length])),
    };
    assert.ok(wallet.balance >= 0);
    assert.ok(predictionList.length > 0);
    assert.ok(report.persistence.liveEvents >= 2);
    assert.ok(report.persistence.openMarkets > 0);
    Object.values(rankingPeriods).forEach(rows => assert.equal(rows.length, 24));
    report.persistence.passed = true;
    save();
    const sporting = ['FOOTBALL', 'BASKETBALL', 'CS2', 'TENNIS', 'MOTORSPORT', 'VALORANT', 'LEAGUE_OF_LEGENDS', 'VOLLEYBALL', 'DOTA2', 'AMERICAN_FOOTBALL'];
    report.eventExamples = sporting.map(code => eventList.find(e => (e.sport?.code || '') === code && !['FINISHED', 'CANCELLED'].includes(e.status))).filter(Boolean).map(e => ({ id: e.id, title: e.title, code: e.sport.code, markets: e.markets.length }));
    const detailRoutes = report.eventExamples.map(e => '/events/' + e.id);
    const routes = ['/app', '/events', '/live', '/predictions', '/pools', '/leagues', '/rankings', '/statistics', '/challenges', '/points', ...detailRoutes];
    for (const route of routes) await audit(route, 1440, 900, 'light');
    const sizes = [[1920,1080], [1536,864], [1366,768], [1280,800], [1024,768], [768,1024], [430,932], [390,844], [360,800]];
    const chosenDetails = report.eventExamples.filter(e => ['FOOTBALL','CS2','BASKETBALL','TENNIS','MOTORSPORT'].includes(e.code)).map(e => '/events/' + e.id);
    for (const [width,height] of sizes) {
      for (const route of ['/app', '/events', '/live', '/rankings', ...chosenDetails]) await audit(route, width, height, 'light');
    }
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto(baseURL + '/account', { waitUntil: 'domcontentloaded' });
    // The theme control is an actual UI action; no mocked CSS or injected theme.
    const preferences = page.getByRole('tab', { name: 'Preferências', exact: true });
    await preferences.click();
    await page.getByRole('button', { name: 'Escuro', exact: true }).click();
    await audit('/events', 1440, 900, 'dark');
    await audit('/rankings', 390, 844, 'dark');
    await page.goto(baseURL + '/account', { waitUntil: 'domcontentloaded' });
    await preferences.click();
    await page.getByRole('button', { name: 'Claro', exact: true }).click();

    await page.evaluate(() => sessionStorage.clear());
    await page.goto(baseURL + '/login', { waitUntil: 'domcontentloaded' });
    await page.getByRole('button', { name: 'Entrar na demonstração como administrador', exact: true }).click();
    await page.waitForURL(/\/admin(?:\/|$)/);
    const adminRoutes = ['/admin', '/admin/sports', '/admin/championships', '/admin/competitors', '/admin/events',
      '/admin/markets', '/admin/results', '/admin/users', '/admin/pools', '/admin/scoring-rules',
      '/admin/engagement', '/admin/moderation', '/admin/reports', '/admin/audit', '/admin/settings'];
    for (const route of adminRoutes) await audit(route, 1440, 900, 'light');
    for (const [width, height] of [[1280,800], [360,800]]) {
      for (const route of ['/admin', '/admin/events', '/admin/markets', '/admin/results', '/admin/users']) {
        await audit(route, width, height, 'light');
      }
    }
    await api.dispose();
    report.completedAt = new Date().toISOString();
    report.passed = report.pages.every(p => !p.overflow && !p.failure && p.missingLabels.length === 0) && report.errors.length === 0 && report.responses.every(r => r.status < 400);
    save();
    console.log(JSON.stringify({ pages: report.pages.length, passed: report.passed, overflow: report.pages.filter(p => p.overflow), errors: report.errors, failures: report.pages.filter(p => p.failure), unlabeled: report.pages.filter(p => p.missingLabels.length) }, null, 2));
  } catch (e) { report.fatal = String(e.stack || e); save(); throw e; }
  finally { await browser.close(); }

  async function audit(route, width, height, theme) {
    const entry = { route, width, height, theme, missingLabels: [] };
    try {
      await page.setViewportSize({ width, height });
      const start = Date.now();
      await page.goto(baseURL + route, { waitUntil: 'domcontentloaded' });
      await page.locator('main h1').waitFor();
      await page.evaluate(() => document.fonts.ready);
      Object.assign(entry, await page.evaluate(() => {
        const root = document.documentElement;
        const visible = e => { const r = e.getBoundingClientRect(); const s = getComputedStyle(e); return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none'; };
        return {
          title: document.querySelector('main h1')?.textContent,
          overflow: root.scrollWidth > window.innerWidth + 1,
          scrollWidth: root.scrollWidth,
          offenders: root.scrollWidth > window.innerWidth + 1 ? Array.from(document.querySelectorAll('main *')).filter(e => visible(e) && e.getBoundingClientRect().right > innerWidth + 1).slice(0, 12).map(e => ({ tag: e.tagName, className: e.className, right: Math.round(e.getBoundingClientRect().right) })) : [],
          missingLabels: Array.from(document.querySelectorAll('main input,main select,main textarea')).filter(e => visible(e) && e.type !== 'hidden' && !e.getAttribute('aria-label') && !e.getAttribute('aria-labelledby') && !(e.labels?.length)).map(e => ({ id: e.id, name: e.name, type: e.type })),
          controls: Array.from(document.querySelectorAll('main select')).filter(visible).map(e => ({ label: e.getAttribute('aria-label') || e.labels?.[0]?.textContent, background: getComputedStyle(e).backgroundColor, color: getComputedStyle(e).color, optionBackground: e.options[0] ? getComputedStyle(e.options[0]).backgroundColor : null, colorScheme: getComputedStyle(e).colorScheme })),
          cards: Array.from(document.querySelectorAll('.events-grid .event-card, .featured-events-grid .event-card')).slice(0,12).map(e => ({ height: Math.round(e.getBoundingClientRect().height), alignSelf: getComputedStyle(e).alignSelf, minHeight: getComputedStyle(e).minHeight, gridAlign: getComputedStyle(e.parentElement).alignItems }))
        };
      }));
      entry.readyMs = Date.now() - start;
      const screenshotRoute = ['/app', '/events', '/live', '/rankings'].includes(route)
        || (width === 1440 && ['/admin', '/admin/markets', '/admin/users'].includes(route));
      if ((width === 360 || width === 1440) && screenshotRoute) {
        const name = (route.slice(1) || 'home').replaceAll('/', '-') + '-' + width + '-' + theme + '.png';
        await page.screenshot({ path: path.join(out, name), fullPage: false });
        entry.screenshot = name;
      }
    } catch (e) { entry.failure = String(e.message); }
    report.pages.push(entry); save();
    console.log(`${route} ${width} ${theme}: ${entry.failure ? 'FAIL' : entry.overflow ? 'OVERFLOW' : 'OK'}`);
  }
})().catch(e => { console.error(e); process.exitCode = 1; });
