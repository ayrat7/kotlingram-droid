package dev.example.tg

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import dev.example.tg.data.TgRepo
import dev.example.tg.media.Attachments
import dev.example.tg.media.AudioPlayer
import dev.example.tg.media.VoiceRecorder
import dev.example.tg.ui.LocalPlayer
import dev.example.tg.ui.LocalRecorder
import dev.example.tg.ui.LocalRepo
import dev.example.tg.ui.TgRoot
import dev.example.tg.ui.TgTheme

class TgApplication : Application() {
    lateinit var repo: TgRepo
    lateinit var player: AudioPlayer
    lateinit var recorder: VoiceRecorder

    override fun onCreate() {
        super.onCreate()
        repo = TgRepo(this)
        player = AudioPlayer(this)
        recorder = VoiceRecorder(this)
        Thread { Attachments.cleanup(this) }.start()
        repo.start()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as TgApplication
        setContent {
            TgTheme {
                CompositionLocalProvider(
                    LocalRepo provides app.repo,
                    LocalPlayer provides app.player,
                    LocalRecorder provides app.recorder,
                ) {
                    TgRoot()
                }
            }
        }
    }
}
