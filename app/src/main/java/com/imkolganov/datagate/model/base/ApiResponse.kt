package com.imkolganov.datagate.model.base

data class ApiResponse<T>(
    val success: Boolean,
    val message: String?,
    val data: T?
)