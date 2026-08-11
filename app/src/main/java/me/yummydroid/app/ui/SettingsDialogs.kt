package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.yummydroid.app.BuildConfig
import me.yummydroid.app.LoadState
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.AppUpdateInfo
import me.yummydroid.app.data.SiteDomainResolver
import me.yummydroid.app.data.isNewerThanVersion
import me.yummydroid.app.data.normalizeSiteBaseUrl
import me.yummydroid.app.data.normalizedSiteBaseUrls
import me.yummydroid.app.readyDataOrNull
import me.yummydroid.app.ui.components.focusRing
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySurfaceRole
import me.yummydroid.app.ui.theme.yummySurfaceColor
import me.yummydroid.app.ui.theme.yummySurfaceContentColor

// SettingsDomainsDialog
@Composable
internal fun SettingsDomainsDialog(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var newDomain by remember(settings.siteDomains) { mutableStateOf("") }
    var domainError by remember(settings.siteDomains) { mutableStateOf<String?>(null) }
    val invalidDomainText = uiText(UiStringKey.InvalidDomain)
    val duplicateDomainText = uiText(UiStringKey.DomainIsAlreadyInTheList)

    AlertDialog(
        modifier = Modifier.yummyDialogMotion(),
        onDismissRequest = onDismiss,
        title = { Text("${uiText(UiStringKey.SiteDomains)} (${settings.siteDomains.size})") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(settings.siteDomains, key = { it }) { domain ->
                        Surface(
                            color = yummySurfaceColor(YummySurfaceRole.Row),
                            contentColor = yummySurfaceContentColor(YummySurfaceRole.Row),
                            shape = YummyRadii.smallShape,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = domain.domainDisplayTitle(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    enabled = settings.siteDomains.size > 1,
                                    onClick = {
                                        onSettingsChange(settings.copy(siteDomains = settings.siteDomains - domain))
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .focusRing(RoundedCornerShape(8.dp)),
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = uiText(UiStringKey.RemoveDomain))
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = newDomain,
                    onValueChange = {
                        newDomain = it
                        domainError = null
                    },
                    singleLine = true,
                    label = { Text(uiText(UiStringKey.NewDomain)) },
                    isError = domainError != null,
                    supportingText = domainError?.let { message -> { Text(message) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(1.dp),
                )
            }
        },
        confirmButton = {
            DialogActionRow {
                DialogActionButton(
                    text = uiText(UiStringKey.Reset),
                    onClick = {
                        newDomain = ""
                        domainError = null
                        onSettingsChange(settings.copy(siteDomains = SiteDomainResolver.DEFAULT_SITE_DOMAINS))
                    },
                )
                DialogActionButton(
                    text = uiText(UiStringKey.Close),
                    onClick = onDismiss,
                )
                DialogActionButton(
                    text = uiText(UiStringKey.Add),
                    primary = true,
                    onClick = {
                        val normalized = normalizeSiteBaseUrl(newDomain)
                        when {
                            normalized == null -> domainError = invalidDomainText
                            settings.siteDomains.any {
                                it.trimEnd('/').equals(normalized.trimEnd('/'), ignoreCase = true)
                            } -> domainError = duplicateDomainText
                            else -> {
                                onSettingsChange(
                                    settings.copy(
                                        siteDomains = (settings.siteDomains + normalized).normalizedSiteBaseUrls(),
                                    ),
                                )
                                newDomain = ""
                                domainError = null
                            }
                        }
                    },
                )
            }
        },
    )
}

internal fun String.domainDisplayTitle(): String {
    return removePrefix("https://")
        .removePrefix("http://")
        .trimEnd('/')
}

// SettingsUpdateDialog
@Composable
internal fun UpdateCheckDialog(
    updateState: LoadState<AppUpdateInfo?>,
    onInstallUpdate: (AppUpdateInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.yummyDialogMotion(),
        onDismissRequest = onDismiss,
        title = { Text(uiText(UiStringKey.Updates)) },
        text = {
            when (updateState) {
                LoadState.Loading -> LoadingPane(Modifier.height(120.dp))
                is LoadState.Error -> InlineErrorMessage(message = updateState.message)
                is LoadState.Ready -> {
                    val info = updateState.data
                    if (info == null) {
                        Text(uiText(UiStringKey.TheUpdateCheckHasNotBeenRunYet))
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val title = info.title.ifBlank { "YummyDroid ${info.version}" }
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                Text(
                                    text = info.body.ifBlank { uiText(UiStringKey.NoReleaseNotesYet) },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val info = updateState.readyDataOrNull()
            DialogActionRow {
                DialogActionButton(text = uiText(UiStringKey.Close), onClick = onDismiss)
                if (info?.apkUrl?.isNotBlank() == true && info.isNewerThanInstalled()) {
                    DialogActionButton(
                        text = uiText(UiStringKey.Refresh),
                        primary = true,
                        onClick = { onInstallUpdate(info) },
                    )
                }
            }
        },
    )
}

internal fun AppUpdateInfo.isNewerThanInstalled(): Boolean {
    return isNewerThanVersion(BuildConfig.VERSION_NAME)
}
