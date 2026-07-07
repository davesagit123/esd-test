package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.DataPointEntity
import com.example.data.DatasetEntity
import com.example.stats.EsdResult
import com.example.stats.EsdStepResult
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EsdScreen(
    viewModel: EsdViewModel,
    modifier: Modifier = Modifier
) {
    val datasets by viewModel.datasets.collectAsStateWithLifecycle()
    val selectedDatasetId by viewModel.selectedDatasetId.collectAsStateWithLifecycle()
    val currentPoints by viewModel.currentPoints.collectAsStateWithLifecycle()
    val alpha by viewModel.alpha.collectAsStateWithLifecycle()
    val maxOutliers by viewModel.maxOutliers.collectAsStateWithLifecycle()
    val selectedFeature by viewModel.selectedFeature.collectAsStateWithLifecycle()
    val esdResult by viewModel.esdResult.collectAsStateWithLifecycle()
    val oneStepScores by viewModel.oneStepScores.collectAsStateWithLifecycle()

    var showAddDatasetDialog by remember { mutableStateOf(false) }
    var showAddPointDialog by remember { mutableStateOf(false) }
    var editingPoint by remember { mutableStateOf<DataPointEntity?>(null) }
    var showDeleteConfirmDatasetId by remember { mutableStateOf<Long?>(null) }

    val currentDataset = datasets.find { it.id == selectedDatasetId }
    val hasPc2 = currentPoints.any { it.value2 != null }

    Scaffold(
        modifier = modifier.testTag("esd_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = "Analytics Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Column {
                            Text(
                                text = "ESD Outlier Detector",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                            Text(
                                text = "Rosner & NIST Statistical Method",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                )
            )
        },
        floatingActionButton = {
            if (selectedDatasetId != null) {
                ExtendedFloatingActionButton(
                    onClick = { showAddPointDialog = true },
                    icon = { Icon(Icons.Default.Add, "Add Data Point") },
                    text = { Text("Add Row") },
                    modifier = Modifier.testTag("add_row_fab"),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Dataset & Column Selector
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Dataset Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Dropdown for selecting dataset
                    var dropdownExpanded by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { dropdownExpanded = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dataset_dropdown_button"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = currentDataset?.name ?: "Select Dataset...",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Dropdown"
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false }
                            ) {
                                datasets.forEach { ds ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(ds.name)
                                                // Don't allow deleting seeded datasets easily
                                                if (ds.name != "Horse Racing Ratings" && ds.name != "Outliers Demo Dataset") {
                                                    IconButton(
                                                        onClick = {
                                                            showDeleteConfirmDatasetId = ds.id
                                                            dropdownExpanded = false
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "Delete Dataset",
                                                            tint = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        onClick = {
                                            viewModel.selectDataset(ds.id)
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { showAddDatasetDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("new_dataset_button")
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = "New Dataset")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New")
                        }
                    }

                    if (hasPc2) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Target Variable",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val isPc1Selected = selectedFeature == "PC1"
                            FilterChip(
                                selected = isPc1Selected,
                                onClick = { viewModel.selectFeature("PC1") },
                                label = { Text("PC1 Ratings") },
                                modifier = Modifier.weight(1f).testTag("chip_pc1"),
                                leadingIcon = if (isPc1Selected) {
                                    { Icon(Icons.Default.Check, "Selected") }
                                } else null
                            )
                            FilterChip(
                                selected = !isPc1Selected,
                                onClick = { viewModel.selectFeature("PC2") },
                                label = { Text("PC2 Ratings") },
                                modifier = Modifier.weight(1f).testTag("chip_pc2"),
                                leadingIcon = if (!isPc1Selected) {
                                    { Icon(Icons.Default.Check, "Selected") }
                                } else null
                            )
                        }
                    }
                }
            }

            // Section 2: Parameters (Alpha & Max Outliers)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Statistical Parameters",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Significance Level Alpha
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Significance Level (α): $alpha",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        // Preset alphas
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(0.01, 0.05, 0.10).forEach { preset ->
                                InputChip(
                                    selected = alpha == preset,
                                    onClick = { viewModel.setAlpha(preset) },
                                    label = { Text(preset.toString()) },
                                    modifier = Modifier.testTag("alpha_chip_$preset")
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Max Outliers Slider
                    val size = currentPoints.size
                    val maxAllowedOutliers = if (size > 2) size - 2 else 1
                    Text(
                        text = "Max Outliers (k): $maxOutliers (limit: $maxAllowedOutliers)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = maxOutliers.toFloat(),
                        onValueChange = { viewModel.setMaxOutliers(it.toInt()) },
                        valueRange = 1f..max(1, maxAllowedOutliers).toFloat(),
                        steps = if (maxAllowedOutliers > 1) maxAllowedOutliers - 1 else 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("max_outliers_slider")
                    )
                }
            }

            // Section 3: Visualizer (Custom Canvas Plot)
            if (currentPoints.isNotEmpty()) {
                EsdVisualizerPlot(
                    points = currentPoints,
                    feature = selectedFeature,
                    esdResult = esdResult,
                    oneStepScores = oneStepScores
                )
            }

            // Section 4: Tabbed Output Summaries
            var activeTab by remember { mutableStateOf(0) }
            val tabs = listOf("Data Table", "ESD Steps Summary")

            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = activeTab == index,
                            onClick = { activeTab = index },
                            text = { Text(title, fontWeight = FontWeight.Bold) },
                            modifier = Modifier.testTag("output_tab_$index")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                AnimatedContent(
                    targetState = activeTab,
                    label = "TabContentAnimation"
                ) { tab ->
                    when (tab) {
                        0 -> DataTableTab(
                            points = currentPoints,
                            feature = selectedFeature,
                            oneStepScores = oneStepScores,
                            esdResult = esdResult,
                            onEditPoint = { editingPoint = it },
                            onDeletePoint = { viewModel.deleteDataPoint(it.id) }
                        )
                        1 -> EsdSummaryTab(
                            esdResult = esdResult
                        )
                    }
                }
            }
        }
    }

    // Dialogs Manager
    if (showAddDatasetDialog) {
        AddDatasetDialog(
            onDismiss = { showAddDatasetDialog = false },
            onConfirm = { name ->
                viewModel.addNewDataset(name)
                showAddDatasetDialog = false
            }
        )
    }

    if (showAddPointDialog) {
        val nextTab = (currentPoints.maxOfOrNull { it.tab } ?: 0) + 1
        AddOrEditPointDialog(
            tab = nextTab,
            label = "Point $nextTab",
            value1 = 0.0,
            value2 = if (hasPc2) 0.0 else null,
            isEdit = false,
            onDismiss = { showAddPointDialog = false },
            onConfirm = { tab, label, val1, val2 ->
                viewModel.addDataPoint(tab, label, val1, val2)
                showAddPointDialog = false
            }
        )
    }

    editingPoint?.let { pt ->
        AddOrEditPointDialog(
            tab = pt.tab,
            label = pt.label,
            value1 = pt.value1,
            value2 = pt.value2,
            isEdit = true,
            onDismiss = { editingPoint = null },
            onConfirm = { tab, label, val1, val2 ->
                viewModel.updateDataPoint(pt.id, tab, label, val1, val2)
                editingPoint = null
            }
        )
    }

    showDeleteConfirmDatasetId?.let { dsId ->
        val dsName = datasets.find { it.id == dsId }?.name ?: ""
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDatasetId = null },
            title = { Text("Delete Dataset") },
            text = { Text("Are you sure you want to delete dataset '$dsName'? All associated points will be lost.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDataset(dsId)
                        showDeleteConfirmDatasetId = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDatasetId = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Custom Canvas Plot demonstrating 1D Distribution with Standard Deviation bounds and outlier markings.
 */
@Composable
fun EsdVisualizerPlot(
    points: List<DataPointEntity>,
    feature: String,
    esdResult: EsdResult?,
    oneStepScores: List<Double>
) {
    val values = points.map { if (feature == "PC2" && it.value2 != null) it.value2 else it.value1 }
    if (values.isEmpty()) return

    val mean = values.average()
    
    // Calculate standard deviation
    var varianceSum = 0.0
    for (v in values) {
        varianceSum += (v - mean) * (v - mean)
    }
    val stdDev = if (values.size > 1) kotlin.math.sqrt(varianceSum / (values.size - 1)) else 0.0

    val minVal = values.minOrNull() ?: 0.0
    val maxVal = values.maxOrNull() ?: 0.0
    
    // Add extra padding to plot limits
    val valueRange = maxVal - minVal
    val yMin = if (valueRange == 0.0) minVal - 5.0 else minVal - 0.25 * valueRange
    val yMax = if (valueRange == 0.0) maxVal + 5.0 else maxVal + 0.25 * valueRange

    val density = LocalDensity.current
    val strokeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    val meanLineColor = MaterialTheme.colorScheme.secondary
    val fenceColor = MaterialTheme.colorScheme.tertiary
    val normalPointColor = MaterialTheme.colorScheme.primary
    val outlierPointColor = MaterialTheme.colorScheme.error

    // Track outlier indices for highlighting
    val outlierIndices = esdResult?.outlierIndices ?: emptySet()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("esd_visualizer_plot"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Distribution Plot ($feature vs Seq Index)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Dashed lines represent Upper/Lower fences. Highlighted items are statistical outliers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clip(RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Grid margin padding
                    val paddingLeft = 45f
                    val paddingRight = 20f
                    val paddingTop = 20f
                    val paddingBottom = 30f

                    val graphW = w - paddingLeft - paddingRight
                    val graphH = h - paddingTop - paddingBottom

                    fun getX(index: Int): Float {
                        if (points.size <= 1) return paddingLeft + graphW / 2
                        return paddingLeft + (index.toFloat() / (points.size - 1)) * graphW
                    }

                    fun getY(value: Double): Float {
                        val fraction = (value - yMin) / (yMax - yMin)
                        return (paddingTop + graphH * (1f - fraction)).toFloat()
                    }

                    // 1. Draw Grid lines and Y Axis Labels
                    val yLabels = listOf(yMin, mean, yMax)
                    yLabels.forEach { yVal ->
                        val yPos = getY(yVal)
                        drawLine(
                            color = strokeColor,
                            start = Offset(paddingLeft, yPos),
                            end = Offset(w - paddingRight, yPos),
                            strokeWidth = 1f
                        )
                    }

                    // 2. Draw Mean Line (Horizontal)
                    val yMean = getY(mean)
                    drawLine(
                        color = meanLineColor,
                        start = Offset(paddingLeft, yMean),
                        end = Offset(w - paddingRight, yMean),
                        strokeWidth = 3f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )

                    // 3. Draw Fences (Upper & Lower 1-Step standard critical values)
                    // If we have oneStepScores, we can derive the exact Grubbs-like multiplier lambda1
                    val maxScore = oneStepScores.maxOrNull() ?: 1.0
                    val maxDev = values.map { abs(it - mean) }.maxOrNull() ?: 1.0
                    val lambdaValue = if (maxScore > 0 && stdDev > 0) (maxDev / stdDev) / maxScore else 2.0
                    
                    val upperFence = mean + lambdaValue * stdDev
                    val lowerFence = mean - lambdaValue * stdDev

                    val yUpperFence = getY(upperFence)
                    val yLowerFence = getY(lowerFence)

                    // Draw Upper Fence
                    drawLine(
                        color = fenceColor.copy(alpha = 0.7f),
                        start = Offset(paddingLeft, yUpperFence),
                        end = Offset(w - paddingRight, yUpperFence),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                    )

                    // Draw Lower Fence
                    drawLine(
                        color = fenceColor.copy(alpha = 0.7f),
                        start = Offset(paddingLeft, yLowerFence),
                        end = Offset(w - paddingRight, yLowerFence),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                    )

                    // 4. Draw Connective Line
                    for (i in 0 until points.size - 1) {
                        val p1X = getX(i)
                        val p1Y = getY(values[i])
                        val p2X = getX(i + 1)
                        val p2Y = getY(values[i + 1])
                        drawLine(
                            color = normalPointColor.copy(alpha = 0.2f),
                            start = Offset(p1X, p1Y),
                            end = Offset(p2X, p2Y),
                            strokeWidth = 2f
                        )
                    }

                    // 5. Draw Points
                    points.forEachIndexed { i, pt ->
                        val px = getX(i)
                        val py = getY(values[i])
                        val isOutlier = outlierIndices.contains(i)

                        if (isOutlier) {
                            // Pulsing/Expanding background circle for outliers
                            drawCircle(
                                color = outlierPointColor.copy(alpha = 0.2f),
                                radius = 18f,
                                center = Offset(px, py)
                            )
                            drawCircle(
                                color = outlierPointColor,
                                radius = 8f,
                                center = Offset(px, py)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 4f,
                                center = Offset(px, py)
                            )
                        } else {
                            drawCircle(
                                color = normalPointColor,
                                radius = 6f,
                                center = Offset(px, py)
                            )
                        }
                    }
                }

                // Horizontal label guides drawn purely in Compose
                Text(
                    text = String.format("Mean (μ): %.2f", mean),
                    color = meanLineColor,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Legend indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(normalPointColor, RoundedCornerShape(5.dp))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Normal Point", style = MaterialTheme.typography.labelMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(outlierPointColor, RoundedCornerShape(5.dp))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ESD Outlier", style = MaterialTheme.typography.labelMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.TrendingFlat,
                        contentDescription = "Fence indicator",
                        tint = fenceColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("NIST Limit", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/**
 * Tab view displaying dataset rows alongside outlier calculations.
 */
@Composable
fun DataTableTab(
    points: List<DataPointEntity>,
    feature: String,
    oneStepScores: List<Double>,
    esdResult: EsdResult?,
    onEditPoint: (DataPointEntity) -> Unit,
    onDeletePoint: (DataPointEntity) -> Unit
) {
    if (points.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Inbox,
                    contentDescription = "Empty Data",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No data points. Tap '+' below to add your first point!",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val outlierIndices = esdResult?.outlierIndices ?: emptySet()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${points.size} Data Rows",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Detected ${outlierIndices.size} outliers",
                style = MaterialTheme.typography.bodySmall,
                color = if (outlierIndices.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }

        // Beautiful table layout using simple column boxes
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column {
                // Table Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Seq", modifier = Modifier.weight(0.12f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text("Label / Name", modifier = Modifier.weight(0.33f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text("Value", modifier = Modifier.weight(0.18f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                    Text("Score", modifier = Modifier.weight(0.18f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                    Text("Actions", modifier = Modifier.weight(0.19f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }

                // Table Items Rows
                points.forEachIndexed { idx, item ->
                    val valToDisplay = if (feature == "PC2" && item.value2 != null) item.value2 else item.value1
                    val score = oneStepScores.getOrNull(idx) ?: 0.0
                    val isOutlier = outlierIndices.contains(idx)

                    val rowBg = if (isOutlier) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                    } else if (idx % 2 == 0) {
                        Color.Transparent
                    } else {
                        MaterialTheme.colorScheme.surfaceColorAtElevation(0.5.dp)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(rowBg)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.tab.toString(),
                            modifier = Modifier.weight(0.12f),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = item.label,
                            modifier = Modifier.weight(0.33f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isOutlier) FontWeight.Bold else FontWeight.Normal,
                            color = if (isOutlier) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = String.format("%.2f", valToDisplay),
                            modifier = Modifier.weight(0.18f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.End,
                            fontWeight = if (isOutlier) FontWeight.Bold else FontWeight.Normal
                        )
                        
                        // Score pill
                        Box(
                            modifier = Modifier
                                .weight(0.18f)
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (isOutlier) MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = String.format("%.2f", score),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isOutlier) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Edit / Delete Actions
                        Row(
                            modifier = Modifier.weight(0.19f),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { onEditPoint(item) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Point",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { onDeletePoint(item) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Point",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tab displaying step-by-step outlier removal list (from Generalized ESD algorithm).
 */
@Composable
fun EsdSummaryTab(
    esdResult: EsdResult?
) {
    if (esdResult == null || esdResult.steps.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Add at least 3 points to view ESD step summaries.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Generalized ESD Results",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Total Points Checked: ${esdResult.n} | Significance α: ${esdResult.alpha}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .background(
                            color = if (esdResult.outlierCount > 0) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${esdResult.outlierCount} Outliers",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (esdResult.outlierCount > 0) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Text(
            text = "Rosner Step-by-Step Outlier Elimination:",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )

        // Row table representation of ESD steps
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Step", modifier = Modifier.weight(0.12f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("Eliminated", modifier = Modifier.weight(0.28f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("Value", modifier = Modifier.weight(0.15f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                    Text("Obs R_i", modifier = Modifier.weight(0.15f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                    Text("Crit λ_i", modifier = Modifier.weight(0.15f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                    Text("Ratio", modifier = Modifier.weight(0.15f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                }

                esdResult.steps.forEachIndexed { idx, step ->
                    val isConfirmedOutlier = step.step <= esdResult.outlierCount
                    val ratioHighlight = step.rObs > step.lambda

                    val bg = if (isConfirmedOutlier) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.20f)
                    } else if (idx % 2 == 1) {
                        MaterialTheme.colorScheme.surfaceColorAtElevation(0.5.dp)
                    } else {
                        Color.Transparent
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bg)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#${step.step}",
                            modifier = Modifier.weight(0.12f),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = step.label,
                            modifier = Modifier.weight(0.28f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = String.format("%.1f", step.value),
                            modifier = Modifier.weight(0.15f),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.End
                        )
                        Text(
                            text = String.format("%.3f", step.rObs),
                            modifier = Modifier.weight(0.15f),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.End,
                            fontWeight = if (ratioHighlight) FontWeight.Bold else FontWeight.Normal,
                            color = if (ratioHighlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = String.format("%.3f", step.lambda),
                            modifier = Modifier.weight(0.15f),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.End
                        )
                        Text(
                            text = String.format("%.3f", step.ratio),
                            modifier = Modifier.weight(0.15f),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.End,
                            fontWeight = if (ratioHighlight) FontWeight.Bold else FontWeight.Normal,
                            color = if (ratioHighlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddDatasetDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Dataset") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter a custom name for your scientific or ratings dataset:")
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        isError = it.isBlank()
                    },
                    label = { Text("Dataset Name") },
                    isError = isError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_dataset_name_input"),
                    shape = RoundedCornerShape(8.dp)
                )
                if (isError) {
                    Text(
                        text = "Name cannot be empty",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name)
                    } else {
                        isError = true
                    }
                },
                modifier = Modifier.testTag("new_dataset_confirm_button")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddOrEditPointDialog(
    tab: Int,
    label: String,
    value1: Double,
    value2: Double?,
    isEdit: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int, String, Double, Double?) -> Unit
) {
    var tabStr by remember { mutableStateOf(tab.toString()) }
    var labelStr by remember { mutableStateOf(label) }
    var val1Str by remember { mutableStateOf(value1.toString()) }
    var val2Str by remember { mutableStateOf(value2?.toString() ?: "") }

    var val1Error by remember { mutableStateOf(false) }
    var val2Error by remember { mutableStateOf(false) }
    var tabError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit Row Data" else "Add New Row Data") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Tab / Sequence Order
                OutlinedTextField(
                    value = tabStr,
                    onValueChange = {
                        tabStr = it
                        tabError = it.toIntOrNull() == null
                    },
                    label = { Text("Sequence / Tab Index") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = tabError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("point_tab_input")
                )

                // Label / Horse name
                OutlinedTextField(
                    value = labelStr,
                    onValueChange = { labelStr = it },
                    label = { Text("Label / Identifier (e.g. Horse Name)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("point_label_input")
                )

                // Value 1
                OutlinedTextField(
                    value = val1Str,
                    onValueChange = {
                        val1Str = it
                        val1Error = it.toDoubleOrNull() == null
                    },
                    label = { Text("PC1 / Value") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = val1Error,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("point_val1_input")
                )

                // Optional Value 2
                if (value2 != null || isEdit) {
                    OutlinedTextField(
                        value = val2Str,
                        onValueChange = {
                            val2Str = it
                            val2Error = it.isNotEmpty() && it.toDoubleOrNull() == null
                        },
                        label = { Text("PC2 Value (Optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = val2Error,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("point_val2_input")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalTab = tabStr.toIntOrNull()
                    val finalVal1 = val1Str.toDoubleOrNull()
                    val finalVal2 = val2Str.toDoubleOrNull()

                    if (finalTab == null) tabError = true
                    if (finalVal1 == null) val1Error = true
                    if (val2Str.isNotEmpty() && finalVal2 == null) val2Error = true

                    if (finalTab != null && finalVal1 != null && (!val2Error || val2Str.isEmpty())) {
                        onConfirm(finalTab, labelStr, finalVal1, finalVal2)
                    }
                },
                modifier = Modifier.testTag("point_confirm_button")
            ) {
                Text(if (isEdit) "Save Changes" else "Add Point")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
