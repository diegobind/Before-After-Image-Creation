package com.example.ui

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.example.data.BAProject
import com.example.utils.ImageExporter
import com.example.viewmodel.BAViewModel
import com.example.viewmodel.BAViewModelFactory
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BAApp() {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val viewModel: BAViewModel = viewModel(factory = BAViewModelFactory(app))

    // States flow collected reactively
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val businessName by viewModel.businessName.collectAsStateWithLifecycle()
    val businessSlogan by viewModel.businessSlogan.collectAsStateWithLifecycle()
    val beforeLabel by viewModel.beforeLabel.collectAsStateWithLifecycle()
    val afterLabel by viewModel.afterLabel.collectAsStateWithLifecycle()
    val beforeImage by viewModel.beforeImage.collectAsStateWithLifecycle()
    val afterImage by viewModel.afterImage.collectAsStateWithLifecycle()
    val logoImage by viewModel.logoImage.collectAsStateWithLifecycle()
    val layoutVersion by viewModel.selectedLayoutVersion.collectAsStateWithLifecycle()
    val savedProjects by viewModel.projectsList.collectAsStateWithLifecycle()
    
    val isExporting by viewModel.isExporting.collectAsStateWithLifecycle()
    val exportSuccessUri by viewModel.exportSuccessUri.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    // Dialog state for Save Name Prompt
    var showSaveDialog by remember { mutableStateOf(false) }
    var inputSaveName by remember { mutableStateOf("") }

    // Resolve category theme styling
    val categoryColors = remember(selectedCategory) { getUiCategoryColor(selectedCategory) }
    val primaryThemeColor = categoryColors.primaryColor
    val gradientBackground = categoryColors.gradient

    // Show status toasts
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearStatusMessage()
        }
    }

    // Trigger share sheet on successful export
    LaunchedEffect(exportSuccessUri) {
        exportSuccessUri?.let { path ->
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, Uri.parse(path))
                    putExtra(Intent.EXTRA_SUBJECT, "My Branded Transformation")
                    putExtra(Intent.EXTRA_TEXT, "Check out this amazing transformation from $businessName! Created with Before & After Social Builder.")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Branded Overlay"))
            } catch (e: Exception) {
                Toast.makeText(context, "Error starting share: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Photo selection launchers
    val beforePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.setBeforeImageUri(it) } }

    val afterPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.setAfterImageUri(it) } }

    val logoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.setLogoImageUri(it) } }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = primaryThemeColor,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "BEFORE / AFTER BUILDER",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            letterSpacing = 1.5.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
                ),
                actions = {
                    IconButton(
                        onClick = { viewModel.loadPresetForCategory(selectedCategory) },
                        modifier = Modifier.testTag("reset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset presets",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            inputSaveName = businessName.ifBlank { "Unlabeled Project" }
                            showSaveDialog = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .testTag("save_draft_button"),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, primaryThemeColor)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text("SAVE DRAFT", fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = primaryThemeColor)
                    }

                    Button(
                        onClick = { viewModel.exportHighResImage() },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryThemeColor),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(56.dp)
                            .testTag("export_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                            Text("EXPORT HD & SHARE", fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Color.White)
                        }
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBackground)
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Intro text summary
                item {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = "Aesthetic Overlay Brander",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = primaryThemeColor
                        )
                        Text(
                            text = "Craft elegant, side-by-side promotional posts customized dynamically for your business sector in seconds.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                // Saved Drafts bar (If history database is populated)
                if (savedProjects.isNotEmpty()) {
                    item {
                        Column {
                            Text(
                                text = "RECENT PROJECTS DRAFTS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp,
                                modifier = Modifier.padding(bottom = 8.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth().testTag("drafts_shelf")
                            ) {
                                items(savedProjects) { p ->
                                    DraftCardItem(
                                        project = p,
                                        primaryColor = getUiCategoryColor(p.category).primaryColor,
                                        onLoad = { viewModel.loadProjectIntoEditor(p) },
                                        onDelete = { viewModel.deleteProject(p) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Sector Selector segment
                item {
                    Column {
                        Text(
                            text = "SELECT BUSINESS SECTOR",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp,
                            modifier = Modifier.padding(bottom = 8.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val sectorsList = listOf("Interior Design", "Construction & Roofing", "Patio & Gardening", "Deep Cleaning")
                            val sectorsIcons = listOf(Icons.Default.Home, Icons.Default.Build, Icons.Default.Eco, Icons.Default.AutoAwesome)
                            
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(sectorsList.size) { i ->
                                    val sec = sectorsList[i]
                                    val icon = sectorsIcons[i]
                                    val isSel = selectedCategory == sec
                                    val secTheme = getUiCategoryColor(sec)
                                    
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { viewModel.loadPresetForCategory(sec) },
                                        label = { Text(sec.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp) },
                                        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = secTheme.primaryColor,
                                            selectedLabelColor = Color.White,
                                            selectedLeadingIconColor = Color.White
                                        ),
                                        modifier = Modifier.testTag("sector_${sec.replace(" ", "_")}")
                                    )
                                }
                            }
                        }
                    }
                }

                // Double Picker layout cards
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ImagePickerCell(
                            label = "BEFORE STATE",
                            imagePath = beforeImage,
                            onClick = { beforePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            accentColor = Color.Red,
                            modifier = Modifier.weight(1f).testTag("pick_before_image_card")
                        )

                        ImagePickerCell(
                            label = "AFTER STATE",
                            imagePath = afterImage,
                            onClick = { afterPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            accentColor = primaryThemeColor,
                            modifier = Modifier.weight(1f).testTag("pick_after_image_card")
                        )
                    }
                }

                // Logo picker layout
                item {
                    LogoPickerCell(
                        logoPath = logoImage,
                        onSelect = { logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onClear = { viewModel.clearLogo() },
                        accentColor = primaryThemeColor,
                        category = selectedCategory
                    )
                }

                // Branded Texts input form block
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = primaryThemeColor, modifier = Modifier.padding(end = 8.dp))
                                Text(
                                    text = "BUSINESS CUSTOMIZATION FORM",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                            }

                            OutlinedTextField(
                                value = businessName,
                                onValueChange = { viewModel.businessName.value = it },
                                label = { Text("Business Brand Name", fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth().testTag("business_name_input"),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = primaryThemeColor,
                                    focusedLabelColor = primaryThemeColor
                                )
                            )

                            OutlinedTextField(
                                value = businessSlogan,
                                onValueChange = { viewModel.businessSlogan.value = it },
                                label = { Text("Slogan / Phone / Website URL", fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth().testTag("business_slogan_input"),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = primaryThemeColor,
                                    focusedLabelColor = primaryThemeColor
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = beforeLabel,
                                    onValueChange = { viewModel.beforeLabel.value = it },
                                    label = { Text("Before tag", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f).testTag("before_tag_input"),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.Red,
                                        focusedLabelColor = Color.Red
                                    )
                                )

                                OutlinedTextField(
                                    value = afterLabel,
                                    onValueChange = { viewModel.afterLabel.value = it },
                                    label = { Text("After tag", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f).testTag("after_tag_input"),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = primaryThemeColor,
                                        focusedLabelColor = primaryThemeColor
                                    )
                                )
                            }
                        }
                    }
                }

                // Layout choices & Live preview (Generates 4 versions!)
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(Icons.Default.Layers, contentDescription = null, tint = primaryThemeColor, modifier = Modifier.padding(end = 8.dp))
                            Text(
                                text = "SELECT BRANDED OVERLAY DESIGN (4 VERSIONS)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }

                        // Choices tabs
                        val layoutsNames = listOf("Classic Split", "Stacked Story", "Spotlight", "Modern Promo")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            layoutsNames.forEachIndexed { index, name ->
                                val isSel = layoutVersion == index
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSel) primaryThemeColor else Color.Transparent)
                                        .clickable { viewModel.selectedLayoutVersion.value = index }
                                        .padding(vertical = 8.dp)
                                        .testTag("layout_tab_$index"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = name,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Background Color Selector
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = primaryThemeColor, modifier = Modifier.padding(end = 8.dp))
                            Text(
                                text = "BACKGROUND COLOR",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }

                        val bgColors = viewModel.predefinedBackgroundColors
                        val currentBgColor by viewModel.backgroundColor.collectAsStateWithLifecycle()
                        
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        ) {
                            items(bgColors) { colorInt ->
                                val isSelected = currentBgColor == colorInt
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(colorInt))
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) primaryThemeColor else Color.Gray.copy(alpha=0.5f),
                                            shape = CircleShape
                                        )
                                        .clickable { viewModel.backgroundColor.value = colorInt }
                                        .testTag("color_picker_$colorInt")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Interactive live simulation card
                        LiveSimulatorCanvas(
                            version = layoutVersion,
                            beforePath = beforeImage,
                            afterPath = afterImage,
                            logoPath = logoImage,
                            businessName = businessName,
                            slogan = businessSlogan,
                            beforeLabel = beforeLabel,
                            afterLabel = afterLabel,
                            primaryColor = primaryThemeColor,
                            bgColorInt = currentBgColor,
                            category = selectedCategory,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(when (layoutVersion) {
                                    0 -> 1.33f // Aspect 4:3
                                    1 -> 0.562f // Aspect 9:16
                                    else -> 1f // Aspect 1:1
                                })
                                .shadow(8.dp, RoundedCornerShape(20.dp))
                                .testTag("live_canvas_card")
                        )
                    }
                }
            }

            // Export Success Overlay
            if (exportSuccessUri != null) {
                ExportConfirmationDialog(
                    imageUri = exportSuccessUri!!,
                    onClose = { viewModel.clearSuccessUri() },
                    onShare = {
                        try {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/jpeg"
                                putExtra(Intent.EXTRA_STREAM, Uri.parse(exportSuccessUri))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Comparison"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error sharing: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    primaryColor = primaryThemeColor
                )
            }

            // Save Project dialog
            if (showSaveDialog) {
                AlertDialog(
                    onDismissRequest = { showSaveDialog = false },
                    title = { Text("Save Project Template", fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text("Assign a name to your project template to save to local history:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = inputSaveName,
                                onValueChange = { inputSaveName = it },
                                label = { Text("Project Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("save_dialog_input_name")
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.saveCurrentProject(inputSaveName)
                                showSaveDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryThemeColor),
                            modifier = Modifier.testTag("save_dialog_confirm")
                        ) {
                            Text("SAVE")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSaveDialog = false }) {
                            Text("CANCEL")
                        }
                    },
                    shape = RoundedCornerShape(20.dp)
                )
            }
        }
    }
}

// ==========================================
// COMPOSABLE VISUAL CELLS & HELPERS
// ==========================================

@Composable
fun ImagePickerCell(
    label: String,
    imagePath: String,
    onClick: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val isPreset = imagePath.startsWith("http")
    val painter = rememberAsyncImagePainter(model = imagePath)

    Card(
        modifier = modifier
            .height(150.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(2.dp, accentColor.copy(alpha = 0.5f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Transparent backing
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
            )

            // Badge indicators
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .background(accentColor, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .align(Alignment.TopStart)
            ) {
                Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            if (isPreset) {
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .align(Alignment.TopEnd)
                ) {
                    Text("PRESET", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "REPLACE PHOTO",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
fun LogoPickerCell(
    logoPath: String?,
    onSelect: () -> Unit,
    onClear: () -> Unit,
    accentColor: Color,
    category: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Visual preview of current logo
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.13f), RoundedCornerShape(12.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (logoPath != null) {
                    Image(
                        painter = rememberAsyncImagePainter(model = logoPath),
                        contentDescription = "Logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Draw offline category logo representation locally
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        ImageExporter.drawDefaultSectorLogo(
                            canvas = drawContext.canvas.nativeCanvas,
                            cx = size.width / 2f,
                            cy = size.height / 2f,
                            size = size.width * 0.9f,
                            sector = category,
                            colorInt = accentColor.hashCode()
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (logoPath != null) "CUSTOM BRAND LOGO" else "DEFAULT SECTOR LOGO",
                    style = MaterialTheme.typography.titleSmall,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Text(
                    text = if (logoPath != null) "Custom PNG/JPG selected successfully." else "No logo uploaded. Using a crisp sector-specific vector graphic.",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (logoPath != null) {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.testTag("clear_logo_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear logo", tint = Color.Red)
                    }
                }
                
                Button(
                    onClick = onSelect,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("upload_logo_button")
                ) {
                    Text("UPLOAD Logo", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Renders high-fidelity Compose simulation overlay models of the 4 design types.
 */
@Composable
fun LiveSimulatorCanvas(
    version: Int,
    beforePath: String,
    afterPath: String,
    logoPath: String?,
    businessName: String,
    slogan: String,
    beforeLabel: String,
    afterLabel: String,
    primaryColor: Color,
    bgColorInt: Int,
    category: String,
    modifier: Modifier = Modifier
) {
    val beforePaint = rememberAsyncImagePainter(model = beforePath)
    val afterPaint = rememberAsyncImagePainter(model = afterPath)
    val logoPaint = rememberAsyncImagePainter(model = logoPath)
    
    val isLight = androidx.core.graphics.ColorUtils.calculateLuminance(bgColorInt) > 0.5
    val titleColor = if (isLight) Color.Black else Color.White
    val sloganColor = if (isLight) Color.DarkGray else Color.LightGray

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(3.dp, primaryColor)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color(bgColorInt))) {
            when (version) {
                0 -> {
                    // VERSION 1: Classic horizontal split
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Before Left
                        Box(modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()) {
                            Image(
                                painter = beforePaint,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            BadgeOverlay(text = beforeLabel, color = Color.Red, modifier = Modifier.align(Alignment.TopStart))
                        }
                        // After Right
                        Box(modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()) {
                            Image(
                                painter = afterPaint,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            BadgeOverlay(text = afterLabel, color = primaryColor, modifier = Modifier.align(Alignment.TopEnd))
                        }
                    }

                    // Divider vertical line
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(4.dp)
                            .background(Color.White)
                            .align(Alignment.Center)
                    )

                    // Footer branded panel overlay
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .background(Color(bgColorInt))
                            .border(BorderStroke(1.dp, primaryColor))
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LiveSimLogoDisplay(logoPath, primaryColor, category, Modifier.size(32.dp))
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text(businessName.uppercase(), color = titleColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
                                    Text(slogan, color = sloganColor, fontSize = 8.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // VERSION 2: Vertical Stack split
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Before Top
                        Box(modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()) {
                            Image(
                                painter = beforePaint,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            BadgeOverlay(text = beforeLabel, color = Color.Red, modifier = Modifier.align(Alignment.TopStart))
                        }
                        // After Bottom
                        Box(modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()) {
                            Image(
                                painter = afterPaint,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            BadgeOverlay(text = afterLabel, color = primaryColor, modifier = Modifier.align(Alignment.BottomEnd))
                        }
                    }

                    // Horizontal divider bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(primaryColor)
                            .align(Alignment.Center)
                    )

                    // Overlay badge bubble over horizontal split
                    Box(
                        modifier = Modifier
                            .background(Color(bgColorInt), RoundedCornerShape(12.dp))
                            .border(1.dp, primaryColor, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .align(Alignment.Center)
                    ) {
                        Text("$beforeLabel & $afterLabel", color = titleColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }

                    // Footer branding block
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(70.dp)
                            .background(Color(bgColorInt))
                            .align(Alignment.BottomCenter)
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        LiveSimLogoDisplay(logoPath, primaryColor, category, Modifier.size(28.dp))
                        Text(businessName.uppercase(), color = titleColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(slogan, color = sloganColor, fontSize = 8.sp)
                    }
                }
                2 -> {
                    // VERSION 3: Spotlight focus style
                    Image(
                        painter = afterPaint,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Semi-dark scrim
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))))
                    )

                    // Before Inset Card (Floating Polaroid style)
                    Card(
                        modifier = Modifier
                            .padding(14.dp)
                            .size(110.dp, 130.dp)
                            .align(Alignment.TopStart),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                painter = beforePaint,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(beforeLabel, fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.DarkGray)
                        }
                    }

                    // After Floating Badge tag
                    BadgeOverlay(text = afterLabel, color = primaryColor, modifier = Modifier.align(Alignment.TopEnd))

                    // Translucent glass bottom bar
                    Card(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth()
                            .height(54.dp)
                            .align(Alignment.BottomCenter),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xCC090A0D)),
                        border = BorderStroke(1.dp, primaryColor)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LiveSimLogoDisplay(logoPath, primaryColor, category, Modifier.size(28.dp))
                            Column(modifier = Modifier.padding(start = 10.dp)) {
                                Text(businessName.uppercase(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(slogan, color = Color.LightGray, fontSize = 8.sp)
                            }
                        }
                    }
                }
                else -> {
                    // VERSION 4: Modern Promo Grid with border padding
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(bgColorInt))
                            .padding(10.dp)
                            .border(BorderStroke(1.dp, primaryColor))
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Side-by-side cropped grid
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(horizontal = 4.dp, vertical = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    Image(
                                        painter = beforePaint,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 8.dp)
                                            .background(Color.Red, RoundedCornerShape(12.dp))
                                            .padding(horizontal = 10.dp, vertical = 2.dp)
                                    ) {
                                        Text(beforeLabel, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    Image(
                                        painter = afterPaint,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 8.dp)
                                            .background(primaryColor, RoundedCornerShape(12.dp))
                                            .padding(horizontal = 10.dp, vertical = 2.dp)
                                    ) {
                                        Text(afterLabel, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Banner promotional section at footer base
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(businessName.uppercase(), color = titleColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
                                    Text(slogan, color = sloganColor, fontSize = 8.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiveSimLogoDisplay(logoPath: String?, accentColor: Color, category: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (logoPath != null) {
            Image(
                painter = rememberAsyncImagePainter(model = logoPath),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                ImageExporter.drawDefaultSectorLogo(
                    canvas = drawContext.canvas.nativeCanvas,
                    cx = size.width / 2f,
                    cy = size.height / 2f,
                    size = size.width * 1.1f,
                    sector = category,
                    colorInt = accentColor.hashCode()
                )
            }
        }
    }
}

@Composable
fun BadgeOverlay(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(10.dp)
            .background(color, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.White)
    }
}

@Composable
fun DraftCardItem(
    project: BAProject,
    primaryColor: Color,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(220.dp)
            .clickable(onClick = onLoad),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                Image(
                    painter = rememberAsyncImagePainter(model = project.afterPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = project.name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = project.category.uppercase(),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryColor,
                    maxLines = 1
                )
                Text(
                    text = SimpleDateFormat("MM-dd kk:mm", Locale.getDefault()).format(Date(project.timestamp)),
                    fontSize = 7.sp,
                    color = Color.Gray
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp).testTag("delete_draft_${project.id}")
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete project", tint = Color.LightGray, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun ExportConfirmationDialog(
    imageUri: String,
    onClose: () -> Unit,
    onShare: () -> Unit,
    primaryColor: Color
) {
    Dialog(onDismissRequest = onClose) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(3.dp, primaryColor)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = primaryColor, modifier = Modifier.size(48.dp))
                
                Text(
                    text = "EXPORT SUCCESSFUL",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    letterSpacing = 1.sp
                )

                Text(
                    text = "A high-resolution composite has been compiled and saved permissionlessly into your Pictures/BeforeAfterCreator album.",
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(model = imageUri),
                        contentDescription = "Export draft",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onClose,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("CLOSE")
                    }

                    Button(
                        onClick = onShare,
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("POST/SHARE")
                    }
                }
            }
        }
    }
}

// Category design mapping helper
data class ResolvedUiCategoryTheme(
    val primaryColor: Color,
    val gradient: Brush
)

fun getUiCategoryColor(category: String): ResolvedUiCategoryTheme {
    return when (category) {
        "Interior Design" -> ResolvedUiCategoryTheme(
            primaryColor = Color(0xFFB8860B), // Luxe Gold Brand
            gradient = Brush.linearGradient(listOf(Color(0xFFFCFBF9), Color(0xFFF7F2EB)))
        )
        "Construction & Roofing" -> ResolvedUiCategoryTheme(
            primaryColor = Color(0xFFE65100), // Contractors Safety Amber
            gradient = Brush.linearGradient(listOf(Color(0xFFF8F9FA), Color(0xFFECEFF1)))
        )
        "Patio & Gardening" -> ResolvedUiCategoryTheme(
            primaryColor = Color(0xFF2E7D32), // Foliage organic grass green
            gradient = Brush.linearGradient(listOf(Color(0xFFF9FBF9), Color(0xFFE8F5E9)))
        )
        else -> ResolvedUiCategoryTheme( // Deep Cleaning
            primaryColor = Color(0xFF0097A7), // Sparkle pristine ocean teal
            gradient = Brush.linearGradient(listOf(Color(0xFFFAFCFC), Color(0xFFE0F7FA)))
        )
    }
}
