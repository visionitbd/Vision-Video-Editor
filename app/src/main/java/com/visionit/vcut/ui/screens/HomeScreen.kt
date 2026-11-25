package com.visionit.vcut.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visionit.vcut.model.EditorTab
import com.visionit.vcut.model.SavedDraftSummary
import com.visionit.vcut.ui.theme.*

private data class HomeToolItem(
    val title: String,
    val icon: ImageVector,
    val targetTab: EditorTab
)

@Composable
fun HomeScreen(
    drafts: List<SavedDraftSummary>,
    onPickNewVideo: (EditorTab) -> Unit,
    onOpenDraft: (SavedDraftSummary) -> Unit,
    onDeleteDraft: (String) -> Unit
) {
    var selectedNav by remember { mutableIntStateOf(0) }

    val homeTools = remember {
        listOf(
            HomeToolItem("Split & Merge", Icons.Outlined.ContentCut, EditorTab.EDIT),
            HomeToolItem("Speed Ramp", Icons.Outlined.Speed, EditorTab.EDIT),
            HomeToolItem("Filters & LUTs", Icons.Outlined.FilterVintage, EditorTab.FILTERS),
            HomeToolItem("Voice Record", Icons.Outlined.Mic, EditorTab.AUDIO),
            HomeToolItem("Enhance Voice", Icons.Outlined.GraphicEq, EditorTab.AUDIO),
            HomeToolItem("Noise Remove", Icons.Outlined.NoiseAware, EditorTab.AUDIO),
            HomeToolItem("Extract Audio", Icons.Outlined.AudioFile, EditorTab.AUDIO),
            HomeToolItem("Text & Captions", Icons.Outlined.ClosedCaption, EditorTab.TEXT),
            HomeToolItem("Overlay PIP", Icons.Outlined.Layers, EditorTab.OVERLAY)
        )
    }

    Scaffold(
        containerColor = PureWhite,
        bottomBar = {
            Surface(
                color = PureWhite,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(vertical = 10.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HomeBottomNavItem(
                        label = "Edit",
                        icon = Icons.Rounded.ContentCut,
                        selected = selectedNav == 0,
                        onClick = { selectedNav = 0 }
                    )
                    HomeBottomNavItem(
                        label = "Drafts",
                        icon = Icons.Outlined.Description,
                        selected = selectedNav == 1,
                        onClick = { selectedNav = 1 }
                    )
                    HomeBottomNavItem(
                        label = "Tools",
                        icon = Icons.Outlined.FolderOpen,
                        selected = selectedNav == 2,
                        onClick = { selectedNav = 2 }
                    )
                    HomeBottomNavItem(
                        label = "Settings",
                        icon = Icons.Outlined.Settings,
                        selected = selectedNav == 3,
                        onClick = { selectedNav = 3 }
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Soft Ice-Blue Gradient Hero Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFB7DCFA),
                                Color(0xFFD9EEFF),
                                PureWhite
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(PureWhite.copy(alpha = 0.65f))
                                .border(1.dp, PureWhite.copy(alpha = 0.9f), RoundedCornerShape(50))
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = "Pro",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = SkyBlueDeep
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "Offline Editor",
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = CharcoalText
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(PureWhite.copy(alpha = 0.7f))
                                .clickable { onPickNewVideo(EditorTab.EDIT) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VideoLibrary,
                                contentDescription = "Pick Video",
                                tint = CharcoalText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(22.dp))

                    Text(
                        text = "Vision Cut",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = CharcoalText
                    )
                    Text(
                        text = "4K Export • Studio Voice • Zero Watermark",
                        fontSize = 12.sp,
                        color = SlateSubtext
                    )

                    Spacer(Modifier.height(18.dp))

                    // Two prominent frosted sky-blue cards side by side
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // New Video Card (wider)
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFFEAF4FF),
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .weight(1.45f)
                                .height(118.dp)
                                .border(1.dp, PureWhite, RoundedCornerShape(20.dp))
                                .clickable { onPickNewVideo(EditorTab.EDIT) }
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(CharcoalText),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = "New video",
                                        tint = PureWhite,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = "New video",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CharcoalText
                                )
                            }
                        }

                        // Quick Trim Card
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFFF0F7FF),
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .weight(1f)
                                .height(118.dp)
                                .border(1.dp, PureWhite, RoundedCornerShape(20.dp))
                                .clickable { onPickNewVideo(EditorTab.AUDIO) }
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Mic,
                                    contentDescription = "Voice Studio",
                                    tint = CharcoalText,
                                    modifier = Modifier.size(30.dp)
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = "Voice Studio",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CharcoalText
                                )
                            }
                        }
                    }
                }
            }

            // Recent Projects Row
            if (drafts.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Projects",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CharcoalText
                    )
                    Text(
                        text = "${drafts.size} drafts",
                        fontSize = 12.sp,
                        color = SlateSubtext
                    )
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(drafts, key = { it.projectId }) { draft ->
                        Box(
                            modifier = Modifier
                                .size(82.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                                    )
                                )
                                .clickable { onOpenDraft(draft) }
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayCircleFilled,
                                contentDescription = null,
                                tint = PureWhite.copy(alpha = 0.85f),
                                modifier = Modifier
                                    .size(24.dp)
                                    .align(Alignment.Center)
                            )
                            Row(
                                modifier = Modifier.align(Alignment.BottomStart),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCut,
                                    contentDescription = null,
                                    tint = PureWhite,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = draft.dateLabel.ifBlank { draft.title },
                                    fontSize = 10.sp,
                                    color = PureWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Delete",
                                tint = PureWhite.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .size(15.dp)
                                    .align(Alignment.TopEnd)
                                    .clickable { onDeleteDraft(draft.projectId) }
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(10.dp))

            // 3x3 Minimal Monochrome Tool Grid on Pure White
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                homeTools.chunked(3).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        rowItems.forEach { tool ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onPickNewVideo(tool.targetTab) }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = tool.icon,
                                    contentDescription = tool.title,
                                    tint = CharcoalText,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = tool.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = CharcoalText,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HomeBottomNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) CharcoalText else SlateSubtext,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) CharcoalText else SlateSubtext
        )
    }
}
