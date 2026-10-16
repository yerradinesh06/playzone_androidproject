package com.ps22.tournament.ui

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.ps22.tournament.data.FixtureEntity
import com.ps22.tournament.data.StandingRow
import com.ps22.tournament.data.TeamEntity
import java.io.File
import java.io.FileOutputStream

fun shareStandingsPdf(context: Context, tournamentName: String, rows: List<StandingRow>) {
    require(rows.isNotEmpty()) { "There are no standings to export yet." }
    val document = PdfDocument()
    val page = document.startPage(PdfDocument.PageInfo.Builder(612, 792, 1).create())
    val canvas = page.canvas

    // PlayZone Deep Navy Header
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(15, 31, 42); textSize = 22f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
    canvas.drawText("PLAYZONE  /  LEAGUE STANDINGS", 42f, 58f, titlePaint)

    // Coral Accent Bar
    val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(255, 138, 101); strokeWidth = 3f }
    canvas.drawLine(42f, 66f, 180f, 66f, barPaint)

    val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f; typeface = Typeface.DEFAULT; color = android.graphics.Color.rgb(80, 95, 105) }
    canvas.drawText(tournamentName.take(64), 42f, 88f, subPaint)

    val leftHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; typeface = Typeface.create("sans-serif", Typeface.BOLD); color = android.graphics.Color.rgb(15, 31, 42); textAlign = Paint.Align.LEFT }
    val centerHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; typeface = Typeface.create("sans-serif", Typeface.BOLD); color = android.graphics.Color.rgb(15, 31, 42); textAlign = Paint.Align.CENTER }

    val colRankX = 42f
    val colClubX = 75f
    val colPX = 310f
    val colWX = 355f
    val colDX = 400f
    val colLX = 445f
    val colGDX = 490f
    val colPtsX = 540f

    canvas.drawText("#", colRankX, 128f, leftHeaderPaint)
    canvas.drawText("CLUB / TEAM", colClubX, 128f, leftHeaderPaint)
    canvas.drawText("P", colPX, 128f, centerHeaderPaint)
    canvas.drawText("W", colWX, 128f, centerHeaderPaint)
    canvas.drawText("D", colDX, 128f, centerHeaderPaint)
    canvas.drawText("L", colLX, 128f, centerHeaderPaint)
    canvas.drawText("GD", colGDX, 128f, centerHeaderPaint)
    canvas.drawText("PTS", colPtsX, 128f, centerHeaderPaint)

    val leftBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT; textSize = 11f; color = android.graphics.Color.rgb(19, 28, 36); textAlign = Paint.Align.LEFT }
    val centerBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT; textSize = 11f; color = android.graphics.Color.rgb(19, 28, 36); textAlign = Paint.Align.CENTER }
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(220, 226, 230); strokeWidth = 0.6f }

    rows.forEachIndexed { i, row ->
        val y = 156f + (i * 26f)
        canvas.drawLine(42f, y - 14f, 570f, y - 14f, linePaint)
        canvas.drawText(row.rank.toString(), colRankX, y, leftBodyPaint)
        canvas.drawText(row.team.name.take(24), colClubX, y, leftBodyPaint)
        canvas.drawText(row.played.toString(), colPX, y, centerBodyPaint)
        canvas.drawText(row.wins.toString(), colWX, y, centerBodyPaint)
        canvas.drawText(row.draws.toString(), colDX, y, centerBodyPaint)
        canvas.drawText(row.losses.toString(), colLX, y, centerBodyPaint)
        canvas.drawText(row.goalDifference.toString(), colGDX, y, centerBodyPaint)
        canvas.drawText(row.points.toString(), colPtsX, y, centerBodyPaint)
    }

    val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = android.graphics.Color.GRAY }
    canvas.drawText("Generated on device · PlayZone Sports & Tournament Manager", 42f, 750f, footerPaint)
    document.finishPage(page)

    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, "ps22-standings.pdf")
    FileOutputStream(file).use(document::writeTo)
    document.close()

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share standings PDF"))
}

fun shareFixturesPdf(context: Context, tournamentName: String, fixtures: List<FixtureEntity>, teams: List<TeamEntity>) {
    require(fixtures.isNotEmpty()) { "There are no fixtures to export." }
    val document = PdfDocument()
    val page = document.startPage(PdfDocument.PageInfo.Builder(612, 792, 1).create())
    val canvas = page.canvas

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(15, 31, 42); textSize = 22f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
    canvas.drawText("PLAYZONE  /  MATCH SCHEDULE & RESULTS", 42f, 58f, titlePaint)

    // Coral Accent Bar
    val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(255, 138, 101); strokeWidth = 3f }
    canvas.drawLine(42f, 66f, 180f, 66f, barPaint)

    val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f; typeface = Typeface.DEFAULT; color = android.graphics.Color.rgb(80, 95, 105) }
    canvas.drawText(tournamentName.take(64), 42f, 88f, subPaint)

    val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; typeface = Typeface.create("sans-serif", Typeface.BOLD); color = android.graphics.Color.rgb(15, 31, 42) }
    canvas.drawText("ROUND", 42f, 128f, headerPaint)
    canvas.drawText("FIXTURE", 150f, 128f, headerPaint)
    canvas.drawText("STATUS / SCORE", 400f, 128f, headerPaint)

    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT; textSize = 11f; color = android.graphics.Color.rgb(19, 28, 36) }
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(220, 226, 230); strokeWidth = 0.6f }
    val teamMap = teams.associateBy { it.id }

    fixtures.take(22).forEachIndexed { i, f ->
        val y = 156f + (i * 26f)
        canvas.drawLine(42f, y - 14f, 570f, y - 14f, linePaint)
        val home = teamMap[f.homeTeamId]?.shortName ?: if (f.homeSourceResolved) "—" else "TBD"
        val away = teamMap[f.awayTeamId]?.shortName ?: if (f.awaySourceResolved) "—" else "TBD"
        val scoreStr = if ((f.homeScore != null) && (f.awayScore != null)) "${f.homeScore} - ${f.awayScore}" else f.status
        canvas.drawText(f.round.take(16), 42f, y, bodyPaint)
        canvas.drawText("$home vs $away", 150f, y, bodyPaint)
        canvas.drawText(scoreStr, 400f, y, bodyPaint)
    }

    val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = android.graphics.Color.GRAY }
    canvas.drawText("Generated on device · PlayZone Sports & Tournament Manager", 42f, 750f, footerPaint)
    document.finishPage(page)

    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, "ps22-fixtures.pdf")
    FileOutputStream(file).use(document::writeTo)
    document.close()

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share fixtures PDF"))
}
