package br.com.mercadofacil.sync

import org.json.JSONObject

/** Converts Supabase authentication responses into useful, safe messages for people using the app. */
object AuthFeedback {
    fun failure(exception: Throwable): String = when (exception) {
        is java.net.UnknownHostException, is java.net.ConnectException ->
            "Não foi possível conectar. Confira sua internet e tente novamente."
        is java.net.SocketTimeoutException ->
            "O servidor demorou para responder. Tente novamente em alguns instantes."
        else -> exception.message ?: "Não foi possível acessar sua conta. Tente novamente."
    }

    fun validEmail(email: String): Boolean = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email.trim())

    fun validate(name: String, email: String, password: String, confirmation: String, signup: Boolean): String? = when {
        signup && name.isBlank() -> "Informe seu nome."
        !validEmail(email) -> "Digite um e-mail válido."
        password.length < 8 -> "A senha precisa ter pelo menos 8 caracteres."
        signup && password != confirmation -> "As senhas não coincidem."
        else -> null
    }

    fun success(body: String, signup: Boolean): String {
        val response = JSONObject(body)
        if (response.optString("access_token").isNotBlank()) return "Conta conectada com sucesso."
        // GoTrue /signup may return the user directly (without a session)
        // when email confirmation is enabled. Some clients wrap it in "user".
        val rootUser = response.optString("id").isNotBlank() && validEmail(response.optString("email"))
        if (signup && (response.optJSONObject("user") != null || rootUser)) {
            return "Cadastro recebido! Confira seu e-mail (inclusive a pasta de spam) e confirme o endereço antes de entrar."
        }
        error("Não foi possível confirmar o cadastro. Tente novamente.")
    }

    fun error(status: Int, body: String): String {
        val data = runCatching { JSONObject(body) }.getOrNull()
        val code = data?.optString("error_code").orEmpty().ifBlank { data?.optString("error").orEmpty() }.lowercase()
        val detail = sequenceOf("msg", "message", "error_description", "error")
            .mapNotNull { data?.optString(it)?.takeIf(String::isNotBlank) }
            .firstOrNull().orEmpty()
        val description = detail.lowercase()
        return when {
            status == 429 || code.contains("rate_limit") || description.contains("rate limit") ->
                "Muitas tentativas de cadastro ou envio de e-mail. Aguarde um pouco e tente novamente."
            code == "email_address_invalid" || description.contains("email address is invalid") ->
                "Este endereço de e-mail não foi aceito. Confira o e-mail informado."
            code == "email_not_confirmed" || description.contains("email not confirmed") ->
                "Confirme seu e-mail pelo link recebido para poder entrar. Verifique também o spam."
            code == "invalid_credentials" || description.contains("invalid login credentials") ->
                "E-mail ou senha incorretos. Confira os dados ou use Recuperar senha."
            code.contains("signup_disabled") || description.contains("signups not allowed") || description.contains("signups are disabled") ->
                "O cadastro por e-mail está desativado no servidor. Tente novamente mais tarde."
            code == "user_already_exists" || description.contains("user already registered") ->
                "Este e-mail já tem uma conta. Entre ou recupere sua senha."
            code == "weak_password" -> "A senha foi recusada. Use uma senha mais forte."
            detail.isNotBlank() -> "Erro no cadastro ou acesso (HTTP $status): $detail"
            else -> "Não foi possível acessar a conta (HTTP $status). Tente novamente."
        }
    }
}
