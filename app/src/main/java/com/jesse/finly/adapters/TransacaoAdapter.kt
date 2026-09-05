package com.jesse.finly.adapters

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
import com.jesse.finly.utils.CurrencyFormatter
import com.jesse.finly.utils.FinanceiroUtils
import com.jesse.finly.utils.Moeda
import java.util.Locale

class TransacaoAdapter(
    private var lista: List<Transacao>,
    private var codigoMoeda: String = "EUR",
    private val onItemClick: (Transacao) -> Unit,
    private val onToggleStatus: (Transacao) -> Unit
): RecyclerView.Adapter<TransacaoAdapter.TransacaoViewHolder>() {

    constructor(
        lista: List<Transacao>,
        moeda: Moeda,
        onItemClick: (Transacao) -> Unit,
        onToggleStatus: (Transacao) -> Unit
    ) : this(lista, moeda.codigo, onItemClick, onToggleStatus)

    fun updateData(novaLista: List<Transacao>, novaMoeda: String = codigoMoeda) {
        this.lista = novaLista
        this.codigoMoeda = novaMoeda
        notifyDataSetChanged()
    }

    fun submeterLista(novaLista: List<Transacao>) {
        this.lista = novaLista
        notifyDataSetChanged()
    }

    fun atualizarMoeda(novaMoeda: Moeda) {
        this.codigoMoeda = novaMoeda.codigo
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

        val corPositiva = ContextCompat.getColor(holder.itemView.context, R.color.colorPositive)
        val corNegativa = ContextCompat.getColor(holder.itemView.context, R.color.colorNegative)

        val moedaEnum = Moeda.porCodigo(codigoMoeda)
        val valorComSinal = if (item.tipo == "DESPESA") -item.valor else item.valor
        val valorFormatado = CurrencyFormatter.formatarComSinal(
            valor = valorComSinal,
            moeda = moedaEnum,
            forcarSinalPositivo = (item.tipo == "RENDA")
        )

        holder.tvValor.text = valorFormatado

        if (item.tipo == "DESPESA") {
            holder.tvValor.setTextColor(corNegativa)
        } else {
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
