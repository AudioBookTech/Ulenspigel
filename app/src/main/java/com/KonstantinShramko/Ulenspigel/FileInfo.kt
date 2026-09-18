package com.KonstantinShramko.Ulenspigel

/**
 * Data model for an audiobook chapter
 * @param fileName System file name in assets/audio/ (e.g., "Q1_001.lyra")
 * @param displayName Display name for the user (e.g., "Chapter 1")
 */
data class FileInfo(
    val fileName: String,
    val displayName: String
)
