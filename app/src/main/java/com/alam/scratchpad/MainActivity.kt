package com.alam.scratchpad

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.view.ViewCompat.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.alam.scratchpad.ui.theme.ScratchPadTheme
import java.io.OutputStream
import kotlin.math.roundToInt


class MainActivity : ComponentActivity() {
    private val drawingController = DrawingController(DrawingModel())
    private lateinit var drawingStorage: DrawingStorage

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val preferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
        drawingController.setDarkCanvas(preferences.getBoolean(PREF_DARK_CANVAS, true))

        drawingStorage = DrawingStorage(filesDir)
        runCatching { drawingStorage.load() }
            .onSuccess(drawingController::restoreDrawPaths)
            .onFailure { Log.e(TAG, "Could not load drawing", it) }

        // Can show the drawing behind the status/navigation bars
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView)

        // When using BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE and hiding system bars,
        // there is no limit to the amount of exclusion (otherwise only the bottom
        // part of the defined areas will be excluded).
        windowInsetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController?.hide(WindowInsetsCompat.Type.systemBars())

        windowInsetsController?.isAppearanceLightStatusBars = true

        setContent {
            ScratchPadTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(), //.systemBarsPadding(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    App(drawingController) { darkCanvas ->
                        preferences.edit().putBoolean(PREF_DARK_CANVAS, darkCanvas).apply()
                    }
                }
            }
        }
    }

    override fun onStop() {
        // ponytail: one atomic lifecycle write; add debounced background saves if large canvases make this slow.
        runCatching { drawingStorage.save(drawingController.getDrawPaths().toList()) }
            .onFailure { Log.e(TAG, "Could not save drawing", it) }
        super.onStop()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        drawingController.onOrientationChanged()
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFERENCES_NAME = "scratchpad_preferences"
        private const val PREF_DARK_CANVAS = "dark_canvas"
    }
}

@Composable
fun App(
    drawingController: DrawingController,
    onDarkCanvasChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val exporter = remember(drawingController) { DrawingExporter(drawingController) }
    val pngLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument()
    ) { uri -> export(context, uri, exporter::writePng) }
    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument()
    ) { uri -> export(context, uri, exporter::writePdf) }

    Box(modifier = Modifier.fillMaxSize()) {
        ScratchPadCanvas(drawingController = drawingController)
        OptionsMenu(
            iconTint = drawingController.getDisplayColor(Color.Black),
            onReverseColors = {
                onDarkCanvasChanged(drawingController.toggleDarkCanvas())
            },
            onExportPng = { pngLauncher.launch("scratchpad.png") },
            onExportPdf = { pdfLauncher.launch("scratchpad.pdf") },
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.TopEnd)
                .padding(8.dp)
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .mandatorySystemGesturesPadding()
        ) {
            var safeAreaSize by remember { mutableStateOf(IntSize.Zero) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { safeAreaSize = it.size }
            ) {
                FloatingToolRail(drawingController, safeAreaSize)
            }
        }
    }

//    if (AppSettings.BackButtonDisabled) {
//        val context = LocalContext.current
//        BackHandler {
//            val toast = Toast.makeText(context, "Back is disabled", Toast.LENGTH_SHORT)
//            toast.show()
//        }
//    }
}

