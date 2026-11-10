package dev.example.tg

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class TgApplication : Application() {
    lateinit var repo: TgRepo

    override fun onCreate() {
        super.onCreate()
        repo = TgRepo(this)
        repo.start()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = (application as TgApplication).repo
        setContent { TgTheme { TgRoot(repo) } }
    }
}
