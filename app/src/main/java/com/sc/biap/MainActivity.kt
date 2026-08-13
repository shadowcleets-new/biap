package com.sc.biap

import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.initialize
import androidx.fragment.app.FragmentActivity
import com.sc.biap.security.AccountLinker
import com.sc.biap.ui.auth.AuthBackButton
import com.sc.biap.ui.auth.AuthChoiceScreen
import com.sc.biap.ui.auth.AuthErrorLine
import com.sc.biap.ui.auth.AuthField
import com.sc.biap.ui.auth.AuthInfoCard
import com.sc.biap.ui.auth.AuthPrimaryAction
import com.sc.biap.ui.auth.AuthScaffold
import com.sc.biap.ui.auth.AuthTitle
import com.sc.biap.ui.auth.OtpCell
import com.sc.biap.ui.VerticalButtonGroup
import com.sc.biap.ui.rememberVerticalButtonGroupShapes
import com.sc.biap.security.AppLock
import com.sc.biap.security.BiometricGate
import com.sc.biap.theme.BiapTheme
import com.sc.biap.theme.ThemePreference
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * How long a sign-in request may sit with no answer before the UI takes the button back.
 *
 * Under Firebase's own 120s timeout on purpose, so the person regains control first.
 */
private const val STALLED_REQUEST_MS = 90_000L

