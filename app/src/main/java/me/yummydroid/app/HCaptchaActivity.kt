package me.yummydroid.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.hcaptcha.sdk.HCaptcha
import com.hcaptcha.sdk.HCaptchaConfig
import com.hcaptcha.sdk.HCaptchaException
import com.hcaptcha.sdk.HCaptchaSize
import com.hcaptcha.sdk.HCaptchaTheme
import com.hcaptcha.sdk.HCaptchaTokenResponse

class HCaptchaActivity : FragmentActivity() {
    private val hCaptcha by lazy { HCaptcha.getClient(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = HCaptchaConfig.builder()
            .siteKey(SITE_KEY)
            .size(HCaptchaSize.NORMAL)
            .theme(HCaptchaTheme.DARK)
            .locale("ru")
            .build()

        hCaptcha.addOnSuccessListener { response: HCaptchaTokenResponse ->
            response.markUsed()
            setResult(
                Activity.RESULT_OK,
                Intent().putExtra(EXTRA_CAPTCHA_TOKEN, response.tokenResult),
            )
            finish()
        }
        hCaptcha.addOnFailureListener { error: HCaptchaException ->
            setResult(
                Activity.RESULT_CANCELED,
                Intent().putExtra(EXTRA_CAPTCHA_ERROR, error.message),
            )
            finish()
        }
        hCaptcha.setup(config).verifyWithHCaptcha()
    }

    override fun onDestroy() {
        hCaptcha.removeAllListeners()
        hCaptcha.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CAPTCHA_TOKEN = "captcha_token"
        const val EXTRA_CAPTCHA_ERROR = "captcha_error"
        private const val SITE_KEY = "b1847961-208e-4a90-9671-1e6bba9e0b36"
    }
}
