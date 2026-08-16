package com.example.data.model

import com.squareup.moshi.Json

data class BpfCoverageCollection(
    @Json(name = "type") val type: String? = null,
    @Json(name = "coverages") val coverages: List<BpfCoverage> = emptyList()
)

data class BpfCoverage(
    @Json(name = "type") val type: String? = null,
    @Json(name = "parameters") val parameters: Map<String, BpfParameter> = emptyMap(),
    @Json(name = "domain") val domain: BpfDomain? = null,
    @Json(name = "ranges") val ranges: Map<String, BpfRange> = emptyMap()
)

data class BpfParameter(
    @Json(name = "unit") val unit: BpfUnit? = null
)

data class BpfUnit(
    @Json(name = "symbol") val symbol: String? = null
)

data class BpfDomain(
    @Json(name = "axes") val axes: Map<String, BpfAxis> = emptyMap()
)

data class BpfAxis(
    @Json(name = "values") val values: List<Any?> = emptyList(),
    @Json(name = "bounds") val bounds: List<Any?> = emptyList()
)

data class BpfRange(
    @Json(name = "axisNames") val axisNames: List<String> = emptyList(),
    @Json(name = "shape") val shape: List<Int> = emptyList(),
    @Json(name = "values") val values: List<Double?> = emptyList()
)
