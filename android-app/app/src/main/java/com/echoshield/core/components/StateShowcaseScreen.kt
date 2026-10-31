package com.echoshield.core.components

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoshield.theme.PrimaryTeal

enum class DemoState {
    LOADING,
    EMPTY,
    NETWORK_ERROR,
    PERMISSION_DENIED,
    BACKEND_UNAVAILABLE
}

@Composable
fun StateShowcaseScreen(
    onNavigateBack: () -> Unit
) {
    var selectedState by remember { mutableStateOf(DemoState.LOADING) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        EchoTopAppBar(
            title = "State Architecture Showcase",
            subtitle = "Screen 31: Error, Empty & Offline Testing",
            onNavigateBack = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Interactive Selector Row
            Text(
                text = "Select Simulation State:",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = PrimaryTeal,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StateFilterChip("Loading", DemoState.LOADING, selectedState) { selectedState = it }
                StateFilterChip("Empty", DemoState.EMPTY, selectedState) { selectedState = it }
                StateFilterChip("Offline", DemoState.NETWORK_ERROR, selectedState) { selectedState = it }
                StateFilterChip("Perms", DemoState.PERMISSION_DENIED, selectedState) { selectedState = it }
                StateFilterChip("Backend", DemoState.BACKEND_UNAVAILABLE, selectedState) { selectedState = it }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!toastMessage.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(PrimaryTeal.copy(alpha = 0.2f))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = toastMessage ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = PrimaryTeal
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Target State Container
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                when (selectedState) {
                    DemoState.LOADING -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            EchoPulseRadar(size = 100.dp, color = PrimaryTeal)
                            Spacer(modifier = Modifier.height(16.dp))
                            EchoCyberSpinner(
                                size = 32.dp,
                                label = "Synchronizing with Security Backend..."
                            )
                        }
                    }
                    DemoState.EMPTY -> {
                        EchoEmptyState(
                            title = "No Family Members Registered",
                            description = "Your protected circle is currently empty. Add family members to activate continuous call voice verification.",
                            icon = Icons.Default.Group,
                            actionButtonText = "Invite First Member",
                            onActionClick = { toastMessage = "Navigating to Add Member..." }
                        )
                    }
                    DemoState.NETWORK_ERROR -> {
                        EchoErrorState(
                            errorType = EchoErrorType.NETWORK,
                            onRetry = {
                                toastMessage = "Network retry executed successfully."
                            }
                        )
                    }
                    DemoState.PERMISSION_DENIED -> {
                        EchoErrorState(
                            errorType = EchoErrorType.PERMISSION_DENIED,
                            onOpenSettings = {
                                toastMessage = "Opening Android application settings..."
                            }
                        )
                    }
                    DemoState.BACKEND_UNAVAILABLE -> {
                        EchoErrorState(
                            errorType = EchoErrorType.BACKEND_UNAVAILABLE,
                            onRetry = {
                                toastMessage = "Reconnecting to backend cluster..."
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StateFilterChip(
    label: String,
    state: DemoState,
    currentState: DemoState,
    onSelect: (DemoState) -> Unit
) {
    val isSelected = state == currentState
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) PrimaryTeal.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                1.dp,
                if (isSelected) PrimaryTeal else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable { onSelect(state) }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (isSelected) PrimaryTeal else MaterialTheme.colorScheme.onSurface
        )
    }
}
