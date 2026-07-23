package me.yummydroid.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.abs
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.AnimeSort
import me.yummydroid.app.data.ContentLanguage
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.PlayerBufferPreset
import me.yummydroid.app.data.PlayerDecoderMode
import me.yummydroid.app.data.PosterCardSize
import me.yummydroid.app.data.PreferredQuality

internal val LocalUiLanguage = staticCompositionLocalOf { ContentLanguage.Russian }

@Composable
internal fun uiText(ru: String): String {
    val language = LocalUiLanguage.current
    return remember(language, ru) { translateUiText(ru, language) }
}
internal fun translateUiText(ru: String, language: ContentLanguage): String {
    if (language == ContentLanguage.Russian) return ru
    val dictionary = when (language) {
        ContentLanguage.English -> englishUiDictionary
        ContentLanguage.Ukrainian -> ukrainianUiDictionary
        ContentLanguage.Russian -> emptyMap()
    }
    return dictionary[ru] ?: ru
}

@Composable
internal fun localizedPluralWord(
    count: Long,
    oneRu: String,
    fewRu: String,
    manyRu: String,
    englishOne: String,
    englishMany: String,
): String {
    if (LocalUiLanguage.current == ContentLanguage.English) {
        return if (kotlin.math.abs(count) == 1L) englishOne else englishMany
    }
    val normalized = kotlin.math.abs(count)
    val mod100 = normalized % 100
    val mod10 = normalized % 10
    val key = when {
        mod100 in 11..14 -> manyRu
        mod10 == 1L -> oneRu
        mod10 in 2L..4L -> fewRu
        else -> manyRu
    }
    return uiText(key)
}

@Composable
internal fun localizedEpisodesWord(count: Int): String {
    return localizedPluralWord(
        count = count.toLong(),
        oneRu = "серия",
        fewRu = "серии",
        manyRu = "серий",
        englishOne = "episode",
        englishMany = "episodes",
    )
}

@Composable
internal fun localizedVotesWord(count: Long): String {
    return localizedPluralWord(
        count = count,
        oneRu = "голос",
        fewRu = "голоса",
        manyRu = "голосов",
        englishOne = "vote",
        englishMany = "votes",
    )
}

@Composable
internal fun BrowseSection.localizedTitle(): String = uiText(
    when (this) {
        BrowseSection.Catalog -> UiStringKey.BrowseCatalog
        BrowseSection.Schedule -> UiStringKey.BrowseSchedule
        BrowseSection.History -> UiStringKey.BrowseHistory
        BrowseSection.Downloads -> UiStringKey.BrowseDownloads
    },
)

internal fun visibleBrowseSections(isAuthorized: Boolean): List<BrowseSection> {
    return if (isAuthorized) {
        listOf(BrowseSection.Catalog, BrowseSection.Schedule, BrowseSection.History)
    } else {
        listOf(BrowseSection.Catalog, BrowseSection.Schedule)
    }
}

@Composable
internal fun PreferredQuality.localizedTitle(): String = when (this) {
    PreferredQuality.Auto -> uiText("Авто")
    else -> title
}

@Composable
internal fun PlayerDecoderMode.localizedTitle(): String = when (this) {
    PlayerDecoderMode.Auto -> uiText("Авто")
    PlayerDecoderMode.Hardware -> uiText("Аппаратный")
    PlayerDecoderMode.Software -> uiText("Программный")
}

@Composable
internal fun PlayerBufferPreset.localizedTitle(): String = uiText(title)

@Composable
internal fun PosterCardSize.localizedTitle(): String = when (this) {
    PosterCardSize.Compact -> uiText("Компактные")
    PosterCardSize.Standard -> uiText("Стандартные")
    PosterCardSize.Large -> uiText("Крупные")
}

