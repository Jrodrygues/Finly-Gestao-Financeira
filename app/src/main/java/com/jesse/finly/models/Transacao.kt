package com.jesse.finly.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.PropertyName

@Entity(tableName = "tabela_transacoes")
data class Transacao(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val item: String = "",
    val valor: Double = 0.0,
    val vencimento: String = "",
    var status: Boolean = false,
    val tipo: String = "DESPESA", // "DESPESA" ou "RENDA"
    val categoria: String = "Geral",
    val mes: String = "",
    val ano: Int = 2026,
    val donoEmail: String = "",
    
    @get:PropertyName("recorrente")
    @set:PropertyName("recorrente")
    var recorrente: Boolean = false,
    
    val parcelasRestantes: Int = 0, // 0 = finalizada ou não recorrente, -1 = infinita, > 0 = faltam meses
    val parcelasTotais: Int = 0 // Usado para exibição (ex: 1/3)
)
