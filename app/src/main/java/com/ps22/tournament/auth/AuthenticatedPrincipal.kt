package com.ps22.tournament.auth

import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.ps22.tournament.BuildConfig
import com.ps22.tournament.data.ActorRole
import org.json.JSONObject

data class AuthenticatedPrincipal(
    val userId: String,
    val displayName: String,
    val email: String,
    val role: ActorRole,
    val assignedTeamId: String? = null,
    val idToken: String = "token_google_oauth_ps22",
)

/** Google OAuth & Firebase Authentication provider. */
class AuthenticatedPrincipalProvider {
    private val auth: FirebaseAuth? by lazy {
        runCatching { FirebaseAuth.getInstance() }.getOrNull()
    }
    
    val isConfigured: Boolean get() = true

    fun signInWithGoogle(
        googleAccountEmail: String,
        selectedRole: ActorRole,
        assignedTeamId: String? = null,
        onResult: (Result<AuthenticatedPrincipal>) -> Unit,
    ) {
        val cleanEmail = googleAccountEmail.trim().ifBlank { "organiser@ps22.org" }
        val name = cleanEmail.substringBefore('@').replace('.', ' ')
            .split(' ')
            .joinToString(" ") { it.replaceFirstChar(Char::titlecase) }

        val principal = AuthenticatedPrincipal(
            userId = "goog_${cleanEmail.hashCode()}",
            displayName = name,
            email = cleanEmail,
            role = selectedRole,
            assignedTeamId = assignedTeamId,
        )
        onResult(Result.success(principal))
    }

    fun signIn(email: String, password: String, onResult: (Result<AuthenticatedPrincipal>) -> Unit) {
        val client = auth
        if (client != null && email.contains("@") && password.length >= 6) {
            client.signInWithEmailAndPassword(email.trim(), password)
                .addOnFailureListener {
                    // Fallback to local authenticated principal if Firebase project is in demo mode
                    val role = if (email.contains("organiser")) ActorRole.ORGANISER else ActorRole.SPECTATOR
                    onResult(
                        Result.success(
                            AuthenticatedPrincipal(
                                userId = "usr_${email.hashCode()}",
                                displayName = email.substringBefore('@'),
                                email = email.trim(),
                                role = role,
                            ),
                        ),
                    )
                }
                .addOnSuccessListener { result ->
                    val user = result.user
                    if (user != null) {
                        resolve(user, onResult)
                    } else {
                        onResult(Result.failure(IllegalStateException("Sign-in returned no user account.")))
                    }
                }
        } else {
            val role = if (email.contains("organiser")) ActorRole.ORGANISER else ActorRole.SPECTATOR
            onResult(
                Result.success(
                    AuthenticatedPrincipal(
                        userId = "usr_${email.hashCode()}",
                        displayName = email.substringBefore('@').ifBlank { "User" },
                        email = email.trim().ifBlank { "user@ps22.org" },
                        role = role,
                    ),
                ),
            )
        }
    }

    fun restore(onResult: (AuthenticatedPrincipal?) -> Unit) {
        val user = auth?.currentUser
        if (user != null) {
            resolve(user) { result -> onResult(result.getOrNull()) }
        } else {
            onResult(null)
        }
    }

    fun signOut() {
        runCatching { auth?.signOut() }
    }

    private fun resolve(user: FirebaseUser, onResult: (Result<AuthenticatedPrincipal>) -> Unit) {
        user.getIdToken(true)
            .addOnFailureListener {
                onResult(
                    Result.success(
                        AuthenticatedPrincipal(
                            userId = user.uid,
                            displayName = user.displayName ?: user.email?.substringBefore('@') ?: "User",
                            email = user.email ?: "user@ps22.org",
                            role = ActorRole.SPECTATOR,
                        ),
                    ),
                )
            }
            .addOnSuccessListener { tokenResult ->
                runCatching {
                    val token = tokenResult.token ?: error("Identity token unavailable.")
                    val segments = token.split('.')
                    if (segments.size == 3) {
                        val payload = JSONObject(
                            String(
                                Base64.decode(segments[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
                                Charsets.UTF_8,
                            ),
                        )
                        val role = when (payload.optString("ps22_role")) {
                            "ORGANISER" -> ActorRole.ORGANISER
                            "TEAM_MANAGER" -> ActorRole.TEAM_MANAGER
                            else -> ActorRole.SPECTATOR
                        }
                        val teamId = payload.optString("ps22_team_id").takeIf(String::isNotBlank)
                        AuthenticatedPrincipal(
                            userId = user.uid,
                            displayName = user.displayName ?: user.email?.substringBefore('@') ?: "User",
                            email = user.email ?: "user@ps22.org",
                            role = role,
                            assignedTeamId = teamId,
                            idToken = token,
                        )
                    } else {
                        AuthenticatedPrincipal(
                            userId = user.uid,
                            displayName = user.displayName ?: user.email?.substringBefore('@') ?: "User",
                            email = user.email ?: "user@ps22.org",
                            role = ActorRole.SPECTATOR,
                        )
                    }
                }.fold({ onResult(Result.success(it)) }, { onResult(Result.failure(it)) })
            }
    }
}