internal fun PosterCardSize.resolveCatalogColumns(screenWidthDp: Int): Int {
    return when {
        screenWidthDp >= 1200 -> when (this) {
            PosterCardSize.Compact -> 6
            PosterCardSize.Standard -> 5
            PosterCardSize.Large -> 4
        }
        screenWidthDp >= 900 -> when (this) {
            PosterCardSize.Compact -> 5
            PosterCardSize.Standard -> 4
            PosterCardSize.Large -> 3
        }
        screenWidthDp >= 600 -> when (this) {
            PosterCardSize.Compact -> 4
            PosterCardSize.Standard -> 3
            PosterCardSize.Large -> 2
        }
        screenWidthDp >= 430 -> when (this) {
            PosterCardSize.Compact -> 3
            PosterCardSize.Standard -> 2
            PosterCardSize.Large -> 1
        }
        else -> when (this) {
            PosterCardSize.Compact -> 2
            PosterCardSize.Standard -> 2
            PosterCardSize.Large -> 1
        }
    }
}

@Composable
internal fun ContentLanguage.localizedTitle(): String = when (this) {
    ContentLanguage.Russian -> "Русский"
    ContentLanguage.English -> "English"
    ContentLanguage.Ukrainian -> "Українська"
}

@Composable
internal fun AnimeSort.localizedTitle(): String = uiText(
    when (this) {
        AnimeSort.Rating -> "Рейтинг"
        AnimeSort.RatingCounters -> "Оценок"
        AnimeSort.Views -> "Просмотры"
        AnimeSort.Year -> "Новые"
        AnimeSort.Top -> "Топ"
        AnimeSort.Title -> "А-Я"
        AnimeSort.Id -> "По добавлению"
        AnimeSort.Random -> "Случайно"
    },
)

@Composable
internal fun FilterOption.localizedTitle(): String = when (value) {
    "released" -> uiText("Вышло")
    "ongoing" -> uiText("Онгоинг")
    "announcement" -> uiText("Анонсы")
    "winter" -> uiText("Зима")
    "spring" -> uiText("Весна")
    "summer" -> uiText("Лето")
    "fall" -> uiText("Осень")
    "dubbing" -> uiText("Полное дублирование")
    "multivoice" -> uiText("Многоголосый")
    "duet" -> uiText("Двухголосый")
    "onevoice" -> uiText("Одноголосый")
    "subtitles" -> uiText("Субтитры")
    "0" -> uiText("Смотрю")
    "1" -> uiText("В планах")
    "2" -> uiText("Просмотрено")
    "3" -> uiText("Брошено")
    "4" -> uiText("Любимые")
    "5" -> uiText("Отложено")
    else -> title
}

internal fun ContentLanguage.voiceRecognizerTag(): String = when (this) {
    ContentLanguage.Russian -> "ru-RU"
    ContentLanguage.English -> "en-US"
    ContentLanguage.Ukrainian -> "uk-UA"
}

