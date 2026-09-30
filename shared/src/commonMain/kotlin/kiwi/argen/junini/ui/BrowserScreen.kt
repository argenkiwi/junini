package kiwi.argen.junini.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import junini.shared.generated.resources.Res
import junini.shared.generated.resources.ic_arrow_forward
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(viewModel: BrowserViewModel = viewModel { BrowserViewModel() }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Hides the URL bar while scrolling down and brings it back as soon as the user scrolls up.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.noticeShown()
        }
    }
    // Show the bar again whenever a new page arrives.
    LaunchedEffect(state.page) { scrollBehavior.state.heightOffset = 0f }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            UrlBar(
                value = state.urlInput,
                onValueChange = viewModel::onUrlInputChange,
                onSubmit = viewModel::submitUrlInput,
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
private fun UrlBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        onSubmit()
    }
    TopAppBar(
        title = {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = { Text("gemini://") },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                trailingIcon = {
                    IconButton(onClick = submit) {
                        Icon(painterResource(Res.drawable.ic_arrow_forward), contentDescription = "Go")
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                    autoCorrectEnabled = false,
                ),
                keyboardActions = KeyboardActions(onGo = { submit() }),
            )
        },
        scrollBehavior = scrollBehavior,
    )
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
            detail = "Enter a gemini:// URL above to start browsing.",
            contentPadding = contentPadding,
        )
        is PageState.Message -> MessageView(page.title, page.detail, contentPadding)
        is PageState.Gemtext -> GemtextView(page.lines, onLinkClick, listState, contentPadding)
        is PageState.PlainText -> PlainTextView(page.text, listState, contentPadding)
    }
}
