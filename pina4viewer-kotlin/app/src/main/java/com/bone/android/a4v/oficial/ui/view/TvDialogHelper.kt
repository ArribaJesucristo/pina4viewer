package com.bone.android.a4v.oficial.ui.view

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.bone.android.a4v.oficial.R
import com.google.android.material.button.MaterialButton

/**
 * Helper centralizado para garantizar que todos los AlertDialogs de Piña4Viewer
 * tengan alto contraste en modo oscuro, borde/relleno dorado inequívoco al navegar
 * con el mando D-Pad en Android TV / Fire TV y foco inicial predecible.
 */
object TvDialogHelper {

    data class DownloadDialogHandle(
        val dialog: AlertDialog,
        private val progressBar: ProgressBar,
        private val percentText: TextView,
        private val messageText: TextView
    ) {
        fun updateProgress(percent: Int) {
            val clamped = percent.coerceIn(0, 100)
            if (progressBar.isIndeterminate) {
                progressBar.isIndeterminate = false
            }
            progressBar.progress = clamped
            percentText.text = "$clamped %"
        }

        fun setIndeterminate() {
            progressBar.isIndeterminate = true
            percentText.text = "Descargando..."
        }

        fun updateMessage(message: String) {
            messageText.text = message
        }

        fun dismissSafely(activity: Activity) {
            if (!activity.isFinishing && !activity.isDestroyed && dialog.isShowing) {
                try {
                    dialog.dismiss()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun show(
        activity: Activity?,
        builder: AlertDialog.Builder,
        preferPositiveFocus: Boolean = true,
        onShowExtra: ((AlertDialog) -> Unit)? = null
    ): AlertDialog? {
        if (activity == null || activity.isFinishing || activity.isDestroyed) return null
        val dialog = builder.create()
        prepare(dialog, preferPositiveFocus, onShowExtra)
        dialog.show()
        return dialog
    }

    fun prepare(
        dialog: AlertDialog,
        preferPositiveFocus: Boolean = true,
        onShowExtra: ((AlertDialog) -> Unit)? = null
    ): AlertDialog {
        dialog.setOnShowListener {
            styleButtonsAndFocus(dialog, preferPositiveFocus)
            onShowExtra?.invoke(dialog)
        }
        return dialog
    }

    fun showDownloadDialog(
        activity: Activity,
        title: String,
        message: String,
        onCancel: () -> Unit
    ): DownloadDialogHandle? {
        if (activity.isFinishing || activity.isDestroyed) return null

        val padH = dpToPx(activity, 22f)
        val padV = dpToPx(activity, 14f)

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, padV, padH, dpToPx(activity, 8f))
        }

        val messageView = TextView(activity).apply {
            text = message
            setTextColor(ContextCompat.getColor(activity, R.color.text_secondary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        }

        val progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            progress = 0
            progressTintList = ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.gold_primary))
            progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#444444"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(activity, 10f)
            ).apply {
                topMargin = dpToPx(activity, 16f)
            }
        }

        val percentView = TextView(activity).apply {
            text = "0 %"
            setTextColor(ContextCompat.getColor(activity, R.color.gold_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(activity, 8f)
            }
        }

        container.addView(messageView)
        container.addView(progressBar)
        container.addView(percentView)

        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(container)
            .setCancelable(false)
            .setNegativeButton("Cancelar") { d, _ ->
                onCancel()
                d.dismiss()
            }
            .create()

        prepare(dialog, preferPositiveFocus = false)
        dialog.show()
        return DownloadDialogHandle(dialog, progressBar, percentView, messageView)
    }

    private fun styleButtonsAndFocus(dialog: AlertDialog, preferPositiveFocus: Boolean) {
        val ctx = dialog.context
        val bgColors = ContextCompat.getColorStateList(ctx, R.color.selector_dialog_button_bg)
        val textColors = ContextCompat.getColorStateList(ctx, R.color.selector_dialog_button_text)
        val strokeColors = ContextCompat.getColorStateList(ctx, R.color.selector_dialog_button_stroke)

        val buttonIds = listOf(
            AlertDialog.BUTTON_POSITIVE,
            AlertDialog.BUTTON_NEGATIVE,
            AlertDialog.BUTTON_NEUTRAL
        )

        val visibleButtons = mutableListOf<android.widget.Button>()
        for (btnId in buttonIds) {
            val btn = dialog.getButton(btnId) ?: continue
            if (btn.visibility != View.VISIBLE) continue
            visibleButtons.add(btn)

            btn.isFocusable = true
            btn.isClickable = true
            btn.isAllCaps = false

            // Evitar recorte de la animación de escala en el ButtonBarLayout / ScrollView contenedor
            (btn.parent as? ViewGroup)?.let { bar ->
                bar.clipChildren = false
                bar.clipToPadding = false
                val extraPad = dpToPx(ctx, 6f)
                if (bar.paddingTop < extraPad || bar.paddingBottom < extraPad) {
                    bar.setPadding(bar.paddingLeft, extraPad, bar.paddingRight, extraPad)
                }
                (bar.parent as? ViewGroup)?.let { scroll ->
                    scroll.clipChildren = false
                    scroll.clipToPadding = false
                }
            }

            if (btn is MaterialButton) {
                btn.backgroundTintList = bgColors
                btn.strokeColor = strokeColors
                btn.strokeWidth = dpToPx(ctx, 2.5f)
                btn.cornerRadius = dpToPx(ctx, 10f)
                btn.setTextColor(textColors)
                btn.rippleColor = ColorStateList.valueOf(Color.parseColor("#55FFFFFF"))
            } else {
                btn.backgroundTintList = null
                btn.background = ContextCompat.getDrawable(ctx, R.drawable.selector_dialog_button_compat_bg)
                btn.setTextColor(textColors)
            }

            btn.setOnFocusChangeListener { v, hasFocus ->
                applyFocusScale(v, hasFocus)
            }
        }

        val listView = dialog.listView
        if (listView != null && listView.count > 0) {
            listView.isFocusable = true
            listView.selector = ContextCompat.getDrawable(ctx, R.drawable.selector_dialog_item_focus)
            listView.setDrawSelectorOnTop(false)
            listView.post {
                listView.requestFocus()
                listView.setSelection(0)
            }
        } else {
            val targetButton = if (preferPositiveFocus) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.takeIf { it.visibility == View.VISIBLE }
                    ?: visibleButtons.firstOrNull()
            } else {
                visibleButtons.firstOrNull()
            }
            targetButton?.post {
                targetButton.requestFocus()
                applyFocusScale(targetButton, targetButton.hasFocus())
            }
        }
    }

    private fun applyFocusScale(view: View, hasFocus: Boolean) {
        val targetScale = if (hasFocus) 1.06f else 1.0f
        view.animate().cancel()
        view.animate()
            .scaleX(targetScale)
            .scaleY(targetScale)
            .setDuration(110)
            .start()
        view.elevation = if (hasFocus) dpToPx(view.context, 6f).toFloat() else 0f
    }

    private fun dpToPx(context: android.content.Context, dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }
}
