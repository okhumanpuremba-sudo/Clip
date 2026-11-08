package com.example.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Pure Jetpack Compose implementation of [TextToolbar] that avoids invoking
 * Android's native `DecorView.startActionMode(..., ActionMode.TYPE_FLOATING)`.
 *
 * This completely prevents the platform race condition:
 * `E/DecorView[]: Destroying unexpected ActionMode instance of TYPE_FLOATING`
 * when users long-press, paste URLs, or tap action buttons while a text selection is active.
 */
class StudioComposeTextToolbar : TextToolbar {
    override var status: TextToolbarStatus by mutableStateOf(TextToolbarStatus.Hidden)
        private set

    var onCopy: (() -> Unit)? by mutableStateOf(null)
        private set
    var onPaste: (() -> Unit)? by mutableStateOf(null)
        private set
    var onCut: (() -> Unit)? by mutableStateOf(null)
        private set
    var onSelectAll: (() -> Unit)? by mutableStateOf(null)
        private set

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        onCopy = onCopyRequested
        onPaste = onPasteRequested
        onCut = onCutRequested
        onSelectAll = onSelectAllRequested
        status = if (
            onCopyRequested != null ||
            onPasteRequested != null ||
            onCutRequested != null ||
            onSelectAllRequested != null
        ) {
            TextToolbarStatus.Shown
        } else {
            TextToolbarStatus.Hidden
        }
    }

    override fun hide() {
        status = TextToolbarStatus.Hidden
        onCopy = null
        onPaste = null
        onCut = null
        onSelectAll = null
    }
}

@Composable
fun ProvideStudioTextToolbar(
    content: @Composable () -> Unit
) {
    val toolbar = remember { StudioComposeTextToolbar() }

    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()

            AnimatedVisibility(
                visible = toolbar.status == TextToolbarStatus.Shown,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp)
                    .zIndex(50f)
            ) {
                Surface(
                    color = StudioSurfaceElevated,
                    border = BorderStroke(1.dp, ForgeAmber),
                    shape = RoundedCornerShape(24.dp),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        toolbar.onPaste?.let { pasteAction ->
                            TextButton(
                                onClick = {
                                    pasteAction()
                                    toolbar.hide()
                                }
                            ) {
                                Text(
                                    text = "Paste",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ForgeAmber,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        toolbar.onCopy?.let { copyAction ->
                            TextButton(
                                onClick = {
                                    copyAction()
                                    toolbar.hide()
                                }
                            ) {
                                Text(
                                    text = "Copy",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextPrimary
                                )
                            }
                        }
                        toolbar.onCut?.let { cutAction ->
                            TextButton(
                                onClick = {
                                    cutAction()
                                    toolbar.hide()
                                }
                            ) {
                                Text(
                                    text = "Cut",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextPrimary
                                )
                            }
                        }
                        toolbar.onSelectAll?.let { selectAllAction ->
                            TextButton(
                                onClick = {
                                    selectAllAction()
                                }
                            ) {
                                Text(
                                    text = "Select All",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TechnicalCyan
                                )
                            }
                        }
                        TextButton(onClick = { toolbar.hide() }) {
                            Text(
                                text = "✕",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
