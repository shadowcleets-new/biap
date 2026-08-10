package com.sc.biap.ui.profile

/**
 * @file ProfileScreen.kt
 * @description Who you are, how you sign in, and what you are allowed to do.
 *
 * [TABLE OF CONTENTS]
 * 1. IMPORTS & DEPENDENCIES
 * 2. LOCAL CONSTANTS & GEOMETRY
 * 3. MAIN SCREEN (orchestrator)
 * 4. PHOTO & IDENTITY HEADER
 * 5. PERSONAL DETAILS
 * 6. SIGN-IN METHODS
 * 7. ACCESS LEVEL (read-only)
 * 8. HELPER UTILITIES
 */

// #region 1. Imports & Dependencies
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.rememberAsyncImagePainter
import com.sc.biap.data.AccessLevel
import com.sc.biap.data.DefaultDataRepository
import com.sc.biap.security.AppLock
import com.sc.biap.theme.AtelierShapes
import com.sc.biap.theme.AtelierSpacing
import com.sc.biap.theme.bodyMediumStrong
import com.sc.biap.theme.labelLargeStrong
import com.sc.biap.theme.titleLargeStrong
import com.sc.biap.theme.titleMediumStrong
import com.sc.biap.util.FileUtils
import kotlinx.coroutines.launch
// #endregion

// #region 2. Local Constants & Geometry
private val AvatarSize = 96.dp
private val AvatarBadgeSize = 28.dp
private val AccessIconSize = 20.dp

private const val MAX_NAME_LENGTH = 40
private const val MAX_DESIGNATION_LENGTH = 40
// #endregion

// #region 3. Main Screen (orchestrator)

