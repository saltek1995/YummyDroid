package me.yummydroid.app


internal fun <T> LoadState<T>.readyDataOrNull(): T? = (this as? LoadState.Ready)?.data

internal fun <T> LoadState<List<T>>.readyListOrEmpty(): List<T> = readyDataOrNull().orEmpty()
