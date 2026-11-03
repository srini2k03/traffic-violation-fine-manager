package com.srini.trafficfine.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.srini.trafficfine.data.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uploads evidence photos to `evidence/{violationId}/{file}` (see firebase/storage.rules).
 * Camera captures are down-sampled first so uploads stay small on mobile data and well
 * under the 5 MB limit enforced by the Storage rules.
 */
@Singleton
class EvidenceUploader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storage: FirebaseStorage,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** @return the download URL, or `null` if the local file no longer exists. */
    suspend fun upload(violationId: String, localPath: String): String? {
        val original = File(localPath)
        if (!original.exists()) return null
        val prepared = withContext(io) { compress(original) }
        try {
            val ref = storage.reference.child("${FirebasePaths.EVIDENCE}/$violationId/${original.nameWithoutExtension}.jpg")
            val metadata = StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .setCustomMetadata("violationId", violationId)
                .build()
            ref.putFile(Uri.fromFile(prepared), metadata).await()
            return ref.downloadUrl.await().toString()
        } finally {
            if (prepared != original) prepared.delete()
        }
    }

    private fun compress(source: File): File {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return source
        var sample = 1
        while (longest / (sample * 2) >= MAX_DIMENSION) sample *= 2
        val bitmap = BitmapFactory.decodeFile(source.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return source
        val out = File(context.cacheDir, "upload_${source.nameWithoutExtension}.jpg")
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        bitmap.recycle()
        return out
    }

    private companion object {
        const val MAX_DIMENSION = 1600
        const val JPEG_QUALITY = 80
    }
}
