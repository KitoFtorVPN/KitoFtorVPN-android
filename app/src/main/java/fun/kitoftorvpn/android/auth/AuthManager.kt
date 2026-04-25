package `fun`.kitoftorvpn.android.auth

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Менеджер авторизации.
 *
 * Схема: приложение открывает браузер (Custom Tabs) на my.kitoftorvpn.fun?platform=android,
 * юзер логинится любым способом, бэкенд делает redirect на kitoftorvpn://auth?token=...
 * Android ловит этот URI через intent-filter в манифесте → вызывается handleIncomingUri()
 * → токен приходит подписчикам через tokenFlow.
 *
 * Гостевой режим не трогает бэкенд вообще, обрабатывается отдельно в UI.
 */
object AuthManager {

    private const val BASE_URL = "https://my.kitoftorvpn.fun"

    // Channel.BUFFERED чтобы не потерять токен, если подписчик ещё не подключился к моменту
    // прихода deep link (приложение было убито и запустилось по intent'у).
    private val tokenChannel = Channel<String>(Channel.BUFFERED)
    val tokenFlow = tokenChannel.receiveAsFlow()

    fun openLogin(context: Context)    = open(context, "/login?platform=android")
    fun openRegister(context: Context) = open(context, "/register?platform=android")
    fun openGoogle(context: Context)   = open(context, "/auth/google?platform=android")
    fun openTelegram(context: Context) = open(context, "/auth/telegram?platform=android")

    private fun open(context: Context, path: String) {
        val intent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        intent.launchUrl(context, Uri.parse("$BASE_URL$path"))
    }

    /**
     * Вызывается из MainActivity.onCreate/onNewIntent когда приходит intent со схемой
     * kitoftorvpn://. Парсит токен и пушит его в Flow.
     */
    fun handleIncomingUri(uri: Uri?) {
        if (uri == null) return
        if (uri.scheme != "kitoftorvpn" || uri.host != "auth") return
        val token = uri.getQueryParameter("token") ?: return
        if (token.isBlank()) return
        tokenChannel.trySend(token)
    }
}
