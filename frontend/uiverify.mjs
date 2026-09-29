import { chromium } from 'playwright';
import fs from 'fs';

const BASE = 'http://localhost:8080';
const EMAIL = fs.readFileSync('/tmp/uitest_email.txt', 'utf8').trim();
const PW = 'Passw0rd123';

const browser = await chromium.launch();
const page = await browser.newPage();
const log = (...a) => console.log(...a);

try {
  // 1) Log in via the UI
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
  await page.getByTestId('login-identifier').fill(EMAIL);
  await page.getByTestId('login-password').fill(PW);
  await page.getByTestId('login-submit').click();
  await page.waitForURL(`${BASE}/`, { timeout: 15000 });
  log('✓ logged in');

  // 2) Go to the support page and open the case
  await page.goto(`${BASE}/support`, { waitUntil: 'networkidle' });
  await page.getByText('UI big image case').first().click();

  // 3) Wait for the attachment thumbnail (img whose src points at the media library) to render
  const thumb = page.locator('img[src*="/api/v1/media/"]').first();
  await thumb.waitFor({ state: 'visible', timeout: 15000 });

  // Wait until the image has actually decoded (complete + naturalWidth > 0), not just present in DOM
  await page.waitForFunction(() => {
    const img = document.querySelector('img[src*="/api/v1/media/"]');
    return img && img.complete && img.naturalWidth > 0;
  }, { timeout: 15000 });

  const dims = await thumb.evaluate((img) => ({ w: img.naturalWidth, h: img.naturalHeight, src: img.getAttribute('src') }));
  log('attachment img:', JSON.stringify(dims));

  // 4) Also confirm the "Attach images" control is present on the reply form
  const attachBtn = await page.getByRole('button', { name: /attach images/i }).count();
  log('attach-images buttons on page:', attachBtn);

  await page.screenshot({ path: '/tmp/support-attachment-ui.png', fullPage: true });

  const ok = dims.w > 0 && attachBtn > 0;
  log(ok ? 'RESULT: PASS — attachment thumbnail rendered in the UI' : 'RESULT: FAIL');
  process.exit(ok ? 0 : 1);
} catch (e) {
  log('RESULT: ERROR —', e.message);
  await page.screenshot({ path: '/tmp/support-attachment-ui.png', fullPage: true }).catch(() => {});
  process.exit(2);
} finally {
  await browser.close();
}
