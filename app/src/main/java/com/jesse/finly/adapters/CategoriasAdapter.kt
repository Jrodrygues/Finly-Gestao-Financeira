package com.jesse.finly.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.jesse.finly.R
import com.jesse.finly.databinding.ItemCategoriaBinding
import com.jesse.finly.models.Categoria
import com.jesse.finly.utils.CurrencyFormatter
import com.jesse.finly.utils.IdiomaUtils
import com.jesse.finly.utils.Moeda

class CategoriasAdapter(
    private var lista: List<Categoria>,
    private var moeda: Moeda,
    private val onEditarClick: (Categoria) -> Unit
) : RecyclerView.Adapter<CategoriasAdapter.ViewHolder>() {

    fun atualizarMoeda(novaMoeda: Moeda) {
        this.moeda = novaMoeda
        notifyDataSetChanged()
    }

    fun submeterLista(novaLista: List<Categoria>) {
        this.lista = novaLista
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemCategoriaBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Categoria) {
            binding.tvNomeCategoria.text = IdiomaUtils.formatarNomeCategoria(itemView.context, item.nome)
            
            // Verifica se a categoria possui limite definido
            if (item.limiteMensal > 0.0) {
                val gastoStr = CurrencyFormatter.formatar(item.totalGastoNoMes, moeda)
                val limiteStr = CurrencyFormatter.formatar(item.limiteMensal, moeda)
                
                binding.tvValoresOrcamento.text = "$gastoStr / $limiteStr"
                
                // Barra de progresso de consumo do orçamento
                val percentagem = ((item.totalGastoNoMes / item.limiteMensal) * 100).toInt()
                binding.progressBarCategoria.progress = percentagem.coerceAtMost(100)
                
                // Alerta visual se ultrapassou o teto
                if (item.totalGastoNoMes > item.limiteMensal) {
                    binding.tvValoresOrcamento.setTextColor(
                        ContextCompat.getColor(itemView.context, R.color.colorNegative)
                    )
                } else {
                    binding.tvValoresOrcamento.setTextColor(
                        ContextCompat.getColor(itemView.context, R.color.textColorSecondary)
                    )
                }
            } else {
                // Caso não tenha teto estipulado
                val gastoStr = CurrencyFormatter.formatar(item.totalGastoNoMes, moeda)
                binding.tvValoresOrcamento.text = itemView.context.getString(R.string.gasto_sem_teto_format, gastoStr)
                binding.progressBarCategoria.progress = 0
            }

            binding.root.setOnClickListener { onEditarClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategoriaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(lista[position])

    override fun getItemCount(): Int = lista.size
}
