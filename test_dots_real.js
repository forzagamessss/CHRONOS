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
    await page.waitForTimeout(2000);

    console.log('Inserting track into IndexedDB...');
    await page.evaluate(() => {
        return new Promise((resolve, reject) => {
            const request = indexedDB.open("M3ExpressiveDB", 2);
            request.onerror = () => reject("DB open error");
            request.onsuccess = (e) => {
                const db = e.target.result;
                const tx = db.transaction("tracks", "readwrite");
                const store = tx.objectStore("tracks");
                const dummyTrack = {
                    id: 'tr_test_123',
                    title: "Don't Speak",
                    artist: 'No Doubt',
                    fileBlob: new Blob(['dummy audio content'], { type: 'audio/mp3' }),
                    coverUrl: null
                };
                store.put(dummyTrack);
                tx.oncomplete = () => {
                    resolve("Track inserted");
                };
                tx.onerror = () => reject("Tx error");
            };
        });
    });

    console.log('Reloading page to load track from DB...');
    await page.reload();
    await page.waitForTimeout(2000);

    console.log('Switching to Library tab via page.click...');
    // The navigation bar is at the bottom, class nav-bar. Let's find div with "Медиатека" but inside nav-bar
    const tabs = await page.$$('.nav-item');
    console.log('Found tabs count:', tabs.length);
    // Click the third tab (Library)
    await tabs[2].click();
    await page.waitForTimeout(500);

    console.log('Checking if .list-action is visible...');
    const actionExists = await page.evaluate(() => {
        const el = document.querySelector('.list-action');
        return el ? { visible: el.offsetWidth > 0, html: el.outerHTML } : null;
    });
    console.log('Action element info:', actionExists);

    if (actionExists) {
        console.log('Clicking the 3 dots (more_vert)...');
        await page.click('.list-action');

        console.log('Waiting to see if modal opens...');
        await page.waitForTimeout(1000);

        const modalVisible = await page.evaluate(() => {
            const backdrop = document.getElementById('modal-backdrop');
            return backdrop ? backdrop.classList.contains('active') : false;
        });
        console.log('Modal active after click:', modalVisible);

        const modalHtml = await page.evaluate(() => {
            return document.getElementById('modal-content')?.innerHTML;
        });
        console.log('Modal HTML:', modalHtml);
    } else {
        console.log('Error: .list-action not found in DOM!');
    }

    await browser.close();
})();
