package com.srini.trafficfine.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileSharing {

    private fun authority(context: Context) = "${context.packageName}.fileprovider"

    /** Creates an empty file in app-private storage for the camera app to write into. */
    fun newEvidenceFile(context: Context): File {
        val dir = File(context.filesDir, "evidence").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File.createTempFile("EVD_${stamp}_", ".jpg", dir)
    }

    fun uriFor(context: Context, file: File): Uri = FileProvider.getUriForFile(context, authority(context), file)

    fun shareChallan(context: Context, pdfPath: String, challanNumber: String, phone: String?) {
        val uri = uriFor(context, File(pdfPath))
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Traffic e-Challan $challanNumber")
            putExtra(
                Intent.EXTRA_TEXT,
                "Your traffic e-challan $challanNumber is attached. Scan the QR code on the challan to verify and pay.",
            )
            phone?.let { putExtra("address", "+91$it") }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share e-challan"))
    }
}
