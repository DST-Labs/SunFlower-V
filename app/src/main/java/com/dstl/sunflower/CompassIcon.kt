import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dstl.sunflower.rememberCompassHeading
import kotlin.math.*

@Composable
fun CompassIcon(
    iconSize: Dp = 56.dp,
    onClick: (() -> Unit)? = null
) {
    val heading by rememberCompassHeading() // 0=북, 90=동, 180=남, 270=서
    val animatedHeading by animateFloatAsState(
        targetValue = heading,
        animationSpec = tween(180),
        label = "miniCompassHeading"
    )

    // ✅ Canvas 안에서 MaterialTheme 호출 문제 방지: 밖에서 캐싱
    val cs = MaterialTheme.colorScheme
    val northColor = Color(0xFFD32F2F) // 🔴 정북 강조(빨강)
    val ringColor = runCatching { cs.outlineVariant }.getOrElse { cs.outline }
    val tickColor = cs.onSurfaceVariant
    val bg = cs.surface
    val centerStrokeColor = cs.outline

    val base = Modifier.size(iconSize)
    val clickable = if (onClick != null) {
        base.clip(RoundedCornerShape(999.dp)).clickable { onClick() }
    } else base

    Surface(
        modifier = clickable,
        shape = RoundedCornerShape(999.dp),
        color = bg,
        tonalElevation = 2.dp
    ) {
        // ✅ N 텍스트는 Canvas가 아니라 Box 위에 올리면 가장 깔끔/안전
        Box(Modifier.fillMaxSize()) {

            Canvas(Modifier.fillMaxSize().padding(6.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = min(size.width, size.height) * 0.45f

                // 링
                drawCircle(ringColor, r, Offset(cx, cy), style = Stroke(width = 6f))

                // 눈금 (0°=북 기준으로 배치 -> Canvas 각도는 (deg - 90) 변환)
                val tickCount = 12
                for (i in 0 until tickCount) {
                    val degCompass = i * (360f / tickCount)     // 0=북, 90=동...
                    val rad = Math.toRadians((degCompass - 90f).toDouble()) // Canvas 변환
                    val len = r * 0.22f

                    val x1 = cx + cos(rad).toFloat() * (r - len)
                    val y1 = cy + sin(rad).toFloat() * (r - len)
                    val x2 = cx + cos(rad).toFloat() * r
                    val y2 = cy + sin(rad).toFloat() * r

                    drawLine(
                        color = tickColor,
                        start = Offset(x1, y1),
                        end = Offset(x2, y2),
                        strokeWidth = 4f
                    )
                }

                // 바늘(삼각형)은 "위쪽"을 기본으로 그리고, -heading 만큼 회전
                // heading=0이면 위(북), heading=90이면 왼쪽(서쪽 방향이 북) -> 정상
                rotate(degrees = -normalize360(animatedHeading), pivot = Offset(cx, cy)) {
                    val needle = Path().apply {
                        val top = Offset(cx, cy - r * 0.85f)
                        val left = Offset(cx - 8f, cy)
                        val right = Offset(cx + 8f, cy)
                        moveTo(top.x, top.y)
                        lineTo(right.x, right.y)
                        lineTo(left.x, left.y)
                        close()
                    }
                    drawPath(needle, northColor)
                }

                // 중심점
                drawCircle(bg, 8f, Offset(cx, cy))
                drawCircle(centerStrokeColor, 8f, Offset(cx, cy), style = Stroke(width = 2f))
            }

            // 🔴 "N"은 항상 위(12시)에 고정
            Text(
                text = "N",
                color = northColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp)
            )
        }
    }
}

private fun normalize360(v: Float): Float = ((v % 360f) + 360f) % 360f
