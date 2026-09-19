package com.rbagent.assistant.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rbagent.assistant.ui.theme.ElectricPink
import com.rbagent.assistant.ui.theme.FrostedBorder
import com.rbagent.assistant.ui.theme.FrostedSlateHeavy
import com.rbagent.assistant.ui.theme.NeonCyan
import com.rbagent.assistant.ui.theme.SlateMuted
import com.rbagent.assistant.ui.theme.TextPrimary

/**
 * AttachmentSheet
 * Material 3 ModalBottomSheet offering Photo / Video / Document picks.
 *
 * Each launcher is specialised to one MIME filter, so the MIME hint
 * passed back to the caller is a compile-time constant — no runtime
 * if-expression is needed inside the callback.
 *
 * @param onPick  (uri, mimeHint) — uri is null if the user cancelled;
 *                mimeHint is non-null for image/video, null for docs
 *                so the caller can resolve the type dynamically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentSheet(
    onDismiss: () -> Unit,
    onPick: (Uri?, String?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // ── Photo picker ──
    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onPick(uri, "image/*")
        } else {
            onPick(null, null)
        }
    }

    // ── Video picker ──
    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onPick(uri, "video/*")
        } else {
            onPick(null, null)
        }
    }

    // ── Document picker (any MIME) ──
    val docLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            // Pass null MIME — the ViewModel resolves it via ContentResolver
            onPick(uri, null)
        } else {
            onPick(null, null)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = FrostedSlateHeavy,
        scrimColor = Color(0xB304070D)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Attach to message",
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Send images, videos or documents to RB Agent",
                color = SlateMuted,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(18.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AttachmentTile(
                    icon = Icons.Filled.Image,
                    label = "Photo",
                    tint = NeonCyan,
                    modifier = Modifier.weight(1f)
                ) { imageLauncher.launch("image/*") }

                AttachmentTile(
                    icon = Icons.Filled.VideoLibrary,
                    label = "Video",
                    tint = ElectricPink,
                    modifier = Modifier.weight(1f)
                ) { videoLauncher.launch("video/*") }

                AttachmentTile(
                    icon = Icons.Filled.Description,
                    label = "Document",
                    tint = NeonCyan,
                    modifier = Modifier.weight(1f)
                ) { docLauncher.launch("*/*") }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AttachmentTile(
    icon: ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0x331E293B))
            .border(1.dp, tint.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.15f))
                .border(1.dp, tint.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
