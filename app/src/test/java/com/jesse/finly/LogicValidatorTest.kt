package com.jesse.finly

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicValidatorTest {

    @Test
    fun emailValido_RetornaTrue() {
        val email = "teste@exemplo.com"
        assertTrue(email.contains("@") && email.contains("."))
    }

    @Test
    fun emailInvalido_RetornaFalse() {
        val email = "email_sem_arroba"
        assertFalse(email.contains("@"))
    }
}
