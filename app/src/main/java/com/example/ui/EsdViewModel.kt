package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.stats.EsdResult
import com.example.stats.EsdTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class EsdViewModel(application: Application) : AndroidViewModel(application) {

    private val db = EsdDatabase.getDatabase(application)
    private val dao = db.esdDao()

    private val _datasets = MutableStateFlow<List<DatasetEntity>>(emptyList())
    val datasets: StateFlow<List<DatasetEntity>> = _datasets.asStateFlow()

    private val _selectedDatasetId = MutableStateFlow<Long?>(null)
    val selectedDatasetId: StateFlow<Long?> = _selectedDatasetId.asStateFlow()

    private val _currentPoints = MutableStateFlow<List<DataPointEntity>>(emptyList())
    val currentPoints: StateFlow<List<DataPointEntity>> = _currentPoints.asStateFlow()

    private val _alpha = MutableStateFlow(0.05)
    val alpha: StateFlow<Double> = _alpha.asStateFlow()

    private val _maxOutliers = MutableStateFlow(10)
    val maxOutliers: StateFlow<Int> = _maxOutliers.asStateFlow()

    private val _selectedFeature = MutableStateFlow("PC1") // "PC1" or "PC2"
    val selectedFeature: StateFlow<String> = _selectedFeature.asStateFlow()

    val esdResult: StateFlow<EsdResult?> = combine(
        _currentPoints,
        _selectedFeature,
        _maxOutliers,
        _alpha
    ) { points, feature, maxOut, a ->
        if (points.isEmpty()) return@combine null
        val values = points.map { if (feature == "PC2" && it.value2 != null) it.value2 else it.value1 }
        val labels = points.map { it.label }
        EsdTest.generalizedEsd(values, labels, maxOut, a)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val oneStepScores: StateFlow<List<Double>> = combine(
        _currentPoints,
        _selectedFeature,
        _alpha
    ) { points, feature, a ->
        if (points.isEmpty()) return@combine emptyList()
        val values = points.map { if (feature == "PC2" && it.value2 != null) it.value2 else it.value1 }
        EsdTest.oneStepScores(values, a)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var isSeeding = false

    init {
        // Observe datasets
        viewModelScope.launch {
            dao.getAllDatasets().collect { list ->
                _datasets.value = list
                if (list.isEmpty()) {
                    seedDefaultData()
                } else if (_selectedDatasetId.value == null) {
                    _selectedDatasetId.value = list.first().id
                }
            }
        }

        // Observe active dataset data points
        viewModelScope.launch {
            _selectedDatasetId.collect { id ->
                if (id != null) {
                    dao.getDataPointsForDataset(id).collect { points ->
                        _currentPoints.value = points
                    }
                } else {
                    _currentPoints.value = emptyList()
                }
            }
        }
    }

    private fun seedDefaultData() {
        if (isSeeding) return
        isSeeding = true
        viewModelScope.launch(Dispatchers.IO) {
            // Seed Horse Racing Dataset
            val datasetId = dao.insertDataset(DatasetEntity(name = "Horse Racing Ratings"))
            val horses = listOf(
                DataPointEntity(datasetId = datasetId, tab = 1, label = "POWERBOUND", value1 = -2.0, value2 = -1.0),
                DataPointEntity(datasetId = datasetId, tab = 2, label = "TIJUANA", value1 = 6.5, value2 = 5.0),
                DataPointEntity(datasetId = datasetId, tab = 3, label = "ALL BUSINESS", value1 = 1.0, value2 = 5.0),
                DataPointEntity(datasetId = datasetId, tab = 5, label = "HOTEI SENSHI", value1 = 2.0, value2 = 5.0),
                DataPointEntity(datasetId = datasetId, tab = 6, label = "DANAUSTAR", value1 = 1.0, value2 = 3.5),
                DataPointEntity(datasetId = datasetId, tab = 8, label = "MAGNETIC CHESS", value1 = 9.5, value2 = 4.5),
                DataPointEntity(datasetId = datasetId, tab = 9, label = "DRUNKEN SAILOR", value1 = 8.5, value2 = 4.0),
                DataPointEntity(datasetId = datasetId, tab = 10, label = "JASKIER", value1 = 10.5, value2 = 7.5),
                DataPointEntity(datasetId = datasetId, tab = 11, label = "FINAL COUNTDOWN", value1 = 2.5, value2 = 3.5),
                DataPointEntity(datasetId = datasetId, tab = 13, label = "JENNI INTIBA", value1 = 3.5, value2 = 2.0),
                DataPointEntity(datasetId = datasetId, tab = 14, label = "SPARKLES", value1 = 7.0, value2 = 3.0),
                DataPointEntity(datasetId = datasetId, tab = 15, label = "ARABIAN MYTH", value1 = 6.5, value2 = 3.5)
            )
            dao.insertDataPoints(horses)

            // Seed a Demo Dataset with clear outliers on both upper and lower ends
            val demoId = dao.insertDataset(DatasetEntity(name = "Outliers Demo Dataset"))
            val demoPoints = listOf(
                DataPointEntity(datasetId = demoId, tab = 1, label = "Item A", value1 = 10.0),
                DataPointEntity(datasetId = demoId, tab = 2, label = "Item B", value1 = 12.0),
                DataPointEntity(datasetId = demoId, tab = 3, label = "Item C", value1 = 11.0),
                DataPointEntity(datasetId = demoId, tab = 4, label = "Item D", value1 = 13.0),
                DataPointEntity(datasetId = demoId, tab = 5, label = "Item E (Outlier)", value1 = 85.0),
                DataPointEntity(datasetId = demoId, tab = 6, label = "Item F", value1 = 12.0),
                DataPointEntity(datasetId = demoId, tab = 7, label = "Item G", value1 = 10.0),
                DataPointEntity(datasetId = demoId, tab = 8, label = "Item H (Outlier)", value1 = -45.0),
                DataPointEntity(datasetId = demoId, tab = 9, label = "Item I", value1 = 11.0),
                DataPointEntity(datasetId = demoId, tab = 10, label = "Item J", value1 = 12.0)
            )
            dao.insertDataPoints(demoPoints)

            _selectedDatasetId.value = datasetId
            isSeeding = false
        }
    }

    fun selectDataset(id: Long) {
        _selectedDatasetId.value = id
        // Default to PC1 when switching datasets
        _selectedFeature.value = "PC1"
    }

    fun selectFeature(feature: String) {
        if (feature == "PC1" || feature == "PC2") {
            _selectedFeature.value = feature
        }
    }

    fun setAlpha(value: Double) {
        _alpha.value = value.coerceIn(0.001, 0.5)
    }

    fun setMaxOutliers(value: Int) {
        val size = _currentPoints.value.size
        val maxAllowed = if (size > 2) size - 2 else 1
        _maxOutliers.value = value.coerceIn(1, maxAllowed)
    }

    fun addNewDataset(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val newId = dao.insertDataset(DatasetEntity(name = name))
            _selectedDatasetId.value = newId
        }
    }

    fun deleteDataset(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteDataset(id)
            if (_selectedDatasetId.value == id) {
                val currentList = _datasets.value
                val remaining = currentList.filter { it.id != id }
                if (remaining.isNotEmpty()) {
                    _selectedDatasetId.value = remaining.first().id
                } else {
                    _selectedDatasetId.value = null
                }
            }
        }
    }

    fun addDataPoint(tab: Int, label: String, value1: Double, value2: Double?) {
        val datasetId = _selectedDatasetId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertDataPoint(
                DataPointEntity(
                    datasetId = datasetId,
                    tab = tab,
                    label = label,
                    value1 = value1,
                    value2 = value2
                )
            )
        }
    }

    fun deleteDataPoint(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteDataPoint(id)
        }
    }

    fun updateDataPoint(id: Long, tab: Int, label: String, value1: Double, value2: Double?) {
        val datasetId = _selectedDatasetId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateDataPoint(
                DataPointEntity(
                    id = id,
                    datasetId = datasetId,
                    tab = tab,
                    label = label,
                    value1 = value1,
                    value2 = value2
                )
            )
        }
    }
}
