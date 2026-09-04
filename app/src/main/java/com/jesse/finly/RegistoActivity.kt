package com.jesse.finly

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.graphics.Rect
import android.widget.EditText
import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.jesse.finly.database.FirebaseManager
import com.jesse.finly.database.MinhaBaseDados
import kotlinx.coroutines.tasks.await
import com.jesse.finly.databinding.ActivityRegistoBinding
import com.jesse.finly.models.Transacao
import com.jesse.finly.models.Utilizador
import com.jesse.finly.utils.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.widget.Button
import android.widget.ImageView
import android.widget.ScrollView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText

class RegistoActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegistoBinding

    companion object {
        private const val PREFS_NAME = "FinlyAppPrefs"
        private const val KEY_EMAIL = "EMAIL"
    }

    private var isEditMode = false
    private var isUserEditMode = false
    private var transacaoId = 0
    private var itemOriginal: String? = null
    private var userId = 0
    private var isNewUserRegistration = false
    private var senhaStr = ""

    private val meses = arrayOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        val isNew = intent.getBooleanExtra("isNewUser", false)
        if (isNew) {
            // Força o modo Light e o fundo degradê apenas para NOVOS utilizadores (fora do ‘app’)
            delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_NO
        }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityRegistoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (isNew) {
            binding.root.setBackgroundResource(R.drawable.login_background)
            binding.mainLayoutRegisto.setBackgroundColor(Color.TRANSPARENT)
        } else {
            val bgCol = ContextCompat.getColor(this, R.color.backgroundColor)
            window.decorView.setBackgroundColor(bgCol)
            binding.root.setBackgroundColor(bgCol)
            binding.mainLayoutRegisto.setBackgroundColor(bgCol)
        }

        isNewUserRegistration = isNew
        isEditMode = intent.getBooleanExtra("isEditTransaction", false)
        isUserEditMode = intent.getBooleanExtra("isUserEdit", false)

        configurarSpinners()





        binding.btnBackRegisto.setOnClickListener { finish() }

        if (isNewUserRegistration) {
            configurarParaNovoUtilizador()
        } else if (isUserEditMode) {
            configurarParaEdicaoUtilizador()
        } else {
            configurarParaTransacao()
            if (isEditMode) {
                configurarParaEdicaoTransacao()
            }
        }

        // Lógica de bloqueio total de teclado na Categoria
        binding.autoCompleteCategoria.apply {
            keyListener = null
            isFocusable = false
            isFocusableInTouchMode = false
            
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    // 1. Derrubar o teclado IMEDIATAMENTE
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)

                    
                    // 2. Limpar qualquer foco que esteja a chamar o teclado
                    currentFocus?.clearFocus()
                    
                    // 3. Abrir a lista
                    postDelayed({ showDropDown() }, 50)
                }
                true 
            }
        }








        binding.btnFinalizarRegisto.setOnClickListener {
            when {
                isNewUserRegistration -> registarNovoUtilizador()
                isUserEditMode -> guardarEdicaoUtilizador()
                else -> guardarTransacao()
            }
        }


        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Aplicar o padding no ConstraintLayout interno para o ScrollView funcionar bem com teclado
            binding.mainLayoutRegisto.updatePadding(top = systemBars.top, bottom = systemBars.bottom)
            insets
        }
    }

    // Lógica Global: Esconder teclado ao tocar fora de qualquer campo de texto
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is EditText) {
                val outRect = Rect()
                v.getGlobalVisibleRect(outRect)
                if (!outRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    v.clearFocus()
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun esconderTeclado() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        val view = currentFocus ?: window.decorView.rootView
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
        currentFocus?.clearFocus()
    }





    override fun onResume() {
        super.onResume()
        configurarSpinners()
    }

    private fun obterCategoriasDisponiveis(): MutableList<String> {
        val lista = mutableListOf(
            "Geral", "Habitação", "Alimentação", "Transporte", "Saúde", "Lazer",
            "Educação", "Compras", "Assinaturas", "Investimentos", "Poupança", "Exterior"
        )
        val customList = obterCategoriasCustomizadas()
        for (cat in customList) {
            if (cat.isNotBlank() && !lista.contains(cat)) {
                lista.add(cat)
            }
        }
        lista.add(getString(R.string.option_nova_categoria))
        return lista
    }

    private fun formatarTelemovel(phone: String): String {
        val clean = phone.trim()
        val digits = clean.filter { it.isDigit() }
        if (digits.length == 9) {
            return "${digits.substring(0, 3)} ${digits.substring(3, 6)} ${digits.substring(6)}"
        }
        return clean
    }

    private fun obterCategoriasCustomizadas(): MutableList<String> {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val userEmail = prefs.getString(KEY_EMAIL, "") ?: ""
        val emailClean = userEmail.trim().lowercase()

        var customSet = prefs.getStringSet("CUSTOM_CATEGORIES_$emailClean", null)

        if (customSet == null) {
            val oldPrefs = getSharedPreferences("PreferenciasDaMinhaApp", MODE_PRIVATE)
            val oldSet = oldPrefs.getStringSet("CUSTOM_CATEGORIES_$emailClean", null)
            if (!oldSet.isNullOrEmpty()) {
                customSet = oldSet
                prefs.edit { putStringSet("CUSTOM_CATEGORIES_$emailClean", oldSet) }
            }
        }

        return (customSet ?: emptySet()).filter { it.isNotBlank() }.toMutableList()
    }

    private fun salvarNovaCategoria(nome: String) {
        val catClean = nome.trim()
        if (catClean.isEmpty()) return

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val userEmail = prefs.getString(KEY_EMAIL, "") ?: ""
        val emailClean = userEmail.trim().lowercase()

        val customSet = prefs.getStringSet("CUSTOM_CATEGORIES_$emailClean", emptySet())?.toMutableSet() ?: mutableSetOf()
        customSet.add(catClean)
        prefs.edit { putStringSet("CUSTOM_CATEGORIES_$emailClean", customSet) }

        sincronizarCategoriasNuvem(customSet)
        configurarSpinners()
        binding.autoCompleteCategoria.setText(catClean, false)
        atualizarIconeCategoria(catClean)
        showToast(getString(R.string.toast_categoria_adicionada, catClean))
    }

    private fun removerCategoriaCustomizada(categoria: String) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val userEmail = prefs.getString(KEY_EMAIL, "") ?: ""
        val emailClean = userEmail.trim().lowercase()

        val customSet = prefs.getStringSet("CUSTOM_CATEGORIES_$emailClean", emptySet())?.toMutableSet() ?: mutableSetOf()
        customSet.remove(categoria)
        prefs.edit { putStringSet("CUSTOM_CATEGORIES_$emailClean", customSet) }

        sincronizarCategoriasNuvem(customSet)
        configurarSpinners()
        val lista = obterCategoriasDisponiveis()
        val primeiraValida = lista.firstOrNull { it != getString(R.string.option_nova_categoria) } ?: "Geral"
        binding.autoCompleteCategoria.setText(primeiraValida, false)
        atualizarIconeCategoria(primeiraValida)
        showToast(getString(R.string.toast_categoria_removida, categoria))
    }

    private fun confirmarEliminacaoCategoria(categoria: String, onConfirm: () -> Unit) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val userEmail = prefs.getString(KEY_EMAIL, "") ?: ""
        val emailClean = userEmail.trim().lowercase()

        lifecycleScope.launch(Dispatchers.IO) {
            val db = MinhaBaseDados.getDatabase(this@RegistoActivity)
            val transacoesAfetadas = db.utilizadorDao().obterTransacoesPorDono(emailClean)
                .filter { it.categoria.equals(categoria, ignoreCase = true) }

            withContext(Dispatchers.Main) {
                if (transacoesAfetadas.isEmpty()) {
                    MaterialAlertDialogBuilder(this@RegistoActivity)
                        .setTitle("Eliminar Categoria")
                        .setMessage("Tem a certeza que deseja eliminar a categoria '$categoria'?")
                        .setPositiveButton(getString(R.string.btn_sim_eliminar)) { _, _ ->
                            onConfirm()
                        }
                        .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                } else {
                    val quantidade = transacoesAfetadas.size
                    val mensagem = if (quantidade == 1) {
                        "1 transação será movida para a categoria Geral."
                    } else {
                        "$quantidade transações serão movidas para a categoria Geral."
                    }
                    MaterialAlertDialogBuilder(this@RegistoActivity)
                        .setTitle("Categoria em uso")
                        .setMessage(mensagem)
                        .setPositiveButton("Eliminar") { _, _ ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                transacoesAfetadas.forEach { trans ->
                                    val transAtualizada = trans.copy(categoria = "Geral")
                                    db.utilizadorDao().atualizarTransacao(transAtualizada)
                                    FirebaseManager.salvarTransacaoNoFirestore(transAtualizada)
                                }
                                withContext(Dispatchers.Main) {
                                    onConfirm()
                                }
                            }
                        }
                        .setNegativeButton("Cancelar") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                }
            }
        }
    }

    private fun sincronizarCategoriasNuvem(customSet: Set<String>) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val userEmail = prefs.getString(KEY_EMAIL, "") ?: ""
        val emailClean = userEmail.trim().lowercase()

        if (emailClean.isNotEmpty() && emailClean != "convidado") {
            lifecycleScope.launch(Dispatchers.IO) {
                val db = MinhaBaseDados.getDatabase(this@RegistoActivity)
                val user = db.utilizadorDao().buscarPorEmail(emailClean)
                val catStr = customSet.joinToString(",")
                val updatedUser = user?.copy(customCategories = catStr) ?: Utilizador(
                    email = emailClean,
                    donoEmail = emailClean,
                    customCategories = catStr
                )
                db.utilizadorDao().atualizarUtilizador(updatedUser)
                FirebaseManager.salvarUtilizadorNoFirestore(updatedUser)
            }
        }
    }

    private fun obterIconeParaCategoria(nome: String): Int {
        return when (nome.trim().lowercase()) {
            "geral" -> R.drawable.ic_list
            "habitação" -> R.drawable.ic_home
            "alimentação" -> R.drawable.ic_restaurant
            "transporte" -> R.drawable.ic_transport
            "saúde" -> R.drawable.ic_health
            "lazer" -> R.drawable.ic_sports
            "educação" -> R.drawable.ic_school
            "compras" -> R.drawable.ic_list
            "assinaturas" -> R.drawable.ic_pdf
            "investimentos" -> R.drawable.ic_euro
            "poupança" -> R.drawable.ic_lock
            "exterior" -> R.drawable.ic_flight
            else -> R.drawable.ic_tag
        }
    }

    private fun atualizarIconeCategoria(nome: String) {
        val iconeRes = obterIconeParaCategoria(nome)
        binding.categoriaLayout.setStartIconDrawable(iconeRes)
    }

    private fun mostrarBottomSheetGerirCategorias() {
        val bottomSheetDialog = BottomSheetDialog(this, R.style.TransparentBottomSheetDialog)
        val bsView = layoutInflater.inflate(R.layout.bottom_sheet_gerir_categorias, null)
        bottomSheetDialog.setContentView(bsView)
        bottomSheetDialog.window?.setDimAmount(0.85f)
        bottomSheetDialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        bottomSheetDialog.setOnShowListener {
            val bottomSheet = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                sheet.setBackgroundColor(Color.TRANSPARENT)
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }

        val etNova = bsView.findViewById<TextInputEditText>(R.id.etNovaCategoriaBS)
        val btnAdd = bsView.findViewById<MaterialButton>(R.id.btnAdicionarCategoriaBS)
        val btnFechar = bsView.findViewById<View>(R.id.btnFecharBS)
        val containerMinhas = bsView.findViewById<LinearLayout>(R.id.containerMinhasCategorias)
        val containerPadrao = bsView.findViewById<LinearLayout>(R.id.containerCategoriasPadrao)
        val tvSemCategorias = bsView.findViewById<TextView>(R.id.tvSemCategoriasCustom)

        val categoriasPadrao = listOf(
            "Geral", "Habitação", "Alimentação", "Transporte", "Saúde", "Lazer",
            "Educação", "Compras", "Assinaturas", "Investimentos", "Poupança", "Exterior"
        )

        var isPadraoExpanded = false
        val btnVerMaisPadrao = bsView.findViewById<Button>(R.id.btnVerMaisPadrao)

        fun atualizarListaCustom() {
            containerMinhas?.removeAllViews()
            containerPadrao?.removeAllViews()
            val customList = obterCategoriasCustomizadas()

            // 1. As Minhas Categorias Personalizadas (NO TOPO)
            if (customList.isEmpty()) {
                tvSemCategorias?.visibility = View.VISIBLE
            } else {
                tvSemCategorias?.visibility = View.GONE
                for (cat in customList) {
                    val itemView = layoutInflater.inflate(R.layout.item_categoria_custom, containerMinhas, false)
                    val ivIcon = itemView.findViewById<ImageView>(R.id.ivIconCategoriaCustom)
                    val tvNome = itemView.findViewById<TextView>(R.id.tvNomeCategoriaCustom)
                    val tvTag = itemView.findViewById<TextView>(R.id.tvTagPadraoCustom)
                    val btnRemover = itemView.findViewById<View>(R.id.btnRemoverCategoriaCustom)

                    ivIcon?.setImageResource(R.drawable.ic_tag)
                    tvNome?.text = cat
                    tvTag?.visibility = View.GONE
                    btnRemover?.visibility = View.VISIBLE
                    btnRemover?.setOnClickListener {
                        confirmarEliminacaoCategoria(cat) {
                            removerCategoriaCustomizada(cat)
                            atualizarListaCustom()
                        }
                    }
                    containerMinhas?.addView(itemView)
                }
            }

            // 2. Categorias do Sistema (Mostrar 4 por omissão ou todas se expandido)
            val padraoParaMostrar = if (isPadraoExpanded) categoriasPadrao else categoriasPadrao.take(4)
            btnVerMaisPadrao?.visibility = View.VISIBLE
            btnVerMaisPadrao?.text = if (isPadraoExpanded) "Ver menos" else "Ver mais categorias do sistema"

            for (catPadrao in padraoParaMostrar) {
                val itemView = layoutInflater.inflate(R.layout.item_categoria_custom, containerPadrao, false)
                val ivIcon = itemView.findViewById<ImageView>(R.id.ivIconCategoriaCustom)
                val tvNome = itemView.findViewById<TextView>(R.id.tvNomeCategoriaCustom)
                val tvTag = itemView.findViewById<TextView>(R.id.tvTagPadraoCustom)
                val btnRemover = itemView.findViewById<View>(R.id.btnRemoverCategoriaCustom)

                ivIcon?.setImageResource(obterIconeParaCategoria(catPadrao))
                tvNome?.text = catPadrao
                tvTag?.visibility = View.VISIBLE
                btnRemover?.visibility = View.GONE
                containerPadrao?.addView(itemView)
            }
        }

        btnVerMaisPadrao?.setOnClickListener {
            isPadraoExpanded = !isPadraoExpanded
            atualizarListaCustom()
        }

        atualizarListaCustom()

        btnAdd?.setOnClickListener {
            val novaCat = etNova?.text.toString().trim()
            if (novaCat.isNotEmpty()) {
                salvarNovaCategoria(novaCat)
                etNova?.setText("")
                etNova?.clearFocus()

                val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(etNova?.windowToken, 0)

                atualizarListaCustom()
            } else {
                showToast("Digite o nome da categoria")
            }
        }

        btnFechar?.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    private fun configurarSpinners() {
        val listaCategorias = obterCategoriasDisponiveis()
        val adapterCat = ArrayAdapter(this, R.layout.dropdown_item, listaCategorias)
        binding.autoCompleteCategoria.setAdapter(adapterCat)

        if (binding.autoCompleteCategoria.text.isNullOrEmpty()) {
            val primeiraValida = listaCategorias.firstOrNull { it != getString(R.string.option_nova_categoria) } ?: "Geral"
            binding.autoCompleteCategoria.setText(primeiraValida, false)
            atualizarIconeCategoria(primeiraValida)
        } else {
            atualizarIconeCategoria(binding.autoCompleteCategoria.text.toString())
        }

        binding.autoCompleteCategoria.setOnItemClickListener { _, _, position, _ ->
            val selecionada = adapterCat.getItem(position)
            if (selecionada == getString(R.string.option_nova_categoria)) {
                val primeiraValida = listaCategorias.firstOrNull { it != getString(R.string.option_nova_categoria) } ?: "Geral"
                binding.autoCompleteCategoria.setText(primeiraValida, false)
                atualizarIconeCategoria(primeiraValida)
                mostrarBottomSheetGerirCategorias()
            } else if (selecionada != null) {
                atualizarIconeCategoria(selecionada)
            }
        }
    }



    private fun configurarParaNovoUtilizador() {
        binding.btnAlterarSenhaPerfil.visibility = View.GONE
        binding.titleRegisto.text = getString(R.string.criar_nova_conta)
        binding.itemLayout.hint = getString(R.string.hint_nome_completo)
        binding.itemLayout.setStartIconDrawable(R.drawable.ic_person)
        
        binding.valorLayout.hint = getString(R.string.hint_email_input)
        binding.valorLayout.setStartIconDrawable(R.drawable.ic_email)
        binding.valorLayout.alpha = 1.0f
        binding.regEmailText.isEnabled = true
        binding.regEmailText.isFocusable = true
        binding.regEmailText.isFocusableInTouchMode = true
        binding.regEmailText.inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        
        binding.dataLayout.hint = getString(R.string.hint_telemovel)
        binding.dataLayout.setStartIconDrawable(R.drawable.ic_phone)
        binding.regPhoneText.inputType = android.text.InputType.TYPE_CLASS_PHONE
        binding.regPhoneText.isFocusableInTouchMode = true
        binding.regPhoneText.isFocusable = true
        binding.regPhoneText.setOnClickListener(null)
        
        binding.senhaLayout.hint = getString(R.string.hint_palavra_passe)
        binding.senhaLayout.visibility = View.VISIBLE
        binding.senhaLayout.setStartIconDrawable(R.drawable.ic_lock)
        binding.confirmarSenhaLayout.hint = getString(R.string.hint_confirmar_palavra_passe)
        binding.confirmarSenhaLayout.visibility = View.VISIBLE
        binding.confirmarSenhaLayout.setStartIconDrawable(R.drawable.ic_lock)
        
        binding.categoriaLayout.visibility = View.GONE
        binding.toggleTipoTransacao.visibility = View.GONE
        binding.llRecorrenciaOpcoes.visibility = View.GONE
        binding.parcelasLayout.visibility = View.GONE
        
        binding.switchRecorrente.isChecked = false
        binding.btnFinalizarRegisto.text = getString(R.string.btn_criar_conta)
    }

    private fun configurarParaEdicaoUtilizador() {
        userId = intent.getIntExtra("id", 0)
        binding.titleRegisto.text = getString(R.string.editar_perfil)
        binding.itemLayout.hint = getString(R.string.hint_nome_completo)
        binding.itemLayout.setStartIconDrawable(R.drawable.ic_person)
        binding.regNameText.setText(intent.getStringExtra("name"))
        
        binding.valorLayout.hint = getString(R.string.hint_email_inalteravel)
        binding.valorLayout.setStartIconDrawable(R.drawable.ic_email)
        binding.regEmailText.setText(intent.getStringExtra("email"))
        binding.regEmailText.isFocusable = false
        binding.regEmailText.isFocusableInTouchMode = false
        binding.regEmailText.isLongClickable = false
        binding.valorLayout.alpha = 0.5f

        val showToastEmailBlock = {
            showToast("E-mail inalterável")
        }

        binding.regEmailText.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                showToastEmailBlock()
                v.performClick()
            }
            true
        }

        binding.valorLayout.setOnClickListener {
            showToastEmailBlock()
        }
        
        binding.dataLayout.hint = getString(R.string.hint_telemovel)
        binding.dataLayout.setStartIconDrawable(R.drawable.ic_phone)
        binding.regPhoneText.setText(formatarTelemovel(intent.getStringExtra("phone") ?: ""))
        binding.regPhoneText.inputType = android.text.InputType.TYPE_CLASS_PHONE
        binding.regPhoneText.isFocusableInTouchMode = true
        binding.regPhoneText.isFocusable = true
        binding.regPhoneText.setOnClickListener(null)

        senhaStr = intent.getStringExtra("senha") ?: ""
        binding.senhaLayout.visibility = View.GONE
        binding.confirmarSenhaLayout.visibility = View.GONE
        binding.senhaLayout.hint = "Nova Palavra-passe"
        binding.confirmarSenhaLayout.hint = "Confirmar Nova Palavra-passe"
        binding.regSenhaText.setText("")
        binding.regConfirmarSenhaText.setText("")

        binding.btnAlterarSenhaPerfil.visibility = View.VISIBLE
        binding.btnAlterarSenhaPerfil.setOnClickListener {
            mostrarDialogValidarSenhaAtual()
        }

        binding.btnCancelarTrocaSenha.setOnClickListener {
            binding.senhaLayout.visibility = View.GONE
            binding.confirmarSenhaLayout.visibility = View.GONE
            binding.btnCancelarTrocaSenha.visibility = View.GONE
            binding.regSenhaText.setText("")
            binding.regConfirmarSenhaText.setText("")
            binding.btnAlterarSenhaPerfil.visibility = View.VISIBLE
        }

        binding.categoriaLayout.visibility = View.GONE
        binding.toggleTipoTransacao.visibility = View.GONE
        binding.llRecorrenciaOpcoes.visibility = View.GONE
        binding.parcelasLayout.visibility = View.GONE
        
        binding.switchRecorrente.isChecked = false
        binding.btnFinalizarRegisto.text = getString(R.string.btn_atualizar_perfil)
    }

    private fun mostrarDialogValidarSenhaAtual() {
        val view = layoutInflater.inflate(R.layout.dialog_validar_senha_atual, binding.root as? ViewGroup, false)
        val etSenhaAtual = view.findViewById<TextInputEditText>(R.id.etSenhaAtual)

        val icon = ContextCompat.getDrawable(this, R.drawable.ic_lock)?.mutate()
        icon?.setTint(ContextCompat.getColor(this, R.color.colorPrimary))

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Palavra-passe Atual")
            .setIcon(icon)
            .setMessage("Por motivos de segurança, introduza a sua palavra-passe atual para definir uma nova.")
            .setView(view)
            .setPositiveButton("Confirmar") { d, _ ->
                val digitada = etSenhaAtual?.text.toString().trim()
                if (digitada == senhaStr) {
                    showToast("Palavra-passe confirmada!")
                    binding.btnAlterarSenhaPerfil.visibility = View.GONE
                    binding.senhaLayout.visibility = View.VISIBLE
                    binding.confirmarSenhaLayout.visibility = View.VISIBLE
                    binding.btnCancelarTrocaSenha.visibility = View.VISIBLE
                    binding.regSenhaText.requestFocus()
                    d.dismiss()
                } else {
                    showToast("Palavra-passe atual incorreta!")
                    binding.btnAlterarSenhaPerfil.visibility = View.VISIBLE
                    binding.senhaLayout.visibility = View.GONE
                    binding.confirmarSenhaLayout.visibility = View.GONE
                    binding.btnCancelarTrocaSenha.visibility = View.GONE
                    d.dismiss()
                }
            }
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.show()

        val btnPositive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val btnNegative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

        btnPositive?.apply {
            setBackgroundColor(ContextCompat.getColor(this@RegistoActivity, R.color.colorPrimary))
            setTextColor(ContextCompat.getColor(this@RegistoActivity, R.color.white))
            val dpHorizontal = (16 * resources.displayMetrics.density).toInt()
            val dpVertical = (8 * resources.displayMetrics.density).toInt()
            setPadding(dpHorizontal, dpVertical, dpHorizontal, dpVertical)
        }

        btnNegative?.apply {
            setTextColor(ContextCompat.getColor(this@RegistoActivity, R.color.textColorSecondary))
        }
    }

    private fun configurarTipoTransacaoToggle() {
        val corPositiva = ContextCompat.getColor(this, R.color.colorPositive)
        val corNegativa = ContextCompat.getColor(this, R.color.colorNegative)

        binding.toggleTipoTransacao.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                if (checkedId == R.id.btnToggleDespesa) {
                    binding.btnToggleDespesa.setBackgroundColor(corNegativa)
                    binding.btnToggleDespesa.setTextColor(Color.WHITE)
                    binding.btnToggleRenda.setBackgroundColor(Color.TRANSPARENT)
                    binding.btnToggleRenda.setTextColor(corPositiva)
                } else if (checkedId == R.id.btnToggleRenda) {
                    binding.btnToggleRenda.setBackgroundColor(corPositiva)
                    binding.btnToggleRenda.setTextColor(Color.WHITE)
                    binding.btnToggleDespesa.setBackgroundColor(Color.TRANSPARENT)
                    binding.btnToggleDespesa.setTextColor(corNegativa)
                }
            }
        }
        binding.toggleTipoTransacao.check(R.id.btnToggleDespesa)
    }

    private fun configurarRecorrenciaSwitch() {
        binding.switchRecorrente.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.llSubRecorrencia.visibility = View.VISIBLE
            } else {
                binding.llSubRecorrencia.visibility = View.GONE
                esconderTeclado()
            }
        }

        binding.rgRecorrenciaTipo.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbRecorrenteParcelada) {
                binding.parcelasLayout.visibility = View.VISIBLE
                binding.etParcelas.requestFocus()
                binding.root.postDelayed({
                    binding.root.fullScroll(View.FOCUS_DOWN)
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(binding.etParcelas, InputMethodManager.SHOW_IMPLICIT)
                }, 350)
            } else {
                binding.parcelasLayout.visibility = View.GONE
                esconderTeclado()
            }
        }

        binding.etParcelas.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.root.postDelayed({
                    binding.root.fullScroll(View.FOCUS_DOWN)
                }, 350)
            }
        }

        binding.etParcelas.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                esconderTeclado()
                true
            } else {
                false
            }
        }
    }

    private fun configurarParaTransacao() {
        binding.btnAlterarSenhaPerfil.visibility = View.GONE
        binding.senhaLayout.visibility = View.GONE
        binding.confirmarSenhaLayout.visibility = View.GONE
        binding.valorLayout.alpha = 1.0f
        binding.regEmailText.isEnabled = true
        binding.regEmailText.isFocusable = true
        binding.regEmailText.isFocusableInTouchMode = true
        binding.itemLayout.setStartIconDrawable(R.drawable.ic_edit)
        binding.valorLayout.setStartIconDrawable(R.drawable.ic_euro)
        binding.dataLayout.setStartIconDrawable(R.drawable.ic_calendar)

        binding.regPhoneText.setOnClickListener { mostrarCalendario() }

        binding.llRecorrenciaOpcoes.visibility = View.VISIBLE
        configurarTipoTransacaoToggle()
        configurarRecorrenciaSwitch()

        if (!isEditMode) {
            val cal = Calendar.getInstance()
            val diaAtual = cal[Calendar.DAY_OF_MONTH]
            val mesAtualIndex = cal[Calendar.MONTH]
            val mesAtualNome = meses[mesAtualIndex]

            val mesVindoDaApp = intent.getStringExtra("MES_ATUAL") ?: mesAtualNome
            val mesParaData = meses.indexOf(mesVindoDaApp) + 1

            binding.regPhoneText.setText(String.format(Locale.getDefault(), "%02d/%02d", diaAtual, mesParaData))
        }
    }

    private fun configurarParaEdicaoTransacao() {
        transacaoId = intent.getIntExtra("id", 0)
        itemOriginal = intent.getStringExtra("item")
        binding.titleRegisto.text = getString(R.string.editar_item)
        binding.btnEliminarTop.visibility = View.VISIBLE
        binding.btnEliminarTop.setOnClickListener { confirmarEliminacaoTransacaoFromEdit() }

        binding.regNameText.setText(itemOriginal)

        val valEdit = intent.getDoubleExtra("valor", 0.0)
        binding.regEmailText.setText(String.format(Locale.US, "%.2f", valEdit))
        binding.regPhoneText.setText(intent.getStringExtra("vencimento"))

        val tipo = intent.getStringExtra("tipo")
        if (tipo == "RENDA") {
            binding.toggleTipoTransacao.check(R.id.btnToggleRenda)
        } else {
            binding.toggleTipoTransacao.check(R.id.btnToggleDespesa)
        }

        val catSalva = intent.getStringExtra("categoria")
        if (catSalva != null) {
            binding.autoCompleteCategoria.setText(catSalva, false)
            atualizarIconeCategoria(catSalva)
        } else {
            val primeiraValida = obterCategoriasDisponiveis().firstOrNull { it != getString(R.string.option_nova_categoria) } ?: "Geral"
            atualizarIconeCategoria(primeiraValida)
        }

        val isRec = intent.getBooleanExtra("isRecorrente", false)
        val tot = intent.getIntExtra("parcelasTotais", 0)

        if (isRec) {
            binding.switchRecorrente.isChecked = true
            binding.llSubRecorrencia.visibility = View.VISIBLE
            if (tot == -1) {
                binding.rbRecorrenteFixa.isChecked = true
                binding.parcelasLayout.visibility = View.GONE
            } else if (tot > 0) {
                binding.rbRecorrenteParcelada.isChecked = true
                binding.etParcelas.setText(tot.toString())
                binding.parcelasLayout.visibility = View.VISIBLE
            }
        } else {
            binding.switchRecorrente.isChecked = false
            binding.llSubRecorrencia.visibility = View.GONE
        }

        binding.btnFinalizarRegisto.text = getString(R.string.atualizar_item)
    }

    private fun confirmarEliminacaoTransacaoFromEdit() {
        val dialog = BottomSheetDialog(this, R.style.TransparentBottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_eliminar_simples, binding.root as? ViewGroup, false)

        view.findViewById<TextView>(R.id.tvMensagemSimples).text =
            getString(R.string.dialog_eliminar_item_msg, itemOriginal ?: "")

        view.findViewById<View>(R.id.btnConfirmarEliminar).setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val db = MinhaBaseDados.getDatabase(this@RegistoActivity)
                val trans = db.utilizadorDao().obterTransacaoPorId(transacaoId)
                trans?.let {
                    db.utilizadorDao().apagarTransacao(it)
                    FirebaseManager.eliminarTransacaoDoFirestore(it)
                }
                withContext(Dispatchers.Main) {
                    showToast("Item removido com sucesso!")
                    finish()
                }
            }
            dialog.dismiss()
        }

        view.findViewById<View>(R.id.btnCancelarSimples).setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(view)
        dialog.window?.setDimAmount(0.90f)
        dialog.show()
    }

    private fun mostrarCalendario() {
        esconderTeclado()
        val c = Calendar.getInstance()
        val dpd = DatePickerDialog(
            this,
            { _, _, month, day ->
                binding.regPhoneText.setText(String.format(Locale.getDefault(), "%02d/%02d", day, month + 1))
            },
            c[Calendar.YEAR],
            c[Calendar.MONTH],
            c[Calendar.DAY_OF_MONTH],
        )
        dpd.show()
    }

    private fun guardarTransacao() {
        val item = binding.regNameText.text.toString().trim()
        val valorStr = binding.regEmailText.text.toString().trim()
        var venc = binding.regPhoneText.text.toString().trim()
        val tipo = if (binding.toggleTipoTransacao.checkedButtonId == R.id.btnToggleRenda) "RENDA" else "DESPESA"
        val categoria = binding.autoCompleteCategoria.text.toString()

        val mes = intent.getStringExtra("mes") ?: intent.getStringExtra("MES_ATUAL") ?: meses[Calendar.getInstance()[Calendar.MONTH]]

        if (item.isEmpty() || valorStr.isEmpty() || venc.isEmpty()) {
            showToast("Introduza a descrição, o valor e a data")
            return
        }

        if (!venc.contains("/")) {
            val diaInt = venc.toIntOrNull()
            if (diaInt != null && diaInt in 1..31) {
                val mesIndex = meses.indexOf(mes) + 1
                venc = String.format(Locale.getDefault(), "%02d/%02d", diaInt, mesIndex)
            } else {
                showToast("Data inválida. Use o formato DD/MM")
                return
            }
        }

        val isRecorrente = binding.switchRecorrente.isChecked
        val isParcelada = binding.rbRecorrenteParcelada.isChecked
        val inputParcelas = binding.etParcelas.text.toString().toIntOrNull() ?: 2

        val numParcelas = when {
            !isRecorrente -> 0
            isParcelada -> if (inputParcelas > 0) inputParcelas else 2
            else -> -1
        }

        val valor = valorStr.replace(",", ".").toDoubleOrNull() ?: 0.0
        val email = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_EMAIL, "CONVIDADO") ?: "CONVIDADO"

        val anoIntent = if (isEditMode) {
            intent.getIntExtra("ano", Calendar.getInstance()[Calendar.YEAR])
        } else {
            intent.getIntExtra("ANO_ATUAL", Calendar.getInstance()[Calendar.YEAR])
        }

        executarGravacao(item, valor, venc, tipo, categoria, mes, email, isRecorrente, numParcelas, anoIntent)
    }

    private fun executarGravacao(item: String, valor: Double, venc: String, tipo: String, cat: String, mes: String, dono: String, isRec: Boolean, numParcelas: Int, ano: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = MinhaBaseDados.getDatabase(this@RegistoActivity)
            
            var finalIsRec = isRec
            var finalNumParcelas = numParcelas
            
            if (isEditMode && !isRec) {
                val transExistente = db.utilizadorDao().obterTransacaoPorId(transacaoId)
                if (transExistente?.recorrente == true) {
                    finalIsRec = true
                    finalNumParcelas = transExistente.parcelasTotais
                }
            }

            val transacao = Transacao(
                id = if (isEditMode) transacaoId else 0,
                item = item, valor = valor, vencimento = venc,
                tipo = tipo, categoria = cat, mes = mes,
                ano = ano,
                donoEmail = dono,
                status = intent.getBooleanExtra("status", false),
                recorrente = finalIsRec,
                parcelasRestantes = when {
                    finalNumParcelas > 0 -> finalNumParcelas - 1
                    finalNumParcelas == -1 -> -1
                    else -> 0
                },
                parcelasTotais = finalNumParcelas,
            )

            if (isEditMode) {
                db.utilizadorDao().atualizarTransacao(transacao)
                FirebaseManager.salvarTransacaoNoFirestore(transacao)
                
                if (finalIsRec) {
                    val todasDoDono = db.utilizadorDao().obterTransacoesPorDono(dono)
                    val mesIndexAtual = meses.indexOfFirst { it.equals(mes, ignoreCase = true) }
                    
                    todasDoDono.filter { 
                        (it.item.trim().equals(itemOriginal?.trim() ?: item.trim(), ignoreCase = true)) && 
                        ((it.ano > ano) || ((it.ano == ano) && (meses.indexOfFirst { m -> m.equals(it.mes, ignoreCase = true) } > mesIndexAtual)))
                    }.forEach { futura ->
                        val atualizada = futura.copy(item = item, valor = valor)
                        db.utilizadorDao().atualizarTransacao(atualizada)
                        FirebaseManager.salvarTransacaoNoFirestore(atualizada)
                    }
                }
            } else {
                val novoId = db.utilizadorDao().inserirTransacao(transacao).toInt()
                FirebaseManager.salvarTransacaoNoFirestore(transacao.copy(id = novoId))
            }

            withContext(Dispatchers.Main) {
                showToast("Dados guardados!")
                finish()
            }
        }
    }

    private fun registarNovoUtilizador() {
        val nome = binding.regNameText.text.toString().trim()
        val emailInput = binding.regEmailText.text.toString().trim()
        val phone = binding.regPhoneText.text.toString().trim()
        val senha = binding.regSenhaText.text.toString().trim()
        val confirmarSenha = binding.regConfirmarSenhaText.text.toString().trim()

        if (nome.isEmpty() || emailInput.isEmpty() || senha.isEmpty() || confirmarSenha.isEmpty()) {
            showToast("Introduza todos os campos obrigatórios")
            return
        }

        if (senha != confirmarSenha) {
            showToast("As palavras-passe não coincidem")
            return
        }

        val email = emailInput.lowercase()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val auth = FirebaseAuth.getInstance()
                auth.createUserWithEmailAndPassword(email, senha).await()

                val novoUtilizador = Utilizador(
                    nome = nome, 
                    email = email, 
                    telemovel = phone, 
                    donoEmail = "SISTEMA", 
                    senha = senha
                )

                val db = MinhaBaseDados.getDatabase(this@RegistoActivity)
                db.utilizadorDao().insertUtilizador(novoUtilizador)
                FirebaseManager.salvarUtilizadorNoFirestore(novoUtilizador)

                withContext(Dispatchers.Main) {
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit { putString("TEMP_EMAIL", email) }
                    showToast("Conta criada!", isLong = true)
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Erro ao criar conta: ${e.message}", isLong = true)
                }
            }
        }
    }

    private fun guardarEdicaoUtilizador() {
        val nome = binding.regNameText.text.toString().trim()
        val emailInput = binding.regEmailText.text.toString().trim()
        val email = emailInput.lowercase()
        val phone = binding.regPhoneText.text.toString().trim()
        val novaSenha = binding.regSenhaText.text.toString().trim()
        val confirmarSenha = binding.regConfirmarSenhaText.text.toString().trim()

        if (nome.isEmpty() || email.isEmpty()) {
            showToast("Nome e E-mail são obrigatórios")
            return
        }

        val senhaFinal = if (binding.senhaLayout.visibility == View.VISIBLE && novaSenha.isNotEmpty()) {
            if (novaSenha != confirmarSenha) {
                showToast("As palavras-passe não coincidem")
                return
            }
            novaSenha
        } else {
            senhaStr
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val db = MinhaBaseDados.getDatabase(this@RegistoActivity)
            val dao = db.utilizadorDao()

            val userExistente = dao.buscarPorEmail(email)
            val utilizadorEditado = Utilizador(
                id = if (userId > 0) userId else (userExistente?.id ?: 0),
                nome = nome,
                email = email,
                telemovel = phone,
                donoEmail = "SISTEMA",
                senha = senhaFinal,
                darkMode = userExistente?.darkMode ?: false,
                notifications = userExistente?.notifications ?: false,
                customCategories = userExistente?.customCategories ?: ""
            )

            dao.atualizarUtilizador(utilizadorEditado)
            FirebaseManager.salvarUtilizadorNoFirestore(utilizadorEditado)

            val sharedPref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            sharedPref.edit {
                putString("NAME", nome)
                putString(KEY_EMAIL, email)
            }

            withContext(Dispatchers.Main) {
                showToast("Perfil atualizado!")
                finish()
            }
        }
    }
}
