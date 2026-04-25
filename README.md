# KitoFtorVPN Android

Android-клиент KitoFtorVPN на базе AmneziaWG 2.0.

## Возможности

* Подключение к VPN одним нажатием
* Обход VPN для выбранных сайтов (split tunneling)
* Исключение приложений из VPN-туннеля
* Уведомления об окончании подписки
* Плитка быстрых настроек (Quick Settings Tile) для управления VPN из шторки
* Хранение конфигов и токенов с шифрованием AES-256-GCM (Android Keystore)
* Авторизация: email, Google, Telegram, гостевой режим
* Реферальная программа
* Автообновления через GitHub Releases

## Установка

Скачай последнюю версию `KitoFtorVPN.apk` со страницы [Releases](https://github.com/KitoFtorVPN/KitoFtorVPN-android/releases) и установи.

При первой установке Android попросит разрешение на установку приложений из этого источника — это нормально.

## Требования

* Android 8.0 (API 26) и выше

## Стек

* [Kotlin](https://kotlinlang.org/) + [Jetpack Compose](https://developer.android.com/jetpack/compose) — UI
* [amneziawg-android](https://github.com/amnezia-vpn/amneziawg-android) — VPN-движок
* [AndroidX Security Crypto](https://developer.android.com/jetpack/androidx/releases/security) — шифрование хранилища
* [Navigation Compose](https://developer.android.com/jetpack/compose/navigation) — навигация
* [Gradle](https://gradle.org/) — сборка

## Сборка из исходников

Требуется Android Studio (Iguana или новее) и JDK 17.

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
│       │   ├── api/             — клиент API кабинета (sub, referral)
│       │   ├── auth/            — OAuth-флоу через браузер
│       │   ├── notifications/   — напоминания о подписке (AlarmManager)
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

## Сайт

[kitoftorvpn.fun](https://kitoftorvpn.fun)