// FragmentActivity rather than ComponentActivity: BiometricPrompt attaches to the
// fragment manager, and there is no supported way to show it from a bare ComponentActivity.
class MainActivity : FragmentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Firebase.initialize(this)

        // Provider is chosen by build variant via source sets (src/debug, src/release),
        // not by a runtime flag. See AppCheckInit.kt in each variant.
        installAppCheck(this)

        // Gate the catalogue behind the device lock. No-ops on a device with no biometric
        // and no PIN, rather than making the data unreachable.
        AppLock.install(this)

        // Read before the first composition, so the app does not paint one theme and then
        // flip to another a frame later.
        ThemePreference.load(this)

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
                    BiometricGate {
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
        // Authenticated: hand off to the restored app. MainScreen's own profile
        // menu owns in-app sign-out (Drive/AuthManager); Firebase sign-out is
        // reached from there too.
        MainNavigation()
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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

    AuthScaffold(actions = {
        AuthPrimaryAction(
            label = if (isSignUp) "Create account" else "Sign in",
            loadingLabel = if (isSignUp) "Creating..." else "Signing in...",
            isLoading = isLoading,
            enabled = true,
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    errorMessage = "Email and password required"
                    return@AuthPrimaryAction
                }

                isLoading = true
                errorMessage = ""

                val alreadySignedIn = auth.currentUser != null
                when {
                    // Adding an email address to the account already in use. Creating one
                    // here instead would strand the user on a second, empty account.
                    alreadySignedIn -> {
                        AccountLinker.signInOrLink(
                            auth,
                            EmailAuthProvider.getCredential(email, password)
                        ) { error ->
                            errorMessage = error ?: ""
                            if (error != null) isLoading = false
                        }
                    }

                    isSignUp -> auth.createUserWithEmailAndPassword(email, password)
                        .addOnFailureListener { e ->
                            errorMessage = e.localizedMessage ?: "Could not create the account"
                            isLoading = false
                        }

                    else -> auth.signInWithEmailAndPassword(email, password)
                        .addOnFailureListener { e ->
                            errorMessage = e.localizedMessage ?: "Sign-in failed"
                            isLoading = false
                        }
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isSignUp) "Already registered?" else "No account yet?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                shapes = ButtonDefaults.shapes(),
                onClick = { isSignUp = !isSignUp },
                enabled = !isLoading
            ) {
                Text(if (isSignUp) "Sign in" else "Create account")
            }
        }
    }) {
        AuthBackButton(onBack = onBack)

        Spacer(modifier = Modifier.height(28.dp))

        AuthTitle(
            title = if (isSignUp) "Create account" else "Sign in",
            supporting = if (isSignUp) {
                "Your admin still decides what you can see."
            } else {
                "Use the address your admin registered."
            }
        )

        Spacer(modifier = Modifier.height(28.dp))

        AuthField(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            isError = errorMessage.isNotEmpty(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )

        Spacer(modifier = Modifier.height(12.dp))

        AuthField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            isError = errorMessage.isNotEmpty(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        if (errorMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            AuthErrorLine(errorMessage)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PhoneAuthScreen(
    auth: FirebaseAuth,
    activity: Activity,
    onBack: () -> Unit
) {
    // The dial code is a separate field with the local one already filled in. Retyping
    // "+91" on every attempt is pure friction, and pre-filling the whole field instead
    // would leave the caret sitting in front of it half the time.
    var dialCode by remember { mutableStateOf("+91") }
    var localNumber by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var otpSent by remember { mutableStateOf(false) }
    var verificationId by remember { mutableStateOf("") }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    /**
     * Held while the reCAPTCHA challenge is in front of us.
     *
     * Phone verification hands the screen to a browser tab, which is indistinguishable from
     * the user leaving BIAP - so the app lock arms itself, and on return the fingerprint
     * prompt replaces this screen, taking the pending verification down with it. The Google
     * sign-in path already brackets its picker this way; this path never did, which is why
     * it hung rather than failed.
     */
    var lockHeld by remember { mutableStateOf(false) }

    fun releaseLock() {
        if (lockHeld) {
            AppLock.endTrustedFlow()
            lockHeld = false
        }
    }

    // A request that never comes back must not leave the button dead forever.
    //
    // Every path Firebase tells us about clears isLoading, but "tells us about" is the weak
    // part: if the challenge is dismissed, or the tab never redirects back, no callback
    // arrives at all and the spinner runs until the app is killed. Not hypothetical - it is
    // what stranded this screen.
    //
    // 90 seconds because someone may genuinely be solving a reCAPTCHA and must not have it
    // cancelled from under them, and because Firebase gives itself 120, so control comes
    // back before its own timeout would.
    LaunchedEffect(isLoading) {
        if (!isLoading) return@LaunchedEffect
        delay(STALLED_REQUEST_MS)
        releaseLock()
        isLoading = false
        errorMessage = "That took too long. Check your connection and try again."
    }

    AuthScaffold(actions = {
        if (!otpSent) {
            AuthPrimaryAction(
                label = "Send code",
                loadingLabel = "Sending...",
                isLoading = isLoading,
                enabled = true,
                onClick = {
                    // Built here rather than trusted from the field: people paste numbers
                    // with spaces, dashes and a leading 0, and all three are recoverable.
                    val code = dialCode.filter { it.isDigit() }
                    val local = localNumber.filter { it.isDigit() }.trimStart('0')
                    val e164 = "+$code$local"

                    if (local.isBlank()) {
                        errorMessage = "Phone number required"
                        return@AuthPrimaryAction
                    }
                    if (!e164.matches(Regex("^\\+\\d{1,3}\\d{6,14}$"))) {
                        errorMessage = "That number does not look right. Check the digits."
                        return@AuthPrimaryAction
                    }
                    phoneNumber = e164

                    isLoading = true
                    errorMessage = ""

                    val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                        override fun onVerificationCompleted(credential: com.google.firebase.auth.PhoneAuthCredential) {
                            releaseLock()
                            isLoading = false
                            // Links to the account already in use rather than switching to a
                            // separate phone-only account with its own empty catalogue.
                            AccountLinker.signInOrLink(auth, credential) { error ->
                                errorMessage = error ?: ""
                            }
                        }

                        override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                            releaseLock()
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
                            releaseLock()
                            verificationId = newVerificationId
                            resendToken = token
                            otpSent = true
                            isLoading = false
                            errorMessage = ""
                        }

                        override fun onCodeAutoRetrievalTimeOut(id: String) {
                            // The SMS auto-reader gave up. Harmless by itself - the code can
                            // still be typed - but if a spinner is somehow still running at
                            // this point, nothing further is coming, so release the button.
                            releaseLock()
                            isLoading = false
                        }
                    }

                    val options = PhoneAuthOptions.newBuilder(auth)
                        .setPhoneNumber(e164)
                        .setTimeout(120L, TimeUnit.SECONDS)
                        .setActivity(activity)
                        .setCallbacks(callbacks)
                        .build()

                    // Claimed BEFORE the call, because the browser tab can appear before it
                    // returns. Every callback above releases it, as does the stall watchdog.
                    AppLock.beginTrustedFlow()
                    lockHeld = true

                    PhoneAuthProvider.verifyPhoneNumber(options)
                }
            )
        } else {
            AuthPrimaryAction(
                label = "Verify",
                loadingLabel = "Verifying...",
                isLoading = isLoading,
                enabled = true,
                onClick = {
                    if (otpCode.isBlank() || otpCode.length != 6) {
                        errorMessage = "Enter 6-digit OTP"
                        return@AuthPrimaryAction
                    }

                    isLoading = true
                    errorMessage = ""

                    val credential = PhoneAuthProvider.getCredential(verificationId, otpCode)
                    AccountLinker.signInOrLink(auth, credential) { error ->
                        // A wrong code and an already-taken number are different problems and
                        // need different wording, so the reason is reported rather than
                        // flattened into "Invalid OTP".
                        errorMessage = error ?: ""
                        if (error != null) isLoading = false
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(
                    onClick = { otpSent = false; errorMessage = "" },
                    enabled = !isLoading
                ) {
                    Text("Use a different number")
                }
            }
        }
    }) {
        // Back steps out of the code screen first, not straight out of sign-in - a mistyped
        // number is the common case and re-sending an SMS to correct it is not free.
        AuthBackButton(
            onBack = if (otpSent) ({ otpSent = false; errorMessage = "" }) else onBack
        )

        Spacer(modifier = Modifier.height(28.dp))

        if (!otpSent) {
            AuthTitle(
                title = "Your number",
                supporting = "We send a 6-digit code by SMS."
            )

            Spacer(modifier = Modifier.height(28.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AuthField(
                    value = dialCode,
                    onValueChange = { entry ->
                        // Always exactly one leading '+', then digits.
                        dialCode = "+" + entry.filter { it.isDigit() }.take(3)
                    },
                    label = "Code",
                    isError = errorMessage.isNotEmpty(),
                    modifier = Modifier.width(116.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                AuthField(
                    value = localNumber,
                    onValueChange = { entry -> localNumber = entry.filter { it.isDigit() }.take(14) },
                    label = "Phone number",
                    isError = errorMessage.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
            }

            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                AuthErrorLine(errorMessage)
            }
        } else {
            AuthTitle(
                title = "Enter code",
                supporting = "Sent to $phoneNumber"
            )

            Spacer(modifier = Modifier.height(32.dp))

            // The six cells ARE the field: a real text field sits underneath with its own
            // rendering suppressed, so the platform keyboard and SMS autofill still work.
            val otpFocus = remember { FocusRequester() }
            BasicTextField(
                value = otpCode,
                onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) otpCode = it },
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                cursorBrush = SolidColor(Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(otpFocus),
                decorationBox = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(6) { index ->
                            OtpCell(
                                digit = otpCode.getOrNull(index),
                                isNext = index == otpCode.length,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            )
            LaunchedEffect(Unit) { otpFocus.requestFocus() }

            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                AuthErrorLine(errorMessage)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
                    AccountLinker.signInOrLink(auth, credential) { error ->
                        errorMessage = error ?: ""
                        if (error != null) isLoading = false
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

    AuthScaffold(actions = {
        AuthPrimaryAction(
            label = "Continue with Google",
            loadingLabel = "Opening...",
            isLoading = isLoading,
            enabled = true,
            onClick = {
                isLoading = true
                errorMessage = ""
                val signInIntent = googleSignInClient.signInIntent
                AppLock.beginTrustedFlow()
                googleSignInLauncher.launch(signInIntent)
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "Access is still governed by your BIAP role.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }) {
        AuthBackButton(onBack = onBack)

        Spacer(modifier = Modifier.height(28.dp))

        // Google's mark keeps its own brand colour; the badge behind it takes the theme.
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(MaterialShapes.Sunny.toShape())
                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "G",
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = Color(0xFF4285F4)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        AuthTitle(
            title = "Connect Google",
            supporting = "Signing in also sets up encrypted backup."
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Say what is actually granted before the picker opens, not after.
        AuthInfoCard(
            title = "Your identity",
            body = "Name and email, used to recognise you across devices."
        )
        Spacer(modifier = Modifier.height(12.dp))
        AuthInfoCard(
            title = "Drive backup",
            body = "An app-private folder holds your encrypted catalogue. Nothing else in Drive is visible to BIAP.",
            highlighted = true
        )
        Spacer(modifier = Modifier.height(12.dp))
        AuthInfoCard(
            title = "Nothing is shared",
            body = "No contacts, no posting, no access to your other files."
        )

        if (errorMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            AuthErrorLine(errorMessage)
        }
    }
}

// The placeholder HomeScreen and the stub BiapTheme that lived here were removed:
// both are superseded by the restored app (MainScreen + com.sc.biap.theme.BiapTheme).