@Composable
private fun FloatingToolRail(
    drawingController: DrawingController,
    safeAreaSize: IntSize,
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    var offsetX by rememberSaveable { mutableStateOf(0) }
    var offsetY by rememberSaveable { mutableStateOf(Int.MIN_VALUE) }
    var thicknessPickerVisible by remember { mutableStateOf(false) }
    var railSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val margin = with(density) { 12.dp.roundToPx() }
    val maxX = (safeAreaSize.width - railSize.width - margin).coerceAtLeast(margin)
    val maxY = (safeAreaSize.height - railSize.height - margin).coerceAtLeast(margin)

    LaunchedEffect(safeAreaSize, railSize) {
        if (safeAreaSize == IntSize.Zero || railSize == IntSize.Zero) return@LaunchedEffect
        offsetX = offsetX.coerceIn(margin, maxX)
        offsetY = if (offsetY == Int.MIN_VALUE) {
            ((safeAreaSize.height - railSize.height) / 2).coerceIn(margin, maxY)
        } else {
            offsetY.coerceIn(margin, maxY)
        }
    }

    val contentColor = drawingController.getDisplayColor(Color.Black)
    val penColor = drawingController.getDisplayColor(
        AppSettings.AvailableDrawingColors[drawingController.getDrawingColorIndex()]
    )
    val displayedY = if (offsetY == Int.MIN_VALUE) 0 else offsetY
    val dragModifier = Modifier.pointerInput(safeAreaSize, railSize) {
        detectDragGestures { change, dragAmount ->
            change.consume()
            offsetX = (offsetX + dragAmount.x.roundToInt()).coerceIn(margin, maxX)
            val currentY = if (offsetY == Int.MIN_VALUE) displayedY else offsetY
            offsetY = (currentY + dragAmount.y.roundToInt()).coerceIn(margin, maxY)
        }
    }

    androidx.compose.material3.Surface(
        modifier = Modifier
            .offset { IntOffset(offsetX, displayedY) }
            .onGloballyPositioned { railSize = it.size },
        color = Color.Transparent,
        shadowElevation = 0.dp,
    ) {
        Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            modifier = Modifier
                .width(56.dp)
                .padding(4.dp),
        ) {
            ToolRailButton(
                description = if (expanded) "Collapse toolbar" else "Expand toolbar",
                onClick = { expanded = !expanded },
                modifier = dragModifier,
            ) {
                Icon(
                    painter = painterResource(R.drawable.toolbar_toggle),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(26.dp),
                )
            }
            if (expanded) {
                Spacer(Modifier.height(2.dp))
                ToolRailButton(
                    description = "Pen",
                    onClick = {
                        if (drawingController.getDrawingMode() != DrawingMode.Pen) {
                            drawingController.setPenMode()
                        } else {
                            drawingController.cycleDrawingColor()
                        }
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.pen),
                        contentDescription = null,
                        tint = penColor,
                        modifier = Modifier.size(26.dp),
                    )
                }
                ToolRailButton(
                    description = "Stroke width ${drawingController.getSelectedStrokeWidth().roundToInt()}",
                    onClick = { thicknessPickerVisible = true },
                ) {
                    androidx.compose.material3.Text(
                        text = drawingController.getSelectedStrokeWidth().roundToInt().toString(),
                        color = contentColor,
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    if (thicknessPickerVisible) {
                        ThicknessPicker(
                            value = drawingController.getSelectedStrokeWidth(),
                            color = contentColor,
                            opensRight = offsetX + railSize.width / 2 < safeAreaSize.width / 2,
                            onValueChange = drawingController::setStrokeWidth,
                            onDismiss = { thicknessPickerVisible = false },
                        )
                    }
                }
                ToolRailButton(
                    description = "Eraser",
                    onClick = drawingController::setEraseMode,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.eraser),
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(26.dp),
                    )
                }
                ToolRailButton(
                    description = if (drawingController.getDrawingMode() == DrawingMode.Select) {
                        "Exit select and move"
                    } else {
                        "Select and move"
                    },
                    onClick = {
                        if (drawingController.getDrawingMode() == DrawingMode.Select) {
                            drawingController.setPenMode()
                        } else {
                            drawingController.setSelectMode()
                        }
                    },
                ) {
                    Box(
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                if (drawingController.getDrawingMode() == DrawingMode.Select) {
                                    contentColor.copy(alpha = 0.12f)
                                } else {
                                    Color.Transparent
                                },
                                RoundedCornerShape(12.dp),
                            ),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.lasso),
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .width(24.dp)
                        .height(1.dp)
                        .background(contentColor.copy(alpha = 0.14f))
                )
                Spacer(Modifier.height(6.dp))
                ToolRailButton(
                    description = "Clear All",
                    onClick = drawingController::clearAll,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.trash),
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolRailButton(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        contentAlignment = androidx.compose.ui.Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .then(modifier)
            .clip(RoundedCornerShape(14.dp))
            .semantics { contentDescription = description }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        content()
    }
}

