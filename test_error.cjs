const puppeteer = require('puppeteer');

(async () => {
  const browser = await puppeteer.launch();
  const page = await browser.newPage();
  
  page.on('console', msg => {
    if (msg.type() === 'error') console.log('ERROR:', msg.text());
  });
  page.on('pageerror', err => {
    console.log('PAGE ERROR:', err.toString());
  });

  await page.goto('http://localhost:3001', { waitUntil: 'networkidle0' });
  await browser.close();
})();
