package com.jesse.finly.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.jesse.finly.`interface`.UtilizadorDAO
import com.jesse.finly.models.MetaPoupanca
import com.jesse.finly.models.Transacao
import com.jesse.finly.models.Utilizador

@Database(entities = [Utilizador::class, Transacao::class, MetaPoupanca::class], version = 25)
abstract class MinhaBaseDados: RoomDatabase() {
    abstract fun utilizadorDao(): UtilizadorDAO

    companion object  {
        private var INSTANCE: MinhaBaseDados? = null

        fun getDatabase(context: Context): MinhaBaseDados {
            return INSTANCE ?: synchronized(this){
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MinhaBaseDados::class.java,
                    "minha_base_dados"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
