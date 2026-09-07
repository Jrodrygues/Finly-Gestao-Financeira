package com.jesse.finly.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.firestore.Exclude

@Entity(tableName = "Tabela_utilizadores")
data class Utilizador (
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "nome_completo") val nome: String = "",
    val email: String = "",
    val telemovel: String = "",
    val donoEmail: String = "SISTEMA",
    @get:Exclude @set:Exclude var senha: String = "",
    val darkMode: Boolean = false,
    val notifications: Boolean = false,
    val biometricAtiva: Boolean = false,
    val moeda: String = "EUR",
    val moedaConfigurada: Boolean = false,
    @ColumnInfo(name = "custom_categories") val customCategories: String = ""
)
