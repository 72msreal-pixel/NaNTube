package com.nan.nantube.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.DisposableEffect
import com.nan.nantube.MainActivity
import com.nan.nantube.SignInKeepaliveService
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Tv
import com.composables.icons.lucide.User
import com.nan.nantube.data.Account
import com.nan.nantube.data.AccountStore
import com.nan.nantube.data.DeviceCode
import com.nan.nantube.data.DeviceCodeAuth
import com.nan.nantube.data.InnerTube
import com.nan.nantube.data.LoginKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

// ------------------------------------------------------------------------------------------
// Bảng chọn khi bấm nút "+": tự nhập hoặc dùng mã
// ------------------------------------------------------------------------------------------

@Composable
fun AddAccountChooser(onDismiss: () -> Unit, onManual: () -> Unit, onCode: () -> Unit) {
    BackHandler(onBack = onDismiss)
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)).plainClickable(onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.72f)
                .clip(RoundedCornerShape(12.dp))
                .background(NanColors.Popup)
                .plainClickable { }
                .padding(vertical = 8.dp)
        ) {
            Text(
                "Thêm tài khoản",
                fontSize = 18.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            ChooserRow(Lucide.User, "Tự nhập Gmail", "Đăng nhập ngay trên trang của Google", onManual)
            ChooserRow(Lucide.Tv, "Dùng mã", "Lấy mã rồi nhập tại yt.be/activate", onCode)
        }
    }
}

@Composable
private fun ChooserRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, fontSize = 14.sp)
            Text(subtitle, fontSize = 12.sp, color = NanColors.TextSecondary)
        }
    }
}

@Composable
private fun SignInHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Lucide.ArrowLeft,
            null,
            Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack).padding(10.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 22.sp)
    }
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFF1F1F1))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

// ------------------------------------------------------------------------------------------
// Đăng nhập bằng mã (device code)
// ------------------------------------------------------------------------------------------

private sealed interface CodeState {
    data object Loading : CodeState
    data class Ready(val code: DeviceCode) : CodeState
    data object Finishing : CodeState
    data class Failed(val message: String) : CodeState
}

@Composable
fun CodeSignInScreen(onSuccess: () -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<CodeState>(CodeState.Loading) }
    var copied by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { SignInKeepaliveService.stop(context) } }

    // Mỗi lần "Thử lại" là một mã mới; thoát màn hình thì coroutine bị huỷ, dừng poll.
    LaunchedEffect(attempt) {
        state = CodeState.Loading
        copied = false
        try {
            val code = DeviceCodeAuth.requestCode()
            state = CodeState.Ready(code)
            val tokens = DeviceCodeAuth.awaitTokens(code)
            state = CodeState.Finishing
            AccountStore.upsert(InnerTube.accountForTokens(tokens))
            SignInKeepaliveService.stop(context)
            // Giống success auto-return của meetube: mở lại MainActivity (CLEAR_TOP|SINGLE_TOP) để
            // đóng Custom Tab đang nằm trên app trong cùng task, đưa người dùng về thẳng đây.
            try {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
            } catch (_: Exception) {
            }
            onSuccess()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state = CodeState.Failed(e.shortMessage ?: "Đăng nhập thất bại.")
        }
    }

    Surface(Modifier.fillMaxSize(), color = NanColors.Background, contentColor = Color.White) {
        Column(Modifier.fillMaxSize()) {
            SignInHeader("Đăng nhập bằng mã", onClose)
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp).navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (val s = state) {
                    CodeState.Loading, CodeState.Finishing -> {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                        if (s == CodeState.Finishing) {
                            Spacer(Modifier.height(16.dp))
                            Text("Đang hoàn tất…", color = NanColors.TextSecondary, fontSize = 14.sp)
                        }
                    }
                    is CodeState.Ready -> {
                        Text(
                            "Mở ${s.code.url.removePrefix("https://")} rồi nhập mã này:",
                            fontSize = 16.sp,
                            color = NanColors.TextSoft,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(
                            s.code.userCode,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(NanColors.Surface)
                                .clickable {
                                    clipboard.setText(AnnotatedString(s.code.userCode))
                                    copied = true
                                }
                                .padding(horizontal = 24.dp, vertical = 14.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (copied) "Đã sao chép mã" else "Chạm vào mã để sao chép",
                            fontSize = 13.sp,
                            color = NanColors.TextSecondary
                        )
                        Spacer(Modifier.height(28.dp))
                        PillButton("Mở trang xác nhận") {
                            // Custom Tab ghim vào trình duyệt, nằm trong task của app (không bị app YouTube chặn link),
                            // kèm service giữ mạng để poll vẫn chạy khi trang duyệt phủ lên.
                            openInBrowserTab(context, s.code.fullUrl)
                            SignInKeepaliveService.start(context)
                        }
                        Spacer(Modifier.height(24.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = NanColors.TextSecondary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Đang chờ bạn xác nhận…", fontSize = 14.sp, color = NanColors.TextSecondary)
                        }
                    }
                    is CodeState.Failed -> {
                        Text(
                            "Không đăng nhập được",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(s.message, fontSize = 14.sp, color = NanColors.TextSecondary, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(24.dp))
                        PillButton("Thử lại") { attempt++ }
                    }
                }
            }
        }
    }
}

/**
 * Giống Utils.openLinkInBrowserTab của meetube: Custom Tab ghim vào một trình duyệt thật để app YouTube
 * (nếu có) không chặn link, và trang nằm trong task của app nên đóng lại được khi đăng nhập xong.
 */
fun openInBrowserTab(context: Context, url: String) {
    try {
        val pkg = CustomTabsClient.getPackageName(context, null)
        if (pkg == null) {
            openInBrowser(context, url)
            return
        }
        val tab = CustomTabsIntent.Builder().build()
        tab.intent.setPackage(pkg)
        tab.launchUrl(context, Uri.parse(url))
    } catch (_: Exception) {
        openInBrowser(context, url)
    }
}

/** Mở link bằng trình duyệt mặc định (không dùng Custom Tab). */
fun openInBrowser(context: Context, url: String) {
    val uri = Uri.parse(url)
    try {
        val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("http://"))
        val browser = context.packageManager.resolveActivity(probe, 0)?.activityInfo?.packageName
        if (browser != null && browser != "android") intent.setPackage(browser)
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) {
            // Không có trình duyệt nào: người dùng vẫn có thể nhập mã thủ công.
        }
    }
}

