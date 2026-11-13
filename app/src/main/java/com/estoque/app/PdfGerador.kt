package com.estoque.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PDF em A4 horizontal (842x595). Cada folha vira um "livrinho aberto":
 * duas páginas lado a lado (esquerda e direita) com a dobra no meio.
 */
object PdfGerador {
    private const val W = 842
    private const val H = 595
    private const val POR_PAGINA = 24
    private const val LINHA_H = 18f
    private const val LARG = 379f

    private fun cabe(s: String, p: Paint, w: Float): String {
        if (p.measureText(s) <= w) return s
        val n = p.breakText(s, true, w - p.measureText("…"), null)
        return s.substring(0, n) + "…"
    }

    private fun centro(cv: Canvas, s: String, cx: Float, y: Float, p: Paint) {
        cv.drawText(s, cx - p.measureText(s) / 2f, y, p)
    }

    private fun unidadeTxt(l: Linha): String =
        if (l.unidade.isEmpty()) "" else if (l.kg > 0) l.unidade + " " + l.kg + "kg" else l.unidade

    fun gerar(ctx: Context, c: Contagem): Uri? {
        val agora = System.currentTimeMillis()
        val linhas = c.linhas.sortedWith(Comparator<Linha> { a, b -> Repo.collator.compare(a.nome, b.nome) })
        val paginas = maxOf(1, (linhas.size + POR_PAGINA - 1) / POR_PAGINA)
        val folhas = (paginas + 1) / 2
        val doc = PdfDocument()

        val negrito = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val pTitulo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 14f; typeface = negrito }
        val pTexto = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 9.5f }
        val pNegrito = Paint(pTexto).apply { typeface = negrito; color = Color.BLACK }
        val pCelula = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 9f }
        val pCelulaB = Paint(pCelula).apply { typeface = negrito }
        val pVerm = Paint(pCelulaB).apply { color = Color.rgb(198, 40, 40) }
        val pCab = Paint(pCelulaB).apply { color = Color.WHITE }
        val pFundoCab = Paint().apply { color = Color.rgb(31, 78, 120) }
        val pZebra = Paint().apply { color = Color.rgb(238, 243, 248) }
        val pLinha = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.7f }
        val pPeq = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GRAY; textSize = 7.5f }
        val pDobra = Paint().apply { color = Color.GRAY; strokeWidth = 0.8f }

        for (f in 0 until folhas) {
            val pagina = doc.startPage(PdfDocument.PageInfo.Builder(W, H, f + 1).create())
            val cv = pagina.canvas
            cv.drawLine(W / 2f, 20f, W / 2f, H - 20f, pDobra)

            for (lado in 0..1) {
                val idx = f * 2 + lado
                val x0 = if (lado == 0) 28f else 435f

                // cabeçalho
                if (lado == 0) {
                    cv.drawText("Controle de Estoque", x0, 40f, pTitulo)
                    if (c.setor.isNotEmpty()) cv.drawText(cabe("Setor: " + c.setor, pTexto, LARG), x0, 54f, pTexto)
                    cv.drawText("Conferência: " + dataHora(c.tempo), x0, 67f, pTexto)
                } else {
                    cv.drawText(cabe("Responsável: " + c.resp, pNegrito, LARG), x0, 40f, pNegrito)
                    if (c.cargo.isNotEmpty()) cv.drawText(cabe("Cargo: " + c.cargo, pTexto, LARG), x0, 54f, pTexto)
                    cv.drawText("PDF gerado em: " + dataHora(agora), x0, 67f, pTexto)
                }
                cv.drawLine(x0, 76f, x0 + LARG, 76f, pLinha)

                // cabeçalho da tabela
                val yCab = 82f
                cv.drawRect(x0, yCab, x0 + LARG, yCab + LINHA_H, pFundoCab)
                cv.drawText("Produto", x0 + 4f, yCab + 12.5f, pCab)
                cv.drawText("Unid.", x0 + 218f, yCab + 12.5f, pCab)
                centro(cv, "Qtd", x0 + 300f, yCab + 12.5f, pCab)
                centro(cv, "Entrada", x0 + 354f, yCab + 12.5f, pCab)

                // linhas
                val ini = idx * POR_PAGINA
                val fim = minOf(linhas.size, ini + POR_PAGINA)
                var y = yCab + LINHA_H
                for (i in ini until fim) {
                    val l = linhas[i]
                    if ((i - ini) % 2 == 1) cv.drawRect(x0, y, x0 + LARG, y + LINHA_H, pZebra)
                    cv.drawText(cabe(l.nome, pCelula, 212f), x0 + 4f, y + 12.5f, pCelula)
                    cv.drawText(cabe(unidadeTxt(l), pCelula, 54f), x0 + 218f, y + 12.5f, pCelula)
                    centro(cv, l.qtd.toString(), x0 + 300f, y + 12.5f, if (l.baixo) pVerm else pCelulaB)
                    centro(cv, if (l.entrada > 0) "+" + l.entrada else "-", x0 + 354f, y + 12.5f, pCelula)
                    cv.drawLine(x0, y + LINHA_H, x0 + LARG, y + LINHA_H, pLinha)
                    y += LINHA_H
                }

                // rodapé
                cv.drawText(
                    "FD = fardo · FD10 / FD30 = fardo com 10 kg / 30 kg · Qtd em vermelho = abaixo do mínimo",
                    x0, 556f, pPeq
                )
                if (idx < paginas) centro(cv, "Pág. " + (idx + 1) + " de " + paginas, x0 + LARG / 2f, 574f, pPeq)
            }
            doc.finishPage(pagina)
        }

        val nome = "Estoque_" + SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date(agora)) + ".pdf"
        val valores = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, nome)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores)
        if (uri == null) {
            doc.close()
            return null
        }
        ctx.contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) }
        doc.close()
        return uri
    }
}
