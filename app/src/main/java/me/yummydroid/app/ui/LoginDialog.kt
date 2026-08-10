package me.yummydroid.app.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.HCaptchaActivity
import me.yummydroid.app.ui.components.focusRing

@Composable
internal fun CaptchaChallengeEffect(
    requestNonce: Long,
    onSolved: (String) -> Unit,
    onCanceled: (String?) -> Unit,
) {
    val context = LocalContext.current
    var handledNonce by remember { mutableLongStateOf(0L) }
    val captchaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val token = result.data
            ?.getStringExtra(HCaptchaActivity.EXTRA_CAPTCHA_TOKEN)
            .orEmpty()
        if (result.resultCode == Activity.RESULT_OK && token.isNotBlank()) {
            onSolved(token)
        } else {
            onCanceled(result.data?.getStringExtra(HCaptchaActivity.EXTRA_CAPTCHA_ERROR))
        }
    }

    LaunchedEffect(requestNonce) {
        if (requestNonce > 0L && requestNonce != handledNonce) {
            handledNonce = requestNonce
            captchaLauncher.launch(Intent(context, HCaptchaActivity::class.java))
        }
    }
}

@Composable
internal fun LoginDialog(
    auth: AuthUiState,
    siteBaseUrl: String,
    onLogin: (String, String, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(auth.profile) {
        if (auth.profile != null) {
            onDismiss()
        }
    }

    AlertDialog(
        modifier = Modifier.yummyDialogMotion(),
        onDismissRequest = onDismiss,
        title = { Text(uiText(UiStringKey.SignIn07205a)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = login,
                    onValueChange = { login = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                    label = { Text(uiText(UiStringKey.Email)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(1.dp),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text(uiText(UiStringKey.Password)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(1.dp),
                )
                auth.error?.let { message ->
                    InlineErrorMessage(message = message)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, sitePageUrl(siteBaseUrl, "register").toUri()),
                            )
                        },
                        modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
                    ) {
                        Text(uiText(UiStringKey.SignUp), maxLines = 1, softWrap = false)
                    }
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, sitePageUrl(siteBaseUrl, "login/reset-password").toUri()),
                            )
                        },
                        modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
                    ) {
                        Text(uiText(UiStringKey.ForgotPassword), maxLines = 1, softWrap = false)
                    }
                }
            }
        },
        confirmButton = {
            DialogActionRow {
                DialogActionButton(
                    text = uiText(UiStringKey.Cancel),
                    onClick = onDismiss,
                )
                DialogActionButton(
                    text = uiText(UiStringKey.SignIn),
                    primary = true,
                    enabled = !auth.loading,
                    loading = auth.loading,
                    onClick = { onLogin(login, password, null) },
                )
            }
        },
    )
}
