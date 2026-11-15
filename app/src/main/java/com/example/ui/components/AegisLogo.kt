package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberIndigo
import com.example.ui.theme.ShieldEmerald

@Composable
fun AegisXEmblem(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val brush = Brush.linearGradient(
            colors = listOf(CyberCyan, CyberIndigo),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        )

        // Outer futuristic hexagonal shield contour
        val shield = Path().apply {
            moveTo(w * 0.50f, h * 0.06f)
            lineTo(w * 0.90f, h * 0.22f)
            lineTo(w * 0.84f, h * 0.64f)
            lineTo(w * 0.50f, h * 0.94f)
            lineTo(w * 0.16f, h * 0.64f)
            lineTo(w * 0.10f, h * 0.22f)
            close()
        }
        drawPath(
            path = shield,
            brush = brush,
            style = Stroke(width = w * 0.075f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Inner geometric 'A' + 'X' nexus lines
        val nexus = Path().apply {
            moveTo(w * 0.30f, h * 0.66f)
            lineTo(w * 0.50f, h * 0.26f)
            lineTo(w * 0.70f, h * 0.66f)
            moveTo(w * 0.36f, h * 0.52f)
            lineTo(w * 0.64f, h * 0.52f)
        }
        drawPath(
            path = nexus,
            color = Color.White,
            style = Stroke(width = w * 0.07f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun AegisXBrandBadge(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AegisXEmblem(size = if (compact) 28.dp else 36.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Aegis",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = if (compact) 18.sp else 22.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "X",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = if (compact) 18.sp else 22.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = CyberCyan
            )
        }
    }
}

@Composable
fun SecurityStatusChip(
    label: String,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val dotColor = if (isActive) ShieldEmerald else MaterialTheme.colorScheme.onSurfaceVariant
    val bgColor = if (isActive) ShieldEmerald.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant
    val borderColor = if (isActive) ShieldEmerald.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline

    Row(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(50))
            .border(1.dp, borderColor, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(dotColor, CircleShape)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) ShieldEmerald else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
