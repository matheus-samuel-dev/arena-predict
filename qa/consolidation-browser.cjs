// Full product regression against the local QA PostgreSQL stack. Does not save JWTs.
const { chromium } = require('playwright');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const base = process.env.ARENA_QA_URL || 'http://localhost:5176';
assert(['127.0.0.1', 'localhost'].includes(new URL(base).hostname), 'QA requires loopback');
const widths = [1920, 1440, 1366, 1024, 768, 430, 390, 360];
const profiles = process.env.ARENA_QA_PROFILE ? [process.env.ARENA_QA_PROFILE] : ['participant', 'admin'];
assert(profiles.every(value => ['participant', 'admin'].includes(value)), 'Invalid QA profile');
const participant = ['/app', '/events', '/live', '/predictions', '/pools', '/leagues', '/rankings', '/statistics', '/challenges', '/achievements', '/community', '/account', '/notifications', '/points', '/help'];
const admin = ['/admin', ...['events', 'markets', 'results', 'sports', 'championships', 'competitors', 'pools', 'scoring-rules', 'challenges', 'achievements', 'users', 'notifications', 'moderation', 'reports', 'audit', 'settings'].map(value => '/admin/' + value)];
const report = { at: new Date().toISOString(), base, pages: [], consoleErrors: [], pageErrors: [], apiErrors: [], requests: 0, menu: [], interactions: [] };
const evidenceName = profiles.length === 1 ? `consolidation-${profiles[0]}-layout.json` : 'consolidation-browser.json';
const save = () => fs.writeFileSync(path.join(__dirname, evidenceName), JSON.stringify(report, null, 2));

