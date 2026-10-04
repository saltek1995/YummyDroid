package me.yummydroid.app.data

enum class PlaybackProvider { Unknown, Alloha, Cvh, Kodik, Aksor, Sibnet, Vk }

fun VideoVariant.playbackProvider(): PlaybackProvider {
    val name = player.cleanVideoSourceLabel().lowercase()
    return when {
        "alloha" in name -> PlaybackProvider.Alloha
        "cvh" in name || "cdnvideohub" in name -> PlaybackProvider.Cvh
        "kodik" in name -> PlaybackProvider.Kodik
        "aksor" in name -> PlaybackProvider.Aksor
        "sibnet" in name -> PlaybackProvider.Sibnet
        name == "vk" || name == "вк" -> PlaybackProvider.Vk
        else -> PlaybackProvider.Unknown
    }
}

/** Alternatives can change container; never carry the previous URL's MIME into the new load. */
data class PlaybackStreamAlternative(
    val url: String,
    val mimeType: String?,
    val headers: Map<String, String>,
    val videoHeight: Int? = null,
    val providerAudioId: String? = null,
)
