package me.yummydroid.app

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.annotation.StringRes
import java.util.Locale
import me.yummydroid.app.data.ContentLanguage

internal fun Context.localizedString(@StringRes resId: Int, language: ContentLanguage): String {
    return localizedConfigurationContext(language).getString(resId)
}

internal fun Context.localizedString(
    @StringRes resId: Int,
    language: ContentLanguage,
    vararg formatArgs: Any,
): String {
    return localizedConfigurationContext(language).getString(resId, *formatArgs)
}

private fun Context.localizedConfigurationContext(language: ContentLanguage): Context {
    val configuration = Configuration(resources.configuration)
    configuration.setLocales(LocaleList(Locale.forLanguageTag(language.languageTag)))
    return createConfigurationContext(configuration)
}

private val ContentLanguage.languageTag: String
    get() = when (this) {
        ContentLanguage.Russian -> "ru"
        ContentLanguage.English -> "en"
        ContentLanguage.Ukrainian -> "uk"
    }
