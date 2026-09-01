package com.jesse.finly.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tabela_metas")
data class MetaPoupanca(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val mes: String = "",
    val ano: Int = 2026,
    val valor: Double = 0.0,
    val donoEmail: String = ""
)