// ------------------------------------------------------------------------------------------
// Tự nhập Gmail (trang đăng nhập Google trong WebView, theo MobileWebSignInActivity của meetube)
// ------------------------------------------------------------------------------------------

private const val WEB_SIGN_IN_URL = "https://accounts.google.com/ServiceLogin?service=youtube&prompt=select_account" +
    "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26app%3Dm%26next%3D%252F"
private const val WEB_ADD_URL = "https://accounts.google.com/AddSession?service=youtube" +
    "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26app%3Dm%26next%3D%252F"
private const val MAX_SLOTS = 10

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebSignInScreen(loginHint: String?, onSuccess: () -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    // Đã có tài khoản web thì thêm vào cùng phiên (giữ cookie cũ), chưa có thì làm sạch rồi đăng nhập mới.
    val adding = remember { AccountStore.accounts.any { it.kind == LoginKind.WEB } }
    var attempt by remember { mutableIntStateOf(0) }
    var ready by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>("Đang tải…") }
    var failed by remember { mutableStateOf(false) }
    val tracker = remember(attempt) { SignInTracker() }
    var web by remember { mutableStateOf<WebView?>(null) }

    BackHandler {
        val w = web
        if (!failed && message == null && w != null && w.canGoBack()) w.goBack() else onClose()
    }

    LaunchedEffect(attempt) {
        ready = false
        failed = false
        message = "Đang tải…"
        try {
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            if (!adding) {
                suspendCancellableCoroutine<Boolean> { c -> cm.removeAllCookies { c.resume(it) } }
            }
            cm.flush()
            ready = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
            message = "Không mở được trang đăng nhập."
        }
    }

    fun fail() {
        tracker.done = true
        failed = true
        message = "Đăng nhập chưa thành công."
        web?.stopLoading()
    }

    fun checkSignedIn(url: String?) {
        val host = url?.let { Uri.parse(it).host } ?: return
        if (tracker.done || !(host == "youtube.com" || host.endsWith(".youtube.com"))) return
        if (!InnerTube.isWebSignedIn()) return
        tracker.done = true
        message = "Đang hoàn tất…"
        scope.launch {
            try {
                val account = findNewWebAccount() ?: throw IllegalStateException()
                AccountStore.upsert(account)
                loginHint?.let { AccountStore.dropPending(it) }
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail()
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = NanColors.Background, contentColor = Color.White) {
        Column(Modifier.fillMaxSize().imePadding()) {
            SignInHeader("Đăng nhập Google", onClose)
            Box(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding()) {
                if (ready && !failed) {
                    key(attempt) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(-1, -1)
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.allowFileAccess = false
                                    settings.allowContentAccess = false
                                    settings.userAgentString =
                                        WebSettings.getDefaultUserAgent(ctx).replace("; wv", "")
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView, url: String?) {
                                            if (!tracker.done) message = null
                                            checkSignedIn(url)
                                        }

                                        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                            checkSignedIn(url)
                                        }

                                        override fun onReceivedError(
                                            view: WebView,
                                            request: WebResourceRequest,
                                            error: WebResourceError
                                        ) {
                                            if (request.isForMainFrame && !tracker.done) fail()
                                        }

                                        override fun shouldOverrideUrlLoading(
                                            view: WebView,
                                            request: WebResourceRequest
                                        ): Boolean = request.url.scheme != "https"
                                    }
                                    web = this
                                    val base = if (adding) WEB_ADD_URL else WEB_SIGN_IN_URL
                                    loadUrl(
                                        if (loginHint.isNullOrBlank()) base
                                        else base + "&Email=" + Uri.encode(loginHint) + "&login_hint=" + Uri.encode(loginHint)
                                    )
                                }
                            },
                            onRelease = {
                                it.stopLoading()
                                it.destroy()
                                if (web === it) web = null
                            }
                        )
                    }
                }
                val msg = message
                if (msg != null) {
                    Column(
                        Modifier.fillMaxSize().background(NanColors.Background).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (!failed) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(16.dp))
                        }
                        Text(msg, fontSize = 14.sp, color = NanColors.TextSecondary, textAlign = TextAlign.Center)
                        if (failed) {
                            Spacer(Modifier.height(24.dp))
                            PillButton("Thử lại") { attempt++ }
                        }
                    }
                }
            }
        }
    }
}

private class SignInTracker {
    @Volatile
    var done = false
}

/**
 * Giống newAccount() của meetube: Google thêm phiên mới vào sau các phiên cũ, nên đi từng ô
 * (X-Goog-AuthUser) và lấy ô đầu tiên chưa có trong app. Gặp tên lặp là đã hết tài khoản.
 */
private suspend fun findNewWebAccount(): Account? {
    val known = AccountStore.accounts.filter { it.kind == LoginKind.WEB }.map { it.id }.toSet()
    val seen = HashSet<String>()
    var first: Account? = null
    for (slot in 0 until MAX_SLOTS) {
        val account = InnerTube.webAccount(slot) ?: break
        if (!seen.add(account.id)) break
        if (first == null) first = account
        if (account.id !in known) return account
    }
    return first
}
