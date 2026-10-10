@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.outsidedays.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable fun CozyCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
@Composable fun Title(title: String, subtitle: String) {
    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun Choices(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    // Wrapping rows keep every option reachable at large font sizes.
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { label -> FilterChip(selected = selected == label, onClick = { onSelect(label) }, label = { Text(label) }) }
    }
}
@Composable fun Meadow(reduced: Boolean, awake: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "Meadow")
    val movement = if(reduced) 0f else transition.animateFloat(0f, 1f, infiniteRepeatable(tween(9000), RepeatMode.Reverse), label = "Cloud drift").value
    Canvas(Modifier.fillMaxWidth().height(185.dp).semantics { contentDescription = "Mori, an original little leaf spirit, resting under a peaceful sky" }) {
        val w = size.width; val h = size.height
        drawRoundRect(Color(0xFFDCECEF), cornerRadius = androidx.compose.ui.geometry.CornerRadius(60f))
        drawCircle(Color(0xFFFFD995), w * .065f, Offset(w * .82f, h * .25f))
        fun cloud(x: Float, y: Float) {
            drawOval(Color(0xFFF9FBF4), Offset(x, y), Size(w * .22f, h * .15f))
            drawCircle(Color(0xFFF9FBF4), h * .09f, Offset(x + w * .1f, y))
        }
        cloud(w * (.08f + movement * .035f), h * .25f); cloud(w * (.58f - movement * .025f), h * .42f)
        val hill = Path().apply { moveTo(0f, h * .75f); quadraticTo(w * .25f, h * .35f, w * .6f, h * .85f); lineTo(w, h * .65f); lineTo(w, h); lineTo(0f, h); close() }
        drawPath(hill, Color(0xFFAFC5A0))
        val front = Path().apply { moveTo(0f, h * .93f); quadraticTo(w * .5f, h * .45f, w, h * .83f); lineTo(w,h); lineTo(0f,h); close() }
        drawPath(front, Color(0xFF829F78))
        val cx = w * .46f; val cy = h * .7f
        drawOval(Color(0xFF526C4D).copy(alpha = .22f), Offset(cx - 52f, cy + 41f), Size(104f, 20f))
        drawOval(Color(0xFFF7EACB), Offset(cx - 44f, cy - 47f), Size(88f, 102f))
        drawOval(Color(0xFF49664D), Offset(cx - 3f, cy - 79f), Size(43f, 35f))
        drawLine(Color(0xFF49664D), Offset(cx,cy-40f), Offset(cx+11f,cy-61f), 5f)
        if (awake) { drawCircle(Color(0xFF354B3A), 4f, Offset(cx - 16f, cy)); drawCircle(Color(0xFF354B3A), 4f, Offset(cx + 16f, cy)) }
        else { drawLine(Color(0xFF354B3A),Offset(cx-22f,cy),Offset(cx-12f,cy),3f); drawLine(Color(0xFF354B3A),Offset(cx+12f,cy),Offset(cx+22f,cy),3f) }
        drawArc(Color(0xFF354B3A), 15f, 150f, false, Offset(cx-8f,cy+6f),Size(16f,10f),style=Stroke(2.5f))
        drawCircle(Color(0xFFE8AA94), 7f, Offset(cx-28f,cy+10f)); drawCircle(Color(0xFFE8AA94),7f,Offset(cx+28f,cy+10f))
        repeat(9) { i -> val x = w * (i + .5f) / 9; val y = h * (.87f + .08f * sin(i.toFloat())); drawLine(Color(0xFF49664D),Offset(x,y),Offset(x-4,y-12),2f); drawCircle(Color(0xFFF9E7AA),4f,Offset(x-4,y-14)) }
    }
}

@Composable fun LandscapeTile(value: Int, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxSize().semantics { contentDescription = if(value == 0) "Empty sky" else "Landscape tile $value" }) {
        Box(contentAlignment = androidx.compose.ui.Alignment.TopStart) {
            Canvas(Modifier.fillMaxSize()) {
                if(value == 0) { drawRect(Color(0xFFEDEEE3)); return@Canvas }
                val column = (value - 1) % 3; val row = (value - 1) / 3
                val fullW = size.width * 3; val fullH = size.height * 3
                // Each piece is a crop of one original, programmatically drawn landscape.
                withTransform({ translate(-column * size.width, -row * size.height) }) {
                    drawRect(Color(0xFFD6E8F1), size = Size(fullW, fullH))
                    drawCircle(Color(0xFFFFD29B), fullW * .13f, Offset(fullW * .77f, fullH * .24f))
                    drawOval(Color(0xFFFDF9EF), Offset(fullW * .08f, fullH * .18f), Size(fullW * .4f, fullH * .12f))
                    drawOval(Color(0xFFFDF9EF), Offset(fullW * .23f, fullH * .1f), Size(fullW * .18f, fullH * .17f))
                    val hill = Path().apply { moveTo(0f,fullH*.6f); quadraticTo(fullW*.3f,fullH*.2f,fullW,fullH*.65f); lineTo(fullW,fullH); lineTo(0f,fullH); close() }
                    drawPath(hill,Color(0xFFB9CDA7))
                    val hill2 = Path().apply { moveTo(0f,fullH*.9f); quadraticTo(fullW*.7f,fullH*.35f,fullW,fullH*.7f); lineTo(fullW,fullH); lineTo(0f,fullH); close() }
                    drawPath(hill2,Color(0xFF819E7C))
                    val river = Path().apply { moveTo(fullW*.67f,fullH*.61f); quadraticTo(fullW*.2f,fullH*.8f,fullW*.65f,fullH); lineTo(fullW*.3f,fullH); quadraticTo(fullW*.1f,fullH*.8f,fullW*.64f,fullH*.61f); close() }
                    drawPath(river,Color(0xFFB0D2DA))
                    repeat(14) { i -> val x=fullW*((i*37%97)/100f); val y=fullH*(.76f+(i%3)*.075f); drawCircle(Color(0xFFF6DBB8),fullW*.009f,Offset(x,y)) }
                }
            }
            if(value != 0) Surface(color = Color(0xFFFAF7EE), shape = RoundedCornerShape(10.dp), modifier = Modifier.padding(6.dp)) { Text(value.toString(), modifier = Modifier.padding(horizontal=9.dp,vertical=3.dp), color=Color(0xFF28372E), style=MaterialTheme.typography.titleMedium) }
        }
    }
}
