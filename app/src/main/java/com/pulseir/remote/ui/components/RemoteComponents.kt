package com.pulseir.remote.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DevicesOther
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulseir.remote.R
import com.pulseir.remote.ir.IrButton
import com.pulseir.remote.ir.IrProtocolEncoder
import com.pulseir.remote.ir.IrSignal
import com.pulseir.remote.ir.TransmitPulse
import com.pulseir.remote.ui.theme.PulseColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Ambient top-bezel infrared emitter glow that fires toward the physical top IR LED
 * on every transmission, giving tactile visual feedback without stealing any screen space.
 */
@Composable
fun TopIrBeamGlow(
    lastPulse: TransmitPulse?,
    modifier: Modifier = Modifier,
) {
    var active by remember { mutableStateOf(false) }
    LaunchedEffect(lastPulse?.timestampMs) {
        if (lastPulse != null) {
            active = true
            delay(360)
            active = false
        }
    }
    val alpha by animateFloatAsState(
        targetValue = if (active) 0.55f else 0f,
        animationSpec = tween(if (active) 90 else 320),
        label = "beamAlpha",
    )
    if (alpha > 0.01f) {
        Canvas(
            modifier = modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        PulseColors.Crimson.copy(alpha = alpha),
                        PulseColors.Cyan.copy(alpha = alpha * 0.35f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width / 2f, 0f),
                    radius = size.width * 0.42f,
                ),
                radius = size.width * 0.42f,
                center = Offset(size.width / 2f, 0f),
            )
        }
    }
}

/**
 * Keeps the screen awake while the composable is active (e.g. during Smart Scan or Remote control).
 */
@Composable
fun KeepScreenOnEffect(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(context, enabled) {
        val activity = context.findActivity()
        if (enabled) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (enabled) {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Live IR Blaster LED indicator pill that glows crimson whenever an IR burst fires.
 */
@Composable
fun IrLedStatusPill(
    hasIrEmitter: Boolean,
    lastPulse: TransmitPulse?,
    modifier: Modifier = Modifier,
) {
    var flashing by remember { mutableStateOf(false) }
    LaunchedEffect(lastPulse?.timestampMs) {
        if (lastPulse != null) {
            flashing = true
            delay(650)
            flashing = false
        }
    }

    val dotColor by animateColorAsState(
        targetValue = when {
            flashing -> PulseColors.Crimson
            hasIrEmitter -> PulseColors.Emerald
            else -> PulseColors.Amber
        },
        animationSpec = tween(140),
        label = "irDot",
    )
    val dotScale by animateFloatAsState(
        targetValue = if (flashing) 1.5f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 520f),
        label = "irDotScale",
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            width = 1.dp,
            color = if (flashing) PulseColors.Crimson.copy(alpha = 0.75f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(dotScale)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Text(
                text = if (flashing && lastPulse != null) {
                    lastPulse.badge
                } else if (hasIrEmitter) {
                    stringResource(R.string.ir_ready)
                } else {
                    stringResource(R.string.ir_demo_mode)
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (flashing) PulseColors.Crimson else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Machined tactile hardware-like IR button with subtle top bevel sheen,
 * spring press physics, press-and-hold auto-repeat, and optional long-press signal inspection.
 */
@Composable
fun TactileRemoteButton(
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    repeatable: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    height: Dp = 54.dp,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.52f, stiffness = 750f),
        label = "btnScale",
    )
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val scope = rememberCoroutineScope()

    val effectiveBg = if (enabled) containerColor else containerColor.copy(alpha = 0.28f)
    val effectiveFg = if (enabled) contentColor else contentColor.copy(alpha = 0.28f)

    val rimBrush = if (pressed) {
        Brush.verticalGradient(listOf(PulseColors.Crimson.copy(alpha = 0.9f), PulseColors.Cyan.copy(alpha = 0.5f)))
    } else if (enabled) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.14f),
                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                Color.Black.copy(alpha = 0.18f),
            ),
        )
    } else {
        Brush.verticalGradient(listOf(MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), Color.Transparent))
    }

    val surfaceSheen = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (enabled && !pressed) 0.06f else 0f),
            Color.Transparent,
            Color.Black.copy(alpha = if (enabled && !pressed) 0.08f else 0f),
        ),
    )

    Box(
        modifier = modifier
            .height(height)
            .scale(scale)
            .clip(shape)
            .background(effectiveBg)
            .background(surfaceSheen)
            .border(width = 1.dp, brush = rimBrush, shape = shape)
            .pointerInput(enabled, repeatable, onLongPress != null) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        pressed = true
                        var longTriggered = false
                        if (repeatable) {
                            currentOnPress()
                            val repeatJob = scope.launch {
                                delay(380)
                                while (true) {
                                    currentOnPress()
                                    delay(145)
                                }
                            }
                            tryAwaitRelease()
                            repeatJob.cancel()
                        } else if (currentOnLongPress != null) {
                            val longJob = scope.launch {
                                delay(520)
                                longTriggered = true
                                currentOnLongPress?.invoke()
                            }
                            tryAwaitRelease()
                            longJob.cancel()
                            if (!longTriggered) currentOnPress()
                        } else {
                            currentOnPress()
                            tryAwaitRelease()
                        }
                        pressed = false
                    },
                )
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null && label != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(icon, contentDescription = label, tint = effectiveFg, modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(2.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = effectiveFg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else if (icon != null) {
            Icon(icon, contentDescription = label, tint = effectiveFg, modifier = Modifier.size(24.dp))
        } else if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = effectiveFg,
                maxLines = 2,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Ergonomic machined vertical capsule rocker (Volume +/- or Channel +/-) with hold-to-repeat.
 */
@Composable
fun RockerControl(
    title: String,
    onUp: (() -> Unit)?,
    onDown: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(38.dp)
    Box(
        modifier = modifier
            .width(74.dp)
            .height(180.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.06f), Color.Transparent, Color.Black.copy(alpha = 0.1f)),
                ),
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.14f), MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                ),
                shape,
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            RockerHalf(
                icon = Icons.Rounded.Add,
                contentDesc = "$title +",
                enabled = onUp != null,
                onTrigger = { onUp?.invoke() },
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RockerHalf(
                icon = Icons.Rounded.Remove,
                contentDesc = "$title -",
                enabled = onDown != null,
                onTrigger = { onDown?.invoke() },
            )
        }
    }
}

