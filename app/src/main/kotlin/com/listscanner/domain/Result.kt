package com.listscanner.domain

sealed interface Result<out T> {
    data class Success<T>(val data: T) : Result<T>
    data class Failure(val exception: Throwable, val message: String) : Result<Nothing>
}
