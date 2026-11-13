package com.estoque.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/** Nova Tabela: tela de conferência (contagem) do estoque. */
class ContagemActivity : Activity() {

    private val visiveis = mutableListOf<Produto>()
    private var filtro = ""
    private var soBaixos = false
    private lateinit var lista: ListView
    private lateinit var resumo: TextView
    private lateinit var adapter: LinhaAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        raiz.addView(barra("Nova Tabela"), LinearLayout.LayoutParams(MATCH, WRAP))

        val busca = EditText(this).apply {
            hint = "Buscar produto"
            setSingleLine(true)
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    filtro = s?.toString() ?: ""
                    atualizar()
                }
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            })
        }
        raiz.addView(busca, LinearLayout.LayoutParams(MATCH, WRAP).apply {
            setMargins(dp(12), dp(6), dp(12), 0)
        })

        val chk = CheckBox(this).apply {
            text = "Só abaixo do mínimo"
            setPadding(dp(4), 0, 0, 0)
            setOnCheckedChangeListener { _, v ->
                soBaixos = v
                atualizar()
            }
        }
        raiz.addView(chk, LinearLayout.LayoutParams(MATCH, WRAP).apply {
            setMargins(dp(12), 0, dp(12), 0)
        })

        resumo = TextView(this).apply {
            setPadding(dp(16), dp(2), dp(16), dp(4))
            setTextColor(Color.GRAY)
            textSize = 13f
        }
        raiz.addView(resumo, LinearLayout.LayoutParams(MATCH, WRAP))

        lista = ListView(this)
        adapter = LinhaAdapter()
        lista.adapter = adapter
        raiz.addView(lista, LinearLayout.LayoutParams(MATCH, 0, 1f))

        val fin = Button(this).apply {
            text = "FINALIZAR E GERAR PDF"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2E7D32"))
            setOnClickListener { finalizar() }
        }
        raiz.addView(fin, LinearLayout.LayoutParams(MATCH, dp(56)).apply {
            setMargins(dp(12), dp(6), dp(12), dp(10))
        })

        setContentView(raiz)
        atualizar()
    }

    inner class LinhaAdapter : BaseAdapter() {
        override fun getCount(): Int = visiveis.size
        override fun getItem(position: Int): Any = visiveis[position]
        override fun getItemId(position: Int): Long = visiveis[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val ctx = this@ContagemActivity
            val p = visiveis[position]
            val abaixo = p.baixo

            val linha = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(8), dp(8), dp(8))
                setBackgroundColor(if (abaixo) Color.parseColor("#FDECEA") else Color.WHITE)
            }

            val textos = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            textos.addView(TextView(ctx).apply {
                text = p.nome
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.BLACK)
            })
            val detalhe = listOfNotNull(
                p.unidade.takeIf { it.isNotEmpty() },
                if (p.minimo > 0) "mín. ${p.minimo}" else null,
                if (p.entrada > 0) "entrada +${p.entrada}" else null
            ).joinToString(" · ")
            if (detalhe.isNotEmpty()) {
                textos.addView(TextView(ctx).apply {
                    text = detalhe
                    textSize = 12f
                    setTextColor(Color.GRAY)
                })
            }
            linha.addView(textos, LinearLayout.LayoutParams(0, WRAP, 1f))

            fun botao(rotulo: String, acao: () -> Unit): Button = Button(ctx).apply {
                text = rotulo
                textSize = 20f
                setPadding(0, 0, 0, 0)
                minWidth = 0
                minHeight = 0
                minimumWidth = 0
                minimumHeight = 0
                setOnClickListener { acao() }
            }

            linha.addView(
                botao("-") {
                    if (p.qtd > 0) {
                        p.qtd -= 1
                        Repo.salvar()
                        atualizar()
                    }
                },
                LinearLayout.LayoutParams(dp(44), dp(44))
            )
            linha.addView(TextView(ctx).apply {
                text = p.qtd.toString()
                textSize = 20f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(if (abaixo) Color.parseColor("#C62828") else Color.BLACK)
                setOnClickListener { definirQtd(p) }
            }, LinearLayout.LayoutParams(dp(52), dp(44)))
            linha.addView(
                botao("+") {
                    p.qtd += 1
                    Repo.salvar()
                    atualizar()
                },
                LinearLayout.LayoutParams(dp(44), dp(44))
            )
            return linha
        }
    }

    private fun atualizar() {
        val q = norm(filtro.trim())
        visiveis.clear()
        for (p in Repo.ordenados()) {
            if (q.isNotEmpty() && !norm(p.nome).contains(q)) continue
            if (soBaixos && !p.baixo) continue
            visiveis.add(p)
        }
        adapter.notifyDataSetChanged()
        val baixos = Repo.produtos.count { it.baixo }
        resumo.text = "${visiveis.size} de ${Repo.produtos.size} produtos · $baixos abaixo do mínimo"
    }

    private fun definirQtd(p: Produto) {
        val fq = campo("", p.qtd.toString(), true)
        val fe = campo("", p.entrada.toString(), true)
        val caixa = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(
                rotulado("Contagem atual (quanto tem agora)", fq),
                LinearLayout.LayoutParams(MATCH, WRAP)
            )
            addView(
                rotulado("Entrada: chegou mercadoria desde a última conferência", fe),
                LinearLayout.LayoutParams(MATCH, WRAP)
            )
        }
        val d = AlertDialog.Builder(this)
            .setTitle(p.nome)
            .setView(caixa)
            .setPositiveButton("Salvar") { _, _ ->
                p.qtd = fq.text.toString().toIntOrNull() ?: 0
                p.entrada = fe.text.toString().toIntOrNull() ?: 0
                Repo.salvar()
                atualizar()
            }
            .setNegativeButton("Cancelar", null)
            .create()
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        d.show()
    }

    private fun finalizar() {
        if (Repo.respNome.isEmpty()) {
            pedirResponsavel { finalizar() }
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Finalizar conferência?")
            .setMessage("Ela será salva no histórico e o PDF será gerado na pasta Downloads.")
            .setPositiveButton("Finalizar") { _, _ ->
                val c = Repo.finalizar()
                gerarEMostrarPdf(c) { finish() }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