@Composable
private fun RockerHalf(
    icon: ImageVector,
    contentDesc: String,
    enabled: Boolean,
    onTrigger: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.87f else 1f, label = "rockerScale")
    val latestTrigger by rememberUpdatedState(onTrigger)
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .size(60.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(if (pressed) PulseColors.Crimson.copy(alpha = 0.24f) else Color.Transparent)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        pressed = true
                        latestTrigger()
                        val job = scope.launch {
                            delay(340)
                            while (true) {
                                latestTrigger()
                                delay(135)
                            }
                        }
                        tryAwaitRelease()
                        job.cancel()
                        pressed = false
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
            modifier = Modifier.size(26.dp),
        )
    }
}

/**
 * Sleek circular machined D-Pad strictly locked to physical LTR geometry so Left is
 * ALWAYS physically left and Right is ALWAYS physically right, even in Arabic RTL mode!
 */
@Composable
fun CircularDPad(
    onUp: (() -> Unit)?,
    onDown: (() -> Unit)?,
    onLeft: (() -> Unit)?,
    onRight: (() -> Unit)?,
    onOk: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        val size = 198.dp
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.06f), Color.Transparent, Color.Black.copy(alpha = 0.12f)),
                    ),
                )
                .border(
                    1.2.dp,
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.16f), MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                    ),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            DPadArrow(
                icon = Icons.Rounded.KeyboardArrowUp,
                desc = "Up",
                enabled = onUp != null,
                onTrigger = { onUp?.invoke() },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            )
            DPadArrow(
                icon = Icons.Rounded.KeyboardArrowDown,
                desc = "Down",
                enabled = onDown != null,
                onTrigger = { onDown?.invoke() },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            )
            DPadArrow(
                icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                desc = "Left",
                enabled = onLeft != null,
                onTrigger = { onLeft?.invoke() },
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
            )
            DPadArrow(
                icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                desc = "Right",
                enabled = onRight != null,
                onTrigger = { onRight?.invoke() },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
            )

            TactileRemoteButton(
                onPress = { onOk?.invoke() },
                label = "OK",
                enabled = onOk != null,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
                height = 72.dp,
                modifier = Modifier.size(72.dp),
            )
        }
    }
}

@Composable
private fun DPadArrow(
    icon: ImageVector,
    desc: String,
    enabled: Boolean,
    onTrigger: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val latest by rememberUpdatedState(onTrigger)
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(if (pressed) PulseColors.Cyan.copy(alpha = 0.24f) else Color.Transparent)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        pressed = true
                        latest()
                        val job = scope.launch {
                            delay(340)
                            while (true) {
                                latest()
                                delay(135)
                            }
                        }
                        tryAwaitRelease()
                        job.cancel()
                        pressed = false
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = desc,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
            modifier = Modifier.size(30.dp),
        )
    }
}

/**
 * Live Infrared Pulse Train Waveform visualizer rendered with Compose Canvas (strictly LTR time axis).
 */
