package com.example.roadwatch.ui.scan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Shared floating controls for the camera and map. Labels stay available to TalkBack. */
@Composable
fun JourneyButton(label: String, symbol: String, onClick: () -> Unit, enabled: Boolean = true, dark: Boolean = false, active: Boolean = false) {
    Surface(shape=CircleShape,color=if(active) Color(0xFFDBF0DF) else if(dark) Color(0xD9202925) else Color(0xFAFFFFFF),shadowElevation=6.dp) {
        IconButton(onClick=onClick,enabled=enabled,modifier=Modifier.size(52.dp).semantics { contentDescription=label }) {
            JourneyGlyph(symbol,if(dark && !active) Color.White else Color(0xFF173D2B))
        }
    }
}

@Composable
fun JourneyGlyph(symbol: String, color: Color = Color.White) {
    Canvas(Modifier.size(24.dp)) {
        val unit=size.width/24f
        fun line(x:Float,y:Float,a:Float,b:Float) = drawLine(color,Offset(x*unit,y*unit),Offset(a*unit,b*unit),2*unit)
        when(symbol) {
            "back" -> { line(19f,12f,5f,12f);line(5f,12f,12f,5f);line(5f,12f,12f,19f) }
            "plus" -> { line(5f,12f,19f,12f);line(12f,5f,12f,19f) }
            "minus" -> line(5f,12f,19f,12f)
            "refresh" -> { drawArc(color,40f,285f,false,Offset(4*unit,4*unit),androidx.compose.ui.geometry.Size(16*unit,16*unit),style=Stroke(2*unit));line(20f,4f,20f,10f);line(20f,10f,14f,10f) }
            "locate" -> { drawCircle(color,6*unit,style=Stroke(2*unit));drawCircle(color,2*unit);line(12f,1f,12f,5f);line(12f,19f,12f,23f);line(1f,12f,5f,12f);line(19f,12f,23f,12f) }
            "flash" -> { line(13f,2f,5f,14f);line(5f,14f,11f,14f);line(11f,14f,10f,22f);line(10f,22f,19f,10f);line(19f,10f,13f,10f);line(13f,10f,13f,2f) }
            "info" -> { drawCircle(color,9*unit,style=Stroke(1.8f*unit));line(12f,10f,12f,17f);drawCircle(color,1.2f*unit,Offset(12*unit,7*unit)) }
            "stop" -> drawRoundRect(color,Offset(6*unit,6*unit),androidx.compose.ui.geometry.Size(12*unit,12*unit),androidx.compose.ui.geometry.CornerRadius(2*unit))
            "scan" -> { line(3f,8f,3f,3f);line(3f,3f,8f,3f);line(16f,3f,21f,3f);line(21f,3f,21f,8f);line(3f,16f,3f,21f);line(3f,21f,8f,21f);line(16f,21f,21f,21f);line(21f,21f,21f,16f);drawCircle(color,4*unit,style=Stroke(1.8f*unit)) }
            else -> { line(6f,3f,18f,3f);line(18f,3f,18f,21f);line(18f,21f,6f,21f);line(6f,21f,6f,3f);line(9f,9f,15f,9f);line(9f,14f,15f,14f) }
        }
    }
}

@Composable
fun JourneyPill(text: String, dark: Boolean = false, indicator: Color? = null) {
    Surface(shape=RoundedCornerShape(50),color=if(dark) Color(0xE6202925) else Color(0xF7FFFFFF),shadowElevation=2.dp) {
        Row(Modifier.padding(horizontal=14.dp,vertical=10.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            indicator?.let { Box(Modifier.size(7.dp).background(it,CircleShape));Spacer(Modifier.width(8.dp)) }
            Text(text,color=if(dark) Color.White else Color(0xFF173D2B),style=MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun JourneySystemBars(darkIcons: Boolean) {
    val view=androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(view,darkIcons) {
        var context=view.context
        while(context is android.content.ContextWrapper && context !is android.app.Activity) context=context.baseContext
        val activity=context as? android.app.Activity
        val controller=activity?.let { androidx.core.view.WindowCompat.getInsetsController(it.window,view) }
        val previousStatus=controller?.isAppearanceLightStatusBars
        val previousNavigation=controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars=darkIcons
        controller?.isAppearanceLightNavigationBars=darkIcons
        onDispose {
            previousStatus?.let { controller?.isAppearanceLightStatusBars=it }
            previousNavigation?.let { controller?.isAppearanceLightNavigationBars=it }
        }
    }
}
