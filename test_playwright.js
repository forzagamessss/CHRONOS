const { chromium } = require('playwright');

(async () => {
    console.log('Launching browser...');
    const browser = await chromium.launch({ headless: true });
    const page = await browser.newPage();
    
    page.on('console', msg => console.log('PAGE LOG:', msg.text()));
    page.on('pageerror', error => console.log('PAGE ERROR:', error.message));

    console.log('Navigating to file...');
    await page.goto('file:///' + __dirname.replace(/\\/g, '/') + '/m3_expressive_player_telegram.html');
    
    console.log('Setting phone input...');
    await page.fill('#tg-phone-input', '+9996623456');
    
    console.log('Clicking send code button...');
    await page.click('text="Получить код"');
    
    console.log('Waiting 10 seconds for results...');
    await page.waitForTimeout(10000);
    
    const toast = await page.evaluate(() => document.getElementById('toast-container')?.innerText);
    console.log('Toast content:', toast);
    
    const modal = await page.evaluate(() => document.getElementById('modal-container')?.style.display);
    console.log('Modal display:', modal);

    await browser.close();
})();
