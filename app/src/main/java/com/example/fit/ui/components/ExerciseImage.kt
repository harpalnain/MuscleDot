package com.example.fit.ui.components

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.fit.R
import com.example.fit.domain.Exercise
import com.example.fit.domain.MuscleGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Which of an exercise's two pictures to show: 0 (start position) or 1 (end
 * position). The app flips it once a second, so every picture on screen switches
 * together and reads as a simple GIF.
 */
val LocalFlipFrame = compositionLocalOf { 0 }

/** Decoded exercise pictures, kept while they fit in a fixed memory budget. */
private object ExercisePictures {
    private const val CACHE_BYTES = 16 * 1024 * 1024

    private val cache = object : LruCache<String, ImageBitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 2 // RGB_565
    }

    fun cached(path: String): ImageBitmap? = cache.get(path)

    suspend fun load(assets: AssetManager, path: String): ImageBitmap? = cached(path) ?: withContext(Dispatchers.IO) {
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
        runCatching { assets.open(path).use { BitmapFactory.decodeStream(it, null, options) } }
            .getOrNull()
            ?.asImageBitmap()
            ?.also { cache.put(path, it) }
    }
}

@DrawableRes
fun muscleIcon(group: MuscleGroup): Int = when (group) {
    MuscleGroup.CHEST -> R.drawable.ic_muscle_chest
    MuscleGroup.BACK -> R.drawable.ic_muscle_back
    MuscleGroup.SHOULDERS -> R.drawable.ic_muscle_shoulders
    MuscleGroup.ARMS -> R.drawable.ic_muscle_arms
    MuscleGroup.LEGS -> R.drawable.ic_muscle_legs
    MuscleGroup.CORE -> R.drawable.ic_muscle_core
    MuscleGroup.CARDIO -> R.drawable.ic_muscle_cardio
}

/**
 * A built-in exercise shows its picture, switching between the start and end
 * position (test tag "exercise-image"). A custom exercise has no picture and shows
 * its muscle group's icon (test tag "exercise-icon").
 */
@Composable
fun ExerciseImage(
    exercise: Exercise,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val shape = RoundedCornerShape(10.dp)
    val base = exercise.imageBase
    if (base == null) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .testTag("exercise-icon"),
        ) {
            Icon(
                painter = painterResource(muscleIcon(exercise.group)),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize(0.6f),
            )
        }
        return
    }

    val assets = LocalContext.current.assets
    val paths = listOf("${base}_0.webp", "${base}_1.webp")
    val frames by produceState(initialValue = paths.map { ExercisePictures.cached(it) }, base) {
        value = paths.map { ExercisePictures.load(assets, it) }
    }
    // The photos have a white studio or gym background, so white reads as part of the picture.
    Box(modifier = modifier.clip(shape).background(Color.White).testTag("exercise-image")) {
        val bitmap = frames.getOrNull(LocalFlipFrame.current) ?: frames.firstOrNull { it != null }
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, contentScale = contentScale, modifier = Modifier.fillMaxSize())
        }
    }
}
