package br.com.mercadofacil

import br.com.mercadofacil.sync.AuthFeedback
import org.junit.Assert.*
import org.junit.Test

class AuthFeedbackTest {
    @Test fun signupConfirmationDoesNotPretendUserIsLoggedIn() {
        assertTrue(AuthFeedback.success("{\"user\":{\"id\":\"abc\"},\"session\":null}", true).contains("e-mail"))
        assertEquals("Conta conectada com sucesso.", AuthFeedback.success("{\"access_token\":\"test\"}", true))
    }

    @Test fun invalidAuthResponseCannotBeMistakenForConfirmation() {
        assertThrows(IllegalStateException::class.java) { AuthFeedback.success("{}", true) }
        assertThrows(IllegalStateException::class.java) { AuthFeedback.success("{\"user\":{}}", false) }
    }

    @Test fun mapsSupabaseErrorCodeAndErrorDescription() {
        assertTrue(AuthFeedback.error(429, "{\"error_code\":\"over_email_send_rate_limit\",\"msg\":\"Email rate limit exceeded\"}").contains("Muitas tentativas"))
        assertTrue(AuthFeedback.error(400, "{\"error_code\":\"email_address_invalid\",\"msg\":\"invalid\"}").contains("e-mail"))
        assertTrue(AuthFeedback.error(400, "{\"error\":\"invalid_grant\",\"error_description\":\"Invalid login credentials\"}").contains("E-mail ou senha"))
        assertTrue(AuthFeedback.error(400, "{\"error_code\":\"email_not_confirmed\"}").contains("Confirme"))
        assertTrue(AuthFeedback.error(400, "{\"error_code\":\"signup_disabled\"}").contains("desativado"))
    }

    @Test fun validatesSignupBeforeRequest() {
        assertEquals("Informe seu nome.", AuthFeedback.validate(" ", "teste@exemplo.com", "12345678", "12345678", true))
        assertEquals("Digite um e-mail válido.", AuthFeedback.validate("Ana", "teste", "12345678", "12345678", true))
        assertEquals("A senha precisa ter pelo menos 8 caracteres.", AuthFeedback.validate("Ana", "teste@exemplo.com", "123", "123", true))
        assertEquals("As senhas não coincidem.", AuthFeedback.validate("Ana", "teste@exemplo.com", "12345678", "87654321", true))
        assertNull(AuthFeedback.validate("Ana", "TESTE@EXEMPLO.COM ", "12345678", "12345678", true))
        assertNull(AuthFeedback.validate("", "teste@exemplo.com", "12345678", "", false))
    }

    @Test fun networkErrorsAreExplainedWithoutTechnicalHostnames() {
        val message = AuthFeedback.failure(java.net.UnknownHostException("igehbjnutmhkqfdlrndx.supabase.co"))
        assertTrue(message.contains("internet"))
        assertFalse(message.contains("igehbjnutmhkqfdlrndx"))
        assertTrue(AuthFeedback.failure(java.net.SocketTimeoutException()).contains("demorou"))
    }
}
