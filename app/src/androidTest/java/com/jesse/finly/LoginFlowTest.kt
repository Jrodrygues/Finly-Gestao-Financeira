package com.jesse.finly

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginFlowTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(LoginActivity::class.java)

    @Test
    fun testRegistoELogin() {
        // 1. Clicar em "Criar Conta" na tela de Login
        onView(withId(R.id.btnRegistar)).perform(click())

        // 2. Preencher os dados de cadastro
        onView(withId(R.id.regNameText)).perform(typeText("Usuario Teste"), closeSoftKeyboard())
        onView(withId(R.id.regEmailText)).perform(typeText("teste@finly.com"), closeSoftKeyboard())
        onView(withId(R.id.regPhoneText)).perform(typeText("912345678"), closeSoftKeyboard())
        onView(withId(R.id.regSenhaText)).perform(typeText("senha123"), closeSoftKeyboard())

        // 3. Clicar em Finalizar Registo
        onView(withId(R.id.btnFinalizarRegisto)).perform(click())

        // 4. Após o registo, o app volta para a LoginActivity e preenche o e-mail automaticamente (TEMP_EMAIL)
        // Vamos verificar se o e-mail está lá
        onView(withId(R.id.emailText)).check(matches(withText("teste@finly.com")))

        // 5. Digitar a senha e entrar
        onView(withId(R.id.senhaText)).perform(typeText("senha123"), closeSoftKeyboard())
        onView(withId(R.id.loginbtn)).perform(click())

        // 6. Verificar se entramos no Resumo (procurar o título ou um botão específico)
        // O botão 'btnMenu' ou o título 'RESUMO MENSAL' devem estar visíveis
        onView(withId(R.id.btnMenu)).check(matches(isDisplayed()))
    }
}
