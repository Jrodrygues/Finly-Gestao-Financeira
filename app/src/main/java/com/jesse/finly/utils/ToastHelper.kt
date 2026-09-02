package com.jesse.finly.utils

import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import com.jesse.finly.R

object ToastHelper {
    fun showCustomToast(context: Context, message: String, isLong: Boolean = false) {
        try {
            // Usar o contexto original (Activity) para respeitar o tema atual (Dark/Light)
            val inflater = LayoutInflater.from(context)
            val layout = inflater.inflate(R.layout.layout_custom_toast, null)

            val text: TextView = layout.findViewById(R.id.tvToastMessage)
            text.text = message

            val toast = Toast(context)
            toast.duration = if (isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
            @Suppress("DEPRECATION")
            toast.view = layout
            toast.show()
        } catch (e: Exception) {
            Toast.makeText(context, message, if (isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
        }
    }
}

fun Context.showToast(message: String, isLong: Boolean = false) {
    ToastHelper.showCustomToast(this, message, isLong)
}
