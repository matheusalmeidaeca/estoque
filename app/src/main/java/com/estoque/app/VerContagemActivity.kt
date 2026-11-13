package com.estoque.app

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/** Mostra uma conferência do histórico e permite gerar o PDF de novo. */
class VerContagemActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        val id = intent.getLongExtra("id", -1L)
        val c = Repo.contagens.firstOrNull { it.id == id }
        if (c == null) {
            finish()
            return
        }

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        raiz.addView(barra("Conferência"), LinearLayout.LayoutParams(MATCH, WRAP))

        val info = buildString {
            append(dataHora(c.tempo))
            if (c.resp.isNotEmpty()) {
                append("\nResponsável: ").append(c.resp)
                if (c.cargo.isNotEmpty()) append(" — ").append(c.cargo)
            }
            if (c.setor.isNotEmpty()) append("\nSetor: ").append(c.setor)
            append("\n").append(c.linhas.size).append(" itens · ")
                .append(c.linhas.count { it.baixo }).append(" abaixo do mínimo")
        }
        raiz.addView(TextView(this).apply {
            text = info
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(dp(16), dp(10), dp(16), dp(8))
        }, LinearLayout.LayoutParams(MATCH, WRAP))

        val lista = ListView(this)
        lista.adapter = object : BaseAdapter() {
            override fun getCount(): Int = c.linhas.size
            override fun getItem(position: Int): Any = c.linhas[position]
            override fun getItemId(position: Int): Long = c.linhas[position].id
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val l = c.linhas[position]
                val linha = LinearLayout(this@VerContagemActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), dp(8), dp(16), dp(8))
                    setBackgroundColor(if (l.baixo) Color.parseColor("#FDECEA") else Color.WHITE)
                }
                linha.addView(TextView(this@VerContagemActivity).apply {
                    text = l.nome
                    textSize = 15f
                    setTextColor(Color.BLACK)
                }, LinearLayout.LayoutParams(0, WRAP, 1f))
                linha.addView(TextView(this@VerContagemActivity).apply {
                    text = l.qtd.toString() + (if (l.entrada > 0) "  (+${l.entrada})" else "")
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (l.baixo) Color.parseColor("#C62828") else Color.BLACK)
                })
                return linha
            }
        }
        raiz.addView(lista, LinearLayout.LayoutParams(MATCH, 0, 1f))

        val bt = Button(this).apply {
            text = "GERAR PDF"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2E7D32"))
            setOnClickListener { gerarEMostrarPdf(c) { } }
        }
        raiz.addView(bt, LinearLayout.LayoutParams(MATCH, dp(56)).apply {
            setMargins(dp(12), dp(6), dp(12), dp(10))
        })

        setContentView(raiz)
    }
}