@Composable
private fun defaultProfileViewModel(): ProfileViewModel {
    val context = LocalContext.current.applicationContext
    return viewModel { ProfileViewModel(DefaultDataRepository.getInstance(context)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = defaultProfileViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        AppLock.endTrustedFlow()
        if (uri != null) {
            scope.launch {
                viewModel.onPhotoSelected(FileUtils.copyUriToInternalStorage(context, uri))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile", style = MaterialTheme.typography.titleLargeStrong) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = viewModel::save,
                        enabled = state.isDirty && !state.isSaving,
                        shape = AtelierShapes.CornerMedium
                    ) {
                        Icon(
                            Icons.Default.Done,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(Modifier.width(AtelierSpacing.Micro))
                        Text("Save")
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AtelierSpacing.Base),
            verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Large),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ProfileHeader(
                photoRef = state.draft.photoRef,
                name = state.draft.fullName,
                designation = state.draft.designation,
                onChangePhoto = {
                    AppLock.beginTrustedFlow()
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            )

            PersonalDetailsCard(
                firstName = state.draft.firstName,
                lastName = state.draft.lastName,
                age = state.draft.age,
                designation = state.draft.designation,
                onFirstNameChange = viewModel::onFirstNameChange,
                onLastNameChange = viewModel::onLastNameChange,
                onAgeChange = viewModel::onAgeChange,
                onDesignationChange = viewModel::onDesignationChange
            )

            SignInMethodsCard(methods = state.signInMethods)

            AccessLevelCard(
                level = state.accessLevel,
                confirmed = state.accessLevelConfirmed
            )
        }
    }
}
// #endregion

// #region 4. Photo & Identity Header

@Composable
private fun ProfileHeader(
    photoRef: String?,
    name: String,
    designation: String,
    onChangePhoto: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Compact)
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(AvatarSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onChangePhoto),
                contentAlignment = Alignment.Center
            ) {
                // A Google photo arrives as an https URL and a chosen one as a local file.
                // Coil takes either, so nothing downstream needs to know which it got.
                val model = remember(photoRef) {
                    when {
                        photoRef.isNullOrBlank() -> null
                        photoRef.startsWith("http") -> photoRef
                        else -> FileUtils.resolveImageFile(context, photoRef)
                    }
                }
                if (model != null) {
                    Image(
                        painter = rememberAsyncImagePainter(model),
                        contentDescription = "Profile photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "No profile photo",
                        modifier = Modifier.size(AvatarSize / 2),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(AvatarBadgeSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(onClick = onChangePhoto),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PhotoCamera,
                    contentDescription = "Change profile photo",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(AvatarBadgeSize / 2)
                )
            }
        }

        Text(
            text = name.ifBlank { "Add your name" },
            style = MaterialTheme.typography.titleMediumStrong,
            color = if (name.isBlank()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
        if (designation.isNotBlank()) {
            Text(
                designation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
// #endregion

// #region 5. Personal Details

@Composable
private fun PersonalDetailsCard(
    firstName: String,
    lastName: String,
    age: String,
    designation: String,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onAgeChange: (String) -> Unit,
    onDesignationChange: (String) -> Unit,
) {
    SectionCard {
        Column(
            modifier = Modifier.padding(AtelierSpacing.Base),
            verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Base)
        ) {
            Text("Your Details", style = MaterialTheme.typography.titleMediumStrong)
            Text(
                "Filled in from your Google account where possible. Anything you change here wins.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(AtelierSpacing.Compact)) {
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { if (it.length <= MAX_NAME_LENGTH) onFirstNameChange(it) },
                    label = { Text("First name") },
                    singleLine = true,
                    shape = AtelierShapes.CornerMedium,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { if (it.length <= MAX_NAME_LENGTH) onLastNameChange(it) },
                    label = { Text("Last name") },
                    singleLine = true,
                    shape = AtelierShapes.CornerMedium,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(AtelierSpacing.Compact)) {
                OutlinedTextField(
                    value = age,
                    onValueChange = onAgeChange,
                    label = { Text("Age") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = AtelierShapes.CornerMedium,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = designation,
                    onValueChange = {
                        if (it.length <= MAX_DESIGNATION_LENGTH) onDesignationChange(it)
                    },
                    label = { Text("Designation") },
                    placeholder = { Text("e.g. Master Tailor") },
                    singleLine = true,
                    shape = AtelierShapes.CornerMedium,
                    modifier = Modifier.weight(2f)
                )
            }
        }
    }
}
// #endregion

// #region 6. Sign-in Methods

/**
 * Every way this ONE account can be signed into.
 *
 * Worth showing plainly: these used to become separate accounts with separate catalogues,
 * so seeing them listed together is the visible proof that they are now one identity.
 */
@Composable
private fun SignInMethodsCard(methods: List<SignInMethod>) {
    SectionCard {
        Column(
            modifier = Modifier.padding(AtelierSpacing.Base),
            verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Cozy)
        ) {
            Text("Sign-in Methods", style = MaterialTheme.typography.titleMediumStrong)
            Text(
                "All of these open the same account and the same catalogue.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (methods.isEmpty()) {
                Text(
                    "No sign-in methods linked yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                methods.forEach { method ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(method.label, style = MaterialTheme.typography.bodyMediumStrong)
                        Text(
                            method.value,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
// #endregion

// #region 7. Access Level (read-only)

/**
 * Shown, never edited.
 *
 * There is deliberately no control here, and no code path in the app that can write a role.
 * A level the holder can change is not a permission, and this app keeps roles in Firestore
 * precisely so the person they apply to cannot edit them.
 */
@Composable
private fun AccessLevelCard(level: AccessLevel, confirmed: Boolean) {
    SectionCard {
        Column(
            modifier = Modifier.padding(AtelierSpacing.Base),
            verticalArrangement = Arrangement.spacedBy(AtelierSpacing.Cozy)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AtelierSpacing.Compact)
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(AccessIconSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("Level of Access", style = MaterialTheme.typography.titleMediumStrong)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AtelierSpacing.Compact)
            ) {
                Icon(
                    Icons.Default.Badge,
                    contentDescription = null,
                    modifier = Modifier.size(AccessIconSize),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(level.label, style = MaterialTheme.typography.labelLargeStrong)
            }

            Text(
                level.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                if (confirmed) {
                    "Only an admin can change this."
                } else {
                    // Never claim a level we could not actually read: showing "Viewer" as
                    // fact when the lookup failed would be a lie about someone's access.
                    "Could not confirm your access level right now, so the most restricted " +
                        "level is assumed. Check your connection."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (confirmed) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
    }
}
// #endregion

// #region 8. Helper Utilities

/** One silhouette for every card on this screen, defined once so they cannot drift apart. */
@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        shape = AtelierShapes.CornerLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        content()
    }
}
// #endregion
