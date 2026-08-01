package com.sc.biap

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.initialize
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Firebase.initialize(this)

        // Provider is chosen by build variant via source sets (src/debug, src/release),
        // not by a runtime flag. See AppCheckInit.kt in each variant.
        installAppCheck(this)

        auth = Firebase.auth

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setContent {
            BiapTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BiapApp(
                        auth = auth,
                        googleSignInClient = googleSignInClient,
                        activity = this
                    )
                }
            }
        }
    }
}

@Composable
fun BiapApp(
    auth: FirebaseAuth,
    googleSignInClient: GoogleSignInClient,
    activity: Activity
) {
    var currentUser by remember { mutableStateOf(auth.currentUser) }

    LaunchedEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
    }

    if (currentUser != null) {
        HomeScreen(
            user = currentUser!!,
            onLogout = {
                auth.signOut()
                googleSignInClient.signOut()
                currentUser = null
            }
        )
    } else {
        AuthNavigationScreen(
            auth = auth,
            googleSignInClient = googleSignInClient,
            activity = activity
        )
    }
}

@Composable
fun AuthNavigationScreen(
    auth: FirebaseAuth,
    googleSignInClient: GoogleSignInClient,
    activity: Activity
) {
    var authMethod by remember { mutableStateOf<String?>(null) }

    when (authMethod) {
        "email" -> EmailAuthScreen(
            auth = auth,
            onBack = { authMethod = null }
        )
        "phone" -> PhoneAuthScreen(
            auth = auth,
            activity = activity,
            onBack = { authMethod = null }
        )
        "google" -> GoogleSignInScreen(
            googleSignInClient = googleSignInClient,
            auth = auth,
            onBack = { authMethod = null }
        )
        else -> AuthChoiceScreen(
            onEmailSelected = { authMethod = "email" },
            onPhoneSelected = { authMethod = "phone" },
            onGoogleSelected = { authMethod = "google" }
        )
    }
}

