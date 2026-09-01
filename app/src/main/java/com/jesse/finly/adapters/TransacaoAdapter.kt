package com.jesse.finly.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import com.jesse.finly.R
import com.jesse.finly.models.Transacao

class TransacaoAdapter(
    private var lista: List<Transacao>,
    private val onItemClick: (Transacao) -> Unit, // Clique na linha (Vai para Detalhes)
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

        // Lógica para mostrar parcelas (ex: 1/3)
        val textoExibicao = if (item.recorrente && item.parcelasTotais > 0) {
            val parcelaAtual = item.parcelasTotais - item.parcelasRestantes
            "${item.item} ($parcelaAtual/${item.parcelasTotais})"
        } else {
            item.item
        }

        holder.tvItem.text = textoExibicao
        holder.tvVencimento.text = item.vencimento
        holder.tvValor.text = String.format("%.2f €", item.valor)

        if (item.tipo == "DESPESA") {
            holder.tvValor.setTextColor("#F44336".toColorInt())
        } else {
            holder.tvValor.setTextColor("#4CAF50".toColorInt())
            // Removido o símbolo + conforme pedido
        }

        if (item.status) {
            holder.ivStatus.setImageResource(android.R.drawable.checkbox_on_background)
            holder.ivStatus.setColorFilter("#009688".toColorInt())
            // Feedback Visual: Diminuir opacidade de itens pagos
            holder.itemView.alpha = 0.5f
            holder.tvItem.paintFlags = holder.tvItem.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            holder.ivStatus.setImageResource(android.R.drawable.checkbox_off_background)
            holder.ivStatus.setColorFilter("#BDBDBD".toColorInt())
            // Resetar visual para itens não pagos
            holder.itemView.alpha = 1.0f
            holder.tvItem.paintFlags = holder.tvItem.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }

        // Clique na linha inteira para ver Detalhes (Onde estará Editar/Excluir)
        holder.itemView.setOnClickListener {
            val currentPos = holder.bindingAdapterPosition
            if (currentPos != RecyclerView.NO_POSITION) {
                onItemClick(lista[currentPos])
            }
        }
        
        // Clique no ícone de status
        holder.ivStatus.setOnClickListener {
            val currentPos = holder.bindingAdapterPosition
            if (currentPos != RecyclerView.NO_POSITION) {
                onToggleStatus(lista[currentPos])
            }
        }
    }

    override fun getItemCount() = lista.size
}