internal val englishUiDictionary = mapOf(
    "Поиск" to "Search",
    "Найти аниме" to "Find anime",
    "Голосовой поиск" to "Voice search",
    "Голосовой поиск недоступен на этом устройстве" to "Voice search is not available on this device",
    "Что найти?" to "What should I find?",
    "Готово" to "Done",
    "Установлена актуальная версия" to "The latest version is installed",
    "Проверка еще не выполнена." to "Update check has not run yet.",
    "Фильтры" to "Filters",
    "Расширенный режим" to "Advanced mode",
    "Сбросить" to "Reset",
    "Отмена" to "Cancel",
    "Применить" to "Apply",
    "Вход" to "Sign in",
    "Email" to "Email",
    "Пароль" to "Password",
    "Регистрация" to "Sign up",
    "Забыли пароль?" to "Forgot password?",
    "Войти" to "Sign in",
    "Аккаунт" to "Account",
    "Профиль" to "Profile",
    "ЛК" to "Profile",
    "Подписки" to "Subscriptions",
    "Подписок нет" to "No subscriptions",
    "Отключить" to "Disable",
    "Аниме" to "Anime",
    "Выйти" to "Sign out",
    "Закрыть" to "Close",
    "Настройки" to "Settings",
    "Хранилище" to "Storage",
    "Скачанные серии" to "Downloaded episodes",
    "Очистить кэш" to "Clear cache",
    "Видео, карточки и прогресс" to "Videos, cards and progress",
    "Потоки загрузки" to "Download threads",
    "Скачивать через мобильный интернет" to "Download over mobile data",
    "Воспроизведение" to "Playback",
    "Качество по умолчанию" to "Default quality",
    "Декодер" to "Decoder",
    "Объём буфера" to "Buffer size",
    "Автоподстройка экрана под видео" to "Match display to video",
    "Автовоспроизведение следующей серии" to "Autoplay next episode",
    "Каталог и оформление" to "Catalog and appearance",
    "Размер карточек" to "Card size",
    "Язык контента" to "App and content language",
    "Автоматические метки" to "Automatic marks",
    "Ставить «Смотрю» при воспроизведении" to "Mark as watching on playback",
    "Ставить «Просмотрено» после последней серии" to "Mark as watched after final episode",
    "Сеть" to "Network",
    "Уведомления приложения" to "App notifications",
    "Домены сайта" to "Site domains",
    "Проверять обновления при запуске" to "Check updates on startup",
    "О программе" to "About",
    "Версия" to "Version",
    "Проверить" to "Check",
    "Качество" to "Quality",
    "Скачать" to "Download",
    "Скачать всё" to "Download all",
    "Скачать все серии" to "Download all episodes",
    "Смотреть" to "Watch",
    "Продолжить" to "Continue",
    "Сбросить просмотр" to "Reset watch progress",
    "Удалить данные просмотра всех серий этого аниме?" to "Delete watch progress for all episodes of this anime?",
    "Показать все серии" to "Show all episodes",
    "Свернуть" to "Collapse",
    "Подписка" to "Subscription",
    "Подписан" to "Subscribed",
    "Озвучка" to "Voice",
    "Просмотр" to "Watch",
    "Серия" to "Episode",
    "Эпизод" to "Episode",
    "Похожие" to "Similar",
    "Комментарии" to "Comments",
    "Комментарий" to "Comment",
    "Трейлеры" to "Trailers",
    "Трейлер" to "Trailer",
    "Описание" to "Description",
    "Описание пока не добавлено." to "No description yet.",
    "Оценка" to "Rating",
    "Повторить" to "Retry",
    "Обновить" to "Refresh",
    "Назад" to "Back",
    "Каталог" to "Catalog",
    "Расписание" to "Schedule",
    "Загрузки" to "Downloads",
    "Очередь загрузок" to "Download queue",
    "Очистить" to "Clear",
    "Пусто" to "Empty",
    "Загрузка" to "Loading",
    "Ошибка" to "Error",
    "Скачано" to "Downloaded",
    "Пауза" to "Paused",
    "Добавлено" to "Added",
    "Авто" to "Auto",
    "Аппаратный" to "Hardware",
    "Программный" to "Software",
    "Компактный" to "Compact",
    "Стандартный" to "Standard",
    "Большой" to "Large",
    "Максимальный" to "Maximum",
    "Компактные" to "Compact",
    "Стандартные" to "Standard",
    "Крупные" to "Large",
    "Оффлайн" to "Offline",
    "Ничего не найдено" to "Nothing found",
    "Каталог пуст" to "Catalog is empty",
    "Расписание пока пустое" to "Schedule is empty",
    "По выбранным фильтрам ничего не найдено" to "No items match the selected filters",
    "Ближайших выходов пока нет" to "No upcoming releases yet",
    "Прошедшие скрыты" to "Past releases hidden",
    "Прошедшие показаны" to "Past releases shown",
    "Доступно офлайн" to "Available offline",
    "В очереди" to "Queued",
    "Отменено" to "Cancelled",
    "Язык приложения и контента" to "App and content language",
    "Скачанных серий пока нет" to "No downloaded episodes yet",
    "скрыто" to "hidden",
    "прошедших" to "past",
    "серий" to "episodes",
    "доменов" to "domains",
    "Будут удалены скачанные серии, кэш карточек аниме и локальный прогресс просмотра. Аккаунт и авторизация останутся." to
        "Downloaded episodes, cached anime cards and local playback progress will be deleted. Account and authorization will remain.",
    "Вы не авторизованы." to "You are not signed in.",
    "Пользователь" to "User",
    "Аккаунт заблокирован на сайте." to "The account is blocked on the site.",
    "О себе" to "About",
    "Роли" to "Roles",
    "Нет" to "No",
    "Уведомления" to "Notifications",
    "Сообщения" to "Messages",
    "Библиотека" to "Library",
    "Не удалось открыть сайт" to "Could not open the site",
    "Сортировка" to "Sorting",
    "Статус" to "Status",
    "Жанры" to "Genres",
    "Исключить жанры" to "Exclude genres",
    "Тип" to "Type",
    "Год" to "Year",
    "Сезон" to "Season",
    "Ограничение" to "Restriction",
    "Рейтинг" to "Rating",
    "Серии" to "Episodes",
    "Метки" to "Marks",
    "От" to "From",
    "До" to "To",
    "Вышло" to "Released",
    "Онгоинг" to "Ongoing",
    "Анонсы" to "Announcements",
    "Зима" to "Winter",
    "Весна" to "Spring",
    "Лето" to "Summer",
    "Осень" to "Fall",
    "Полное дублирование" to "Full dubbing",
    "Многоголосый" to "Multi-voice",
    "Двухголосый" to "Two-voice",
    "Одноголосый" to "Single-voice",
    "Субтитры" to "Subtitles",
    "Выкл." to "Off",
    "Смотрю" to "Watching",
    "В планах" to "Planned",
    "Просмотрено" to "Watched",
    "Брошено" to "Dropped",
    "Любимые" to "Favorites",
    "Отложено" to "Postponed",
    "Оценок" to "Votes",
    "Просмотры" to "Views",
    "Новые" to "New",
    "Топ" to "Top",
    "А-Я" to "A-Z",
    "По добавлению" to "Recently added",
    "Случайно" to "Random",
    "Оффлайн: фильтруются только скачанные аниме" to "Offline: only downloaded anime are shown",
    "Все" to "All",
    "от" to "from",
    "до" to "to",
    "выбрано" to "selected",
    "Удалить аниме" to "Delete anime",
    "Удалить серию" to "Delete episode",
    "Обновления" to "Updates",
    "Проверка еще не выполнена." to "The update check has not been run yet.",
    "Описание версии пока не добавлено." to "No release notes yet.",
    "Выбери озвучку" to "Choose voice",
    "Далее" to "Next",
    "Удалить домен" to "Remove domain",
    "Новый домен" to "New domain",
    "Добавить" to "Add",
    "Некорректный домен" to "Invalid domain",
    "Домен уже в списке" to "Domain is already in the list",
    "Карточка не найдена" to "Anime card not found",
    "Войти в аккаунт" to "Sign in to account",
    "Синхронизация" to "Syncing",
    "Жанр" to "Genre",
    "Год выхода" to "Year",
    "Альт. названия" to "Alt. titles",
    "Первоисточник" to "Source",
    "Студия" to "Studio",
    "Режиссёр" to "Director",
    "До выхода" to "Until release",
    "Длительность" to "Duration",
    "В списках" to "In lists",
    "Кадры" to "Screenshots",
    "Порядок просмотра" to "Watch order",
    "Загружено" to "Downloaded",
    "серия" to "episode",
    "серии" to "episodes",
    "голос" to "vote",
    "голоса" to "votes",
    "голосов" to "votes",
    "Пока нет оценок" to "No ratings yet",
    "загружено" to "loaded",
    "Отправить" to "Send",
    "Видео для этого аниме пока нет" to "No videos for this anime yet",
    "Скачать серию" to "Download episode",
    "Удалить скачанную серию" to "Delete downloaded episode",
    "Удалить" to "Delete",
    "Все скачанные варианты" to "All downloaded variants",
    "Предыдущая серия" to "Previous episode",
    "Следующая серия" to "Next episode",
    "Еще раз" to "Try again",
    "офлайн" to "offline",
    "локально" to "local",
    "скачано" to "downloaded",
    "Пропустить" to "Skip",
    "Следующая" to "Next",
    "из" to "of",
    "Войдите в аккаунт, чтобы открыть библиотеку" to "Sign in to open the library",
    "Библиотека пока пустая" to "The library is empty",
    "Поставить на паузу" to "Pause",
    "Возобновить загрузку" to "Resume download",
    "Отменить загрузку" to "Cancel download",
    "неизвестно" to "unknown",
    "с" to "s",
)

