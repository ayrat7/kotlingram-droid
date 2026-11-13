package dev.example.tg.data

import org.drinkless.tdlib.TdApi

/** Подарки и музыка в профиле. Читаем через [Rf], чтобы сборка не зависела от точной версии TDLib. */
internal object ProfileExtras {

    suspend fun gifts(repo: TgRepo, userId: Long): List<GiftUi> {
        val fn = Rf.fn(
            "GetReceivedGifts",
            "ownerId" to TdApi.MessageSenderUser(userId),
            "offset" to "",
            "limit" to 60,
        ) ?: return emptyList()
        val res = runCatching { repo.call(fn) }.getOrNull() ?: return emptyList()
        val out = ArrayList<GiftUi>()
        for ((i, rg) in Rf.array(res, "gifts").withIndex()) {
            val sent = Rf.get(rg, "gift") ?: continue
            val inner = Rf.get(sent, "gift") ?: continue
            val upgraded = Rf.kind(sent).contains("Upgraded")
            var sticker: TdApi.Sticker?
            var title: String
            var stars = 0L
            var number = 0
            var total = 0
            var center = 0
            var edge = 0
            if (upgraded) {
                title = Rf.str(inner, "title")
                number = Rf.int(inner, "number")
                total = Rf.int(inner, "maxUpgradedCount")
                sticker = Rf.get(Rf.get(inner, "model"), "sticker") as? TdApi.Sticker
                val colors = Rf.get(Rf.get(inner, "backdrop"), "colors")
                center = Rf.int(colors, "centerColor")
                edge = Rf.int(colors, "edgeColor")
            } else {
                title = "Подарок"
                stars = Rf.long(inner, "starCount")
                total = Rf.int(inner, "totalCount")
                sticker = Rf.get(inner, "sticker") as? TdApi.Sticker
            }
            val st = sticker ?: continue
            repo.reg(st.sticker)
            repo.reg(st.thumbnail?.file)
            val animated = st.format is TdApi.StickerFormatTgs
            val fileId = when {
                st.format is TdApi.StickerFormatWebm -> st.thumbnail?.file?.id
                else -> st.sticker.id
            }
            val sender = Rf.get(rg, "senderId") as? TdApi.MessageSenderUser
            val isPrivate = Rf.get(rg, "isPrivate") as? Boolean == true
            val fromId = if (isPrivate) 0L else sender?.userId ?: 0L
            val fromName = if (fromId != 0L) repo.userName(fromId) else if (isPrivate) "Скрыто" else ""
            val text = (Rf.get(rg, "text") as? TdApi.FormattedText)?.text.orEmpty()
            out += GiftUi(
                key = Rf.str(rg, "receivedGiftId").ifBlank { "g$i" },
                title = title,
                sticker = fileId,
                animated = animated && st.format !is TdApi.StickerFormatWebm,
                stars = stars,
                upgraded = upgraded,
                number = number,
                total = total,
                bgCenter = center,
                bgEdge = edge,
                fromId = fromId,
                fromName = fromName,
                text = text,
                date = Rf.int(rg, "date"),
            )
        }
        return out
    }

    /** Музыка, добавленная в профиль. Если в этой версии TDLib такого запроса нет — пусто. */
    suspend fun audios(repo: TgRepo, userId: Long): List<Track> {
        val fn = Rf.fn("GetUserProfileAudios", "userId" to userId, "offset" to 0, "limit" to 100) ?: return emptyList()
        val res = runCatching { repo.call(fn) }.getOrNull() ?: return emptyList()
        return Rf.array(res, "audios").mapNotNull { a ->
            val audio = a as? TdApi.Audio ?: return@mapNotNull null
            repo.reg(audio.audio)
            repo.reg(audio.albumCoverThumbnail?.file)
            Track(
                fileId = audio.audio.id,
                title = audio.title.ifBlank { audio.fileName.ifBlank { "Аудио" } },
                performer = audio.performer,
                durationSec = audio.duration,
                cover = audio.albumCoverThumbnail?.file?.id,
            )
        }
    }
}
