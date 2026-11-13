package dev.example.tg.data

import org.drinkless.tdlib.TdApi

/**
 * Доступ к частям TDLib API, которые меняются от версии к версии (подарки, музыка профиля, веб-приложения).
 * Работает через рефлексию: если в вашей сборке TDLib класса или поля нет, функция просто вернёт null/пусто,
 * а сборка не упадёт. Release-сборка с R8 должна оставлять org.drinkless.tdlib.** как есть.
 */
internal object Rf {
    fun new(cls: String, vararg fields: Pair<String, Any?>): Any? = runCatching {
        val o = Class.forName("org.drinkless.tdlib.TdApi\$$cls").getDeclaredConstructor().newInstance()
        for ((k, v) in fields) set(o, k, v)
        o
    }.getOrNull()

    fun fn(cls: String, vararg fields: Pair<String, Any?>): TdApi.Function<*>? = new(cls, *fields) as? TdApi.Function<*>

    fun set(o: Any, name: String, value: Any?) {
        runCatching { o.javaClass.getField(name).set(o, value) }
    }

    fun get(o: Any?, name: String): Any? =
        if (o == null) null else runCatching { o.javaClass.getField(name).get(o) }.getOrNull()

    fun str(o: Any?, name: String): String = get(o, name) as? String ?: ""
    fun int(o: Any?, name: String): Int = (get(o, name) as? Number)?.toInt() ?: 0
    fun long(o: Any?, name: String): Long = (get(o, name) as? Number)?.toLong() ?: 0L

    fun kind(o: Any?): String = o?.javaClass?.simpleName.orEmpty()

    fun array(o: Any?, name: String): List<Any?> = (get(o, name) as? Array<*>)?.toList() ?: emptyList()
}
