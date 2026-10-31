package com.echoshield.authentication

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.echoshield.core.components.EchoGhostButton
import com.echoshield.core.components.EchoPrimaryButton
import com.echoshield.core.components.EchoTextField
import com.echoshield.core.components.EchoTopAppBar
import com.echoshield.theme.DangerRed
import com.echoshield.theme.PrimaryTeal

@Composable
fun SignupScreen(
    onSignupSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToTerms: () -> Unit,
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()

    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var termsAccepted by remember { mutableStateOf(false) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmError by remember { mutableStateOf<String?>(null) }
    var termsError by remember { mutableStateOf<String?>(null) }

    fun validateAndSubmit() {
        var valid = true

        if (fullName.trim().isEmpty()) {
            nameError = "Full name is required"
            valid = false
        } else nameError = null

        if (email.isBlank() || !email.contains("@")) {
            emailError = "Valid email is required"
            valid = false
        } else emailError = null

        if (password.length < 8) {
            passwordError = "Password must be at least 8 characters"
            valid = false
        } else passwordError = null

        if (confirmPassword != password) {
            confirmError = "Passwords do not match"
            valid = false
        } else confirmError = null

        if (!termsAccepted) {
            termsError = "You must agree to the Terms & Biometric Privacy Policy"
            valid = false
        } else termsError = null

        if (valid) {
            viewModel.clearErrorMessage()
            viewModel.signup(
                name = fullName,
                email = email,
                password = password,
                confirmPassword = confirmPassword,
                termsAccepted = termsAccepted,
                onSuccess = onSignupSuccess
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        EchoTopAppBar(
            title = "Create Family Account",
            subtitle = "Establish secure voice defense",
            onNavigateBack = onNavigateToLogin
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text(
                text = "Register Family Circle",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Create your master admin identity to invite family members and register voice signatures.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Global error banner
            if (uiState.errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DangerRed.copy(alpha = 0.12f))
                        .border(1.dp, DangerRed.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Error",
                            tint = DangerRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = uiState.errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = DangerRed
                        )
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            EchoTextField(
                value = fullName,
                onValueChange = {
                    fullName = it
                    if (nameError != null) nameError = null
                    if (uiState.errorMessage != null) viewModel.clearErrorMessage()
                },
                label = "Full Name",
                placeholder = "Sarah Henderson",
                leadingIcon = Icons.Default.Person,
                errorMessage = nameError
            )

            Spacer(modifier = Modifier.height(16.dp))

            EchoTextField(
                value = email,
                onValueChange = {
                    email = it
                    if (emailError != null) emailError = null
                    if (uiState.errorMessage != null) viewModel.clearErrorMessage()
                },
                label = "Email Address",
                placeholder = "sarah.h@echoshield.family",
                leadingIcon = Icons.Default.Email,
                errorMessage = emailError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            Spacer(modifier = Modifier.height(16.dp))

            EchoTextField(
                value = password,
                onValueChange = {
                    password = it
                    if (passwordError != null) passwordError = null
                    if (uiState.errorMessage != null) viewModel.clearErrorMessage()
                },
                label = "Master Password",
                placeholder = "Minimum 8 characters",
                isPassword = true,
                leadingIcon = Icons.Default.Lock,
                errorMessage = passwordError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            Spacer(modifier = Modifier.height(16.dp))

            EchoTextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    if (confirmError != null) confirmError = null
                    if (uiState.errorMessage != null) viewModel.clearErrorMessage()
                },
                label = "Confirm Master Password",
                placeholder = "Re-enter password",
                isPassword = true,
                leadingIcon = Icons.Default.Lock,
                errorMessage = confirmError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Terms Acceptance Checkbox
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        termsAccepted = !termsAccepted
                        if (termsError != null) termsError = null
                    }
            ) {
                Checkbox(
                    checked = termsAccepted,
                    onCheckedChange = {
                        termsAccepted = it
                        if (termsError != null) termsError = null
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = PrimaryTeal,
                        uncheckedColor = MaterialTheme.colorScheme.outline
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "I accept the Terms of Service & Biometric Data Handling Consent",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    EchoGhostButton(
                        text = "Read Biometric Disclosures",
                        onClick = onNavigateToTerms,
                        color = PrimaryTeal
                    )
                }
            }

            if (termsError != null) {
                Text(
                    text = termsError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = DangerRed,
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            EchoPrimaryButton(
                text = "Create Family Circle",
                onClick = { validateAndSubmit() },
                isLoading = uiState.isLoading
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Already have an account?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                EchoGhostButton(
                    text = "Log In",
                    onClick = onNavigateToLogin,
                    color = PrimaryTeal
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
