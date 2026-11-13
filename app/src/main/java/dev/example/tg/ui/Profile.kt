package dev.example.tg.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private const val BIO_LIMIT = 70

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val repo = LocalRepo.current
    val me by repo.me.collectAsState()
    val busy by repo.profileBusy.collectAsState()
    val p = me

    // поля заполняются, когда профиль загрузился
    var first by remember(p?.id) { mutableStateOf(p?.firstName.orEmpty()) }
    var last by remember(p?.id) { mutableStateOf(p?.lastName.orEmpty()) }
    var username by remember(p?.id) { mutableStateOf(p?.username.orEmpty()) }
    var bio by remember(p?.id) { mutableStateOf(p?.bio.orEmpty()) }

    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) repo.setAvatar(uri)
    }

    val changed = p != null && (first != p.firstName || last != p.lastName || username != p.username || bio != p.bio)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Профиль") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box {
                    Avatar(p?.avatarBig ?: p?.avatarSmall, p?.id ?: 0L, p?.fullName.orEmpty(), 120.dp)
                    FilledTonalIconButton(
                        onClick = { pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.align(Alignment.BottomEnd).size(40.dp),
                        enabled = !busy,
                    ) { Icon(Icons.Filled.CameraAlt, "Сменить фото") }
                }
                if ((p?.photoId ?: 0L) != 0L) {
                    OutlinedButton({ repo.deleteAvatar() }, enabled = !busy) {
                        Icon(Icons.Filled.Delete, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Удалить фото")
                    }
                }

                OutlinedTextField(
                    first, { first = it.take(64) }, Modifier.fillMaxWidth(),
                    label = { Text("Имя") }, singleLine = true, isError = first.isBlank(),
                )
                OutlinedTextField(
                    last, { last = it.take(64) }, Modifier.fillMaxWidth(),
                    label = { Text("Фамилия") }, singleLine = true,
                )
                OutlinedTextField(
                    username, { username = it.trim().removePrefix("@").take(32) }, Modifier.fillMaxWidth(),
                    label = { Text("Имя пользователя") }, prefix = { Text("@") }, singleLine = true,
                    supportingText = { Text("Латиница, цифры и _; от 5 символов. Пусто — скрыть.") },
                )
                OutlinedTextField(
                    bio, { bio = it.take(BIO_LIMIT) }, Modifier.fillMaxWidth(),
                    label = { Text("О себе") }, maxLines = 3,
                    supportingText = {
                        Text("${bio.length}/$BIO_LIMIT", Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                    },
                )
                OutlinedTextField(
                    p?.phone?.takeIf { it.isNotBlank() }?.let { "+$it" }.orEmpty(), {}, Modifier.fillMaxWidth(),
                    label = { Text("Телефон") }, enabled = false, singleLine = true,
                )

                Button(
                    onClick = { repo.saveProfile(first.trim(), last.trim(), username, bio.trim()) },
                    enabled = changed && first.isNotBlank() && !busy,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text("Сохранить") }

                if (p != null) {
                    Column(Modifier.fillMaxWidth()) {
                        GiftsSection(p.id)
                        ProfileMusicSection(p.id)
                    }
                }
            }
        }
    }
}
