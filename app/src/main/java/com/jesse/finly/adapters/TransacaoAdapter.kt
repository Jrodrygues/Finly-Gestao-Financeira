package com.jesse.finly.adapters

import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import com.jesse.finly.R
import com.jesse.finly.models.Transacao
import com.jesse.finly.utils.FinanceiroUtils
import java.util.Locale

class TransacaoAdapter(
    private var lista: List<Transacao>,
    private val onItemClick: (Transacao) -> Unit,
    private val onToggleStatus: (Transacao) -> Unit
): RecyclerView.Adapter<TransacaoAdapter.TransacaoViewHolder>() {

    fun updateData(novaLista: List<Transacao>) {
        this.lista = novaLista
        notifyDataSetChanged()
    }

    fun getItemAt(position: Int): Transacao {
        return lista[position]
    }

    class TransacaoViewHolder(view: View): RecyclerView.ViewHolder(view) {
        val tvItem: TextView = view.findViewById(R.id.tvItem)
        val tvVencimento: TextView = view.findViewById(R.id.tvVencimento)
        val tvValor: TextView = view.findViewById(R.id.tvValor)
        val ivStatus: ImageView = view.findViewById(R.id.ivStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransacaoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transacao, parent, false)
        return TransacaoViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransacaoViewHolder, position: Int) {
        val item = lista[position]

        val textoExibicao = if (item.recorrente && item.parcelasTotais > 0) {
            val parcelaAtual = item.parcelasTotais - item.parcelasRestantes
            "${item.item} ($parcelaAtual/${item.parcelasTotais})"
        } else {
            item.item
        }

        holder.tvItem.text = textoExibicao
        holder.tvVencimento.text = item.vencimento
        holder.tvValor.text = String.format(Locale.getDefault(), "%.2f €", item.valor)

        val corPositiva = ContextCompat.getColor(holder.itemView.context, R.color.colorPositive)
        val corNegativa = ContextCompat.getColor(holder.itemView.context, R.color.colorNegative)

        if (item.tipo == "DESPESA") {
            holder.tvValor.text = FinanceiroUtils.formatarMoeda(item.valor, isPositivo = false)
            holder.tvValor.setTextColor(corNegativa)
        } else {
            holder.tvValor.text = FinanceiroUtils.formatarMoeda(item.valor, isPositivo = true)
            holder.tvValor.setTextColor(corPositiva)
        }

        // Manter fundo padrao e opacidade total
        val corFundoPadrao = ContextCompat.getColor(holder.itemView.context, R.color.backgroundColor)
        holder.itemView.setBackgroundColor(corFundoPadrao)
        holder.itemView.alpha = 1.0f

        if (item.status) {
            holder.ivStatus.setImageResource(android.R.drawable.checkbox_on_background)
            holder.ivStatus.setColorFilter(corPositiva)
        } else {
            holder.ivStatus.setImageResource(android.R.drawable.checkbox_off_background)
            holder.ivStatus.setColorFilter("#9E9E9E".toColorInt())
        }

        // Clique na linha inteira para ver Detalhes
        holder.itemView.setOnClickListener {
            val currentPos = holder.bindingAdapterPosition
            if (currentPos != RecyclerView.NO_POSITION) {
                onItemClick(lista[currentPos])
            }
        }

        // Clique no ícone de status
        holder.ivStatus.setOnClickListener { view ->
            FinanceiroUtils.dispararHapticFeedback(view)
            val currentPos = holder.bindingAdapterPosition
            if (currentPos != RecyclerView.NO_POSITION) {
                onToggleStatus(lista[currentPos])
            }
        }
    }

    override fun getItemCount() = lista.size
}
