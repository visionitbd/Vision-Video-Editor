package com.visionit.vcut.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visionit.vcut.model.ExportResolution
import com.visionit.vcut.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportBottomSheet(
    isExporting: Boolean,
    progress: Float,
    onDismiss: () -> Unit,
    onStartExport: (ExportResolution, Int) -> Unit
) {
    var selectedRes by remember { mutableStateOf(ExportResolution.FHD_1080) }
    var selectedFps by remember { mutableIntStateOf(30) }
    val fpsOptions = listOf(24, 30, 60)

    ModalBottomSheet(
        onDismissRequest = { if (!isExporting) onDismiss() },
        containerColor = PureWhite,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.MovieCreation,
                    contentDescription = null,
                    tint = SkyBluePrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Export Video (Watermark-Free)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = CharcoalText
                )
            }

            Spacer(Modifier.height(18.dp))

            Text("Resolution", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SlateSubtext)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExportResolution.entries.forEach { res ->
                    val active = selectedRes == res
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (active) SoftSkySurface else IceBlueTint)
                            .border(
                                width = if (active) 1.5.dp else 1.dp,
                                color = if (active) SkyBluePrimary else SubtleBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable(enabled = !isExporting) { selectedRes = res }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = res.label,
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) SkyBlueDeep else CharcoalText
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("Frame Rate", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SlateSubtext)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                fpsOptions.forEach { fps ->
                    val active = selectedFps == fps
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (active) SoftSkySurface else IceBlueTint)
                            .border(
                                width = if (active) 1.5.dp else 1.dp,
                                color = if (active) SkyBluePrimary else SubtleBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable(enabled = !isExporting) { selectedFps = fps }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${fps}fps",
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) SkyBlueDeep else CharcoalText
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            if (isExporting) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(50)),
                        color = SkyBluePrimary,
                        trackColor = SoftSkySurface
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Exporting ${(progress * 100).toInt()}% • Movies/VisionCut",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = SkyBlueDeep
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.horizontalGradient(listOf(SkyBluePrimary, SkyBlueDeep))
                        )
                        .clickable { onStartExport(selectedRes, selectedFps) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = PureWhite,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Export ${selectedRes.label} (${selectedFps}fps)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
        }
    }
}