@Composable
private fun ThicknessPicker(
    value: Float,
    color: Color,
    opensRight: Boolean,
    onValueChange: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val offset = with(density) { 60.dp.roundToPx() } * if (opensRight) 1 else -1
    val previewSize = with(density) { value.toDp() }
    val panelColor = if (color == Color.Black) Color(0xFFF7F7F5) else Color(0xFF181818)

    Popup(
        alignment = if (opensRight) {
            androidx.compose.ui.Alignment.CenterStart
        } else {
            androidx.compose.ui.Alignment.CenterEnd
        },
        offset = IntOffset(offset, 0),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(18.dp),
            color = panelColor,
            contentColor = color,
            border = BorderStroke(1.dp, color.copy(alpha = 0.10f)),
            shadowElevation = 6.dp,
        ) {
            Column(
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(240.dp)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                Box(
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                    modifier = Modifier.size(34.dp),
                ) {
                    Box(
                        Modifier
                            .size(previewSize)
                            .background(color, CircleShape)
                    )
                }
                androidx.compose.material3.Slider(
                    value = value,
                    onValueChange = { onValueChange(it.roundToInt().toFloat()) },
                    valueRange = AppSettings.MinStrokeWidth..AppSettings.MaxStrokeWidth,
                    steps = (AppSettings.MaxStrokeWidth - AppSettings.MinStrokeWidth).roundToInt() - 1,
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = color,
                        activeTrackColor = color,
                        inactiveTrackColor = color.copy(alpha = 0.18f),
                    ),
                )
                androidx.compose.material3.Text(
                    text = value.roundToInt().toString(),
                    color = color,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun OptionsMenu(
    iconTint: Color,
    onReverseColors: () -> Unit,
    onExportPng: () -> Unit,
    onExportPdf: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val menuColor = if (iconTint == Color.Black) Color(0xFFF7F7F5) else Color(0xFF181818)
    val menuOffset = with(LocalDensity.current) { 48.dp.roundToPx() }

    Box(modifier = modifier) {
        androidx.compose.material3.IconButton(onClick = { expanded = true }) {
            androidx.compose.material3.Icon(
                painter = painterResource(R.drawable.more_vert),
                contentDescription = "Options",
                tint = iconTint,
            )
        }
        if (expanded) {
            Popup(
                alignment = androidx.compose.ui.Alignment.TopEnd,
                offset = IntOffset(0, menuOffset),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                androidx.compose.material3.Surface(
                    modifier = Modifier.width(152.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = menuColor,
                    contentColor = iconTint,
                    border = BorderStroke(1.dp, iconTint.copy(alpha = 0.10f)),
                    shadowElevation = 6.dp,
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(6.dp),
                    ) {
                        MinimalMenuItem("Reverse colors", iconTint) {
                            expanded = false
                            onReverseColors()
                        }
                        MinimalMenuItem("Export PNG", iconTint) {
                            expanded = false
                            onExportPng()
                        }
                        MinimalMenuItem("Export PDF", iconTint) {
                            expanded = false
                            onExportPdf()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MinimalMenuItem(label: String, color: Color, onClick: () -> Unit) {
    Box(
        contentAlignment = androidx.compose.ui.Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
    ) {
        androidx.compose.material3.Text(
            text = label,
            color = color,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun export(context: Context, uri: Uri?, write: (OutputStream) -> Unit) {
    if (uri == null) return
    runCatching {
        context.contentResolver.openOutputStream(uri)?.use(write)
            ?: error("Could not open export file")
    }.onSuccess {
        Toast.makeText(context, "Exported", Toast.LENGTH_SHORT).show()
    }.onFailure {
        Log.e("ScratchPadExport", "Could not export drawing", it)
        Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ScratchPadCanvas(
    drawingController: DrawingController
) {
    val drawingPointer = remember { DrawingPointerTracker() }
    val lassoPoints = remember { mutableStateListOf<Offset>() }
    var selection by remember { mutableStateOf<Rect?>(null) }
    var selectedPaths by remember { mutableStateOf(emptySet<Int>()) }
    var lastMovePoint by remember { mutableStateOf<Offset?>(null) }
    val transformState = rememberTransformableState { scalingDelta, offsetDelta, _ ->
        drawingController.updateScale(scalingDelta)
        drawingController.updateOffset(offsetDelta)
    }

    val size = drawingController.getSize()
    val scale = drawingController.getScale()
    val offset = drawingController.getOffset()
    val drawingBackground = drawingController.getDrawingBackground()
    val drawingColor = drawingController.getDrawingColor()
    val strokeWidth = drawingController.getStrokeWidth()
    val drawingMode = drawingController.getDrawingMode()

    LaunchedEffect(drawingMode) {
        if (drawingMode != DrawingMode.Select) {
            lassoPoints.clear()
            selection = null
            selectedPaths = emptySet()
            lastMovePoint = null
        }
    }

    Canvas(
        modifier = Modifier
            .clip(RectangleShape)
            .fillMaxSize()
            .background(drawingBackground)
            .onGloballyPositioned {
                drawingController.setFullSize(it.size.toSize())
            }
            .systemGestureExclusion {
                // Exclude left side
                Rect(
                    Offset(0f, 0f),
                    Offset(200f, it.size.height.toFloat())
                )
            }
            .systemGestureExclusion {
                // Exclude right side
                Rect(
                    Offset(it.size.width.toFloat() - 200f, 0f),
                    Offset(it.size.width.toFloat(), it.size.height.toFloat())
                )
            }
            .mandatorySystemGesturesPadding()
            .onGloballyPositioned {
                drawingController.setSize(it.size.toSize())
            }
            .pointerInteropFilter { event ->
                val actionIndex = event.actionIndex
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                        val previousPointerId = drawingPointer.pointerId
                        if (drawingPointer.onPointerDown(
                                event.getPointerId(actionIndex),
                                event.getToolType(actionIndex),
                                event.buttonState,
                            )
                        ) {
                            if (previousPointerId != MotionEvent.INVALID_POINTER_ID) {
                                drawingController.clearPoints()
                            }
                            if (drawingPointer.isEraser) {
                                drawingController.setEraseMode()
                            }
                            val point = drawingController.getMappedOffset(
                                Offset(event.getX(actionIndex), event.getY(actionIndex))
                            )
                            if (drawingController.getDrawingMode() == DrawingMode.Select) {
                                val currentSelection = selection
                                if (currentSelection != null && selectedPaths.isNotEmpty() &&
                                    currentSelection.contains(point)
                                ) {
                                    lastMovePoint = point
                                } else {
                                    lassoPoints.clear()
                                    lassoPoints.add(point)
                                    selection = null
                                    selectedPaths = emptySet()
                                    lastMovePoint = null
                                }
                            } else {
                                drawingController.addPoint(point)
                            }
                        } else if (!drawingPointer.isStylus) {
                            drawingPointer.clear()
                            drawingController.clearPoints()
                            lassoPoints.clear()
                            lastMovePoint = null
                            if (selectedPaths.isEmpty()) selection = null
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val pointerIndex = event.findPointerIndex(drawingPointer.pointerId)
                        if (pointerIndex >= 0) {
                            val point = drawingController.getMappedOffset(
                                Offset(event.getX(pointerIndex), event.getY(pointerIndex))
                            )
                            if (drawingController.getDrawingMode() == DrawingMode.Select) {
                                val previous = lastMovePoint
                                if (previous != null) {
                                    val amount = point - previous
                                    drawingController.movePaths(selectedPaths, amount)
                                    selection = selection?.moveBy(amount)
                                    lastMovePoint = point
                                } else {
                                    lassoPoints.add(point)
                                }
                            } else {
                                drawingController.addPoint(point)
                            }
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                        if (drawingPointer.isActive(event.getPointerId(actionIndex))) {
                            val canceled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                event.actionMasked == MotionEvent.ACTION_POINTER_UP &&
                                event.flags and MotionEvent.FLAG_CANCELED != 0
                            if (canceled) {
                                drawingController.clearPoints()
                            } else if (drawingController.getDrawingMode() == DrawingMode.Select) {
                                val point = drawingController.getMappedOffset(
                                    Offset(event.getX(actionIndex), event.getY(actionIndex))
                                )
                                val previous = lastMovePoint
                                if (previous != null) {
                                    val amount = point - previous
                                    drawingController.movePaths(selectedPaths, amount)
                                    selection = selection?.moveBy(amount)
                                } else {
                                    lassoPoints.add(point)
                                    selectedPaths = drawingController.getPathsIn(lassoPoints)
                                    selection = drawingController.getBounds(selectedPaths)
                                }
                            } else {
                                drawingController.addPoint(
                                    drawingController.getMappedOffset(
                                        Offset(event.getX(actionIndex), event.getY(actionIndex))
                                    )
                                )
                                drawingController.addPointsToPaths()
                            }
                            lassoPoints.clear()
                            lastMovePoint = null
                            if (selectedPaths.isEmpty()) selection = null
                            drawingPointer.clear()
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        drawingPointer.clear()
                        drawingController.clearPoints()
                        lassoPoints.clear()
                        lastMovePoint = null
                        if (selectedPaths.isEmpty()) selection = null
                    }
                }

                true
            }
            .transformable(state = transformState)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y,
                transformOrigin = TransformOrigin(
                    0.5f - offset.x / size.width,
                    0.5f - offset.y / size.height
                )
            )
    ) {
        fun drawStroke(path: Path, color: Color, width: Float) {
            drawPath(
                color = color,
                path = path,
                style = Stroke(
                    width = width,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                ),
            )
        }

        // draw existing paths
        for (path in drawingController.getDrawPaths()) {
            drawStroke(
                path.path,
                drawingController.getDisplayColor(path.color),
                path.strokeWidth
            )
        }

        // draw the current path from the points still being smoothed and added to
        drawStroke(
            drawingController.getPointsPath(),
            drawingController.getDisplayColor(drawingColor),
            strokeWidth
        )

        if (drawingMode == DrawingMode.Select) {
            val selectionColor = Color(0xFF3D7DFF)
            if (lassoPoints.size > 1) {
                val lasso = Path().apply {
                    lassoPoints.forEachIndexed { index, point ->
                        if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                    }
                }
                drawPath(
                    lasso,
                    selectionColor,
                    style = Stroke(width = 2.dp.toPx() / scale),
                )
            }
            selection?.let { selectedArea ->
                drawRect(
                    selectionColor.copy(alpha = 0.10f),
                    topLeft = selectedArea.topLeft,
                    size = selectedArea.size,
                )
                drawRect(
                    selectionColor,
                    topLeft = selectedArea.topLeft,
                    size = selectedArea.size,
                    style = Stroke(width = 2.dp.toPx() / scale),
                )
            }
        }
    }
}

private fun Rect.moveBy(amount: Offset) = Rect(
    left + amount.x,
    top + amount.y,
    right + amount.x,
    bottom + amount.y,
)

internal class DrawingPointerTracker {
    var pointerId = MotionEvent.INVALID_POINTER_ID
        private set
    var isStylus = false
        private set
    var isEraser = false
        private set

    fun onPointerDown(pointerId: Int, toolType: Int, buttonState: Int = 0): Boolean {
        val candidateIsStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS ||
            toolType == MotionEvent.TOOL_TYPE_ERASER
        if (this.pointerId != MotionEvent.INVALID_POINTER_ID &&
            (!candidateIsStylus || isStylus)
        ) {
            return false
        }
        this.pointerId = pointerId
        isStylus = candidateIsStylus
        isEraser = toolType == MotionEvent.TOOL_TYPE_ERASER ||
            candidateIsStylus && buttonState and STYLUS_BUTTONS != 0
        return true
    }

    fun isActive(pointerId: Int) = pointerId == this.pointerId

    fun clear() {
        pointerId = MotionEvent.INVALID_POINTER_ID
        isStylus = false
        isEraser = false
    }

    companion object {
        private const val STYLUS_BUTTONS = MotionEvent.BUTTON_STYLUS_PRIMARY or
            MotionEvent.BUTTON_STYLUS_SECONDARY or MotionEvent.BUTTON_SECONDARY or
            MotionEvent.BUTTON_TERTIARY
    }
}