internal val ukrainianUiDictionary = mapOf(
    "Поиск" to "Пошук",
    "Найти аниме" to "Знайти аніме",
    "Голосовой поиск" to "Голосовий пошук",
    "Голосовой поиск недоступен на этом устройстве" to "Голосовий пошук недоступний на цьому пристрої",
    "Что найти?" to "Що знайти?",
    "Готово" to "Готово",
    "Установлена актуальная версия" to "Встановлено актуальну версію",
    "Проверка еще не выполнена." to "Перевірку ще не виконано.",
    "Фильтры" to "Фільтри",
    "Расширенный режим" to "Розширений режим",
    "Сбросить" to "Скинути",
    "Отмена" to "Скасувати",
    "Применить" to "Застосувати",
    "Вход" to "Вхід",
    "Email" to "Email",
    "Пароль" to "Пароль",
    "Регистрация" to "Реєстрація",
    "Забыли пароль?" to "Забули пароль?",
    "Войти" to "Увійти",
    "Аккаунт" to "Акаунт",
    "Профиль" to "Профіль",
    "ЛК" to "Профіль",
    "Подписки" to "Підписки",
    "Подписок нет" to "Підписок немає",
    "Отключить" to "Вимкнути",
    "Аниме" to "Аніме",
    "Выйти" to "Вийти",
    "Закрыть" to "Закрити",
    "Настройки" to "Налаштування",
    "Хранилище" to "Сховище",
    "Скачанные серии" to "Завантажені серії",
    "Очистить кэш" to "Очистити кеш",
    "Видео, карточки и прогресс" to "Відео, картки та прогрес",
    "Потоки загрузки" to "Потоки завантаження",
    "Скачивать через мобильный интернет" to "Завантажувати через мобільний інтернет",
    "Воспроизведение" to "Відтворення",
    "Качество по умолчанию" to "Якість за замовчуванням",
    "Декодер" to "Декодер",
    "Объём буфера" to "Обсяг буфера",
    "Автоподстройка экрана под видео" to "Автопідлаштування екрана під відео",
    "Автовоспроизведение следующей серии" to "Автовідтворення наступної серії",
    "Каталог и оформление" to "Каталог і вигляд",
    "Размер карточек" to "Розмір карток",
    "Язык контента" to "Мова застосунку й контенту",
    "Автоматические метки" to "Автоматичні мітки",
    "Ставить «Смотрю» при воспроизведении" to "Ставити «Дивлюся» під час відтворення",
    "Ставить «Просмотрено» после последней серии" to "Ставити «Переглянуто» після останньої серії",
    "Сеть" to "Мережа",
    "Уведомления приложения" to "Сповіщення застосунку",
    "Домены сайта" to "Домени сайту",
    "Проверять обновления при запуске" to "Перевіряти оновлення під час запуску",
    "О программе" to "Про застосунок",
    "Версия" to "Версія",
    "Проверить" to "Перевірити",
    "Качество" to "Якість",
    "Скачать" to "Завантажити",
    "Скачать всё" to "Завантажити все",
    "Скачать все серии" to "Завантажити всі серії",
    "Смотреть" to "Дивитися",
    "Продолжить" to "Продовжити",
    "Сбросить просмотр" to "Скинути перегляд",
    "Удалить данные просмотра всех серий этого аниме?" to "Видалити дані перегляду всіх серій цього аніме?",
    "Показать все серии" to "Показати всі серії",
    "Свернуть" to "Згорнути",
    "Подписка" to "Підписка",
    "Подписан" to "Підписано",
    "Озвучка" to "Озвучення",
    "Просмотр" to "Перегляд",
    "Серия" to "Серія",
    "Эпизод" to "Епізод",
    "Похожие" to "Схожі",
    "Комментарии" to "Коментарі",
    "Комментарий" to "Коментар",
    "Трейлеры" to "Трейлери",
    "Трейлер" to "Трейлер",
    "Описание" to "Опис",
    "Описание пока не добавлено." to "Опис поки не додано.",
    "Оценка" to "Оцінка",
    "Повторить" to "Повторити",
    "Обновить" to "Оновити",
    "Назад" to "Назад",
    "Каталог" to "Каталог",
    "Расписание" to "Розклад",
    "Загрузки" to "Завантаження",
    "Очередь загрузок" to "Черга завантажень",
    "Очистить" to "Очистити",
    "Пусто" to "Порожньо",
    "Загрузка" to "Завантаження",
    "Ошибка" to "Помилка",
    "Скачано" to "Завантажено",
    "Пауза" to "Пауза",
    "Авто" to "Авто",
    "Аппаратный" to "Апаратний",
    "Программный" to "Програмний",
    "Компактный" to "Компактний",
    "Стандартный" to "Стандартний",
    "Большой" to "Великий",
    "Максимальный" to "Максимальний",
    "Компактные" to "Компактні",
    "Стандартные" to "Стандартні",
    "Крупные" to "Великі",
    "Оффлайн" to "Офлайн",
    "Ничего не найдено" to "Нічого не знайдено",
    "Каталог пуст" to "Каталог порожній",
    "Расписание пока пустое" to "Розклад поки порожній",
    "По выбранным фильтрам ничего не найдено" to "За вибраними фільтрами нічого не знайдено",
    "Ближайших выходов пока нет" to "Найближчих виходів поки немає",
    "Прошедшие скрыты" to "Минулі приховано",
    "Прошедшие показаны" to "Минулі показано",
    "Доступно офлайн" to "Доступно офлайн",
    "В очереди" to "У черзі",
    "Отменено" to "Скасовано",
    "Язык приложения и контента" to "Мова застосунку й контенту",
    "Скачанных серий пока нет" to "Завантажених серій поки немає",
    "скрыто" to "приховано",
    "прошедших" to "минулих",
    "серий" to "серій",
    "доменов" to "доменів",
    "Будут удалены скачанные серии, кэш карточек аниме и локальный прогресс просмотра. Аккаунт и авторизация останутся." to
        "Буде видалено завантажені серії, кеш карток аніме та локальний прогрес перегляду. Акаунт і авторизація залишаться.",
    "Вы не авторизованы." to "Ви не авторизовані.",
    "Пользователь" to "Користувач",
    "Аккаунт заблокирован на сайте." to "Акаунт заблоковано на сайті.",
    "О себе" to "Про себе",
    "Роли" to "Ролі",
    "Нет" to "Немає",
    "Уведомления" to "Сповіщення",
    "Сообщения" to "Повідомлення",
    "Библиотека" to "Бібліотека",
    "Не удалось открыть сайт" to "Не вдалося відкрити сайт",
    "Сортировка" to "Сортування",
    "Статус" to "Статус",
    "Жанры" to "Жанри",
    "Исключить жанры" to "Виключити жанри",
    "Тип" to "Тип",
    "Год" to "Рік",
    "Сезон" to "Сезон",
    "Ограничение" to "Обмеження",
    "Рейтинг" to "Рейтинг",
    "Серии" to "Серії",
    "Метки" to "Мітки",
    "От" to "Від",
    "До" to "До",
    "Вышло" to "Вийшло",
    "Онгоинг" to "Онгоїнг",
    "Анонсы" to "Анонси",
    "Зима" to "Зима",
    "Весна" to "Весна",
    "Лето" to "Літо",
    "Осень" to "Осінь",
    "Полное дублирование" to "Повне дублювання",
    "Многоголосый" to "Багатоголосий",
    "Двухголосый" to "Двоголосий",
    "Одноголосый" to "Одноголосий",
    "Субтитры" to "Субтитри",
    "Выкл." to "Вимк.",
    "Смотрю" to "Дивлюся",
    "В планах" to "У планах",
    "Просмотрено" to "Переглянуто",
    "Брошено" to "Кинуто",
    "Любимые" to "Улюблені",
    "Отложено" to "Відкладено",
    "Оценок" to "Оцінок",
    "Просмотры" to "Перегляди",
    "Новые" to "Нові",
    "Топ" to "Топ",
    "А-Я" to "А-Я",
    "По добавлению" to "За додаванням",
    "Случайно" to "Випадково",
    "Оффлайн: фильтруются только скачанные аниме" to "Офлайн: показуються лише завантажені аніме",
    "Все" to "Усі",
    "от" to "від",
    "до" to "до",
    "выбрано" to "вибрано",
    "Удалить аниме" to "Видалити аніме",
    "Удалить серию" to "Видалити серію",
    "Обновления" to "Оновлення",
    "Проверка еще не выполнена." to "Перевірку оновлень ще не виконано.",
    "Описание версии пока не добавлено." to "Опис версії поки не додано.",
    "Выбери озвучку" to "Вибери озвучення",
    "Далее" to "Далі",
    "Удалить домен" to "Видалити домен",
    "Новый домен" to "Новий домен",
    "Добавить" to "Додати",
    "Некорректный домен" to "Некоректний домен",
    "Домен уже в списке" to "Домен уже у списку",
    "Карточка не найдена" to "Картку аніме не знайдено",
    "Войти в аккаунт" to "Увійти в акаунт",
    "Синхронизация" to "Синхронізація",
    "Жанр" to "Жанр",
    "Год выхода" to "Рік виходу",
    "Альт. названия" to "Альт. назви",
    "Первоисточник" to "Першоджерело",
    "Студия" to "Студія",
    "Режиссёр" to "Режисер",
    "До выхода" to "До виходу",
    "Длительность" to "Тривалість",
    "В списках" to "У списках",
    "Кадры" to "Кадри",
    "Порядок просмотра" to "Порядок перегляду",
    "Загружено" to "Завантажено",
    "серия" to "серія",
    "серии" to "серії",
    "голос" to "голос",
    "голоса" to "голоси",
    "голосов" to "голосів",
    "Пока нет оценок" to "Оцінок поки немає",
    "загружено" to "завантажено",
    "Отправить" to "Надіслати",
    "Видео для этого аниме пока нет" to "Відео для цього аніме поки немає",
    "Скачать серию" to "Завантажити серію",
    "Удалить скачанную серию" to "Видалити завантажену серію",
    "Удалить" to "Видалити",
    "Все скачанные варианты" to "Усі завантажені варіанти",
    "Предыдущая серия" to "Попередня серія",
    "Следующая серия" to "Наступна серія",
    "Еще раз" to "Ще раз",
    "офлайн" to "офлайн",
    "локально" to "локально",
    "скачано" to "завантажено",
    "Пропустить" to "Пропустити",
    "Следующая" to "Наступна",
    "из" to "з",
    "Войдите в аккаунт, чтобы открыть библиотеку" to "Увійдіть в акаунт, щоб відкрити бібліотеку",
    "Библиотека пока пустая" to "Бібліотека поки порожня",
    "Поставить на паузу" to "Поставити на паузу",
    "Возобновить загрузку" to "Відновити завантаження",
    "Отменить загрузку" to "Скасувати завантаження",
    "неизвестно" to "невідомо",
    "с" to "с",
)
