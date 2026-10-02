package kiwi.argen.junini.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.BottomAppBarScrollBehavior
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kiwi.argen.junini.identity.IdentityFiles
import junini.shared.generated.resources.Res
import junini.shared.generated.resources.ic_arrow_back
import junini.shared.generated.resources.ic_arrow_forward
import junini.shared.generated.resources.ic_more_vert
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel = viewModel { BrowserViewModel() },
    identityFiles: IdentityFiles? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Read here, above the Scaffold's own inset handling, so nothing has consumed it yet.
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Hides the bottom bar while scrolling down and brings it back as soon as the user scrolls up.
    val scrollBehavior = BottomAppBarDefaults.exitAlwaysScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = state.canGoBack, onBack = viewModel::goBack)
    BackHandler(enabled = state.identities != null, onBack = viewModel::closeIdentities)

    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.noticeShown()
        }
    }
    // Show the bar again whenever a new page arrives, and keep it in place under the suggestions.
    LaunchedEffect(state.page) { scrollBehavior.state.heightOffset = 0f }
    val showSuggestions = state.suggestions.isNotEmpty()
    LaunchedEffect(showSuggestions) { if (showSuggestions) scrollBehavior.state.heightOffset = 0f }
    // -1 means nothing is highlighted, so Enter submits whatever was typed.
    var highlighted by remember(state.suggestions) { mutableIntStateOf(-1) }
    val focusManager = LocalFocusManager.current
    val pickSuggestion = { url: String ->
        focusManager.clearFocus()
        viewModel.pickSuggestion(url)
    }

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            // The default content insets leave out the IME, so lift the whole screen (bar included) above it.
            .imePadding()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when {
                        (event.isMetaPressed && event.key == Key.LeftBracket) ||
                        (event.isAltPressed && event.key == Key.DirectionLeft) -> {
                            if (state.canGoBack) {
                                viewModel.goBack()
                                true
                            } else false
                        }
                        (event.isMetaPressed && event.key == Key.RightBracket) ||
                        (event.isAltPressed && event.key == Key.DirectionRight) -> {
                            if (state.canGoForward) {
                                viewModel.goForward()
                                true
                            } else false
                        }
                        (event.isMetaPressed && event.key == Key.R) ||
                        (event.isCtrlPressed && event.key == Key.R) ||
                        (event.key == Key.F5) -> {
                            viewModel.reload()
                            true
                        }
                        else -> false
                    }
                } else false
            },
        bottomBar = {
            UrlBottomBar(
                value = state.urlInput,
                onValueChange = viewModel::onUrlInputChange,
                onSubmit = viewModel::submitUrlInput,
                canGoBack = state.canGoBack,
                onBack = viewModel::goBack,
                canGoForward = state.canGoForward,
                onForward = viewModel::goForward,
                onClearHistory = viewModel::clearHistory,
                onIdentities = if (state.identitiesAvailable) viewModel::openIdentities else null,
                onFocusLost = viewModel::dismissSuggestions,
                // The best match sits right above the field, so Up moves away from it.
                onSuggestionKey = { event ->
                    if (!showSuggestions || event.type != KeyEventType.KeyDown) return@UrlBottomBar false
                    when (event.key) {
                        Key.DirectionUp -> {
                            highlighted = (highlighted + 1).coerceAtMost(state.suggestions.lastIndex)
                            true
                        }
                        Key.DirectionDown -> {
                            highlighted = (highlighted - 1).coerceAtLeast(-1)
                            true
                        }
                        Key.Enter, Key.NumPadEnter -> {
                            val url = state.suggestions.getOrNull(highlighted) ?: return@UrlBottomBar false
                            pickSuggestion(url)
                            true
                        }
                        Key.Escape -> {
                            viewModel.dismissSuggestions()
                            true
                        }
                        else -> false
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { scaffoldPadding ->
        // Scaffold's bottom padding follows the bar, so it drops to zero once the bar hides on scroll and the
        // last lines would end up under the system navigation bar. Never go below the navigation bar's inset.
        val layoutDirection = LocalLayoutDirection.current
        val innerPadding = PaddingValues(
            start = scaffoldPadding.calculateStartPadding(layoutDirection),
            top = scaffoldPadding.calculateTopPadding(),
            end = scaffoldPadding.calculateEndPadding(layoutDirection),
            bottom = maxOf(scaffoldPadding.calculateBottomPadding(), navigationBarBottom),
        )
        Box(Modifier.fillMaxSize().consumeWindowInsets(scaffoldPadding)) {
            // A fresh list state per page, so every page opens scrolled to the top.
            // Only a page that was loaded from somewhere can be loaded again. The box stays in the
            // composition either way, so the page keeps its scroll position when this flips.
            val canRefresh = state.currentUrl != null && state.page !is PageState.Input && state.identities == null
            val refreshState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { if (canRefresh) viewModel.reload() },
                modifier = Modifier.fillMaxSize(),
                state = refreshState,
                // Starts below the status bar, since the page has no top bar to push it down.
                indicator = {
                    if (canRefresh) {
                        PullToRefreshDefaults.Indicator(
                            state = refreshState,
                            isRefreshing = state.isRefreshing,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = innerPadding.calculateTopPadding()),
                        )
                    }
                },
            ) {
                key(state.page) {
                    PageContent(
                        page = state.page,
                        onLinkClick = viewModel::onLinkClick,
                        onTrustCertificate = viewModel::trustNewCertificate,
                        onCancelCertificate = viewModel::cancelCertificateChange,
                        onUseIdentity = viewModel::useIdentity,
                        onImportIdentity = viewModel::importIdentityForPage,
                        identityFiles = identityFiles,
                        onUnassignIdentity = viewModel::unassignIdentityForPage,
                        listState = rememberLazyListState(),
                        contentPadding = innerPadding,
                    )
                }
            }
            state.identities?.let { items ->
                IdentitiesScreen(
                    items = items,
                    currentHost = state.currentUrl?.host,
                    files = identityFiles,
                    onImport = viewModel::importIdentity,
                    onExport = viewModel::exportIdentity,
                    onDelete = viewModel::deleteIdentity,
                    onAssign = viewModel::assignIdentity,
                    onUnassign = viewModel::unassignIdentity,
                    onClose = viewModel::closeIdentities,
                    contentPadding = innerPadding,
                )
            }
            (state.page as? PageState.Input)?.let { page ->
                InputSheet(page = page, isLoading = state.isLoading, onSubmit = viewModel::submitInput, onCancel = viewModel::cancelCertificateChange)
            }
            if (state.isLoading && !state.isRefreshing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = innerPadding.calculateTopPadding())
                        .align(Alignment.TopCenter),
                )
            }
            // Drawn over the page rather than in a popup, so it always opens upwards from the bar,
            // follows it and the IME, and never takes focus from the URL field.
            if (showSuggestions) {
                SuggestionList(
                    suggestions = state.suggestions,
                    highlighted = highlighted,
                    onPick = pickSuggestion,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = innerPadding.calculateBottomPadding()),
                )
            }
        }
    }
}

