package com.chronos.musicplayer;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import org.json.JSONException;

@CapacitorPlugin(
    name = "ChronosNative",
    permissions = {
        @Permission(alias = "mediaAudio", strings = { Manifest.permission.READ_MEDIA_AUDIO }),
        @Permission(alias = "legacyStorage", strings = { Manifest.permission.READ_EXTERNAL_STORAGE }),
        @Permission(alias = "notifications", strings = {
            Manifest.permission.POST_NOTIFICATIONS
        })
    }
)
public class ChronosNativePlugin extends Plugin {
    private static final String CHANNEL_ID = "chronos_playback";
    private static final int NOTIFICATION_ID = 1201;
    private static ChronosNativePlugin instance;
    private MediaSession mediaSession;
    private String title = "CHRONOS";
    private String artist = "";
    private boolean playing = false;
    private long duration = 0;
    private long position = 0;

    @Override
    public void load() {
        instance = this;
        createMediaSession();
        createNotificationChannel();
    }

    @Override
    protected void handleOnDestroy() {
        if (mediaSession != null) mediaSession.release();
        instance = null;
        super.handleOnDestroy();
    }

    @PluginMethod
    public void scanMusic(PluginCall call) {
        if (!hasAudioPermission()) {
            requestPermissionForAlias(audioPermissionAlias(), call, "audioPermissionCallback");
            return;
        }
        performScan(call);
    }

    @PermissionCallback
    private void audioPermissionCallback(PluginCall call) {
        if (!hasAudioPermission()) {
            call.reject("Доступ к аудиофайлам не предоставлен");
            return;
        }
        performScan(call);
    }

    @PluginMethod
    public void requestNotificationPermission(PluginCall call) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            call.resolve();
            return;
        }
        requestPermissionForAlias("notifications", call, "notificationPermissionCallback");
    }

    @PermissionCallback
    private void notificationPermissionCallback(PluginCall call) {
        call.resolve();
    }

    @PluginMethod
    public void updatePlayback(PluginCall call) {
        title = call.getString("title", "CHRONOS");
        artist = call.getString("artist", "");
        playing = Boolean.TRUE.equals(call.getBoolean("playing", false));
        duration = Math.max(0, call.getLong("duration", 0L));
        position = Math.max(0, call.getLong("position", 0L));
        updateSessionAndNotification();
        call.resolve();
    }

    @PluginMethod
    public void hidePlayback(PluginCall call) {
        playing = false;
        if (mediaSession != null) mediaSession.setActive(false);
        NotificationManagerCompat.from(getContext()).cancel(NOTIFICATION_ID);
        call.resolve();
    }

    public static void dispatchMediaCommand(String command) {
        if (instance == null || command == null) return;
        JSObject payload = new JSObject();
        payload.put("command", command);
        instance.notifyListeners("mediaCommand", payload, true);
    }

    private boolean hasAudioPermission() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            ? Manifest.permission.READ_MEDIA_AUDIO
            : Manifest.permission.READ_EXTERNAL_STORAGE;
        return ActivityCompat.checkSelfPermission(getContext(), permission) == PackageManager.PERMISSION_GRANTED;
    }

    private String audioPermissionAlias() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? "mediaAudio" : "legacyStorage";
    }

    private void performScan(PluginCall call) {
        JSArray tracks = new JSArray();
        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DISPLAY_NAME
        };
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";

        try (Cursor cursor = getContext().getContentResolver().query(
            collection, projection, selection, null, MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC"
        )) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
                int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE);
                int modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED);
                int mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE);
                int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);

                while (cursor.moveToNext()) {
                    long mediaId = cursor.getLong(idColumn);
                    JSObject track = new JSObject();
                    track.put("id", "android_" + mediaId);
                    track.put("mediaId", mediaId);
                    track.put("title", cursor.getString(titleColumn));
                    track.put("artist", cleanUnknown(cursor.getString(artistColumn)));
                    track.put("album", cleanUnknown(cursor.getString(albumColumn)));
                    track.put("duration", cursor.getLong(durationColumn));
                    track.put("size", cursor.getLong(sizeColumn));
                    track.put("dateModified", cursor.getLong(modifiedColumn));
                    track.put("mimeType", cursor.getString(mimeColumn));
                    track.put("fileName", cursor.getString(nameColumn));
                    track.put("sourceUri", ContentUris.withAppendedId(collection, mediaId).toString());
                    tracks.put(track);
                }
            }
            JSObject result = new JSObject();
            result.put("tracks", tracks);
            call.resolve(result);
        } catch (Exception error) {
            call.reject("Не удалось просканировать медиатеку", error);
        }
    }

    private String cleanUnknown(String value) {
        return value == null || "<unknown>".equalsIgnoreCase(value) ? "" : value;
    }

    private void createMediaSession() {
        mediaSession = new MediaSession(getContext(), "CHRONOS");
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { dispatchMediaCommand("play"); }
            @Override public void onPause() { dispatchMediaCommand("pause"); }
            @Override public void onSkipToNext() { dispatchMediaCommand("next"); }
            @Override public void onSkipToPrevious() { dispatchMediaCommand("previous"); }
            @Override public void onSeekTo(long pos) {
                JSObject payload = new JSObject();
                payload.put("command", "seek");
                payload.put("position", pos);
                notifyListeners("mediaCommand", payload, true);
            }
        });
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID, "Воспроизведение музыки", NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Управление воспроизведением CHRONOS");
        channel.setShowBadge(false);
        getContext().getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private void updateSessionAndNotification() {
        if (mediaSession == null) return;
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE |
            PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS |
            PlaybackState.ACTION_SEEK_TO;
        int state = playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
        mediaSession.setPlaybackState(new PlaybackState.Builder()
            .setActions(actions)
            .setState(state, position, playing ? 1f : 0f)
            .build());
        mediaSession.setMetadata(new MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
            .build());
        mediaSession.setActive(true);

        Intent launchIntent = getContext().getPackageManager().getLaunchIntentForPackage(getContext().getPackageName());
        PendingIntent contentIntent = PendingIntent.getActivity(
            getContext(), 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        String centerCommand = playing ? "pause" : "play";
        int centerIcon = playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
        Notification notification = new NotificationCompat.Builder(getContext(), CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .addAction(android.R.drawable.ic_media_previous, "Назад", commandIntent("previous", 1))
            .addAction(centerIcon, playing ? "Пауза" : "Играть", commandIntent(centerCommand, 2))
            .addAction(android.R.drawable.ic_media_next, "Далее", commandIntent("next", 3))
            .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(android.support.v4.media.session.MediaSessionCompat.Token.fromToken(mediaSession.getSessionToken()))
                .setShowActionsInCompactView(0, 1, 2))
            .build();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(getContext()).notify(NOTIFICATION_ID, notification);
        }
    }

    private PendingIntent commandIntent(String command, int requestCode) {
        Intent intent = new Intent(getContext(), ChronosMediaButtonReceiver.class);
        intent.putExtra("command", command);
        return PendingIntent.getBroadcast(
            getContext(), requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
