// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.awesomebookmarks

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.mateof.awesomebookmarks.data.AppSettings
import io.github.mateof.awesomebookmarks.save.SaveBookmarkActivity
import io.github.mateof.awesomebookmarks.ui.MainEvent
import io.github.mateof.awesomebookmarks.ui.MainUiState
import io.github.mateof.awesomebookmarks.ui.MainViewModel
import io.github.mateof.awesomebookmarks.ui.lock.AppLock
import io.github.mateof.awesomebookmarks.ui.settings.SettingsActivity
import io.github.mateof.awesomebookmarks.ui.setup.SignInScreen
import io.github.mateof.awesomebookmarks.ui.setup.StatusScreen
import io.github.mateof.awesomebookmarks.ui.theme.AwesomeBookmarksTheme
import io.github.mateof.awesomebookmarks.ui.web.BookmarksWebChromeClient
import io.github.mateof.awesomebookmarks.ui.web.BookmarksWebViewClient
import io.github.mateof.awesomebookmarks.ui.web.Downloader
import io.github.mateof.awesomebookmarks.ui.web.QuickActionButton
import io.github.mateof.awesomebookmarks.ui.web.WebPageState
import io.github.mateof.awesomebookmarks.ui.web.applyBookmarksConfiguration
import io.github.mateof.awesomebookmarks.update.UpdateCheckWorker
import io.github.mateof.awesomebookmarks.update.UpdateDialog
import io.github.mateof.awesomebookmarks.update.UpdateViewModel
import io.github.mateof.awesomebookmarks.util.WebViewInfo
import io.github.mateof.awesomebookmarks.util.isSameOrigin
import java.io.File

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val updateViewModel: UpdateViewModel by viewModels()

    private var webView: WebView? = null
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var pendingCameraUri: Uri? = null
    private var pendingMediaPermission: PermissionRequest? = null
    private var lastBackPressAt = 0L

    private val downloader by lazy { Downloader(applicationContext) }

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = fileChooserCallback ?: return@registerForActivityResult
            fileChooserCallback = null
            val cameraUri = pendingCameraUri
            pendingCameraUri = null

            val uris = when {
                result.resultCode != RESULT_OK -> null
                result.data?.data == null && result.data?.clipData == null && cameraUri != null ->
                    arrayOf(cameraUri)

                else -> WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            }
            callback.onReceiveValue(uris)
        }

    private val recordAudioLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val request = pendingMediaPermission ?: return@registerForActivityResult
            pendingMediaPermission = null
            if (granted) request.grant(request.resources) else request.deny()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            AwesomeBookmarksTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }
    }

    override fun onStop() {
        // Saved on the way out rather than in onSaveInstanceState, so it also
        // covers the case where the activity is killed without one.
        webView?.saveState(viewModel.webViewState)
        super.onStop()
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    @Composable
    private fun AppRoot() {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val settings by viewModel.settings.collectAsStateWithLifecycle()

        val loaded = settings ?: run {
            LoadingBox()
            return
        }

        KeepScreenOnEffect(loaded.keepScreenOn)
        UpdateCheckEffect(loaded.updateChecksEnabled)

        val lockEnabled = loaded.appLockEnabled && remember { AppLock.isAvailable(this) }
        var locked by rememberSaveable { mutableStateOf(lockEnabled) }
        LockLifecycleEffect(enabled = lockEnabled, onLock = { locked = true })

        // The lock is drawn on top rather than replacing the content. Taking the
        // content out of the composition would release the AndroidView, destroy
        // the WebView and lose the page you were on, which is exactly what
        // happened every time a link opened in the browser.
        Box(Modifier.fillMaxSize()) {
            AppContent(uiState, loaded)
            if (lockEnabled && locked) {
                LockOverlay(onUnlocked = { locked = false })
            }
        }
    }

    @Composable
    private fun AppContent(uiState: MainUiState, loaded: AppSettings) {
        UpdateAvailableDialog()

        when (val state = uiState) {
            MainUiState.Loading -> LoadingBox()

            is MainUiState.NeedsSignIn -> Box(Modifier.statusBarsPadding().navigationBarsPadding()) {
                SignInScreen(
                    prefill = state.prefill,
                    problem = state.problem,
                    serverMessage = state.serverMessage,
                    unreachable = state.unreachable,
                    needsTotp = state.needsTotp,
                    isSubmitting = false,
                    onSubmit = viewModel::submitSignIn,
                )
            }

            is MainUiState.Unreachable -> Box(Modifier.statusBarsPadding().navigationBarsPadding()) {
                StatusScreen(
                    title = stringResource(R.string.error_unreachable_title),
                    body = stringResource(R.string.error_unreachable_body, state.tried.joinToString("\n")),
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = { viewModel.connect() },
                    secondaryLabel = stringResource(R.string.action_open_settings),
                    onSecondary = { openSettings() },
                )
            }

            is MainUiState.Connected -> ConnectedContent(state, loaded)
        }
    }

    @Composable
    private fun ConnectedContent(state: MainUiState.Connected, settings: AppSettings) {
        var pageState by remember { mutableStateOf<WebPageState>(WebPageState.Loading) }

        WebViewCompatibilityWarning()

        // Measured here so the draggable button can clamp itself without
        // introducing a screen sized layer of its own over the WebView.
        var containerSize by remember { mutableStateOf(IntSize.Zero) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .onSizeChanged { containerSize = it },
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        applyBookmarksConfiguration(settings)
                        webViewClient = BookmarksWebViewClient(
                            baseUrl = { state.baseUrl },
                            onSessionExpired = viewModel::onSessionExpired,
                            onExternalUrl = ::handleExternalUrl,
                            onStateChanged = { pageState = it },
                        )
                        webChromeClient = BookmarksWebChromeClient(
                            onFileChooser = ::showFileChooser,
                            onMediaPermission = ::handleMediaPermission,
                            onProgress = { },
                        )
                        setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                            startDownload(url, userAgent, contentDisposition, mimeType)
                        }
                        addJavascriptInterface(BlobDownloadBridge(), Downloader.BRIDGE_NAME)
                        // Restoring beats reloading: it brings back the whole
                        // back stack, not just the last address. Guarded by an
                        // origin check so a history saved against a previous
                        // server address cannot resurface after it changes.
                        val restored = if (viewModel.webViewState.isEmpty) {
                            null
                        } else {
                            restoreState(viewModel.webViewState)?.currentItem?.url
                        }
                        if (restored == null || !isSameOrigin(Uri.parse(restored), state.baseUrl)) {
                            loadUrl(state.baseUrl)
                        }
                        this@MainActivity.webView = this
                    }
                },
                update = { view -> view.settings.textZoom = settings.textZoom },
                onRelease = { view ->
                    if (this@MainActivity.webView === view) this@MainActivity.webView = null
                    view.destroy()
                },
            )

            if (pageState is WebPageState.Failed) {
                StatusScreen(
                    title = stringResource(R.string.error_page_title),
                    body = (pageState as WebPageState.Failed).reason,
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = { webView?.reload() },
                )
            }

            if (pageState is WebPageState.SessionRejected) {
                StatusScreen(
                    title = stringResource(R.string.error_session_title),
                    body = stringResource(R.string.error_session_body),
                    actionLabel = stringResource(R.string.action_open_settings),
                    onAction = { openSettings() },
                )
            }

            if (settings.showQuickButton) {
                QuickActionButton(
                    containerSize = containerSize,
                    positionX = settings.quickButtonX,
                    positionY = settings.quickButtonY,
                    onPositionChanged = viewModel::setQuickButtonPosition,
                    modifier = Modifier.align(Alignment.TopStart),
                    onSettings = ::openSettings,
                    onQuickCapture = {
                        startActivity(Intent(this@MainActivity, SaveBookmarkActivity::class.java))
                    },
                    onReload = { webView?.reload() },
                    onSearch = { viewModel.navigateTo("/search") },
                )
            }
        }

        BackHandler {
            val view = webView
            when {
                view != null && view.canGoBack() -> view.goBack()
                else -> confirmExit()
            }
        }

        LaunchedEffect(Unit) {
            viewModel.events.collect { event ->
                when (event) {
                    is MainEvent.Load -> webView?.loadUrl(event.url)
                    MainEvent.Reload -> webView?.reload()
                }
            }
        }
    }

    private fun openSettings() = startActivity(Intent(this, SettingsActivity::class.java))

    private fun confirmExit() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBackPressAt < EXIT_CONFIRM_WINDOW_MS) {
            finish()
        } else {
            lastBackPressAt = now
            toast(getString(R.string.toast_press_back_again))
        }
    }

    private fun handleExternalUrl(uri: Uri): Boolean {
        val settings = viewModel.settings.value ?: AppSettings()
        val isWeb = uri.scheme == "http" || uri.scheme == "https"
        if (isWeb && !settings.openExternalLinksInBrowser) return false

        return runCatching {
            if (isWeb) {
                CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this, uri)
            } else {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
            true
        }.getOrElse {
            toast(getString(R.string.toast_no_app_for_link))
            true
        }
    }

    private fun showFileChooser(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams,
    ): Boolean {
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = callback

        val chooser = Intent(Intent.ACTION_CHOOSER).apply {
            putExtra(Intent.EXTRA_INTENT, params.createIntent())
            putExtra(Intent.EXTRA_TITLE, getString(R.string.file_chooser_title))
            if (params.acceptsImages()) {
                cameraCaptureIntent()?.let { putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(it)) }
            }
        }

        return try {
            fileChooserLauncher.launch(chooser)
            true
        } catch (e: ActivityNotFoundException) {
            fileChooserCallback = null
            pendingCameraUri = null
            false
        }
    }

    private fun WebChromeClient.FileChooserParams.acceptsImages(): Boolean {
        val types = acceptTypes.orEmpty().filter { it.isNotBlank() }
        return types.isEmpty() || types.any { it.contains("image") || it.contains("*/*") }
    }

    private fun cameraCaptureIntent(): Intent? = runCatching {
        val dir = File(cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        pendingCameraUri = uri
        Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            .putExtra(MediaStore.EXTRA_OUTPUT, uri)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }.getOrNull()

    private fun handleMediaPermission(request: PermissionRequest) {
        if (!request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
            request.deny()
            return
        }
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
        } else {
            pendingMediaPermission = request
            recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startDownload(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
        if (url.startsWith("blob:")) {
            webView?.evaluateJavascript(
                Downloader.blobDownloadScript(url, "bookmarks-export-${System.currentTimeMillis()}", mimeType.orEmpty()),
                null,
            )
            return
        }
        downloader.enqueueHttpDownload(url, userAgent, contentDisposition, mimeType)
            .onSuccess { toast(getString(R.string.toast_download_started, it)) }
            .onFailure { toast(getString(R.string.toast_download_failed)) }
    }

    /** Receives blob payloads pushed back from the page by the download script. */
    private inner class BlobDownloadBridge {
        @android.webkit.JavascriptInterface
        fun onBlobDownloaded(dataUrl: String, fileName: String, mimeType: String) {
            val bytes = downloader.decodeDataUrl(dataUrl) ?: run {
                runOnUiThread { toast(getString(R.string.toast_download_failed)) }
                return
            }
            val resolvedMime = mimeType.ifBlank { dataUrl.substringAfter("data:").substringBefore(';') }
            val extension = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(resolvedMime)
            val name = if (extension.isNullOrBlank()) fileName else "$fileName.$extension"

            downloader.saveToDownloads(bytes, name, resolvedMime)
                .onSuccess { runOnUiThread { toast(getString(R.string.toast_download_saved, name)) } }
                .onFailure { runOnUiThread { toast(getString(R.string.toast_download_failed)) } }
        }

        @android.webkit.JavascriptInterface
        fun onBlobFailed(reason: String) {
            runOnUiThread { toast(getString(R.string.toast_download_failed)) }
        }
    }

    @Composable
    private fun LoadingBox() {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }

    /**
     * Opaque full screen cover. It must hide the content completely and swallow
     * every touch, because the WebView is still alive and composed underneath.
     */
    @Composable
    private fun LockOverlay(onUnlocked: () -> Unit) {
        var promptVisible by remember { mutableStateOf(false) }

        fun prompt() {
            promptVisible = true
            AppLock.prompt(
                activity = this@MainActivity,
                title = getString(R.string.lock_title),
                subtitle = getString(R.string.lock_subtitle),
                cancelLabel = getString(R.string.action_cancel),
                onSuccess = onUnlocked,
                onDismissed = { promptVisible = false },
            )
        }

        LaunchedEffect(Unit) { prompt() }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .blockTouches(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(Modifier.statusBarsPadding().navigationBarsPadding()) {
                StatusScreen(
                    title = stringResource(R.string.lock_title),
                    body = stringResource(R.string.lock_body),
                    actionLabel = if (promptVisible) null else stringResource(R.string.action_unlock),
                    onAction = if (promptVisible) null else ({ prompt() }),
                )
            }
        }
    }

    @Composable
    private fun LockLifecycleEffect(enabled: Boolean, onLock: () -> Unit) {
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, enabled) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP ->
                        viewModel.backgroundedAt = SystemClock.elapsedRealtime()

                    Lifecycle.Event.ON_START -> {
                        val leftAt = viewModel.backgroundedAt
                        val awayFor = SystemClock.elapsedRealtime() - leftAt
                        if (enabled && leftAt != 0L && awayFor > AppLock.GRACE_PERIOD_MS) {
                            onLock()
                        }
                    }

                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    @Composable
    private fun KeepScreenOnEffect(enabled: Boolean) {
        DisposableEffect(enabled) {
            if (enabled) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
    }

    @Composable
    private fun UpdateCheckEffect(enabled: Boolean) {
        LaunchedEffect(enabled) {
            if (enabled) {
                UpdateCheckWorker.schedule(applicationContext)
                updateViewModel.check()
            } else {
                UpdateCheckWorker.cancel(applicationContext)
            }
        }
    }

    @Composable
    private fun UpdateAvailableDialog() {
        val state by updateViewModel.state.collectAsStateWithLifecycle()
        if (state.available == null || state.dismissed) return

        UpdateDialog(
            state = state,
            onDownload = updateViewModel::download,
            onInstall = ::installUpdate,
            onSkip = updateViewModel::skipCurrent,
            onDismiss = updateViewModel::dismiss,
        )
    }

    private fun installUpdate() {
        val apk = updateViewModel.state.value.downloaded ?: return
        if (!updateViewModel.canInstall()) {
            toast(getString(R.string.update_allow_unknown_sources))
            runCatching { startActivity(updateViewModel.unknownSourcesIntent()) }
            return
        }
        runCatching { startActivity(updateViewModel.installIntent(apk)) }
            .onFailure { toast(getString(R.string.update_install_failed)) }
    }

    @Composable
    private fun WebViewCompatibilityWarning() {
        val info = remember { WebViewInfo.of(this) }
        LaunchedEffect(info) {
            when {
                info.isMissing -> toast(getString(R.string.warning_webview_missing))
                info.isOutdated -> toast(getString(R.string.warning_webview_outdated, info.versionName.orEmpty()))
            }
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private companion object {
        const val EXIT_CONFIRM_WINDOW_MS = 2_000L
    }
}

/**
 * Consumes every pointer event in the Initial pass, so nothing reaches the
 * WebView that is still composed behind the lock.
 */
private fun Modifier.blockTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}
