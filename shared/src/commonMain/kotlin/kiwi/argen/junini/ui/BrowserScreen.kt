package kiwi.argen.junini.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.BottomAppBarScrollBehavior
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import junini.shared.generated.resources.Res
import junini.shared.generated.resources.ic_arrow_back
import junini.shared.generated.resources.ic_arrow_forward
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(viewModel: BrowserViewModel = viewModel { BrowserViewModel() }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Hides the bottom bar while scrolling down and brings it back as soon as the user scrolls up.
    val scrollBehavior = BottomAppBarDefaults.exitAlwaysScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = state.canGoBack, onBack = viewModel::goBack)

    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.noticeShown()
        }
    }
    // Show the bar again whenever a new page arrives.
    LaunchedEffect(state.page) { scrollBehavior.state.heightOffset = 0f }

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
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
                            viewModel.submitUrlInput()
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
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            // A fresh list state per page, so every page opens scrolled to the top.
            key(state.page) {
                PageContent(
                    page = state.page,
                    onLinkClick = viewModel::onLinkClick,
                    listState = rememberLazyListState(),
                    contentPadding = innerPadding,
                )
            }
            if (state.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = innerPadding.calculateTopPadding())
                        .align(Alignment.TopCenter),
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
    scrollBehavior: BottomAppBarScrollBehavior,
) {
    val focusManager = LocalFocusManager.current
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
                    .padding(start = 4.dp, end = 8.dp),
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
        }
    }
}

@Composable
private fun PageContent(
    page: PageState,
    onLinkClick: (String) -> Unit,
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
    }
}

