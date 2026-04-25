package `fun`.kitoftorvpn.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `fun`.kitoftorvpn.android.ui.theme.Bg
import `fun`.kitoftorvpn.android.ui.theme.Border
import `fun`.kitoftorvpn.android.ui.theme.Surface
import `fun`.kitoftorvpn.android.ui.theme.TextDim
import `fun`.kitoftorvpn.android.ui.theme.TextMain

private data class FaqItem(val question: String, val answer: String)

private val FAQ_LIST = listOf(
    FaqItem(
        "Что делать, если VPN не подключается?",
        "Сначала проверьте, что у вас активная подписка и загружен файл конфигурации (.conf). " +
        "Попробуйте перезапустить приложение и подключиться снова. " +
        "Если не помогло — удалите конфигурацию в настройках и загрузите её заново из личного кабинета. " +
        "Также убедитесь, что на телефоне нет другого активного VPN."
    ),
    FaqItem(
        "Почему интернет работает медленно с VPN?",
        "Скорость зависит от загруженности сервера и расстояния до него. " +
        "VPN всегда немного снижает скорость из-за дополнительных сетевых операций. " +
        "Если скорость сильно упала — проверьте сигнал Wi-Fi или мобильной сети, попробуйте переподключиться."
    ),
    FaqItem(
        "Как отключить VPN для конкретного приложения (например, банка)?",
        "Откройте настройки приложения → раздел «Сплит-туннелинг» → «Исключения приложений». " +
        "Поставьте галочки напротив тех приложений, которые должны работать напрямую, минуя VPN. " +
        "Изменения применятся автоматически."
    ),
    FaqItem(
        "Как добавить сайт в обход VPN?",
        "Настройки → «Сплит-туннелинг» → «Белый список». " +
        "Введите домены (например, example.com), IP или подсети — каждое значение с новой строки. " +
        "Сайты из списка будут открываться напрямую, минуя VPN."
    ),
    FaqItem(
        "Где купить подписку?",
        "Подписка покупается в личном кабинете на сайте my.kitoftorvpn.fun или через Telegram-бота. " +
        "После оплаты нужно скачать файл конфигурации (.conf) и загрузить его в приложение."
    ),
    FaqItem(
        "Что будет, когда подписка закончится?",
        "VPN перестанет подключаться, но приложение и сохранённый конфиг останутся. " +
        "После продления подписки на сайте всё снова заработает — заново загружать ничего не нужно. " +
        "За 3 дня до окончания вы получите уведомление."
    ),
    FaqItem(
        "Можно ли использовать на нескольких устройствах?",
        "Да, в рамках одной подписки можно использовать столько устройств, сколько вы выбрали при покупке. " +
        "Каждое устройство получает свой отдельный конфиг — скачайте нужный из личного кабинета."
    ),
    FaqItem(
        "Безопасно ли это? Видит ли кто-то мои данные?",
        "Используется протокол AmneziaWG 2.0 — современный стандарт защиты соединения. " +
        "Все данные между вашим устройством и сервером передаются по защищённому каналу. " +
        "Конфигурация и токен сессии хранятся в защищённом хранилище Android (Keystore) с AES-256-GCM."
    ),
    FaqItem(
        "Что такое гостевой режим?",
        "Гостевой режим — для тех, у кого уже есть готовый файл конфигурации (.conf). " +
        "Не требует регистрации, не проверяет подписку. Просто загрузите файл и подключайтесь. " +
        "Подходит, если конфиг вам выдали из другого аккаунта или вы хотите попробовать перед покупкой."
    ),
    FaqItem(
        "Как восстановить доступ, если сменил телефон?",
        "Зайдите на сайт my.kitoftorvpn.fun со своим аккаунтом, скачайте файл конфигурации и загрузите его в приложение на новом телефоне. " +
        "Подписка привязана к аккаунту, а не к устройству."
    ),
    FaqItem(
        "Уведомления не приходят — что делать?",
        "Проверьте, что в настройках приложения уведомления включены. " +
        "В системных настройках Android разрешите приложению показывать уведомления. " +
        "На некоторых телефонах (Samsung, Xiaomi, Huawei) нужно дополнительно отключить «оптимизацию батареи» для приложения, иначе система может блокировать фоновые задачи."
    ),
    FaqItem(
        "Кнопка «Отключить» в шторке уведомлений не работает",
        "Если кнопка не реагирует — попробуйте свернуть шторку и открыть заново. " +
        "Также проверьте, что приложение запущено в фоне (не закрыто принудительно). " +
        "Альтернативно — используйте плитку быстрых настроек (добавляется через редактирование панели быстрых настроек)."
    ),
)

@Composable
fun FaqScreen(onBack: () -> Unit = {}) {
    var expandedIndex by remember { mutableStateOf<Int?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Bg)) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(20.dp))

            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Surface)
                        .border(1.dp, Border, RoundedCornerShape(10.dp))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        imageVector = faqBackArrow(TextMain),
                        contentDescription = "Назад",
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.size(12.dp))
                Text(
                    "Помощь",
                    color = TextMain,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Частые вопросы и ответы. Если не нашли ответ — напишите в поддержку через сайт или Telegram.",
                color = TextDim,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)
            ) {
                itemsIndexed(FAQ_LIST, key = { idx, _ -> idx }) { index, item ->
                    FaqRow(
                        item = item,
                        expanded = expandedIndex == index,
                        onClick = {
                            expandedIndex = if (expandedIndex == index) null else index
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FaqRow(item: FaqItem, expanded: Boolean, onClick: () -> Unit) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200),
        label = "chevron"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.question,
                color = TextMain,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 20.sp,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.size(8.dp))
            androidx.compose.foundation.Image(
                imageVector = faqChevron(TextDim),
                contentDescription = null,
                modifier = Modifier.size(18.dp).rotate(rotation)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(200)) + fadeIn(animationSpec = tween(200)),
            exit = shrinkVertically(animationSpec = tween(150)) + fadeOut(animationSpec = tween(150))
        ) {
            Column {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Border))
                Text(
                    text = item.answer,
                    color = TextDim,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                )
            }
        }
    }
}

private fun faqBackArrow(color: Color): ImageVector =
    ImageVector.Builder(
        name = "Back", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).path(
        stroke = SolidColor(color),
        strokeLineWidth = 2.5f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(15f, 18f); lineTo(9f, 12f); lineTo(15f, 6f)
    }.build()

private fun faqChevron(color: Color): ImageVector =
    ImageVector.Builder(
        name = "Chevron", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).path(
        stroke = SolidColor(color),
        strokeLineWidth = 2.5f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f)
    }.build()
