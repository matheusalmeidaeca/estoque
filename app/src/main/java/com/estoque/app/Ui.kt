package com.estoque.app

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

fun norm(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

fun dataHora(t: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(t))

fun fmt(d: Double): String = String.format(Locale("pt", "BR"), "%.1f", d)

fun Context.campo(rotulo: String, valor: String, numerico: Boolean): EditText =
    EditText(this).apply {
        hint = rotulo
        setText(valor)
        setSelectAllOnFocus(true)
        inputType = if (numerico) {
            InputType.TYPE_CLASS_NUMBER
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
    }

/** Campo com legenda em cima (a dica some quando o campo tem texto). */
fun Context.rotulado(rotulo: String, et: EditText): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(0, dp(6), 0, 0)
    addView(TextView(context).apply {
        text = rotulo
        textSize = 12f
        setTextColor(Color.GRAY)
    })
    addView(et, LinearLayout.LayoutParams(MATCH, WRAP))
}

fun Context.barra(titulo: String): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    setBackgroundColor(Color.parseColor("#1F4E78"))
    setPadding(dp(16), dp(14), dp(16), dp(14))
    addView(TextView(context).apply {
        text = titulo
        textSize = 18f
        setTypeface(null, Typeface.BOLD)
        setTextColor(Color.WHITE)
    })
}

class Hamburguer(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val bw = w * 0.5f
        val bh = h * 0.07f
        val x = (w - bw) / 2f
        for (i in -1..1) {
            val y = h / 2f + i * h * 0.17f - bh / 2f
            cv.drawRoundRect(x, y, x + bw, y + bh, bh / 2f, bh / 2f, p)
        }
    }
}

fun Context.pedirResponsavel(aoSalvar: () -> Unit) {
    val nome = campo("", Repo.respNome, false)
    val cargo = campo("", Repo.respCargo, false)
    val setor = campo("", Repo.setor, false)
    val caixa = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(8), dp(20), 0)
        addView(rotulado("Nome do responsável", nome), LinearLayout.LayoutParams(MATCH, WRAP))
        addView(rotulado("Cargo", cargo), LinearLayout.LayoutParams(MATCH, WRAP))
        addView(
            rotulado("Setor / descrição (ex.: Mercearia)", setor),
            LinearLayout.LayoutParams(MATCH, WRAP)
        )
    }
    AlertDialog.Builder(this)
        .setTitle("Responsável pelo documento")
        .setView(caixa)
        .setPositiveButton("Salvar") { _, _ ->
            val n = nome.text.toString().trim()
            if (n.isEmpty()) {
                toast("Digite o nome do responsável")
                return@setPositiveButton
            }
            Repo.respNome = n
            Repo.respCargo = cargo.text.toString().trim()
            Repo.setor = setor.text.toString().trim()
            Repo.salvar()
            aoSalvar()
        }
        .setNegativeButton("Cancelar", null)
        .show()
}

fun Context.abrirPdf(uri: Uri) {
    try {
        startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    } catch (e: Exception) {
        toast("Nenhum leitor de PDF encontrado. O arquivo está em Downloads.")
    }
}

fun Context.compartilharPdf(uri: Uri) {
    val i = Intent(Intent.ACTION_SEND).setType("application/pdf")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val ch = Intent.createChooser(i, "Compartilhar PDF")
    ch.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(ch)
}

fun Context.gerarEMostrarPdf(c: Contagem, aoFechar: () -> Unit) {
    val uri = try {
        PdfGerador.gerar(this, c)
    } catch (e: Exception) {
        null
    }
    if (uri == null) {
        AlertDialog.Builder(this)
            .setTitle("Não consegui gerar o PDF")
            .setMessage("A conferência está salva no histórico. Abra-a por lá e toque em Gerar PDF para tentar de novo.")
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ -> aoFechar() }
            .show()
        return
    }
    AlertDialog.Builder(this)
        .setTitle("PDF salvo")
        .setMessage("O arquivo foi salvo na pasta Downloads.")
        .setCancelable(false)
        .setPositiveButton("Abrir") { _, _ ->
            abrirPdf(uri)
            aoFechar()
        }
        .setNeutralButton("Compartilhar") { _, _ ->
            compartilharPdf(uri)
            aoFechar()
        }
        .setNegativeButton("Fechar") { _, _ -> aoFechar() }
        .show()
}

private fun csv(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""

fun Context.exportarCsv() {
    val sb = StringBuilder("Produto;Unidade;Kg por fardo;Quantidade;Estoque mínimo;Observações\n")
    for (p in Repo.ordenados()) {
        sb.append(csv(p.nome)).append(';').append(csv(p.unidade)).append(';')
            .append(p.kg).append(';').append(p.qtd).append(';')
            .append(p.minimo).append(';').append(csv(p.obs)).append('\n')
    }
    val i = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "Estoque")
        .putExtra(Intent.EXTRA_TEXT, sb.toString())
    startActivity(Intent.createChooser(i, "Exportar estoque"))
}
