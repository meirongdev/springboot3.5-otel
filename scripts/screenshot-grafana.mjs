import { chromium } from 'playwright';

const SCREENSHOTS_DIR = process.argv[2] || './docs/screenshots';
const GRAFANA_URL = process.env.GRAFANA_URL || 'http://localhost:3000';
const GRAFANA_USER = process.env.GRAFANA_USER || 'admin';
const GRAFANA_PASSWORD = process.env.GRAFANA_PASSWORD || 'admin';

const dashboards = [
  {
    name: 'Logs & Traces',
    url: `${GRAFANA_URL}/d/logs-dashboard?orgId=1&refresh=5s`,
    file: 'logs-traces.png',
    wait: 5000,
    headless: true,
  },
  {
    name: 'JVM Metrics',
    url: `${GRAFANA_URL}/d/jvm-metrics?orgId=1&refresh=5s`,
    file: 'jvm-metrics.png',
    wait: 5000,
    headless: true,
  },
  // Note: Services Overview requires manual screenshot due to Grafana render service limitations
  // To manually screenshot:
  // 1. Open: http://localhost:3000/d/services-overview?orgId=1
  // 2. Wait for panels to load (5-10 seconds)
  // 3. Cmd+Shift+4 (macOS) to screenshot
  // 4. Save as: docs/screenshots/services-overview.png
];

(async () => {
  console.log('📸 Starting Grafana screenshot automation...\n');
  
  const browser = await chromium.launch({
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  });
  
  const context = await browser.newContext({
    viewport: { width: 1920, height: 1080 },
    locale: 'en-US',
  });
  
  // Login to Grafana (optional - skip if anonymous access is enabled)
  console.log('🔐 Attempting Grafana login...');
  const loginPage = await context.newPage();
  try {
    await loginPage.goto(`${GRAFANA_URL}/login`, { 
      waitUntil: 'domcontentloaded',
      timeout: 10000 
    });
    
    // Check if login form exists
    const hasLoginForm = await loginPage.$('input[name="user"]');
    if (hasLoginForm) {
      await loginPage.fill('input[name="user"]', GRAFANA_USER);
      await loginPage.fill('input[name="password"]', GRAFANA_PASSWORD);
      await Promise.all([
        loginPage.click('button[type="submit"]'),
        loginPage.waitForLoadState('networkidle').catch(() => {})
      ]);
      await loginPage.waitForTimeout(2000);
      console.log('✓ Logged in successfully\n');
    } else {
      console.log('ℹ No login form found (anonymous access)\n');
    }
  } catch (e) {
    console.log('ℹ Login skipped:', e.message.split('\n')[0], '\n');
  } finally {
    await loginPage.close();
  }
  
  // Screenshot each dashboard
  for (const dashboard of dashboards) {
    console.log(`📊 Capturing: ${dashboard.name}`);
    console.log(`   URL: ${dashboard.url}`);
    
    // Launch browser with appropriate mode
    const dashboardBrowser = await chromium.launch({
      headless: dashboard.headless !== false,  // default to true
      args: ['--no-sandbox', '--disable-setuid-sandbox']
    });
    
    const dashboardContext = await dashboardBrowser.newContext({
      viewport: { width: 1920, height: 1080 },
      locale: 'en-US',
    });
    const dashboardPage = await dashboardContext.newPage();
    
    try {
      await dashboardPage.goto(dashboard.url, { 
        waitUntil: 'domcontentloaded',
        timeout: 30000 
      });
      
      // Wait for panels to render
      console.log(`   ⏳ Waiting ${dashboard.wait}ms for panels to render...`);
      await dashboardPage.waitForTimeout(dashboard.wait);
      
      // Wait for all panels to be loaded
      await dashboardPage.waitForSelector('.panel-container', { 
        state: 'visible',
        timeout: 10000 
      }).catch(() => {
        console.log('   ⚠ Warning: Some panels may not have loaded');
      });
      
      // Additional wait for charts/animations
      await dashboardPage.waitForTimeout(2000);
      
      // Take screenshot
      const outputPath = `${SCREENSHOTS_DIR}/${dashboard.file}`;
      await dashboardPage.screenshot({
        path: outputPath,
        fullPage: false,
      });
      
      console.log(`   ✓ Saved: ${outputPath}\n`);
    } catch (error) {
      console.error(`   ✗ Failed to capture ${dashboard.name}:`, error.message);
      console.log(`   ⚠ You may need to manually screenshot this dashboard\n`);
    } finally {
      await dashboardBrowser.close();
    }
  }
  
  console.log('==========================================');
  console.log('✅ Screenshot automation complete!');
  console.log('==========================================\n');
})();
