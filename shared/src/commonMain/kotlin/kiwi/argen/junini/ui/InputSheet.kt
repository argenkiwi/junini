package kiwi.argen.junini.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first

/** A bottom sheet asking for the input a server requested with status 10 or 11. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputSheet(
    page: PageState.Input,
    isLoading: Boolean,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var text by remember(page) { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val submit = { if (!isLoading && text.isNotBlank()) onSubmit(text) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // The sheet lives in its own window, which can miss the keyboard's insets if the keyboard opens while
    // the window is still appearing and ends up drawn underneath it. Focus the field once the sheet has settled.
    LaunchedEffect(page) {
        snapshotFlow { sheetState.currentValue }.first { it == SheetValue.Expanded }
        focusRequester.requestFocus()
    }

    // The sheet already pads its content for the keyboard (its default window insets include the IME).
    ModalBottomSheet(onDismissRequest = onCancel, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = page.prompt.ifBlank { "The server asked for input." },
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                enabled = !isLoading,
                visualTransformation = if (page.sensitive) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (page.sensitive) KeyboardType.Password else KeyboardType.Text,
                    imeAction = ImeAction.Send,
                    autoCorrectEnabled = false,
                ),
                keyboardActions = KeyboardActions(onSend = { submit() }),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Button(onClick = submit, enabled = !isLoading && text.isNotBlank()) { Text("Send") }
            }
        }
    }
}