@Composable
fun WaveformCanvas(
    patternUs: IntArray,
    modifier: Modifier = Modifier,
) {
    val lineColor = PulseColors.Cyan
    val fillColor = PulseColors.Cyan.copy(alpha = 0.18f)
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(82.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 10.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val highY = h * 0.16f
                val lowY = h * 0.86f

                drawLine(gridColor, Offset(0f, lowY), Offset(w, lowY), strokeWidth = 1f)
                drawLine(gridColor.copy(alpha = 0.45f), Offset(0f, highY), Offset(w, highY), strokeWidth = 1f)

                if (patternUs.isEmpty()) return@Canvas

                val sliceCount = minOf(patternUs.size, 96)
                var total = 0L
                for (i in 0 until sliceCount) total += patternUs[i].coerceAtMost(20_000)
                if (total <= 0L) return@Canvas

                val path = Path()
                val fillPath = Path()
                var x = 0f
                path.moveTo(0f, lowY)
                fillPath.moveTo(0f, lowY)

                for (i in 0 until sliceCount) {
                    val isMark = (i % 2 == 0)
                    val dur = patternUs[i].coerceAtMost(20_000).toFloat()
                    val segW = (dur / total.toFloat()) * w
                    val y = if (isMark) highY else lowY
                    path.lineTo(x, y)
                    path.lineTo(x + segW, y)
                    fillPath.lineTo(x, y)
                    fillPath.lineTo(x + segW, y)
                    x += segW
                }
                path.lineTo(w, lowY)
                fillPath.lineTo(w, lowY)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(listOf(fillColor, Color.Transparent)),
                )
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

/**
 * Hidden-power Signal Inspector modal triggered by long-pressing any remote button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalInspectorSheet(
    button: IrButton,
    onTest: () -> Unit,
    onSaveToCustom: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val encoded = remember(button) { IrProtocolEncoder.encode(button.signal, toggleOverride = false) }
    val totalMs = remember(encoded) { encoded.patternUs.sum() / 1000.0 }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(stringResource(R.string.signal_inspector_title), style = MaterialTheme.typography.labelLarge, color = PulseColors.Cyan)
                    Text(button.name, style = MaterialTheme.typography.titleLarge)
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = PulseColors.Cyan.copy(alpha = 0.14f),
                ) {
                    Text(
                        text = "${encoded.frequencyHz / 1000.0} kHz",
                        style = MaterialTheme.typography.labelLarge,
                        color = PulseColors.Cyan,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = when (val s = button.signal) {
                            is IrSignal.Parsed -> s.summary()
                            is IrSignal.Raw -> s.summary()
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${encoded.patternUs.size} pulses · ${"%.1f".format(totalMs)} ms burst",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            WaveformCanvas(patternUs = encoded.patternUs)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onTest,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.test))
                }
                Button(
                    onClick = {
                        onSaveToCustom()
                        Toast.makeText(context, context.getString(R.string.saved_to_custom_ir), Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1.3f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PulseColors.Emerald),
                ) {
                    Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.save_to_custom_ir), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}

fun categoryIcon(categoryId: String): ImageVector = when (categoryId) {
    "tv" -> Icons.Rounded.Tv
    "ac" -> Icons.Rounded.AcUnit
    "box" -> Icons.Rounded.Router
    "av" -> Icons.Rounded.SurroundSound
    "soundbar" -> Icons.Rounded.SpeakerGroup
    "speaker" -> Icons.Rounded.Speaker
    "projector" -> Icons.Rounded.Videocam
    "fan" -> Icons.Rounded.Air
    "led" -> Icons.Rounded.Lightbulb
    "streaming" -> Icons.Rounded.Cast
    "dvd", "audio" -> Icons.Rounded.Album
    "camera" -> Icons.Rounded.CameraAlt
    "climate" -> Icons.Rounded.Whatshot
    "vacuum" -> Icons.Rounded.CleaningServices
    "monitor" -> Icons.Rounded.Computer
    "car" -> Icons.Rounded.DirectionsCar
    "console" -> Icons.Rounded.SportsEsports
    "custom" -> Icons.Rounded.SettingsRemote
    else -> Icons.Rounded.DevicesOther
}

fun categoryAccentColor(categoryId: String): Color = when (categoryId) {
    "tv" -> PulseColors.Crimson
    "ac" -> PulseColors.Cyan
    "fan" -> Color(0xFF38BDF8)
    "led" -> Color(0xFFF59E0B)
    "box", "streaming" -> PulseColors.Violet
    "av", "soundbar", "speaker" -> PulseColors.Emerald
    "projector" -> Color(0xFFEC4899)
    "climate" -> Color(0xFFF97316)
    else -> Color(0xFF64748B)
}

@Composable
fun roomDisplayName(roomId: String): String = when (roomId) {
    "living" -> stringResource(R.string.room_living)
    "bedroom" -> stringResource(R.string.room_bedroom)
    "office" -> stringResource(R.string.room_office)
    else -> stringResource(R.string.room_other)
}

val ALL_ROOMS = listOf("living", "bedroom", "office", "other")
