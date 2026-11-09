# KitoFtorVPN Android

Android-клиент KitoFtorVPN на базе AmneziaWG. Поддерживает конфигурации AmneziaWG 2.0 и 3.1.

## Возможности

* Подключение к VPN одним нажатием
* Импорт конфигурации из файла `.conf`
* Обход VPN для выбранных сайтов (split tunneling)
* Исключение приложений из VPN-туннеля
* Уведомление о неожиданном разрыве соединения
* Плитка быстрых настроек (Quick Settings Tile) для управления VPN из шторки
* Хранение конфигурации с шифрованием AES-256-GCM (Android Keystore)
* Автообновления через GitHub Releases

## Установка

Скачай последнюю версию `KitoFtorVPN.apk` со страницы [Releases](https://github.com/KitoFtorVPN/KitoFtorVPN-android/releases) и установи.

При первой установке Android попросит разрешение на установку приложений из этого источника — это нормально.

Файл конфигурации (`.conf`) выдаёт администратор. Один конфиг — одно устройство.

## Требования

* Android 8.0 (API 26) и выше

## Стек

* [Kotlin](https://kotlinlang.org/) + [Jetpack Compose](https://developer.android.com/jetpack/compose) — UI
* [WG Tunnel Core](https://github.com/wgtunnel/core) — VPN-движок (WireGuard / AmneziaWG)
* [AndroidX Security Crypto](https://developer.android.com/jetpack/androidx/releases/security) — шифрование хранилища
* [Navigation Compose](https://developer.android.com/jetpack/compose/navigation) — навигация
* [Gradle](https://gradle.org/) — сборка

## Сборка из исходников

Требуется свежая Android Studio и JDK 21.

```
# Открыть проект в Android Studio
File → Open → выбрать папку проекта

# Сборка debug-версии
./gradlew assembleDebug

# Сборка release-версии (требуется keystore)
./gradlew assembleRelease
```

APK появится в `app/build/outputs/apk/`.

## Структура

```
KitoFtorVPN-android/
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/fun/kitoftorvpn/android/
│       │   ├── MainActivity.kt
│       │   ├── notifications/   — уведомление о разрыве соединения
│       │   ├── perapp/          — исключения приложений из VPN
│       │   ├── settings/        — AppPreferences
│       │   ├── storage/         — ConfigStore (зашифрованное хранилище)
│       │   ├── ui/              — экраны Compose
│       │   ├── update/          — автообновление APK
│       │   ├── vpn/             — VPN-репозиторий, Quick Tile, Receiver
│       │   └── whitelist/       — split tunneling
│       └── res/                 — иконки, темы, строки
├── app/build.gradle.kts         — конфиг сборки
└── gradle/libs.versions.toml    — зависимости
```