(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe' });
  try {
    for (const profile of profiles) {
      const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'pt-BR', reducedMotion: 'reduce' });
      const page = await context.newPage();
      page.setDefaultTimeout(30000);
      page.on('pageerror', error => report.pageErrors.push({ url: page.url(), message: error.message }));
      page.on('console', message => {
        if (message.type() === 'error') report.consoleErrors.push({ url: page.url(), message: message.text(), source: message.location().url });
      });
      page.on('response', response => {
        if (response.url().startsWith(base + '/api/')) {
          report.requests++;
          if (response.status() >= 400) report.apiErrors.push({ path: new URL(response.url()).pathname, status: response.status() });
        }
      });
      await page.goto(base + '/login', { waitUntil: 'domcontentloaded' });
      await page.getByRole('button', { name: profile === 'admin' ? 'Entrar na demonstração como administrador' : 'Entrar na demonstração como participante' }).click();
      await page.waitForURL(base + (profile === 'admin' ? '/admin' : '/app'), { waitUntil: 'domcontentloaded' });
      await page.locator('#main-content h1').waitFor();
      const nav = page.getByRole('navigation', { name: 'Navegação principal', exact: true });
      const groups = profile === 'admin' ? ['Operação', 'Catálogo', 'Engajamento', 'Gestão', 'Governança'] : ['Principal', 'Competições', 'Comunidade e conta'];
      for (const group of groups) {
        const button = nav.getByRole('button', { name: group, exact: true });
        if (await button.getAttribute('aria-expanded') !== 'true') await button.click();
      }
      const links = await nav.locator('a').evaluateAll(nodes => nodes.map(node => ({ text: node.textContent.trim(), href: node.getAttribute('href') })));
      const paths = [...(profile === 'admin' ? admin : participant)];
      assert(paths.filter(value => profile === 'admin' || !['/notifications', '/points', '/help'].includes(value)).every(value => links.some(link => link.href === value)), 'Missing product menu item');
      report.menu.push({ profile, groups, links });
      const snapshotResponse = page.waitForResponse(response => response.url() === base + '/api/demo/scenario' && response.status() === 200);
      await page.goto(base + '/demo', { waitUntil: 'domcontentloaded' });
      const snapshot = await (await snapshotResponse).json();
      paths.push('/demo', '/events/' + snapshot.event.id);

      // Filtering is a real UI interaction and does not write sports data.
      await page.goto(base + (profile === 'admin' ? '/admin/events' : '/events'));
      await page.locator('#main-content h1').waitFor();
      const search = page.getByRole('textbox', { name: profile === 'admin' ? 'Buscar em Gerenciar eventos' : 'Buscar eventos' });
      if (await search.count()) {
        await search.fill('nenhum-evento-qa-xyz');
        if (profile === 'participant') {
          await page.getByRole('button', { name: 'Buscar', exact: true }).click();
          await page.getByRole('heading', { name: 'Nenhum resultado encontrado' }).waitFor();
        } else {
          await page.getByRole('heading', { name: 'Nenhum registro com esses filtros' }).waitFor();
        }
        report.interactions.push({ profile, action: 'search', value: 'no-match', passed: true });
        await search.fill('');
        if (profile === 'participant') await page.getByRole('button', { name: 'Buscar', exact: true }).click();
      }
      for (const width of widths) {
        await page.setViewportSize({ width, height: width <= 430 ? 844 : 1000 });
        for (const route of paths) {
          await page.goto(base + route, { waitUntil: 'domcontentloaded' });
          await page.locator('#main-content h1').waitFor();
          await page.waitForTimeout(80);
          const content = await page.locator('#main-content').innerText();
          assert(!/Essa arquibancada não existe|Acesso restrito|Não foi possível carregar|em breve/i.test(content), route + ' failed to load');
          const geometry = await page.evaluate(() => ({ viewport: innerWidth, document: document.documentElement.scrollWidth, body: document.body.scrollWidth }));
          const overflow = Math.max(geometry.document, geometry.body) > width + 1;
          const genericWrites = profile === 'admin' && route !== '/admin' ? await page.locator('#main-content button').allTextContents() : [];
          assert(!genericWrites.some(text => /^(Criar|Liquidar|Revisar|Aplicar catálogo)/.test(text.trim())), 'Demo admin offered a generic command');
          report.pages.push({ profile, route, width, title: await page.locator('#main-content h1').first().innerText(), overflow, ...geometry });
          if ([1440, 360].includes(width) && (['/app', '/events', '/admin', '/admin/events', '/admin/markets', '/demo'].includes(route) || route.startsWith('/events/'))) {
            await page.screenshot({ path: path.join(__dirname, `consolidation-${profile}-${route.replaceAll('/', '-')}-${width}.png`), fullPage: false });
          }
        }
        // The mobile drawer exposes the same complete grouped navigation.
        if (width <= 768) {
          await page.getByRole('button', { name: 'Abrir menu', exact: true }).click();
          await page.locator('.sidebar__close').waitFor({ state: 'visible' });
          assert(await nav.locator('a').count() === links.length, 'Mobile menu lost product links');
          await page.screenshot({ path: path.join(__dirname, `consolidation-${profile}-drawer-${width}.png`) });
          await page.locator('.sidebar__close').click();
        }
        save(); console.log(profile + ' ' + width + ': ' + paths.length + ' pages checked');
      }
      await context.close();
    }
    const loginPage = await browser.newPage();
    for (const width of widths) {
      await loginPage.setViewportSize({ width, height: 1000 }); await loginPage.goto(base + '/login');
      await loginPage.getByRole('heading', { name: 'Entre na sua arena' }).waitFor();
      const overflow = await loginPage.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1);
      report.pages.push({ route: '/login', width, overflow });
    }
    report.result = report.pages.some(item => item.overflow) || report.consoleErrors.length || report.pageErrors.length || report.apiErrors.length ? 'FAIL' : 'PASS';
    save(); assert.equal(report.result, 'PASS', 'See consolidation-browser.json');
    console.log('PASS: ' + report.pages.length + ' page/viewport combinations; ' + report.requests + ' API responses');
  } catch (error) { report.result = 'FAIL'; report.failure = error.message; save(); throw error; }
  finally { await browser.close(); }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
