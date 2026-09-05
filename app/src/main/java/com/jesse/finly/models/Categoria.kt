package com.jesse.finly.models

import android.os.Parcel
import android.os.Parcelable

data class Categoria(
    val id: String = "",
    val nome: String = "",
    val limiteMensal: Double = 0.0,
    val totalGastoNoMes: Double = 0.0
) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readDouble(),
        parcel.readDouble()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(nome)
        parcel.writeDouble(limiteMensal)
        parcel.writeDouble(totalGastoNoMes)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Categoria> {
        override fun createFromParcel(parcel: Parcel): Categoria = Categoria(parcel)
        override fun newArray(size: Int): Array<Categoria?> = arrayOfNulls(size)
    }
}
