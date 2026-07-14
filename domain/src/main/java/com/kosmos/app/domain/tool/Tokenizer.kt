package com.kosmos.app.domain.tool

interface Tokenizer {
    fun sizeInTokens(text: String): Int
}
