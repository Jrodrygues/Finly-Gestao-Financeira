package com.jesse.finly.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.jesse.finly.R
import com.jesse.finly.databinding.BottomSheetEditarCategoriaBinding
import com.jesse.finly.models.Categoria
import com.jesse.finly.utils.Moeda
import com.jesse.finly.utils.MoneyTextWatcher
import com.jesse.finly.utils.UserPreferencesManager
import kotlin.math.round

class EditarCategoriaBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetEditarCategoriaBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefsManager: UserPreferencesManager
    private var moedaAtual: Moeda = Moeda.EUR
    private lateinit var moneyWatcher: MoneyTextWatcher

    var onSalvarCategoriaListener: ((String, Double) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetEditarCategoriaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefsManager = UserPreferencesManager(requireContext())
        moedaAtual = prefsManager.obterMoedaAtual()

        configurarCampoLimite()

        binding.btnSalvarCategoria.setOnClickListener {
            salvarCategoria()
        }
    }

    private fun configurarCampoLimite() {
        // Ajusta o prefixo/sufixo dinamicamente conforme EUR ou BRL
        if (moedaAtual == Moeda.BRL) {
            binding.tilLimite.prefixText = "R$ "
            binding.tilLimite.suffixText = null
        } else {
            binding.tilLimite.prefixText = null
            binding.tilLimite.suffixText = " €"
        }

        // Aplica o MoneyTextWatcher para digitação assistida
        moneyWatcher = MoneyTextWatcher(binding.etLimite, moedaAtual)
        binding.etLimite.addTextChangedListener(moneyWatcher)

        // Se já tiver um limite cadastrado (ex: categoria existente)
        @Suppress("DEPRECATION")
        val categoriaAtual = arguments?.getParcelable<Categoria>("categoria")
        if (categoriaAtual != null) {
            binding.etNomeCategoria.setText(categoriaAtual.nome)
            if (categoriaAtual.limiteMensal > 0.0) {
                val centavos = round(categoriaAtual.limiteMensal * 100).toLong()
                binding.etLimite.setText(centavos.toString())
            }
        }
    }

    private fun salvarCategoria() {
        val nome = binding.etNomeCategoria.text.toString().trim()
        val limiteValor = moneyWatcher.obterValorDouble() // Retorna Double limpo

        if (nome.isEmpty()) {
            binding.tilNomeCategoria.error = getString(R.string.msg_digite_nome_categoria)
            return
        } else {
            binding.tilNomeCategoria.error = null
        }

        onSalvarCategoriaListener?.invoke(nome, limiteValor)
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
