package com.jesse.finly.utils

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

class MoneyTextWatcher(
    private val editText: EditText,
    moeda: Moeda
) : TextWatcher {

    private var current = ""
    // Garante que o formatador use o separador decimal correto do Locale (vírgula para PT/BR)
    private val symbols = DecimalFormatSymbols(moeda.locale).apply {
        currencySymbol = "" // Removemos o símbolo do miolo do número
    }
    private val formatter = DecimalFormat("#,##0.00", symbols)

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

    override fun afterTextChanged(s: Editable?) {
        if (s.toString() != current) {
            editText.removeTextChangedListener(this)

            // Remove todos os caracteres não numéricos
            val cleanString = s.toString().replace("[^\\d]".toRegex(), "")

            if (cleanString.isNotEmpty()) {
                val parsed = cleanString.toDouble() / 100 // Ex: 1250 vira 12.50
                val formatted = formatter.format(parsed)

                current = formatted
                editText.setText(formatted)
                editText.setSelection(formatted.length) // Mantém o cursor no final
            } else {
                current = ""
                editText.setText("")
            }

            editText.addTextChangedListener(this)
        }
    }

    // Helper para extrair o valor Double limpo para salvar no banco
    fun obterValorDouble(): Double {
        val cleanString = editText.text.toString().replace("[^\\d]".toRegex(), "")
        return if (cleanString.isNotEmpty()) cleanString.toDouble() / 100 else 0.0
    }

    companion object {
        fun obterValorDouble(editText: EditText): Double {
            val cleanString = editText.text.toString().replace("[^\\d]".toRegex(), "")
            return if (cleanString.isNotEmpty()) cleanString.toDouble() / 100 else 0.0
        }
    }
}