@Composable
private fun SuggestionList(
    suggestions: List<String>,
    highlighted: Int,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            // Best match last, so it's the one closest to the URL field.
            suggestions.asReversed().forEachIndexed { reversedIndex, url ->
                val index = suggestions.lastIndex - reversedIndex
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (index == highlighted) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        // Clicking mustn't pull focus from the URL field, which would dismiss the list mid-click.
                        .focusProperties { canFocus = false }
                        .clickable { onPick(url) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UrlBottomBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    canGoBack: Boolean,
    onBack: () -> Unit,
    canGoForward: Boolean,
    onForward: () -> Unit,
    onClearHistory: () -> Unit,
    onIdentities: (() -> Unit)?,
    onFocusLost: () -> Unit,
    onSuggestionKey: (KeyEvent) -> Boolean,
    scrollBehavior: BottomAppBarScrollBehavior,
) {
    val focusManager = LocalFocusManager.current
    var menuOpen by remember { mutableStateOf(false) }
    val submit = {
        focusManager.clearFocus()
        onSubmit()
    }
    BottomAppBar(
        scrollBehavior = scrollBehavior,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    focusManager.clearFocus()
                    onBack()
                },
                enabled = canGoBack,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_arrow_back),
                    contentDescription = "Back",
                )
            }
            IconButton(
                onClick = {
                    focusManager.clearFocus()
                    onForward()
                },
                enabled = canGoForward,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_arrow_forward),
                    contentDescription = "Forward",
                )
            }
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
                    .onFocusChanged { if (!it.isFocused) onFocusLost() }
                    .onPreviewKeyEvent(onSuggestionKey),
                textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = { Text("gemini://") },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                    autoCorrectEnabled = false,
                ),
                keyboardActions = KeyboardActions(onGo = { submit() }),
            )
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_more_vert),
                        contentDescription = "More options",
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (onIdentities != null) {
                        DropdownMenuItem(
                            text = { Text("Identities") },
                            onClick = {
                                menuOpen = false
                                focusManager.clearFocus()
                                onIdentities()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Clear history") },
                        onClick = {
                            menuOpen = false
                            onClearHistory()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PageContent(
    page: PageState,
    onLinkClick: (String) -> Unit,
    onTrustCertificate: () -> Unit,
    onCancelCertificate: () -> Unit,
    onUseIdentity: (String) -> Unit,
    onImportIdentity: (List<String>) -> Unit,
    identityFiles: IdentityFiles?,
    onUnassignIdentity: () -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
    when (page) {
        PageState.Idle -> MessageView(
            title = "Welcome to Junini",
            detail = "Enter a gemini:// URL below to start browsing.",
            contentPadding = contentPadding,
        )
        is PageState.Message -> MessageView(page.title, page.detail, contentPadding)
        is PageState.Gemtext -> GemtextView(page.lines, onLinkClick, listState, contentPadding)
        is PageState.PlainText -> PlainTextView(page.text, listState, contentPadding)
        // The prompt itself is a bottom sheet over this empty page.
        is PageState.Input -> Box(Modifier.fillMaxSize())
        is PageState.CertificateChanged -> CertificateChangedView(
            page = page,
            onTrust = onTrustCertificate,
            onCancel = onCancelCertificate,
            contentPadding = contentPadding,
        )
        is PageState.ClientCertificateRequired -> ClientCertificateRequiredView(
            page = page,
            onUseIdentity = onUseIdentity,
            onImportIdentity = onImportIdentity,
            files = identityFiles,
            onUnassign = onUnassignIdentity,
            onCancel = onCancelCertificate,
            contentPadding = contentPadding,
        )
    }
}

