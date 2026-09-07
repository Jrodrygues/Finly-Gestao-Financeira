package com.jesse.finly.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.jesse.finly.R

object IdiomaUtils {

    /**
     * Aplica o idioma escolhido na aplicação sem precisar de reiniciar o telemóvel.
     * @param tagLocale ex: "pt-PT", "pt-BR", "en-US", "es" ou "" (para seguir o sistema do telemóvel)
     */
    fun aplicarIdioma(tagLocale: String) {
        val appLocale = if (tagLocale.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList() // Segue o padrão do dispositivo
        } else {
            LocaleListCompat.forLanguageTags(tagLocale)
        }
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    /**
     * Obtém o código do idioma ativo atualmente no Finly
     */
    fun obterTagIdiomaAtual(): String {
        val locaisAtuais = AppCompatDelegate.getApplicationLocales()
        return if (!locaisAtuais.isEmpty) {
            locaisAtuais[0]?.toLanguageTag() ?: ""
        } else {
            "" // Segue o sistema
        }
    }

    /**
     * Devolve o nome legível do idioma para mostrar na interface
     */
    fun obterNomeIdiomaAtual(context: Context): String {
        val tag = obterTagIdiomaAtual()
        return when {
            tag.startsWith("pt-BR", ignoreCase = true) -> "Português (Brasil)"
            tag.startsWith("en", ignoreCase = true) -> "English (US)"
            tag.startsWith("es", ignoreCase = true) -> "Español"
            else -> "Português (Portugal)"
        }
    }

    /**
     * Versão simplificada sem Context
     */
    fun obterNomeIdiomaAtual(): String {
        val tag = obterTagIdiomaAtual()
        return when {
            tag.startsWith("pt-BR", ignoreCase = true) -> "Português (Brasil)"
            tag.startsWith("en", ignoreCase = true) -> "English (US)"
            tag.startsWith("es", ignoreCase = true) -> "Español"
            else -> "Português (Portugal)"
        }
    }

    /**
     * Traduz o nome da categoria padrão para o idioma ativo da interface
     */
    fun formatarNomeCategoria(context: Context, nomeCategoria: String?): String {
        if (nomeCategoria.isNullOrBlank()) return ""
        return when (nomeCategoria.trim().lowercase()) {
            "geral" -> context.getString(R.string.cat_geral)
            "habitação", "habitacao" -> context.getString(R.string.cat_habitacao)
            "alimentação", "alimentacao" -> context.getString(R.string.cat_alimentacao)
            "transporte" -> context.getString(R.string.cat_transporte)
            "saúde", "saude" -> context.getString(R.string.cat_saude)
            "lazer" -> context.getString(R.string.cat_lazer)
            "edução", "educação", "educacao" -> context.getString(R.string.cat_educacao)
            "compras" -> context.getString(R.string.cat_compras)
            "assinaturas" -> context.getString(R.string.cat_assinaturas)
            "investimentos" -> context.getString(R.string.cat_investimentos)
            "poupança", "poupanca" -> context.getString(R.string.cat_poupanca)
            "exterior" -> context.getString(R.string.cat_exterior)
            "others", "outros" -> context.getString(R.string.cat_outros)
            else -> nomeCategoria
        }
    }

    /**
     * Obtém a chave interna canónica da categoria a partir do texto exibido na interface
     */
    fun obterChaveInternaCategoria(context: Context, exibida: String?): String {
        if (exibida.isNullOrBlank()) return "Geral"
        val padroes = listOf(
            "Geral", "Habitação", "Alimentação", "Transporte", "Saúde", "Lazer",
            "Educação", "Compras", "Assinaturas", "Investimentos", "Poupança", "Exterior"
        )
        for (key in padroes) {
            val traduzida = formatarNomeCategoria(context, key)
            if (exibida.equals(traduzida, ignoreCase = true) || exibida.equals(key, ignoreCase = true)) {
                return key
            }
        }
        return exibida
    }

    /**
     * Traduz o nome canónico do mês (ex: "Janeiro") para o idioma ativo da interface
     */
    fun formatarNomeMes(context: Context, mesCanonical: String?): String {
        if (mesCanonical.isNullOrBlank()) return ""
        return when (mesCanonical.trim().lowercase()) {
            "janeiro" -> context.getString(R.string.mes_jan)
            "fevereiro" -> context.getString(R.string.mes_fev)
            "março", "marco" -> context.getString(R.string.mes_mar)
            "abril" -> context.getString(R.string.mes_abr)
            "maio" -> context.getString(R.string.mes_mai)
            "junho" -> context.getString(R.string.mes_jun)
            "julho" -> context.getString(R.string.mes_jul)
            "agosto" -> context.getString(R.string.mes_ago)
            "setembro" -> context.getString(R.string.mes_set)
            "outubro" -> context.getString(R.string.mes_out)
            "novembro" -> context.getString(R.string.mes_nov)
            "dezembro" -> context.getString(R.string.mes_dez)
            else -> mesCanonical
        }
    }
}