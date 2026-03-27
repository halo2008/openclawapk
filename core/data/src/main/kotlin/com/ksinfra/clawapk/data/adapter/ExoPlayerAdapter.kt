package com.ksinfra.clawapk.data.adapter

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.datasource.DataSource
import android.net.Uri
import com.ksinfra.clawapk.domain.model.AudioData
import com.ksinfra.clawapk.domain.port.AudioPlayerPort
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@UnstableApi
class ExoPlayerAdapter(
    private val context: Context
) : AudioPlayerPort {

    private var player: ExoPlayer? = null

    override suspend fun play(audioData: AudioData) = suspendCancellableCoroutine { cont ->
        val exoPlayer = ExoPlayer.Builder(context).build()
        player = exoPlayer

        val dataSourceFactory = DataSource.Factory {
            ByteArrayDataSource(audioData.bytes).apply {
                open(DataSpec(Uri.EMPTY))
            }
        }

        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(Uri.EMPTY))

        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                    exoPlayer.release()
                    player = null
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        })
        exoPlayer.prepare()
        exoPlayer.play()

        cont.invokeOnCancellation {
            exoPlayer.release()
            player = null
        }
    }

    override fun stop() {
        player?.release()
        player = null
    }

    override fun isPlaying(): Boolean = player?.isPlaying == true
}
