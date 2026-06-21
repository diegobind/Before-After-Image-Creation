package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.BAProject
import com.example.data.ProjectRepository
import com.example.utils.ImageExporter
import com.example.utils.LocalImageUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BAViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ProjectRepository

    // List of saved projects
    private val _projectsList = MutableStateFlow<List<BAProject>>(emptyList())
    val projectsList: StateFlow<List<BAProject>> = _projectsList.asStateFlow()

    // Presets database for initial testing and quick visual setup
    val presets = mapOf(
        "Interior Design" to PresetData(
            name = "Luxe Spaces",
            slogan = "Premium styling for discerning homes",
            before = "https://images.unsplash.com/photo-1513694203232-719a280e022f?w=800&auto=format&fit=crop",
            after = "https://images.unsplash.com/photo-1616486338812-3dadae4b4ace?w=800&auto=format&fit=crop"
        ),
        "Construction & Roofing" to PresetData(
            name = "Apex Builders",
            slogan = "Quality craftsmanship from foundation to roof",
            before = "https://images.unsplash.com/photo-1504307651254-35680f356dfd?w=800&auto=format&fit=crop",
            after = "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?w=800&auto=format&fit=crop"
        ),
        "Patio & Gardening" to PresetData(
            name = "Evergreen Greens",
            slogan = "Curating premium outdoor living sanctuaries",
            before = "https://images.unsplash.com/photo-1530731141654-5961eaed110f?w=800&auto=format&fit=crop",
            after = "https://images.unsplash.com/photo-1558904541-efa8c1a68fc6?w=800&auto=format&fit=crop"
        ),
        "Deep Cleaning" to PresetData(
            name = "Clean Horizon",
            slogan = "Impeccable details of clinical fresh sparkle",
            before = "https://images.unsplash.com/photo-1584622650111-993a426fbf0a?w=800&auto=format&fit=crop",
            after = "https://images.unsplash.com/photo-1527515637462-cff94eecc1ac?w=800&auto=format&fit=crop"
        )
    )

    // Current State Flow variables matching editable form
    val selectedCategory = MutableStateFlow("Interior Design")
    val businessName = MutableStateFlow("Luxe Spaces")
    val businessSlogan = MutableStateFlow("Premium styling for discerning homes")
    val beforeLabel = MutableStateFlow("BEFORE")
    val afterLabel = MutableStateFlow("AFTER")

    val predefinedBackgroundColors = listOf(
        0xFF1B1D1F.toInt(), // Jet black
        0xFF122C34.toInt(), // Deep blue
        0xFF2A2320.toInt(), // Warm brown
        0xFF1F3A2B.toInt(), // Forest green
        0xFF21151F.toInt(), // Deep plum
        0xFFF4F6F8.toInt(), // Soft white
        0xFF8E9EAA.toInt()  // Neutral steel
    )
    val backgroundColor = MutableStateFlow(predefinedBackgroundColors[0])
    
    // Image sources (URLs or internal absolute paths)
    val beforeImage = MutableStateFlow(presets["Interior Design"]!!.before)
    val afterImage = MutableStateFlow(presets["Interior Design"]!!.after)
    val logoImage = MutableStateFlow<String?>(null) // Null indicates using default vector emblem based on category

    val selectedLayoutVersion = MutableStateFlow(0) // 0: Split horizontal, 1: Vertical Portrait, 2: Spotlight, 3: Promotional
    val activeProjectId = MutableStateFlow<Int?>(null) // Null means it is a new draft

    // Operation screen statuses
    private val _isExporting = MutableStateFlow(false)
    val isExporting = _isExporting.asStateFlow()

    private val _exportSuccessUri = MutableStateFlow<String?>(null)
    val exportSuccessUri = _exportSuccessUri.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        repository = ProjectRepository(db.projectDao())
        
        // Observe all saved room projects
        viewModelScope.launch {
            repository.allProjects.collectLatest { list ->
                _projectsList.value = list
            }
        }
    }

    /**
     * Resets the configuration back to selected industry category presets.
     */
    fun loadPresetForCategory(category: String) {
        val preset = presets[category] ?: return
        selectedCategory.value = category
        businessName.value = preset.name
        businessSlogan.value = preset.slogan
        beforeImage.value = preset.before
        afterImage.value = preset.after
        logoImage.value = null // reset logo to sector default
        activeProjectId.value = null
        selectedLayoutVersion.value = 0
    }

    /**
     * Triggers photo updates from visual local image picker.
     * Copying picked image files immediately prevents URI expire crashes.
     */
    fun setBeforeImageUri(uri: Uri) {
        viewModelScope.launch {
            val localPath = LocalImageUtils.copyUriToInternalStorage(getApplication(), uri, "before")
            if (localPath != null) {
                beforeImage.value = localPath
            } else {
                _statusMessage.value = "Failed to copy before image"
            }
        }
    }

    fun setAfterImageUri(uri: Uri) {
        viewModelScope.launch {
            val localPath = LocalImageUtils.copyUriToInternalStorage(getApplication(), uri, "after")
            if (localPath != null) {
                afterImage.value = localPath
            } else {
                _statusMessage.value = "Failed to copy after image"
            }
        }
    }

    fun setLogoImageUri(uri: Uri) {
        viewModelScope.launch {
            val localPath = LocalImageUtils.copyUriToInternalStorage(getApplication(), uri, "logo")
            if (localPath != null) {
                logoImage.value = localPath
            } else {
                _statusMessage.value = "Failed to copy logo image"
            }
        }
    }

    fun clearLogo() {
        logoImage.value = null
    }

    /**
     * Saves the current comparison template config to the SQLite Room database.
     */
    fun saveCurrentProject(projectName: String) {
        viewModelScope.launch {
            try {
                val proj = BAProject(
                    id = activeProjectId.value ?: 0,
                    name = projectName.ifBlank { "Untitled Project" },
                    category = selectedCategory.value,
                    beforePath = beforeImage.value,
                    afterPath = afterImage.value,
                    logoPath = logoImage.value,
                    businessName = businessName.value,
                    slogan = businessSlogan.value,
                    beforeLabel = beforeLabel.value,
                    afterLabel = afterLabel.value,
                    bgColorInt = backgroundColor.value,
                    accentColorIndex = selectedLayoutVersion.value,
                    layoutVersion = selectedLayoutVersion.value,
                    timestamp = System.currentTimeMillis()
                )
                val idLong = repository.insert(proj)
                if (activeProjectId.value == null) {
                    activeProjectId.value = idLong.toInt()
                }
                _statusMessage.value = "Project saved successfully!"
            } catch (e: Exception) {
                e.printStackTrace()
                _statusMessage.value = "Failed to save project: ${e.message}"
            }
        }
    }

    /**
     * Loads a project from the Room database into the editor state.
     */
    fun loadProjectIntoEditor(project: BAProject) {
        activeProjectId.value = project.id
        selectedCategory.value = project.category
        businessName.value = project.businessName
        businessSlogan.value = project.slogan
        beforeImage.value = project.beforePath
        afterImage.value = project.afterPath
        logoImage.value = project.logoPath
        beforeLabel.value = project.beforeLabel
        afterLabel.value = project.afterLabel
        backgroundColor.value = project.bgColorInt
        selectedLayoutVersion.value = project.layoutVersion
    }

    /**
     * Deletes a project from the saved list.
     */
    fun deleteProject(project: BAProject) {
        viewModelScope.launch {
            try {
                repository.delete(project)
                if (activeProjectId.value == project.id) {
                    // reset editor if current project deleted
                    loadPresetForCategory(selectedCategory.value)
                }
                _statusMessage.value = "Project deleted successfully"
            } catch (e: Exception) {
                e.printStackTrace()
                _statusMessage.value = "Failed to delete project"
            }
        }
    }

    /**
     * Direct High-Resolution Bitmap image compiler execution task.
     */
    fun exportHighResImage() {
        viewModelScope.launch {
            _isExporting.value = true
            _statusMessage.value = "Compiling and drawing high-resolution image..."
            
            try {
                val compiledResultUri = ImageExporter.compositeHighResBeforeAfter(
                    context = getApplication(),
                    beforeSource = beforeImage.value,
                    afterSource = afterImage.value,
                    logoSource = logoImage.value,
                    businessName = businessName.value,
                    slogan = businessSlogan.value,
                    beforeLabel = beforeLabel.value,
                    afterLabel = afterLabel.value,
                    category = selectedCategory.value,
                    layoutVersion = selectedLayoutVersion.value,
                    bgColorInt = backgroundColor.value
                )

                if (compiledResultUri != null) {
                    _exportSuccessUri.value = compiledResultUri.toString()
                    _statusMessage.value = "Image exported to gallery!"
                } else {
                    _statusMessage.value = "Failed to compile high-resolution image"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _statusMessage.value = "Export error: ${e.message}"
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun clearSuccessUri() {
        _exportSuccessUri.value = null
    }

    // Helper Presets class
    data class PresetData(
        val name: String,
        val slogan: String,
        val before: String,
        val after: String
    )
}

/**
 * Basic Factory to instantiate viewmodels with Android Context.
 */
class BAViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BAViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BAViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
