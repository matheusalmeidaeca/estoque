package com.estoque.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Gráficos e médias de venda, estimadas a partir das conferências semanais. */
class EstatisticasActivity : Activity() {

    private fun texto(s: String, tam: Float, cor: Int, negrito: Boolean = false): TextView =
        TextView(this).apply {
            text = s
            textSize = tam
            setTextColor(cor)
            if (negrito) setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(10), 0, dp(4))
        }

    private fun un(s: Stat): String =
        if (s.unidade == "fardo") "fardos" else if (s.unidade.isEmpty()) "un." else s.unidade

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        raiz.addView(barra("Gráficos e médias"), LinearLayout.LayoutParams(MATCH, WRAP))

        val rolagem = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(24))
        }
        rolagem.addView(col)
        raiz.addView(rolagem, LinearLayout.LayoutParams(MATCH, 0, 1f))

        val st = Repo.stats()
        if (st.isEmpty()) {
            col.addView(
                texto(
                    "Para calcular as médias, faça pelo menos 2 conferências (por exemplo, duas " +
                        "segundas-feiras seguidas). Ao conferir, registre também as entradas " +
                        "(mercadoria que chegou) tocando no número de cada produto; assim a venda " +
                        "da semana sai certa.",
                    14f, Color.DKGRAY
                )
            )
        } else {
            val topo = st.take(10)
            col.addView(texto("Mais vendidos por semana (média)", 16f, Color.BLACK, true))
            col.addView(
                BarChartView(this, topo.map { it.nome }, topo.map { it.semana }),
                LinearLayout.LayoutParams(MATCH, WRAP)
            )
            val kgSem = st.filter { it.kg > 0 }.sumOf { it.semana * it.kg }
            if (kgSem > 0.0) {
                col.addView(
                    texto(
                        "Fardos de 10/30 kg: ≈ ${fmt(kgSem)} kg por semana · ≈ ${fmt(kgSem * 30.0 / 7.0)} kg por mês",
                        13f, Color.DKGRAY
                    )
                )
            }
            col.addView(texto("Por produto (toque para ver o histórico)", 16f, Color.BLACK, true))
            for (s in st) {
                val linha = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, dp(8), 0, dp(8))
                    setOnClickListener { historico(s) }
                }
                linha.addView(TextView(this).apply {
                    text = s.nome
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.BLACK)
                })
                val kgTxt = if (s.kg > 0) " (≈ ${fmt(s.mes * s.kg)} kg)" else ""
                linha.addView(TextView(this).apply {
                    text = "por semana: ${fmt(s.semana)} ${un(s)} · por mês: ${fmt(s.mes)} ${un(s)}$kgTxt"
                    textSize = 13f
                    setTextColor(Color.DKGRAY)
                })
                col.addView(linha, LinearLayout.LayoutParams(MATCH, WRAP))
                col.addView(View(this).apply { setBackgroundColor(Color.LTGRAY) }, LinearLayout.LayoutParams(MATCH, 1))
            }
            col.addView(
                texto(
                    "Venda da semana = quantidade da conferência anterior + entradas − quantidade atual. " +
                        "O mês usa 30 dias.",
                    12f, Color.GRAY
                )
            )
        }
        setContentView(raiz)
    }

    private fun historico(s: Stat) {
        val pts = Repo.contagens.sortedBy { it.tempo }.mapNotNull { c ->
            c.linhas.firstOrNull { it.id == s.id }?.let { Pair(c.tempo, it.qtd.toFloat()) }
        }
        val conteudo: View = if (pts.size >= 2) {
            LineChartView(this, pts.map { it.first }, pts.map { it.second })
        } else {
            texto("Poucos dados ainda.", 14f, Color.DKGRAY)
        }
        AlertDialog.Builder(this)
            .setTitle(s.nome)
            .setMessage("Quantidade em estoque a cada conferência")
            .setView(conteudo)
            .setPositiveButton("Fechar", null)
            .show()
    }
}
