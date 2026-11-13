package com.estoque.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
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
import android.widget.PopupMenu
import android.widget.TextView

class MainActivity : Activity() {

    private val itens = mutableListOf<Contagem>()
    private lateinit var subtitulo: TextView
    private lateinit var vazio: TextView
    private lateinit var lista: ListView
    private lateinit var adapter: BaseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        val barra = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#1F4E78"))
            setPadding(dp(4), dp(4), dp(16), dp(4))
        }
        val ham = Hamburguer(this)
        ham.setOnClickListener { abrirMenu(ham) }
        barra.addView(ham, LinearLayout.LayoutParams(dp(52), dp(52)))
        barra.addView(TextView(this).apply {
            text = "Controle de Estoque"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        raiz.addView(barra, LinearLayout.LayoutParams(MATCH, WRAP))

        subtitulo = TextView(this).apply {
            setPadding(dp(16), dp(10), dp(16), 0)
            setTextColor(Color.DKGRAY)
            textSize = 13f
        }
        raiz.addView(subtitulo, LinearLayout.LayoutParams(MATCH, WRAP))

        val nova = Button(this).apply {
            text = "NOVA TABELA"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2E7D32"))
            setOnClickListener {
                startActivity(Intent(this@MainActivity, ContagemActivity::class.java))
            }
        }
        raiz.addView(nova, LinearLayout.LayoutParams(MATCH, dp(68)).apply {
            setMargins(dp(16), dp(12), dp(16), dp(8))
        })

        raiz.addView(TextView(this).apply {
            text = "Histórico de conferências"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            setPadding(dp(16), dp(8), dp(16), dp(4))
        }, LinearLayout.LayoutParams(MATCH, WRAP))

        vazio = TextView(this).apply {
            text = "Nenhuma conferência ainda. Toque em Nova Tabela para começar."
            setTextColor(Color.GRAY)
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        raiz.addView(vazio, LinearLayout.LayoutParams(MATCH, WRAP))

        lista = ListView(this)
        adapter = object : BaseAdapter() {
            override fun getCount(): Int = itens.size
            override fun getItem(position: Int): Any = itens[position]
            override fun getItemId(position: Int): Long = itens[position].id
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val c = itens[position]
                val baixos = c.linhas.count { it.baixo }
                val col = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), dp(10), dp(16), dp(10))
                }
                col.addView(TextView(this@MainActivity).apply {
                    text = dataHora(c.tempo)
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.BLACK)
                })
                col.addView(TextView(this@MainActivity).apply {
                    text = "${c.linhas.size} itens · $baixos abaixo do mínimo" +
                        (if (c.resp.isNotEmpty()) " · ${c.resp}" else "")
                    textSize = 12f
                    setTextColor(Color.GRAY)
                })
                return col
            }
        }
        lista.adapter = adapter
        lista.setOnItemClickListener { _, _, pos, _ ->
            startActivity(
                Intent(this, VerContagemActivity::class.java).putExtra("id", itens[pos].id)
            )
        }
        lista.setOnItemLongClickListener { _, _, pos, _ ->
            confirmarExcluir(itens[pos])
            true
        }
        raiz.addView(lista, LinearLayout.LayoutParams(MATCH, 0, 1f))

        setContentView(raiz)
    }

    override fun onResume() {
        super.onResume()
        atualizar()
    }

    private fun atualizar() {
        subtitulo.text = if (Repo.respNome.isEmpty()) {
            "Responsável não definido (abra o menu no canto superior esquerdo)."
        } else {
            "Responsável: " + Repo.respNome +
                (if (Repo.respCargo.isNotEmpty()) " — " + Repo.respCargo else "")
        }
        itens.clear()
        itens.addAll(Repo.contagens.sortedByDescending { it.tempo })
        vazio.visibility = if (itens.isEmpty()) View.VISIBLE else View.GONE
        adapter.notifyDataSetChanged()
    }

    private fun abrirMenu(ancora: View) {
        val m = PopupMenu(this, ancora)
        m.menu.add(0, 1, 0, "Nova Tabela")
        m.menu.add(0, 2, 1, "Editar itens")
        m.menu.add(0, 3, 2, "Gráficos e médias")
        m.menu.add(0, 4, 3, "Dados do responsável")
        m.menu.add(0, 5, 4, "Exportar estoque atual (CSV)")
        m.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> startActivity(Intent(this, ContagemActivity::class.java))
                2 -> startActivity(Intent(this, ItensActivity::class.java))
                3 -> startActivity(Intent(this, EstatisticasActivity::class.java))
                4 -> pedirResponsavel { atualizar() }
                5 -> exportarCsv()
            }
            true
        }
        m.show()
    }

    private fun confirmarExcluir(c: Contagem) {
        AlertDialog.Builder(this)
            .setTitle("Excluir conferência?")
            .setMessage(dataHora(c.tempo))
            .setPositiveButton("Excluir") { _, _ ->
                Repo.contagens.removeAll { it.id == c.id }
                Repo.salvar()
                atualizar()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
