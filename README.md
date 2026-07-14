# YummyDroid

<p align="center">
<img src="YummyDroid_true_transparent.png" alt="YummyDroid" width="820">
</p>

<p align="center">
  <a href="https://github.com/saltek1995/YummyDroid/releases/latest">Последняя версия</a>
  ·
  <a href="https://api.yani.tv/swagger">API YummyAnime</a>
</p>

YummyDroid — неофициальный клиент YummyAnime для Android, Android TV, телефонов, планшетов и ТВ-приставок.

## Возможности

- Каталог, расписание, история просмотра и очередь загрузок.
- Поиск, голосовой поиск, сортировка и расширенные фильтры.
- Авторизация, профиль, метки, избранное, оценки, комментарии и подписки на озвучки.
- Карточки аниме с описанием, кадрами, жанрами, студиями, режиссёрами, порядком просмотра, рекомендациями и трейлерами.
- Нативный плеер на Media3/ExoPlayer: озвучки, качество, PiP, продолжение просмотра, автопереход к следующей серии и пропуск OP/ED.
- Офлайн-просмотр: загрузка отдельных серий или всего аниме с выбором озвучки и качества.
- Фоновая очередь загрузок, пауза, продолжение, ограничение мобильного интернета и системное уведомление о прогрессе.
- Автоматическое переключение доменов YummyAnime и ручное управление списком доменов.
- Проверка обновлений через GitHub Releases.

## Установка

APK доступен на странице релизов:

https://github.com/saltek1995/YummyDroid/releases/latest

## Сборка

Требования:

- Android SDK.
- JDK 21.

Собрать release APK:

```powershell
.\gradlew.bat :app:assembleRelease
```

Проверить компиляцию Kotlin:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```
