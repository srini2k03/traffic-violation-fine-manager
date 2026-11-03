package com.srini.trafficfine.data.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.srini.trafficfine.data.di.IoDispatcher
import com.srini.trafficfine.domain.model.Violation
import com.srini.trafficfine.domain.model.ViolationStatus
import com.srini.trafficfine.domain.repository.ChallanDocument
import com.srini.trafficfine.domain.repository.ChallanDocumentGenerator
import com.srini.trafficfine.domain.util.ChallanQr
import com.srini.trafficfine.domain.validation.VehicleNumberValidator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders an A4 e-challan / payment receipt with [PdfDocument] and an embedded QR code.
 * Files are written to `cacheDir/challans/` and shared through the app's FileProvider.
 */
@Singleton
class AndroidChallanPdfGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val plateValidator: VehicleNumberValidator,
) : ChallanDocumentGenerator {

    private val rupees: NumberFormat = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build()).apply {
        maximumFractionDigits = 0
    }
    private val dateTime = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH)

    override suspend fun generate(violation: Violation): ChallanDocument = withContext(io) {
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
            draw(page.canvas, violation)
            document.finishPage(page)

            val dir = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
            val file = File(dir, "${violation.challanNumber}.pdf")
            file.outputStream().use { document.writeTo(it) }
            ChallanDocument(file.absolutePath)
        } finally {
            document.close()
        }
    }

    private fun draw(canvas: Canvas, v: Violation) {
        val title = paint(20f, bold = true)
        val heading = paint(12f, bold = true)
        val body = paint(11f)
        val small = paint(9f, color = Color.DKGRAY)
        val accent = Paint().apply { color = ACCENT }

        // Header band
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 88f, accent)
        val white = paint(20f, bold = true, color = Color.WHITE)
        canvas.drawText("TRAFFIC E-CHALLAN", MARGIN, 44f, white)
        canvas.drawText(
            if (v.status == ViolationStatus.PAID) "Payment receipt" else "Notice of traffic violation",
            MARGIN,
            68f,
            paint(11f, color = Color.WHITE),
        )

        // QR code (top right, overlapping the band)
        val qr = QrCodeRenderer.render(ChallanQr.payload(v), QR_SIZE)
        val qrLeft = PAGE_WIDTH - MARGIN.toInt() - QR_SIZE
        canvas.drawRect(Rect(qrLeft - 4, 100 - 4, qrLeft + QR_SIZE + 4, 100 + QR_SIZE + 4), Paint().apply { color = Color.WHITE })
        canvas.drawBitmap(qr, qrLeft.toFloat(), 100f, null)
        canvas.drawText("Scan to verify", qrLeft.toFloat() + 22f, 100f + QR_SIZE + 14f, small)

        var y = 120f
        fun row(label: String, value: String?) {
            canvas.drawText(label, MARGIN, y, small)
            canvas.drawText(value?.takeIf { it.isNotBlank() }?.take(MAX_VALUE_CHARS) ?: "—", MARGIN + 130f, y, body)
            y += 20f
        }

        canvas.drawText(v.challanNumber, MARGIN, y, title.apply { textSize = 15f })
        y += 28f
        row("Issued on", formatTime(v.issuedAt))
        row("Vehicle number", formatPlate(v.vehicleNumber))
        row("Offender", v.offenderName)
        row("Mobile", v.offenderPhone?.let { "+91 ${it.take(5)} ${it.drop(5)}" })
        row("Location", v.location?.let { it.address ?: "%.5f, %.5f".format(Locale.US, it.latitude, it.longitude) })
        row("Issuing officer", "${v.officerName} (${v.officerBadge})")
        row("Status", v.status.name)
        v.payment?.let {
            row("Paid on", formatTime(it.paidAt))
            row("Payment", "${it.mode.name} · ${it.reference}")
        }

        // Fine table
        y += 16f
        canvas.drawText("Offences", MARGIN, y, heading)
        y += 10f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, body)
        y += 16f
        canvas.drawText("Section", MARGIN, y, small)
        canvas.drawText("Description", MARGIN + 60f, y, small)
        canvas.drawText("Multiplier", PAGE_WIDTH - MARGIN - 150f, y, small)
        drawRight(canvas, "Amount", PAGE_WIDTH - MARGIN, y, small)
        y += 18f
        v.fine.lines.forEach { line ->
            canvas.drawText(line.section, MARGIN, y, body)
            canvas.drawText(line.title.take(48), MARGIN + 60f, y, body)
            val mult = if (line.priorOffences > 0) "×%.2f (%d prior)".format(Locale.US, line.multiplierPercent / 100.0, line.priorOffences) else "×1.00"
            canvas.drawText(mult, PAGE_WIDTH - MARGIN - 150f, y, body)
            drawRight(canvas, rupees.format(line.amount), PAGE_WIDTH - MARGIN, y, body)
            y += 18f
        }
        canvas.drawLine(MARGIN, y - 8f, PAGE_WIDTH - MARGIN, y - 8f, body)
        y += 8f
        canvas.drawText("Total payable", MARGIN, y, heading)
        drawRight(canvas, rupees.format(v.fine.total), PAGE_WIDTH - MARGIN, y, heading)
        if (v.fine.isRepeatOffence) {
            y += 16f
            canvas.drawText("Repeat-offender escalation applied (offences in the last 12 months).", MARGIN, y, small)
        }

        if (v.notes.isNotBlank()) {
            y += 28f
            canvas.drawText("Officer notes", MARGIN, y, heading)
            y += 16f
            wrap(v.notes, 90).take(6).forEach {
                canvas.drawText(it, MARGIN, y, body)
                y += 15f
            }
        }

        // Footer
        val footerY = PAGE_HEIGHT - 60f
        canvas.drawLine(MARGIN, footerY, PAGE_WIDTH - MARGIN, footerY, small)
        canvas.drawText(
            "Pay within 60 days to avoid court proceedings. Amounts are compoundable under the Motor Vehicles Act, 1988.",
            MARGIN, footerY + 16f, small,
        )
        canvas.drawText(
            "This is a computer-generated document and does not require a signature.",
            MARGIN, footerY + 30f, small,
        )
    }

    private fun formatTime(millis: Long) = dateTime.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    private fun formatPlate(plate: String) =
        (plateValidator.validate(plate) as? VehicleNumberValidator.Result.Valid)?.formatted ?: plate

    private fun drawRight(canvas: Canvas, text: String, right: Float, y: Float, paint: Paint) {
        canvas.drawText(text, right - paint.measureText(text), y, paint)
    }

    private fun paint(size: Float, bold: Boolean = false, color: Int = Color.BLACK) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun wrap(text: String, maxChars: Int): List<String> {
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        text.split(Regex("\\s+")).forEach { word ->
            if (current.length + word.length + 1 > maxChars && current.isNotEmpty()) {
                lines += current.toString()
                current = StringBuilder()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(word)
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines
    }

    companion object {
        const val DIRECTORY = "challans"
        private const val PAGE_WIDTH = 595 // A4 @ 72 dpi
        private const val PAGE_HEIGHT = 842
        private const val MARGIN = 40f
        private const val QR_SIZE = 120
        private const val MAX_VALUE_CHARS = 42
        private val ACCENT = Color.rgb(0x0B, 0x3D, 0x91)
    }
}
