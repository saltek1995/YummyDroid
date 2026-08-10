package me.yummydroid.app.data

import okhttp3.OkHttpClient

class YummyAnimeApi(
    client: OkHttpClient = defaultYummyAnimeApiClient,
    initialContentLanguage: ContentLanguage = ContentLanguage.Russian,
) : YummyAnimeApiRuntime(client, initialContentLanguage)
