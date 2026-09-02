package com.jesse.finly

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.graphics.Rect
import android.widget.EditText
import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
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
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
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
        } else {
            binding.root.setBackgroundResource(R.color.backgroundColor)
        }

        isNewUserRegistration = isNew
        isEditMode = intent.getBooleanExtra("isEditTransaction", false)
        isUserEditMode = intent.getBooleanExtra("isUserEdit", false)

        configurarSpinners()

        binding.cbRepetirSempre.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.cbRepetirVezes.isChecked = false
                binding.parcelasLayout.visibility = View.GONE
            }
        }

        // Lógica para Parcelas ser visível e rolar ao fundo
        binding.cbRepetirVezes.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.cbRepetirSempre.isChecked = false
                binding.parcelasLayout.visibility = View.VISIBLE
                
                binding.root.postDelayed({
                    // Scroll para o fim absoluto do ScrollView
                    binding.root.fullScroll(View.FOCUS_DOWN)
                    
                    binding.etParcelas.requestFocus()
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(binding.etParcelas, InputMethodManager.SHOW_IMPLICIT)
                }, 300)
            } else {
                binding.parcelasLayout.visibility = View.GONE
            }
        }





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
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        // Forçar o fecho usando o ‘token’ da janela ou do foco atual
        val view = currentFocus ?: window.decorView
        imm.hideSoftInputFromWindow(view.windowToken, 0)
        
        // Limpar o foco para garantir que o teclado não tente reaparecer
        currentFocus?.clearFocus()
    }





    private fun obterCategoriasDisponiveis(): MutableList<String> {
        val lista = mutableListOf(
            "Geral", "Habitação", "Alimentação", "Transporte", "Saúde", "Lazer",
            "Educação", "Compras", "Assinaturas", "Investimentos", "Poupança", "Exterior"
        )
        val prefs = getSharedPreferences("PreferenciasDaMinhaApp", MODE_PRIVATE)
        val userEmail = prefs.getString("EMAIL", "") ?: ""
        val emailClean = userEmail.trim().lowercase()

        val customSet = prefs.getStringSet("CUSTOM_CATEGORIES_$emailClean", emptySet()) ?: emptySet()
        for (cat in customSet) {
            if (cat.isNotBlank() && !lista.contains(cat)) {
                lista.add(cat)
            }
        }
        lista.add(getString(R.string.option_nova_categoria))
        return lista
    }

    private fun obterCategoriasCustomizadas(): MutableList<String> {
        val prefs = getSharedPreferences("PreferenciasDaMinhaApp", MODE_PRIVATE)
        val userEmail = prefs.getString("EMAIL", "") ?: ""
        val emailClean = userEmail.trim().lowercase()

        val customSet = prefs.getStringSet("CUSTOM_CATEGORIES_$emailClean", emptySet()) ?: emptySet()
        return customSet.filter { it.isNotBlank() }.toMutableList()
    }

    private fun salvarNovaCategoria(nome: String) {
        val catClean = nome.trim()
        if (catClean.isEmpty()) return

        val prefs = getSharedPreferences("PreferenciasDaMinhaApp", MODE_PRIVATE)
        val userEmail = prefs.getString("EMAIL", "") ?: ""
        val emailClean = userEmail.trim().lowercase()

        val customSet = prefs.getStringSet("CUSTOM_CATEGORIES_$emailClean", emptySet())?.toMutableSet() ?: mutableSetOf()
        customSet.add(catClean)
        prefs.edit { putStringSet("CUSTOM_CATEGORIES_$emailClean", customSet) }

        configurarSpinners()
        binding.autoCompleteCategoria.setText(catClean, false)
        showToast(getString(R.string.toast_categoria_adicionada, catClean))
    }

    private fun removerCategoriaCustomizada(categoria: String) {
        val prefs = getSharedPreferences("PreferenciasDaMinhaApp", MODE_PRIVATE)
        val userEmail = prefs.getString("EMAIL", "") ?: ""
        val emailClean = userEmail.trim().lowercase()

        val customSet = prefs.getStringSet("CUSTOM_CATEGORIES_$emailClean", emptySet())?.toMutableSet() ?: mutableSetOf()
        customSet.remove(categoria)
        prefs.edit { putStringSet("CUSTOM_CATEGORIES_$emailClean", customSet) }

        configurarSpinners()
        val lista = obterCategoriasDisponiveis()
        val primeiraValida = lista.firstOrNull { it != getString(R.string.option_nova_categoria) } ?: "Geral"
        binding.autoCompleteCategoria.setText(primeiraValida, false)
        showToast(getString(R.string.toast_categoria_removida, categoria))
    }

    private fun mostrarBottomSheetGerirCategorias() {
        val bottomSheetDialog = BottomSheetDialog(this, R.style.TransparentBottomSheetDialog)
        val bsView = layoutInflater.inflate(R.layout.bottom_sheet_gerir_categorias, binding.root, false)
        bottomSheetDialog.setContentView(bsView)
        bottomSheetDialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(
            Color.TRANSPARENT)

        val etNova = bsView.findViewById<TextInputEditText>(R.id.etNovaCategoriaBS)
        val btnAdd = bsView.findViewById<MaterialButton>(R.id.btnAdicionarCategoriaBS)
        val btnFechar = bsView.findViewById<MaterialButton>(R.id.btnFecharBS)
        val containerCustom = bsView.findViewById<LinearLayout>(R.id.containerCategoriasCustom)
        val tvSemCategorias = bsView.findViewById<TextView>(R.id.tvSemCategoriasCustom)

        fun atualizarListaCustom() {
            containerCustom.removeAllViews()
            val customList = obterCategoriasCustomizadas()

            if (customList.isEmpty()) {
                tvSemCategorias.visibility = View.VISIBLE
            } else {
                tvSemCategorias.visibility = View.GONE
                for (cat in customList) {
                    val itemView = layoutInflater.inflate(R.layout.item_categoria_custom, containerCustom, false)
                    val tvNome = itemView.findViewById<TextView>(R.id.tvNomeCategoriaCustom)
                    val btnRemover = itemView.findViewById<View>(R.id.btnRemoverCategoriaCustom)

                    tvNome.text = cat
                    btnRemover.setOnClickListener {
                        removerCategoriaCustomizada(cat)
                        atualizarListaCustom()
                    }
                    containerCustom.addView(itemView)
                }
            }
        }

        atualizarListaCustom()

        btnAdd.setOnClickListener {
            val novaCat = etNova.text.toString().trim()
            if (novaCat.isNotEmpty()) {
                salvarNovaCategoria(novaCat)
                etNova.setText("")
                atualizarListaCustom()
            } else {
                showToast("Digite o nome da categoria")
            }
        }

        btnFechar.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    private fun configurarSpinners() {
        val listaCategorias = obterCategoriasDisponiveis()
        val adapterCat = ArrayAdapter(this, R.layout.dropdown_item, listaCategorias)
        binding.autoCompleteCategoria.setAdapter(adapterCat)

        binding.autoCompleteCategoria.setOnItemClickListener { _, _, position, _ ->
            val selecionada = adapterCat.getItem(position)
            if (selecionada == getString(R.string.option_nova_categoria)) {
                val primeiraValida = listaCategorias.firstOrNull { it != getString(R.string.option_nova_categoria) } ?: "Geral"
                binding.autoCompleteCategoria.setText(primeiraValida, false)
                mostrarBottomSheetGerirCategorias()
            }
        }
    }



    private fun configurarParaNovoUtilizador() {
        binding.titleRegisto.text = getString(R.string.criar_nova_conta)
        binding.itemLayout.hint = getString(R.string.hint_nome_completo)
        binding.itemLayout.setStartIconDrawable(R.drawable.ic_person)
        
        binding.valorLayout.hint = getString(R.string.hint_email_input)
        binding.valorLayout.setStartIconDrawable(R.drawable.ic_email)
        binding.regEmailText.inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        
        binding.dataLayout.hint = getString(R.string.hint_telemovel)
        binding.dataLayout.setStartIconDrawable(R.drawable.ic_phone)
        binding.regPhoneText.inputType = android.text.InputType.TYPE_CLASS_PHONE
        binding.regPhoneText.isFocusableInTouchMode = true
        binding.regPhoneText.isFocusable = true
        binding.regPhoneText.setOnClickListener(null)
        
        binding.senhaLayout.visibility = View.VISIBLE
        binding.senhaLayout.setStartIconDrawable(R.drawable.ic_lock)
        
        binding.categoriaLayout.visibility = View.GONE
        binding.rgTipo.visibility = View.GONE
        binding.llRecorrenciaOpcoes.visibility = View.GONE
        binding.parcelasLayout.visibility = View.GONE
        
        binding.cbRepetirSempre.isChecked = false
        binding.cbRepetirVezes.isChecked = false
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
        binding.regEmailText.isEnabled = false
        binding.regEmailText.alpha = 0.6f
        
        binding.dataLayout.hint = getString(R.string.hint_telemovel)
        binding.dataLayout.setStartIconDrawable(R.drawable.ic_phone)
        binding.regPhoneText.setText(intent.getStringExtra("phone"))
        binding.regPhoneText.inputType = android.text.InputType.TYPE_CLASS_PHONE
        binding.regPhoneText.isFocusableInTouchMode = true
        binding.regPhoneText.isFocusable = true
        binding.regPhoneText.setOnClickListener(null)

        binding.senhaLayout.visibility = View.VISIBLE
        binding.senhaLayout.setStartIconDrawable(R.drawable.ic_lock)
        binding.regSenhaText.setText(intent.getStringExtra("senha"))

        binding.categoriaLayout.visibility = View.GONE
        binding.rgTipo.visibility = View.GONE
        binding.llRecorrenciaOpcoes.visibility = View.GONE
        binding.parcelasLayout.visibility = View.GONE
        
        binding.cbRepetirSempre.isChecked = false
        binding.cbRepetirVezes.isChecked = false
        binding.btnFinalizarRegisto.text = getString(R.string.btn_atualizar_perfil)
    }

    private fun configurarParaTransacao() {
        binding.senhaLayout.visibility = View.GONE
        binding.itemLayout.setStartIconDrawable(R.drawable.ic_edit)
        binding.valorLayout.setStartIconDrawable(R.drawable.ic_attach_money)
        binding.dataLayout.setStartIconDrawable(R.drawable.ic_calendar)
        
        binding.regPhoneText.setOnClickListener { mostrarCalendario() }

        binding.llRecorrenciaOpcoes.visibility = View.VISIBLE
        
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
        binding.regNameText.setText(itemOriginal)
        binding.regEmailText.setText(intent.getDoubleExtra("valor", 0.0).toString())
        binding.regPhoneText.setText(intent.getStringExtra("vencimento"))

        val tipo = intent.getStringExtra("tipo")
        if (tipo == "RENDA") binding.rbRenda.isChecked = true else binding.rbDespesa.isChecked = true

        val catSalva = intent.getStringExtra("categoria")
        if (catSalva != null) {
            binding.autoCompleteCategoria.setText(catSalva, false)
        }

        val isRec = intent.getBooleanExtra("isRecorrente", false)
        val tot = intent.getIntExtra("parcelasTotais", 0)

        if (isRec) {
            if (tot == -1) {
                binding.cbRepetirSempre.isChecked = true
            } else if (tot > 0) {
                binding.cbRepetirVezes.isChecked = true
                binding.etParcelas.setText(tot.toString())
                binding.parcelasLayout.visibility = View.VISIBLE
            }
        }

        binding.btnFinalizarRegisto.text = getString(R.string.atualizar_item)
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
        val tipo = if (binding.rbRenda.isChecked) "RENDA" else "DESPESA"
        val categoria = binding.autoCompleteCategoria.text.toString()

        // Obter o mês: primeiro tenta o mês da transação (se for edição), 
        // depois o mês atual da tela, e por fim o mês do sistema.
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

        val isSempre = binding.cbRepetirSempre.isChecked
        val isVezes = binding.cbRepetirVezes.isChecked
        val inputParcelas = binding.etParcelas.text.toString().toIntOrNull() ?: 1

        val isRecorrente = isSempre || isVezes
        val numParcelas = when {
            isSempre -> -1
            isVezes -> if (inputParcelas > 0) inputParcelas else 0
            else -> 0
        }

        val valor = valorStr.toDoubleOrNull() ?: 0.0
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

        if (nome.isEmpty() || emailInput.isEmpty() || senha.isEmpty()) {
            showToast("Introduza todos os campos obrigatórios")
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
        val senha = binding.regSenhaText.text.toString().trim()

        if (nome.isEmpty() || email.isEmpty()) {
            showToast("Nome e E-mail são obrigatórios")
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val db = MinhaBaseDados.getDatabase(this@RegistoActivity)
            val dao = db.utilizadorDao()
            
            val utilizadorEditado = Utilizador(
                id = userId,
                nome = nome,
                email = email,
                telemovel = phone,
                donoEmail = "SISTEMA",
                senha = senha
            )
            
            // 1. Atualizar localmente sempre
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
