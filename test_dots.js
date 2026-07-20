const { chromium } = require('playwright');

(async () => {
    console.log('Launching browser...');
    const browser = await chromium.launch({ headless: true });
    const page = await browser.newPage();
    
    page.on('console', msg => {
        console.log('PAGE LOG:', msg.text());
    });
    page.on('pageerror', error => {
        console.log('PAGE ERROR:', error.message);
    });

    console.log('Navigating to local player...');
    await page.goto('http://localhost:8080/m3_expressive_player_telegram.html');

    // Wait a bit for page to load
    await page.waitForTimeout(1000);

    console.log('Mocking a track in localTracks...');
    await page.evaluate(() => {
        const dummyTrack = {
            id: 'tr_test_123',
            title: 'Test Title',
            artist: 'Test Artist',
            fileBlob: new Blob(['dummy audio content'], { type: 'audio/mp3' }),
            coverUrl: null
        };
        window.localTracks = [dummyTrack];
        window.renderLibrary();
    });

    console.log('Switching to Library tab...');
    // Find tab for library. Let's see: the tab buttons have class "nav-item" or text "Медиатека".
    await page.click('text="Медиатека"');
    
    console.log('Clicking the 3 dots (more_vert)...');
    // Click on the more_vert icon inside the list item
    await page.click('.list-action');

    console.log('Waiting to see if modal opens...');
    await page.waitForTimeout(2000);

    const modalVisible = await page.evaluate(() => {
        const backdrop = document.getElementById('modal-backdrop');
        return backdrop ? backdrop.classList.contains('active') : false;
    });
    console.log('Modal active:', modalVisible);

    const modalTitle = await page.evaluate(() => {
        return document.getElementById('modal-title')?.innerText;
    });
    console.log('Modal title:', modalTitle);

    await browser.close();
})();
