package com.echoshield.family

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.echoshield.core.components.EchoCard
import com.echoshield.core.components.EchoPrimaryButton
import com.echoshield.core.components.EchoTextField
import com.echoshield.core.components.EchoTopAppBar
import com.echoshield.theme.AccentBlue
import com.echoshield.theme.DangerRed
import com.echoshield.theme.PrimaryTeal
import com.echoshield.theme.SuccessGreen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddFamilyMemberScreen(
    onNavigateBack: () -> Unit,
    onInvitationSent: () -> Unit,
    viewModel: FamilyViewModel = viewModel(factory = FamilyViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()

    var emailAddress by remember { mutableStateOf("") }
    var selectedRelationship by remember { mutableStateOf("Parent") }
    var localEmailError by remember { mutableStateOf<String?>(null) }

    val relationships = listOf(
        "Parent", "Spouse", "Child", "Sibling", "Grandparent", "Other Relative"
    )

    fun validateAndSubmit() {
        val trimmed = emailAddress.trim()
        if (trimmed.isEmpty() || !trimmed.contains("@")) {
            localEmailError = "Please enter a valid family email address"
            return
        }
        localEmailError = null
        viewModel.clearErrorMessage()
        viewModel.sendInvitation(
            recipientEmail = trimmed,
            relationship = selectedRelationship,
            onSuccess = onInvitationSent
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        EchoTopAppBar(
            title = "Add Family Member",
            subtitle = "Expand your protected circle",
            onNavigateBack = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Error banner
            if (uiState.errorMessage != null || localEmailError != null) {
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
                            contentDescription = null,
                            tint = DangerRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = uiState.errorMessage ?: localEmailError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = DangerRed
                        )
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            // Recipient Email input with safe directory query
            Text(
                text = "Family Member Email",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Enter the registered email of your family member. A connection request will be transmitted.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    EchoTextField(
                        value = emailAddress,
                        onValueChange = {
                            emailAddress = it
                            if (localEmailError != null) localEmailError = null
                            if (uiState.errorMessage != null) viewModel.clearErrorMessage()
                        },
                        label = "Recipient Email",
                        placeholder = "family.member@example.com",
                        leadingIcon = Icons.Default.Email,
                        errorMessage = localEmailError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (emailAddress.contains("@")) {
                            viewModel.searchUser(emailAddress)
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PrimaryTeal.copy(alpha = 0.15f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Directory",
                        tint = PrimaryTeal
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Directory Safe Search Feedback
            if (uiState.searchResult != null) {
                val result = uiState.searchResult!!
                EchoCard(
                    borderColor = if (result.exists) SuccessGreen.copy(alpha = 0.5f) else AccentBlue.copy(alpha = 0.5f),
                    padding = 14.dp
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (result.exists) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (result.exists) SuccessGreen else AccentBlue,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (result.exists) "Verified Account Found" else "Account Not Yet Registered",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (result.exists) {
                                    result.name?.let { "$it (${result.email})" } ?: result.email
                                } else {
                                    "Your invitation will allow them to join and connect instantly upon signup."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            // Relationship Selector Chips
            Text(
                text = "Select Relationship",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                relationships.forEach { relation ->
                    val isSelected = selectedRelationship == relation
                    val shape = RoundedCornerShape(12.dp)
                    Box(
                        modifier = Modifier
                            .clip(shape)
                            .background(
                                if (isSelected) PrimaryTeal.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { selectedRelationship = relation }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = relation,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isSelected) PrimaryTeal else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Action Button
            EchoPrimaryButton(
                text = "Dispatch Security Invitation",
                onClick = { validateAndSubmit() },
                isLoading = uiState.isLoading,
                leadingIcon = Icons.Default.PersonAdd
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
