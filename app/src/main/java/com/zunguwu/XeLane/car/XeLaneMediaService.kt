package com.zunguwu.XeLane.car

import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import com.zunguwu.XeLane.R

class XeLaneMediaService : MediaBrowserServiceCompat() {

    private lateinit var mediaSession: MediaSessionCompat

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(this, "XeLaneMediaSession").apply {
            isActive = true
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                    launchMainApp()
                    updateSessionState(
                        title = getString(R.string.app_name),
                        subtitle = getString(R.string.auto_media_item_subtitle),
                        state = PlaybackStateCompat.STATE_PLAYING
                    )
                }

                override fun onPlay() {
                    launchMainApp()
                    updateSessionState(
                        title = getString(R.string.app_name),
                        subtitle = getString(R.string.auto_media_item_subtitle),
                        state = PlaybackStateCompat.STATE_PLAYING
                    )
                }

                override fun onPause() {
                    updateSessionState(
                        title = getString(R.string.app_name),
                        subtitle = getString(R.string.auto_media_item_subtitle),
                        state = PlaybackStateCompat.STATE_PAUSED
                    )
                }
            })
        }
        sessionToken = mediaSession.sessionToken
        updateSessionState(
            title = getString(R.string.app_name),
            subtitle = getString(R.string.auto_media_item_subtitle),
            state = PlaybackStateCompat.STATE_PAUSED
        )
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        return BrowserRoot(ROOT_ID, null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        val item = MediaBrowserCompat.MediaItem(
            MediaDescriptionCompat.Builder()
                .setMediaId(ITEM_ID_OPEN_APP)
                .setTitle(getString(R.string.auto_media_item_title))
                .setSubtitle(getString(R.string.auto_media_item_subtitle))
                .build(),
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        )
        result.sendResult(mutableListOf(item))
    }

    override fun onDestroy() {
        mediaSession.release()
        super.onDestroy()
    }

    private fun launchMainApp() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(launchIntent)
    }

    private fun updateSessionState(title: String, subtitle: String, state: Int) {
        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, subtitle)
                .build()
        )
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
                )
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build()
        )
    }

    companion object {
        private const val ROOT_ID = "xelane_root"
        private const val ITEM_ID_OPEN_APP = "open_xelane"
    }
}
