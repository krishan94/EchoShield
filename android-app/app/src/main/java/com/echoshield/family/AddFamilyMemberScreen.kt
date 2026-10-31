package com.echoshield.family

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.echoshield.core.components.EchoCard
import com.echoshield.core.components.EchoPrimaryButton
import com.echoshield.core.components.EchoSecondaryButton
import com.echoshield.core.components.EchoTextField
import com.echoshield.core.components.EchoTopAppBar
import com.echoshield.theme.PrimaryTeal

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddFamilyMemberScreen(
    onNavigateBack: () -> Unit,
    onInvitationSent: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var emailAddress by remember { mutableStateOf("") }
    var selectedRelationship by remember { mutableStateOf("Parent") }
    var isSending by remember { mutableStateOf(false) }

    val relationships = listOf(
        "Parent", "Spouse", "Child", "Sibling", "Grandparent", "Other Relative"
    )

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
            // Search existing EchoShield users
            Text(
                text = "Search EchoShield Directory",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            EchoTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = "Username or EchoShield ID",
                placeholder = "e.g., mark.henderson#78",
                leadingIcon = Icons.Default.Search
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Or Invite via Email
            Text(
                text = "Or Send Family Invitation",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            EchoTextField(
                value = emailAddress,
                onValueChange = { emailAddress = it },
                label = "Recipient Email",
                placeholder = "family.member@example.com",
                leadingIcon = Icons.Default.Email
            )

            Spacer(modifier = Modifier.height(20.dp))

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

            Spacer(modifier = Modifier.height(24.dp))

            // Invite Code Card
            EchoCard(padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(PrimaryTeal.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = PrimaryTeal,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Instant Circle Invite Code",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "HEND-7821",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = PrimaryTeal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Action Buttons
            EchoPrimaryButton(
                text = "Dispatch Security Invitation",
                onClick = {
                    isSending = true
                    onInvitationSent()
                },
                isLoading = isSending,
                leadingIcon = Icons.Default.PersonAdd
            )

            Spacer(modifier = Modifier.height(14.dp))

            EchoSecondaryButton(
                text = "Share Invite Code Link",
                onClick = onInvitationSent,
                leadingIcon = Icons.Default.Share
            )
        }
    }
}

