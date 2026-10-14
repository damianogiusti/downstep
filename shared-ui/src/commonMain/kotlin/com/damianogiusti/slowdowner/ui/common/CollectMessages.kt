package com.damianogiusti.slowdowner.ui.common

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/** Shows each [UiMessage] as a snackbar while the screen is started. */
@Composable
fun CollectMessages(messages: Flow<UiMessage>, snackbarHostState: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(messages, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            messages.collect { message ->
                // Resolved here, in order; only the snackbar is launched, so a long-lived one does
                // not stop us from draining the flow.
                val args = message.args.map { arg ->
                    if (arg is UiPlural) getPluralString(arg.id, arg.count, arg.count) else arg
                }
                val text = getString(message.text, *args.toTypedArray())
                scope.launch { snackbarHostState.showSnackbar(text) }
            }
        }
    }
}
