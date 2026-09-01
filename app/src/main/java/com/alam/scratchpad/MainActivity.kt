package com.alam.scratchpad

import android.annotation.SuppressLint
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.core.view.ViewCompat.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.alam.scratchpad.ui.theme.ScratchPadTheme
import java.io.OutputStream


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

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterialScaffoldPaddingParameter", "UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun App(
    drawingController: DrawingController,
    onDarkCanvasChanged: (Boolean) -> Unit = {},
) {
    var toolbarVisible by rememberSaveable { mutableStateOf(true) }
    val context = LocalContext.current
    val exporter = remember(drawingController) { DrawingExporter(drawingController) }
    val pngLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument()
    ) { uri -> export(context, uri, exporter::writePng) }
    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument()
    ) { uri -> export(context, uri, exporter::writePdf) }

    Scaffold(
        floatingActionButtonPosition = if (toolbarVisible) FabPosition.Center else FabPosition.End,
        floatingActionButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier
                    .alpha(0.7f)
                    .mandatorySystemGesturesPadding()
            ) {
                val buttonContentColor = drawingController.getDisplayColor(Color.Black)
                val buttonElevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp,
                )

                if (toolbarVisible) {
                    val penColor = drawingController.getDisplayColor(
                        AppSettings.AvailableDrawingColors[drawingController.getDrawingColorIndex()]
                    )
                    FloatingActionButton(
                        onClick = {
                            if (drawingController.getDrawingMode() != DrawingMode.Pen) {
                                drawingController.setPenMode()
                            } else {
                                drawingController.cycleDrawingColor()
                            }
                        },
                        containerColor = Color.Transparent,
                        contentColor = buttonContentColor,
                        elevation = buttonElevation,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.pen),
                            contentDescription = "Pen",
                            modifier = Modifier.size(26.dp),
                            tint = penColor
                        )
                    }
                    FloatingActionButton(
                        onClick = {
                            drawingController.cycleStrokeWidth()
                        },
                        containerColor = Color.Transparent,
                        contentColor = buttonContentColor,
                        elevation = buttonElevation,
                    ) {
                        val sizeIcons = listOf(R.drawable.size1, R.drawable.size2)
                        Icon(
                            painter = painterResource(sizeIcons[drawingController.getStrokeWidthIndex()]),
                            contentDescription = "Stroke Width",
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    FloatingActionButton(
                        onClick = {
                            drawingController.setEraseMode()
                        },
                        containerColor = Color.Transparent,
                        contentColor = buttonContentColor,
                        elevation = buttonElevation,
                    ) {
                        Icon(
                            painterResource(R.drawable.eraser),
                            contentDescription = "Eraser",
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    FloatingActionButton(
                        onClick = {
                            drawingController.clearAll()
                        },
                        containerColor = Color.Transparent,
                        contentColor = buttonContentColor,
                        elevation = buttonElevation,
                    ) {
                        Icon(
                            painterResource(R.drawable.trash),
                            contentDescription = "Clear All",
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                androidx.compose.material3.SmallFloatingActionButton(
                    onClick = { toolbarVisible = !toolbarVisible },
                    containerColor = Color.Transparent,
                    contentColor = buttonContentColor,
                    elevation = buttonElevation,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.toolbar_toggle),
                        contentDescription = if (toolbarVisible) "Hide toolbar" else "Show toolbar",
                        modifier = Modifier
                            .size(26.dp)
                            .rotate(if (toolbarVisible) 0f else 180f)
                    )
                }
            }
        },
        content = {
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
            }
        }
    )

//    if (AppSettings.BackButtonDisabled) {
//        val context = LocalContext.current
//        BackHandler {
//            val toast = Toast.makeText(context, "Back is disabled", Toast.LENGTH_SHORT)
//            toast.show()
//        }
//    }
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

    Box(modifier = modifier) {
        androidx.compose.material3.IconButton(onClick = { expanded = true }) {
            androidx.compose.material3.Icon(
                painter = painterResource(R.drawable.more_vert),
                contentDescription = "Options",
                tint = iconTint,
            )
        }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            androidx.compose.material3.DropdownMenuItem(
                text = { androidx.compose.material3.Text("Reverse colors") },
                onClick = {
                    expanded = false
                    onReverseColors()
                },
            )
            androidx.compose.material3.DropdownMenuItem(
                text = { androidx.compose.material3.Text("Export PNG") },
                onClick = {
                    expanded = false
                    onExportPng()
                },
            )
            androidx.compose.material3.DropdownMenuItem(
                text = { androidx.compose.material3.Text("Export PDF") },
                onClick = {
                    expanded = false
                    onExportPdf()
                },
            )
        }
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
                            drawingController.addPoint(
                                drawingController.getMappedOffset(
                                    Offset(event.getX(actionIndex), event.getY(actionIndex))
                                )
                            )
                        } else if (!drawingPointer.isStylus) {
                            drawingPointer.clear()
                            drawingController.clearPoints()
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val pointerIndex = event.findPointerIndex(drawingPointer.pointerId)
                        if (pointerIndex >= 0) {
                            drawingController.addPoint(
                                drawingController.getMappedOffset(
                                    Offset(event.getX(pointerIndex), event.getY(pointerIndex))
                                )
                            )
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                        if (drawingPointer.isActive(event.getPointerId(actionIndex))) {
                            val canceled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                event.actionMasked == MotionEvent.ACTION_POINTER_UP &&
                                event.flags and MotionEvent.FLAG_CANCELED != 0
                            if (canceled) {
                                drawingController.clearPoints()
                            } else {
                                drawingController.addPoint(
                                    drawingController.getMappedOffset(
                                        Offset(event.getX(actionIndex), event.getY(actionIndex))
                                    )
                                )
                                drawingController.addPointsToPaths()
                            }
                            drawingPointer.clear()
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        drawingPointer.clear()
                        drawingController.clearPoints()
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
    }
}

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
