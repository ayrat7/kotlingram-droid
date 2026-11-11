package dev.example.tg

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.example.tg.data.AppSettings
import dev.example.tg.data.TgRepo
import dev.example.tg.media.Attachments
import dev.example.tg.media.AudioPlayer
import dev.example.tg.media.VoiceRecorder
import dev.example.tg.notify.Notifier
import dev.example.tg.ui.LocalPlayer
import dev.example.tg.ui.LocalRecorder
import dev.example.tg.ui.LocalRepo
import dev.example.tg.ui.TgRoot
import dev.example.tg.ui.TgTheme
import dev.example.tg.ui.isDarkFor

class TgApplication : Application() {
    lateinit var settings: AppSettings
    lateinit var repo: TgRepo
    lateinit var player: AudioPlayer
    lateinit var recorder: VoiceRecorder

    private var started = 0

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        repo = TgRepo(this, settings)
        player = AudioPlayer(this)
        recorder = VoiceRecorder(this)
        Thread { Attachments.cleanup(this) }.start()
        repo.start()

        // «на экране / в фоне» — от этого зависят уведомления и статус «в сети»
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (++started == 1) repo.setForeground(true)
            }

            override fun onActivityStopped(activity: Activity) {
                if (--started == 0) repo.setForeground(false)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as TgApplication
        handleIntent(intent)
        setContent {
            val mode by app.settings.theme.collectAsState()
            val dynamic by app.settings.dynamicColor.collectAsState()
            val dark = isDarkFor(mode)
            DisposableEffect(dark) {
                val bar = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
                onDispose {}
            }
            TgTheme(mode, dynamic) {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Нажатие на уведомление открывает нужный чат. */
    private fun handleIntent(i: Intent?) {
        val chatId = i?.getLongExtra(Notifier.EXTRA_CHAT, 0L) ?: 0L
        if (chatId != 0L) (application as TgApplication).repo.pendingOpen.value = chatId
    }
}
