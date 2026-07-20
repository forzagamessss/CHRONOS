const fs = require('fs');
const path = require('path');

// Dynamically import Jimp
async function generate() {
    let Jimp;
    try {
        Jimp = require('jimp');
    } catch (e) {
        console.error('Jimp is not installed. Please run: npm install jimp');
        process.exit(1);
    }

    const src = 'assets/icon-512.png';
    if (!fs.existsSync(src)) {
        console.error('Source icon-512.png not found!');
        process.exit(1);
    }

    const image = await Jimp.read(src);

    const androidResDirs = [
        { dir: 'android/app/src/main/res/mipmap-mdpi', size: 48 },
        { dir: 'android/app/src/main/res/mipmap-hdpi', size: 72 },
        { dir: 'android/app/src/main/res/mipmap-xhdpi', size: 96 },
        { dir: 'android/app/src/main/res/mipmap-xxhdpi', size: 144 },
        { dir: 'android/app/src/main/res/mipmap-xxxhdpi', size: 192 }
    ];

    for (const target of androidResDirs) {
        if (!fs.existsSync(target.dir)) {
            fs.mkdirSync(target.dir, { recursive: true });
        }
        
        // Save launcher icon (normal, round, and adaptive foreground)
        const resized = image.clone().resize(target.size, target.size);
        await resized.writeAsync(path.join(target.dir, 'ic_launcher.png'));
        await resized.writeAsync(path.join(target.dir, 'ic_launcher_round.png'));
        await resized.writeAsync(path.join(target.dir, 'ic_launcher_foreground.png'));
        console.log(`Generated Android icons for size ${target.size}x${target.size} inside ${target.dir}`);
    }
}

generate().catch(console.error);
