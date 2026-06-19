package com.example.biap.util

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

class AuthManager(private val context: Context) {
    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestProfile()
        .requestScopes(Scope("https://www.googleapis.com/auth/drive.appdata"))
        .build()

    private val signInClient: GoogleSignInClient by lazy { GoogleSignIn.getClient(context, gso) }

    private val _currentUser = MutableStateFlow<GoogleSignInAccount?>(
        try {
            GoogleSignIn.getLastSignedInAccount(context)
        } catch (e: Exception) {
            null
        }
    )
    val currentUser: StateFlow<GoogleSignInAccount?> = _currentUser.asStateFlow()

    fun getSignInIntent(): Intent {
        return signInClient.signInIntent
    }

    fun handleSignInResult(account: GoogleSignInAccount?) {
        _currentUser.update { account }
    }

    fun signOut(onComplete: () -> Unit) {
        signInClient.signOut().addOnCompleteListener {
            _currentUser.update { null }
            onComplete()
        }
    }

    suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        val account = _currentUser.value ?: return@withContext null
        val gAccount = account.account ?: return@withContext null
        try {
            GoogleAuthUtil.getToken(
                context,
                gAccount,
                "oauth2:https://www.googleapis.com/auth/drive.appdata"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
