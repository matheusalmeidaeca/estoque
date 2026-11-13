package com.estoque.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BarChartView(
    c: Context,
    private val rotulos: List<String>,
    private val valores: List<Double>
) : View(c) {
    private val pTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = c.dp(12).toFloat()
    }
    private val pBar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1F4E78") }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), rotulos.size * context.dp(34) + context.dp(8))
    }

    private fun cortar(s: String, w: Float): String {
        if (pTxt.measureText(s) <= w) return s
        val n = pTxt.breakText(s, true, w - pTxt.measureText("…"), null)
        return s.substring(0, n) + "…"
    }

    override fun onDraw(cv: Canvas) {
        val max = (valores.maxOrNull() ?: 1.0).coerceAtLeast(0.0001)
        val rowH = context.dp(34).toFloat()
        val labelW = width * 0.45f
        val valW = context.dp(48).toFloat()
        val barMax = width - labelW - valW
        for (i in rotulos.indices) {
            val y = i * rowH
            cv.drawText(cortar(rotulos[i], labelW - 8f), 0f, y + rowH * 0.62f, pTxt)
            val bw = (valores[i] / max).toFloat() * barMax
            cv.drawRect(labelW, y + rowH * 0.2f, labelW + bw, y + rowH * 0.75f, pBar)
            cv.drawText(fmt(valores[i]), labelW + bw + 6f, y + rowH * 0.62f, pTxt)
        }
    }
}

class LineChartView(
    c: Context,
    private val xs: List<Long>,
    private val ys: List<Float>
) : View(c) {
    private val pTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = c.dp(11).toFloat()
    }
    private val pEixo = Paint().apply { color = Color.LTGRAY; strokeWidth = 2f }
    private val pLinha = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1F4E78")
        strokeWidth = c.dp(2).toFloat()
    }
    private val pPonto = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#C62828") }
    private val fd = SimpleDateFormat("dd/MM", Locale("pt", "BR"))

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), context.dp(200))
    }

    override fun onDraw(cv: Canvas) {
        val n = ys.size
        if (n < 2) return
        val l = context.dp(40).toFloat()
        val r = width - context.dp(14).toFloat()
        val t = context.dp(14).toFloat()
        val b = height - context.dp(30).toFloat()
        var mn = ys.minOrNull() ?: 0f
        var mx = ys.maxOrNull() ?: 1f
        if (mx == mn) {
            mx += 1f
            mn = (mn - 1f).coerceAtLeast(0f)
        }
        cv.drawLine(l, t, l, b, pEixo)
        cv.drawLine(l, b, r, b, pEixo)
        cv.drawText(mx.toInt().toString(), 2f, t + 8f, pTxt)
        cv.drawText(mn.toInt().toString(), 2f, b, pTxt)
        val x0 = xs.first()
        val span = (xs.last() - x0).coerceAtLeast(1L).toFloat()
        var px = 0f
        var py = 0f
        for (i in 0 until n) {
            val x = l + (xs[i] - x0) / span * (r - l)
            val y = b - (ys[i] - mn) / (mx - mn) * (b - t)
            if (i > 0) cv.drawLine(px, py, x, y, pLinha)
            cv.drawCircle(x, y, context.dp(3).toFloat(), pPonto)
            px = x
            py = y
        }
        val yTxt = height - context.dp(10).toFloat()
        cv.drawText(fd.format(Date(xs.first())), l, yTxt, pTxt)
        val fim = fd.format(Date(xs.last()))
        cv.drawText(fim, r - pTxt.measureText(fim), yTxt, pTxt)
    }
}
