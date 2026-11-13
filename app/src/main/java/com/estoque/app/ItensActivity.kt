package com.estoque.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView

/** Editar itens: nome, unidade, peso do fardo, mínimo e observações; incluir e excluir. */
class ItensActivity : Activity() {

    private val visiveis = mutableListOf<Produto>()
    private var filtro = ""
    private lateinit var adapter: BaseAdapter

    private fun lp() = LinearLayout.LayoutParams(MATCH, WRAP)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        raiz.addView(barra("Editar itens"), lp())

        val novo = Button(this).apply {
            text = "+ NOVO ITEM"
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2E7D32"))
            setOnClickListener { abrirForm(null) }
        }
        raiz.addView(novo, LinearLayout.LayoutParams(MATCH, dp(48)).apply {
            setMargins(dp(12), dp(8), dp(12), 0)
        })

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
            setMargins(dp(12), dp(4), dp(12), 0)
        })

        val lista = ListView(this)
        adapter = object : BaseAdapter() {
            override fun getCount(): Int = visiveis.size
            override fun getItem(position: Int): Any = visiveis[position]
            override fun getItemId(position: Int): Long = visiveis[position].id
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val p = visiveis[position]
                val col = LinearLayout(this@ItensActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), dp(10), dp(16), dp(10))
                }
                col.addView(TextView(this@ItensActivity).apply {
                    text = p.nome
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.BLACK)
                })
                val detalhe = listOfNotNull(
                    p.unidade.takeIf { it.isNotEmpty() },
                    if (p.kg > 0) "${p.kg} kg/fardo" else null,
                    if (p.minimo > 0) "mín. ${p.minimo}" else null,
                    p.obs.takeIf { it.isNotEmpty() }
                ).joinToString(" · ")
                if (detalhe.isNotEmpty()) {
                    col.addView(TextView(this@ItensActivity).apply {
                        text = detalhe
                        textSize = 12f
                        setTextColor(Color.GRAY)
                    })
                }
                return col
            }
        }
        lista.adapter = adapter
        lista.setOnItemClickListener { _, _, pos, _ -> abrirForm(visiveis[pos]) }
        raiz.addView(lista, LinearLayout.LayoutParams(MATCH, 0, 1f))

        setContentView(raiz)
        atualizar()
    }

    private fun atualizar() {
        val q = norm(filtro.trim())
        visiveis.clear()
        for (p in Repo.ordenados()) {
            if (q.isNotEmpty() && !norm(p.nome).contains(q)) continue
            visiveis.add(p)
        }
        adapter.notifyDataSetChanged()
    }

    private fun abrirForm(existente: Produto?) {
        val fNome = campo("", existente?.nome ?: "", false)
        val fUn = campo("", existente?.unidade ?: "", false)
        val fKg = campo("", (existente?.kg ?: 0).toString(), true)
        val fMin = campo("", (existente?.minimo ?: 0).toString(), true)
        val fObs = campo("", existente?.obs ?: "", false)
        val caixa = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(rotulado("Nome do produto", fNome), lp())
            addView(rotulado("Unidade (ex.: fardo, unidade, caixa)", fUn), lp())
            addView(rotulado("Peso do fardo em kg (0 se não for fardo)", fKg), lp())
            addView(rotulado("Estoque mínimo", fMin), lp())
            addView(rotulado("Observações", fObs), lp())
        }
        val rolagem = ScrollView(this).apply { addView(caixa) }

        val b = AlertDialog.Builder(this)
            .setTitle(if (existente == null) "Novo item" else "Editar item")
            .setView(rolagem)
            .setPositiveButton("Salvar") { _, _ ->
                val nome = fNome.text.toString().trim().replace(Regex("\\s+"), " ")
                if (nome.isEmpty()) {
                    toast("Digite o nome do produto")
                    return@setPositiveButton
                }
                if (Repo.produtos.any { it !== existente && norm(it.nome) == norm(nome) }) {
                    toast("Já existe um produto com esse nome")
                    return@setPositiveButton
                }
                var un = fUn.text.toString().trim()
                var kg = fKg.text.toString().toIntOrNull() ?: 0
                if (kg == 0) kg = Repo.kgDoNome(nome)
                if (un.isEmpty() && kg > 0) un = "fardo"
                val min = fMin.text.toString().toIntOrNull() ?: 0
                val obs = fObs.text.toString().trim()
                val alvo = existente ?: Repo.novoProduto(nome).also { Repo.produtos.add(it) }
                alvo.nome = nome
                alvo.unidade = un
                alvo.kg = kg
                alvo.minimo = min
                alvo.obs = obs
                Repo.salvar()
                atualizar()
            }
            .setNegativeButton("Cancelar", null)

        if (existente != null) {
            b.setNeutralButton("Excluir") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("Excluir item?")
                    .setMessage(existente.nome + "\n\nAs conferências antigas continuam no histórico.")
                    .setPositiveButton("Excluir") { _, _ ->
                        Repo.produtos.removeAll { it === existente }
                        Repo.salvar()
                        atualizar()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }
        b.show()
    }
}
