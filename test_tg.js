global.window = {
    location: { protocol: 'https:' },
    WebSocket: require('ws')
};
const { TelegramClient, sessions } = require('./telegram.browser.js').telegram;
const client = new TelegramClient(new sessions.StringSession(''), 24525868, '80f682cf8572ceea6247c1f1ec761005', {
    connectionRetries: 1
});

async function test() {
    try {
        console.log('Connecting...');
        await client.connect();
        console.log('Connected! Sending code...');
        // Just testing with a dummy number to see if Telegram API rejects it or returns sendCode result
        const res = await client.sendCode({ apiId: 24525868, apiHash: '80f682cf8572ceea6247c1f1ec761005' }, '+9996612345');
        console.log('sendCode result:', res);
        process.exit(0);
    } catch (e) {
        console.error('ERROR OCCURRED:', e);
        process.exit(1);
    }
}
test();
