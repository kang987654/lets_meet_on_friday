package com.localfriday.app.domain.tool

interface Tokenizer {
    fun sizeInTokens(text: String): Int
}
