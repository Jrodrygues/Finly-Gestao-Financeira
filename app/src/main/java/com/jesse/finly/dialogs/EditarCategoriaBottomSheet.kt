package com.jesse.finly.dialogs

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.jesse.finly.PaywallActivity
import com.jesse.finly.R
import com.jesse.finly.databinding.BottomSheetEditarCategoriaBinding
import com.jesse.finly.models.Categoria
import com.jesse.finly.utils.FinanceiroUtils
import com.jesse.finly.utils.Moeda
import com.jesse.finly.utils.MoneyTextWatcher
import com.jesse.finly.utils.ToastHelper
import com.jesse.finly.utils.UserPreferencesManager
import kotlin.math.round

class EditarCategoriaBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetEditarCategoriaBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefsManager: UserPreferencesManager
    private var moedaAtual: Moeda = Moeda.EUR
    private lateinit var moneyWatcher: MoneyTextWatcher

    var onSalvarCategoriaListener: ((String, Double) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.TransparentBottomSheetDialog)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val bottomSheet = (dialog as? BottomSheetDialog)
            ?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        
        bottomSheet?.let { sheet ->
            val behavior = BottomSheetBehavior.from(sheet)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        }

        dialog?.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundColor(Color.TRANSPARENT)
    }

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

        configurarCampos()

        binding.btnSalvarCategoria.setOnClickListener {
            salvarCategoria()
        }
    }

    private fun configurarCampos() {
        // Ajusta o ícone de moeda para € (ou R$)
        if (moedaAtual == Moeda.BRL) {
            binding.tilLimite.startIconDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_real)
            binding.tilLimite.prefixText = "R$ "
            binding.tilLimite.suffixText = null
        } else {
            binding.tilLimite.startIconDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_euro)
            binding.tilLimite.prefixText = null
            binding.tilLimite.suffixText = " €"
        }

        // Foca e exibe o teclado no campo da categoria
        binding.etNomeCategoria.requestFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(binding.etNomeCategoria, InputMethodManager.SHOW_IMPLICIT)

        // Aplica o MoneyTextWatcher para digitação assistida
        moneyWatcher = MoneyTextWatcher(binding.etLimite, moedaAtual)
        binding.etLimite.addTextChangedListener(moneyWatcher)

        // Se já tiver uma categoria selecionada
        @Suppress("DEPRECATION")
        val categoriaAtual = arguments?.getParcelable<Categoria>("categoria")
        if (categoriaAtual != null) {
            binding.etNomeCategoria.setText(categoriaAtual.nome)
            atualizarIconeCategoria(categoriaAtual.nome)

            // Desabilita a edição do nome da categoria já selecionada
            binding.etNomeCategoria.isEnabled = false
            binding.tilNomeCategoria.isEnabled = false
            binding.etNomeCategoria.alpha = 0.7f

            if (categoriaAtual.limiteMensal > 0.0) {
                val centavos = round(categoriaAtual.limiteMensal * 100).toLong()
                binding.etLimite.setText(centavos.toString())
                binding.btnRemoverTeto.visibility = View.VISIBLE
                binding.btnRemoverTeto.setOnClickListener {
                    onSalvarCategoriaListener?.invoke(categoriaAtual.nome, 0.0)
                    ToastHelper.showCustomToast(requireContext(), getString(R.string.toast_teto_removido))
                    dismiss()
                }
            } else {
                binding.btnRemoverTeto.visibility = View.GONE
            }

            // Foco e teclado direto no campo do limite
            binding.etLimite.requestFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(binding.etLimite, InputMethodManager.SHOW_IMPLICIT)
        } else {
            atualizarIconeCategoria("")
            binding.etNomeCategoria.isEnabled = true
            binding.tilNomeCategoria.isEnabled = true
            binding.etNomeCategoria.alpha = 1.0f
            binding.btnRemoverTeto.visibility = View.GONE

            // Foco e teclado no nome da categoria se for nova
            binding.etNomeCategoria.requestFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(binding.etNomeCategoria, InputMethodManager.SHOW_IMPLICIT)
        }

        // Atualiza o ícone da categoria dinamicamente conforme digita
        binding.etNomeCategoria.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                atualizarIconeCategoria(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun atualizarIconeCategoria(nome: String) {
        val iconeRes = FinanceiroUtils.obterIconeParaCategoria(nome)
        binding.tilNomeCategoria.startIconDrawable = ContextCompat.getDrawable(requireContext(), iconeRes)
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

        // Bloqueio Freemium: Se o utilizador for Grátis, limita a 2 tetos orçamentais
        if (limiteValor > 0.0 && !prefsManager.isPremium()) {
            val limiteAtual = prefsManager.obterLimiteCategoria(nome)
            val tetosAtivos = prefsManager.obterQuantidadeTetosConfigurados()
            if (limiteAtual <= 0.0 && tetosAtivos >= 2) {
                ToastHelper.showCustomToast(requireContext(), "Limite de 2 tetos no plano Grátis 🌟")
                PaywallActivity.abrir(requireContext())
                return
            }
        }

        onSalvarCategoriaListener?.invoke(nome, limiteValor)
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
