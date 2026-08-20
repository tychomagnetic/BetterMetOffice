package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.PreferencesManager
import com.example.data.model.MapFrame
import com.example.data.model.MapOrder
import com.example.data.repository.MapImagesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

data class MapImagesUiState(
    val isLoadingCatalog: Boolean = true,
    val isLoadingImage: Boolean = false,
    val errorMessage: String? = null,
    val warningMessage: String? = null,
    val availableOrders: List<MapOrder> = emptyList(),
    val selectedOrderId: String = "",
    val runDateTime: String = "",
    val layers: List<String> = emptyList(),
    val selectedLayerId: String = "",
    val frames: List<MapFrame> = emptyList(),
    val selectedLeadTimeHours: Int = 0,
    val bitmap: Bitmap? = null,
    val isPreloadingDay: Boolean = false,
    val preloadedFrameCount: Int = 0,
    val dayFrameCount: Int = 0,
    val apiKeyConfigured: Boolean = false
) {
    val selectedFrame: MapFrame?
        get() = frames.firstOrNull {
            it.layerId == selectedLayerId && it.leadTimeHours == selectedLeadTimeHours
        }

    val leadTimes: List<Int>
        get() = frames.asSequence().filter { it.layerId == selectedLayerId }
            .map { it.leadTimeHours }.distinct().sorted().toList()
}

class MapImagesViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = PreferencesManager(application.applicationContext)
    private val repository = MapImagesRepository(application.applicationContext, preferences)
    private val _uiState = MutableStateFlow(MapImagesUiState())
    val uiState: StateFlow<MapImagesUiState> = _uiState.asStateFlow()
    private var imageJob: Job? = null
    private var preloadJob: Job? = null
    private val bitmapCache = mutableMapOf<String, Bitmap>()
    private val mapZone = ZoneId.of("Europe/London")

    fun loadCatalog(forceRefresh: Boolean = false, orderId: String? = null) {
        val key = preferences.getMapImagesApiKey()
        if (key.isBlank()) {
            _uiState.update {
                it.copy(
                    isLoadingCatalog = false,
                    apiKeyConfigured = false,
                    errorMessage = "Add your Met Office Map Images API key in Settings to view maps."
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoadingCatalog = true, apiKeyConfigured = true, errorMessage = null, warningMessage = null)
            }
            runCatching { repository.loadCatalog(key, forceRefresh, orderId) }
                .onSuccess { result ->
                    val catalog = result.catalog
                    val layers = catalog.frames.map { it.layerId }.distinct().sortedBy(::layerSortOrder)
                    val savedLayer = preferences.getSelectedMapLayerId()
                    val layer = savedLayer.takeIf(layers::contains) ?: layers.first()
                    val leads = catalog.frames.filter { it.layerId == layer }.map { it.leadTimeHours }.distinct().sorted()
                    val previousLead = _uiState.value.selectedLeadTimeHours
                    val lead = leads.minByOrNull { kotlin.math.abs(it - previousLead) } ?: 0
                    _uiState.update {
                        it.copy(
                            isLoadingCatalog = false,
                            warningMessage = result.warning,
                            availableOrders = catalog.availableOrders,
                            selectedOrderId = catalog.orderId,
                            runDateTime = catalog.runDateTime,
                            layers = layers,
                            selectedLayerId = layer,
                            frames = catalog.frames,
                            selectedLeadTimeHours = lead,
                            bitmap = null
                        )
                    }
                    preloadDayContaining(lead)
                }
                .onFailure { error ->
                    if (error is CancellationException) return@onFailure
                    _uiState.update {
                        it.copy(
                            isLoadingCatalog = false,
                            errorMessage = error.localizedMessage ?: "Unable to load weather maps."
                        )
                    }
                }
        }
    }

    fun selectOrder(orderId: String) {
        if (orderId != _uiState.value.selectedOrderId) loadCatalog(forceRefresh = true, orderId = orderId)
    }

    fun selectLayer(layerId: String) {
        val state = _uiState.value
        if (layerId == state.selectedLayerId) return
        preferences.setSelectedMapLayerId(layerId)
        val leads = state.frames.filter { it.layerId == layerId }.map { it.leadTimeHours }.distinct()
        val lead = leads.minByOrNull { kotlin.math.abs(it - state.selectedLeadTimeHours) } ?: 0
        _uiState.update {
            it.copy(
                selectedLayerId = layerId,
                selectedLeadTimeHours = lead,
                bitmap = bitmapCache[cacheKey(it, layerId, lead)]
            )
        }
        preloadDayContaining(lead)
    }

    fun selectLeadTime(hours: Int) {
        if (hours == _uiState.value.selectedLeadTimeHours) return
        val state = _uiState.value
        val cached = bitmapCache[cacheKey(state, state.selectedLayerId, hours)]
        _uiState.update {
            it.copy(
                selectedLeadTimeHours = hours,
                bitmap = cached,
                isLoadingImage = cached == null
            )
        }
        if (cached == null && !state.isPreloadingDay) loadSelectedImage()
    }

    fun selectDay(leadTimes: List<Int>) {
        val firstLead = leadTimes.firstOrNull() ?: return
        val state = _uiState.value
        val cached = bitmapCache[cacheKey(state, state.selectedLayerId, firstLead)]
        _uiState.update { it.copy(selectedLeadTimeHours = firstLead, bitmap = cached) }
        preloadLeadTimes(leadTimes)
    }

    fun stepTime(direction: Int) {
        val state = _uiState.value
        val index = state.leadTimes.indexOf(state.selectedLeadTimeHours)
        val next = (index + direction).coerceIn(0, state.leadTimes.lastIndex)
        if (index >= 0 && next != index) selectLeadTime(state.leadTimes[next])
    }

    private fun loadSelectedImage() {
        imageJob?.cancel()
        val state = _uiState.value
        val frame = state.selectedFrame ?: return
        val key = preferences.getMapImagesApiKey()
        imageJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingImage = true, errorMessage = null) }
            runCatching { repository.loadImage(key, state.selectedOrderId, frame.fileId) }
                .onSuccess { bytes ->
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap == null) {
                        _uiState.update { it.copy(isLoadingImage = false, errorMessage = "The downloaded map could not be decoded.") }
                    } else {
                        bitmapCache[cacheKey(state, state.selectedLayerId, frame.leadTimeHours)] = bitmap
                        _uiState.update { it.copy(isLoadingImage = false, bitmap = bitmap) }
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) return@onFailure
                    _uiState.update {
                        it.copy(isLoadingImage = false, errorMessage = error.localizedMessage ?: "Unable to download map image.")
                    }
                }
        }
    }

    private fun preloadDayContaining(leadTime: Int) {
        val state = _uiState.value
        val selectedDate = forecastDate(state.runDateTime, leadTime) ?: return
        val leads = state.frames.asSequence()
            .filter { it.layerId == state.selectedLayerId }
            .filter { forecastDate(it.runDateTime, it.leadTimeHours) == selectedDate }
            .map { it.leadTimeHours }
            .distinct()
            .sorted()
            .toList()
        preloadLeadTimes(leads)
    }

    private fun preloadLeadTimes(leadTimes: List<Int>) {
        preloadJob?.cancel()
        imageJob?.cancel()
        val state = _uiState.value
        val frames = leadTimes.mapNotNull { lead ->
            state.frames.firstOrNull { it.layerId == state.selectedLayerId && it.leadTimeHours == lead }
        }
        if (frames.isEmpty()) return
        val key = preferences.getMapImagesApiKey()
        val alreadyCached = frames.count { bitmapCache.containsKey(cacheKey(state, it.layerId, it.leadTimeHours)) }
        _uiState.update {
            val selectedBitmap = bitmapCache[cacheKey(state, state.selectedLayerId, it.selectedLeadTimeHours)]
            it.copy(
                isPreloadingDay = alreadyCached < frames.size,
                isLoadingImage = selectedBitmap == null,
                preloadedFrameCount = alreadyCached,
                dayFrameCount = frames.size,
                bitmap = selectedBitmap ?: it.bitmap,
                errorMessage = null
            )
        }
        if (alreadyCached == frames.size) return

        preloadJob = viewModelScope.launch {
            val semaphore = Semaphore(4)
            frames.map { frame ->
                async {
                    val cacheKey = cacheKey(state, frame.layerId, frame.leadTimeHours)
                    if (bitmapCache.containsKey(cacheKey)) return@async null
                    semaphore.withPermit {
                        runCatching {
                            val bytes = repository.loadImage(key, state.selectedOrderId, frame.fileId)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                ?: error("Map frame could not be decoded")
                        }.fold(
                            onSuccess = { bitmap -> Result.success(Triple(frame, cacheKey, bitmap)) },
                            onFailure = { error ->
                                if (error is CancellationException) throw error
                                Result.failure(error)
                            }
                        )
                    }
                }
            }.map { task ->
                async {
                    task.await()?.onSuccess { (frame, cacheKey, bitmap) ->
                        bitmapCache[cacheKey] = bitmap
                        _uiState.update { current ->
                            val isSelected = current.selectedLayerId == frame.layerId &&
                                current.selectedLeadTimeHours == frame.leadTimeHours
                            current.copy(
                                preloadedFrameCount = (current.preloadedFrameCount + 1).coerceAtMost(current.dayFrameCount),
                                isLoadingImage = if (isSelected) false else current.isLoadingImage,
                                bitmap = if (isSelected) bitmap else current.bitmap
                            )
                        }
                    }
                }
            }.awaitAll()
            _uiState.update { current ->
                val selectedBitmap = bitmapCache[cacheKey(current, current.selectedLayerId, current.selectedLeadTimeHours)]
                current.copy(
                    isPreloadingDay = false,
                    isLoadingImage = false,
                    bitmap = selectedBitmap ?: current.bitmap,
                    errorMessage = if (selectedBitmap == null) "Some map frames could not be downloaded." else current.errorMessage
                )
            }
        }
    }

    private fun forecastDate(runDateTime: String, leadTime: Int) = runCatching {
        Instant.parse(runDateTime).plusSeconds(leadTime * 3600L).atZone(mapZone).toLocalDate()
    }.getOrNull()

    private fun cacheKey(state: MapImagesUiState, layerId: String, leadTime: Int): String =
        "${state.selectedOrderId}|${state.runDateTime}|$layerId|$leadTime"

    private fun layerSortOrder(layer: String): Int = when (layer) {
        "total_precipitation_rate" -> 0
        "cloud_amount_total" -> 1
        "temperature_at_surface" -> 2
        "mean_sea_level_pressure" -> 3
        else -> 10
    }
}
