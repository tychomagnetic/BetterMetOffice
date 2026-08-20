package com.example.data.model

data class MapOrdersResponse(
    val orders: List<MapOrder> = emptyList()
)

data class MapOrder(
    val orderId: String = "",
    val name: String = "",
    val description: String = "",
    val modelId: String = "",
    val requiredLatestRuns: List<String> = emptyList(),
    val format: String = ""
)

data class MapLatestResponse(
    val orderDetails: MapOrderDetails = MapOrderDetails()
)

data class MapOrderDetails(
    val order: MapOrder = MapOrder(),
    val files: List<MapImageFile> = emptyList()
)

data class MapImageFile(
    val fileId: String = "",
    val runDateTime: String = "",
    val run: String = ""
)

data class MapFrame(
    val fileId: String,
    val layerId: String,
    val leadTimeHours: Int,
    val runDateTime: String
)

data class MapManifestCache(
    val checkedBoundaryMillis: Long,
    val orderId: String,
    val orderName: String,
    val availableOrders: List<MapOrder>,
    val runDateTime: String,
    val frames: List<MapFrame>
)

data class MapCatalogResult(
    val catalog: MapManifestCache,
    val warning: String? = null
)
