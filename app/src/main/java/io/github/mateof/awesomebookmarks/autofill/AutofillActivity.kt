// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.awesomebookmarks.autofill

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dagger.hilt.android.AndroidEntryPoint
import io.github.mateof.awesomebookmarks.R
import io.github.mateof.awesomebookmarks.ui.theme.AwesomeBookmarksTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

@AndroidEntryPoint
class AutofillActivity : ComponentActivity() {

    @Inject
    lateinit var autofill: AutofillRepository

    @Inject
    lateinit var json: Json

    private var webView: WebView? = null
    private var plan: Plan? = null
    private var ran = false
    private val reports = MutableStateFlow<String?>(null)

    private data class Plan(
        val automation: RunnableAutomation?,
        val variables: Map<String, String>,
        val runner: String?,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) {
            finish()
            return
        }
        val bookmarkId = intent.getStringExtra(EXTRA_BOOKMARK_ID)

        setContent {
            AwesomeBookmarksTheme {
                AutofillScreen(url = url)
            }
        }

        lifecycleScope.launch {
            val state = autofill.refresh(maxAgeMs = 0)
            plan = when (state) {
                is AutofillState.Ready -> {
                    val runtime = state.runtime
                    val automation = runtime.automations.firstOrNull { it.bookmarkId == bookmarkId }
                        ?: automationFor(runtime, url)
                    Plan(automation, runtime.variables, state.runner)
                }

                else -> Plan(null, emptyMap(), null)
            }
            if (state !is AutofillState.Ready) reports.value = describe(state)
            webView?.let { start(it, url) }
        }
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AutofillScreen(url: String) {
        var title by remember { mutableStateOf("") }
        var currentUrl by remember { mutableStateOf(url) }
        var progress by remember { mutableStateOf(0) }
        var canGoBack by remember { mutableStateOf(false) }
        val snackbar = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            reports.collect { message -> if (message != null) snackbar.showSnackbar(message) }
        }

        BackHandler(enabled = canGoBack) { webView?.goBack() }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                title.ifBlank { stringResource(R.string.autofill_title) },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                Uri.parse(currentUrl).host.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                        }
                    },
                    actions = {
                        IconButton(onClick = { openInBrowser(currentUrl) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = stringResource(R.string.autofill_open_in_browser),
                            )
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        createWebView(
                            context = context,
                            onTitle = { title = it },
                            onProgress = { progress = it },
                            onNavigated = { u, back ->
                                currentUrl = u
                                canGoBack = back
                            },
                        ).also { view ->
                            webView = view
                            if (plan != null) start(view, url)
                        }
                    },
                )
                if (progress in 1..99) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    private fun start(view: WebView, url: String) {
        if (view.url != null) return
        val automation = plan?.automation
        val origin = automation?.let { originOf(it.url) }
        if (origin != null && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(view, AUTOFILL_BRIDGE, setOf(origin)) { _, message, _, _, _ ->
                onReport(message.data.orEmpty())
            }
        }
        view.loadUrl(url)
    }

    private fun onReport(raw: String) {
        val report = parseAutofillReport(raw) { json.parseToJsonElement(it) } ?: return
        val current = plan
        if (report.ok && current?.automation != null && current.automation.script.isNotBlank()) {
            webView?.evaluateJavascript(scriptInvocation(current.automation.script, current.variables), null)
        }
        reports.value = if (report.ok) {
            getString(R.string.autofill_done, report.done, report.total)
        } else {
            getString(R.string.autofill_failed, report.error.orEmpty())
        }
    }

    private fun runIfDue(view: WebView, pageUrl: String) {
        val current = plan ?: return
        val automation = current.automation ?: return
        val runner = current.runner ?: return
        if (ran || originOf(pageUrl) != originOf(automation.url)) return
        ran = true
        view.evaluateJavascript(stepsInvocation(runner, automation, current.variables), null)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(
        context: Context,
        onTitle: (String) -> Unit,
        onProgress: (Int) -> Unit,
        onNavigated: (String, Boolean) -> Unit,
    ): WebView = WebView(context).apply webView@{
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.setSupportMultipleWindows(false)
        settings.javaScriptCanOpenWindowsAutomatically = false
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(this@webView, true)
        }
        webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView, title: String?) = onTitle(title.orEmpty())
            override fun onProgressChanged(view: WebView, newProgress: Int) = onProgress(newProgress)
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme
                if (scheme == "http" || scheme == "https") return false
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                return true
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                onNavigated(url, view.canGoBack())
            }

            override fun onPageFinished(view: WebView, url: String) {
                onNavigated(url, view.canGoBack())
                runIfDue(view, url)
            }
        }
    }

    private fun openInBrowser(url: String) {
        runCatching {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this, Uri.parse(url))
        }
    }

    private fun describe(state: AutofillState): String = when (state) {
        AutofillState.NotAllowed -> getString(R.string.autofill_status_not_allowed)
        AutofillState.Unsupported -> getString(R.string.autofill_status_unsupported)
        is AutofillState.Failed -> getString(R.string.autofill_status_failed, state.message)
        is AutofillState.Ready -> ""
    }

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_BOOKMARK_ID = "bookmarkId"

        fun intent(context: Context, url: String, bookmarkId: String?): Intent =
            Intent(context, AutofillActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_BOOKMARK_ID, bookmarkId)
    }
}
