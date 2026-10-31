package com.afyzfur.afyzhub.data.remote.provider

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class TavilyRequest(
    @SerialName("api_key") val apiKey: String,
    val query: String,
    @SerialName("max_results") val maxResults: Int,
    @SerialName("search_depth") val searchDepth: String = "basic",
    @SerialName("include_answer") val includeAnswer: Boolean = false,
    @SerialName("include_raw_content") val includeRawContent: Boolean = false
)
