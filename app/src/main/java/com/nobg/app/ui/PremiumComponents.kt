package com.nobg.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object PremiumDimens {
    val ScreenGutter = 22.dp
    val WideScreenGutter = 32.dp
    val GroupGap = 18.dp
    val GroupRadius = 26.dp
    val RowHorizontalPadding = 24.dp
    val RowMinHeight = 78.dp
    val IconSize = 28.dp
    val IconTextGap = 20.dp
    val ContentMaxWidth = 840.dp
}

object PremiumAccent {
    val Blue = Color(0xFF2D8CF0)
    val Green = Color(0xFF35BE87)
    val Orange = Color(0xFFFFAA35)
    val Yellow = Color(0xFFFFBE45)
    val Pink = Color(0xFFEB6C83)
    val Purple = Color(0xFF7065DA)
    val Teal = Color(0xFF35AE9D)
}

/** Flat outer surface for every functional group. Explicit colors/shapes are accepted for
 * source compatibility, while the premium surface contract remains consistent app-wide. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(PremiumDimens.GroupRadius),
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    androidx.compose.material3.Card(
        modifier = modifier,
        shape = RoundedCornerShape(PremiumDimens.GroupRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = null,
        content = content
    )
}

@Composable
fun Card(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(PremiumDimens.GroupRadius),
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable ColumnScope.() -> Unit
) {
    androidx.compose.material3.Card(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(PremiumDimens.GroupRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = null,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
fun PremiumSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium
    )
}

@Composable
fun PremiumInsetDivider(
    modifier: Modifier = Modifier,
    iconSlotWidth: Dp = PremiumDimens.IconSize + PremiumDimens.IconTextGap
) {
    HorizontalDivider(
        modifier = modifier.padding(
            start = PremiumDimens.RowHorizontalPadding + iconSlotWidth,
            end = PremiumDimens.RowHorizontalPadding
        ),
        thickness = 0.75.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
fun PremiumNavigationRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    showChevron: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = PremiumDimens.RowMinHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = PremiumDimens.RowHorizontalPadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(PremiumDimens.IconSize),
            tint = accent
        )
        Spacer(Modifier.width(PremiumDimens.IconTextGap))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 23.sp
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (!trailing.isNullOrBlank()) {
            Text(
                text = trailing,
                modifier = Modifier.padding(start = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (showChevron) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                modifier = Modifier.padding(start = 10.dp).size(18.dp),
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

fun Modifier.premiumContentWidth(): Modifier = this
    .fillMaxWidth()
    .widthIn(max = PremiumDimens.ContentMaxWidth)
