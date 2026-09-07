package com.jesse.finly

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.containsString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionLifecycleTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(LoginActivity::class.java)

    @Test
    fun testCicloDeVidaTransacao() {
        // 1. Entrar como convidado
        onView(withId(R.id.btnConvidado)).perform(click())
        onView(withText("Sim")).perform(click())

        // 2. Ir para a Planilha (MainActivity)
        onView(withId(R.id.btnVerDetalhes)).perform(click())

        // 3. Adicionar uma nova despesa
        onView(withId(R.id.fabAddTransacao)).perform(click())
        onView(withId(R.id.regNameText)).perform(typeText("Almoço Teste"), closeSoftKeyboard())
        onView(withId(R.id.regEmailText)).perform(typeText("15.50"), closeSoftKeyboard())
        // A data já vem preenchida por padrão, vamos apenas salvar
        onView(withId(R.id.btnFinalizarRegisto)).perform(click())

        // 4. Verificar se aparece na lista
        onView(withText("Almoço Teste")).check(matches(isDisplayed()))

        // 5. Clicar para ver detalhes
        onView(withText("Almoço Teste")).perform(click())
// Usar um matcher mais flexível para o valor, pois o separador decimal muda conforme o idioma do telemóvel
        onView(withId(R.id.tvValorHighlight)).check(matches(withText(containsString("15"))))
        onView(withId(R.id.tvValorHighlight)).check(matches(withText(containsString("50"))))
        // 6. Editar a transação
        onView(withId(R.id.btnEditarTransacao)).perform(click())
        onView(withId(R.id.regNameText)).perform(replaceText("Almoço Editado"), closeSoftKeyboard())
        onView(withId(R.id.btnFinalizarRegisto)).perform(click())

        // 7. Verificar mudança nos detalhes (ao voltar)
        onView(withText("Almoço Editado")).check(matches(isDisplayed()))

        // 8. Eliminar a transação
        onView(withId(R.id.btnEliminarTransacao)).perform(click())
        onView(withId(R.id.btnConfirmarEliminar)).perform(click())

        // 9. Verificar se sumiu da lista principal (ao voltar para a MainActivity)
        onView(withText("Almoço Editado")).check(doesNotExist())
    }
}