@Composable
fun AuthChoiceScreen(
    onEmailSelected: () -> Unit,
    onPhoneSelected: () -> Unit,
    onGoogleSelected: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "BIAP",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            "Ethnic Wear Operations",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 8.dp, bottom = 48.dp)
        )

        Button(
            onClick = onEmailSelected,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("Sign In with Email", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onGoogleSelected,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("Sign In with Google", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onPhoneSelected,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("Sign In with Phone", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun EmailAuthScreen(
    auth: FirebaseAuth,
    onBack: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            if (isSignUp) "Create Account" else "Sign In",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            enabled = !isLoading,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        if (errorMessage.isNotEmpty()) {
            Text(
                errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    errorMessage = "Email and password required"
                    return@Button
                }

                isLoading = true
                errorMessage = ""

                if (isSignUp) {
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnFailureListener { e ->
                            errorMessage = "Authentication failed"
                            isLoading = false
                        }
                } else {
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnFailureListener { e ->
                            errorMessage = "Authentication failed"
                            isLoading = false
                        }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(if (isSignUp) "Create Account" else "Sign In")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = { isSignUp = !isSignUp }, enabled = !isLoading) {
            Text(
                if (isSignUp) "Already have an account? Sign in" else "Don't have an account? Sign up"
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = !isLoading
        ) {
            Text("Back")
        }
    }
}

@Composable
fun PhoneAuthScreen(
    auth: FirebaseAuth,
    activity: Activity,
    onBack: () -> Unit
) {
    var phoneNumber by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var otpSent by remember { mutableStateOf(false) }
    var verificationId by remember { mutableStateOf("") }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Phone Sign In",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (!otpSent) {
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("Phone Number (+91XXXXXXXXXX)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )

            if (errorMessage.isNotEmpty()) {
                Text(
                    errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (phoneNumber.isBlank()) {
                        errorMessage = "Phone number required"
                        return@Button
                    }
                    if (!phoneNumber.matches(Regex("^\\+\\d{1,3}\\d{6,14}$"))) {
                        errorMessage = "Invalid format. Use: +91XXXXXXXXXX"
                        return@Button
                    }

                    isLoading = true
                    errorMessage = ""

                    val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                        override fun onVerificationCompleted(credential: com.google.firebase.auth.PhoneAuthCredential) {
                            isLoading = false
                            auth.signInWithCredential(credential)
                                .addOnFailureListener {
                                    errorMessage = "Auto sign-in failed. Enter OTP manually."
                                }
                        }

                        override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                            isLoading = false
                            errorMessage = when {
                                e.message?.contains("reCAPTCHA") == true ->
                                    "reCAPTCHA verification failed. Check Firebase Console Phone Auth setup."
                                e.message?.contains("quota") == true ->
                                    "Request quota exceeded. Try again later."
                                e.message?.contains("appVerification") == true ->
                                    "App verification failed. Ensure App Check is configured in Firebase."
                                else -> "Verification failed: ${e.message?.take(50) ?: "Unknown error"}"
                            }
                        }

                        override fun onCodeSent(
                            newVerificationId: String,
                            token: PhoneAuthProvider.ForceResendingToken
                        ) {
                            verificationId = newVerificationId
                            resendToken = token
                            otpSent = true
                            isLoading = false
                            errorMessage = ""
                        }
                    }

                    val options = PhoneAuthOptions.newBuilder(auth)
                        .setPhoneNumber(phoneNumber)
                        .setTimeout(120L, TimeUnit.SECONDS)
                        .setActivity(activity)
                        .setCallbacks(callbacks)
                        .build()

                    PhoneAuthProvider.verifyPhoneNumber(options)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Send OTP")
                }
            }
        } else {
            Text(
                "OTP sent to $phoneNumber",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            OutlinedTextField(
                value = otpCode,
                onValueChange = { otpCode = it },
                label = { Text("Enter 6-digit OTP") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            if (errorMessage.isNotEmpty()) {
                Text(
                    errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (otpCode.isBlank() || otpCode.length != 6) {
                        errorMessage = "Enter 6-digit OTP"
                        return@Button
                    }

                    isLoading = true
                    errorMessage = ""

                    val credential = PhoneAuthProvider.getCredential(verificationId, otpCode)
                    auth.signInWithCredential(credential)
                        .addOnFailureListener {
                            errorMessage = "Invalid OTP. Try again."
                            isLoading = false
                        }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Verify OTP")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = {
                otpSent = false
                errorMessage = ""
            }, enabled = !isLoading) {
                Text("Use different number")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = !isLoading
        ) {
            Text("Back")
        }
    }
}

@Composable
fun GoogleSignInScreen(
    googleSignInClient: GoogleSignInClient,
    auth: FirebaseAuth,
    onBack: () -> Unit
) {
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val account = task.getResult(ApiException::class.java)

                if (account?.idToken != null) {
                    isLoading = true
                    val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                    auth.signInWithCredential(credential)
                        .addOnFailureListener {
                            errorMessage = "Authentication failed"
                            isLoading = false
                        }
                } else {
                    errorMessage = "Sign in failed"
                }
            } catch (e: ApiException) {
                errorMessage = "Sign in cancelled"
                isLoading = false
            }
        } else {
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Google Sign In",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            "Select your Google account to continue",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (errorMessage.isNotEmpty()) {
            Text(
                errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 24.dp)
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = {
                isLoading = true
                errorMessage = ""
                val signInIntent = googleSignInClient.signInIntent
                googleSignInLauncher.launch(signInIntent)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Open Google Account Selector")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = !isLoading
        ) {
            Text("Back")
        }
    }
}

@Composable
fun HomeScreen(
    user: com.google.firebase.auth.FirebaseUser,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Welcome to BIAP",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            "Ethnic Wear Operations Management",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 16.dp)
        )

        Text(
            "Logged in as: ${user.email ?: user.phoneNumber ?: user.displayName ?: "User"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 24.dp, bottom = 48.dp)
        )

        Button(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("Sign Out", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun BiapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        content = content
    )
}
