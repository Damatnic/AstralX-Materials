package com.astralx.browser.domain.model

import java.util.UUID

/**
 * Browser tab model
 */
data class Tab(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val title: String = "New Tab",
    val favicon: String? = null,
    val isPrivate: Boolean = false,
    val isActive: Boolean = false,
    val lastAccessed: Long = System.currentTimeMillis(),
    val screenshot: String? = null
)