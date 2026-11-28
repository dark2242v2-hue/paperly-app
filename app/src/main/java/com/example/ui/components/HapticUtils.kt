package com.example.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

class PaperlyHaptics(
    private val view: View,
    private val composeHaptic: HapticFeedback
) {
    fun selectionClick() {
        val performed = runCatching {
            composeHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            true
        }.getOrDefault(false)
        if (!performed) {
            runCatching {
                val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    HapticFeedbackConstants.CLOCK_TICK
                } else {
                    HapticFeedbackConstants.KEYBOARD_TAP
                }
                view.performHapticFeedback(constant)
            }
        }
    }

    fun lightTap() {
        val performed = runCatching {
            val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                HapticFeedbackConstants.KEYBOARD_TAP
            } else {
                HapticFeedbackConstants.CLOCK_TICK
            }
            view.performHapticFeedback(constant)
        }.getOrDefault(false)
        if (!performed) {
            runCatching { composeHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
        }
    }

    fun confirmSuccess() {
        val performed = runCatching {
            val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.LONG_PRESS
            }
            view.performHapticFeedback(constant)
        }.getOrDefault(false)
        if (!performed) {
            runCatching { composeHaptic.performHapticFeedback(HapticFeedbackType.LongPress) }
        }
    }
}

@Composable
fun rememberPaperlyHaptics(): PaperlyHaptics {
    val view = LocalView.current
    val composeHaptic = LocalHapticFeedback.current
    return remember(view, composeHaptic) {
        PaperlyHaptics(view = view, composeHaptic = composeHaptic)
    }
}
