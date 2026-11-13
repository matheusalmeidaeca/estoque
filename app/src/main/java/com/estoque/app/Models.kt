package com.estoque.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.Collator
import java.util.Locale

data class Produto(
    val id: Long,
    var nome: String,
    var unidade: String,
    var kg: Int,
    var minimo: Int,
    var obs: String,
    var qtd: Int,
    var entrada: Int
) {
    val baixo: Boolean get() = minimo > 0 && qtd < minimo
}

data class Linha(
    val id: Long,
    val nome: String,
    val unidade: String,
    val kg: Int,
    val qtd: Int,
    val entrada: Int,
    val minimo: Int
) {
    val baixo: Boolean get() = minimo > 0 && qtd < minimo
}

data class Contagem(
    val id: Long,
    val tempo: Long,
    val resp: String,
    val cargo: String,
    val setor: String,
    val linhas: List<Linha>
)

class Stat(
    val id: Long,
    val nome: String,
    val unidade: String,
    val kg: Int,
    val semana: Double,
    val mes: Double
)

object Repo {
    private var ctx: Context? = null
    private var seq = System.currentTimeMillis()

    val produtos = mutableListOf<Produto>()
    val contagens = mutableListOf<Contagem>() // ordem cronológica
    var respNome = ""
    var respCargo = ""
    var setor = ""

    val collator: Collator =
        Collator.getInstance(Locale("pt", "BR")).apply { strength = Collator.PRIMARY }

    private fun prefs() = ctx!!.getSharedPreferences("estoque", Context.MODE_PRIVATE)

    fun init(c: Context) {
        if (ctx != null) return
        ctx = c.applicationContext
        carregar()
    }

    fun novoId(): Long {
        seq += 1
        return seq
    }

    fun kgDoNome(nome: String): Int =
        Regex("\\b(?:FD|DF)(\\d+)\\b", RegexOption.IGNORE_CASE)
            .find(nome)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    fun novoProduto(nome: String, qtd: Int = 0): Produto {
        val kg = kgDoNome(nome)
        return Produto(novoId(), nome, if (kg > 0) "fardo" else "", kg, 0, "", qtd, 0)
    }

    fun ordenados(): List<Produto> =
        produtos.sortedWith(Comparator<Produto> { a, b -> collator.compare(a.nome, b.nome) })

    private fun carregar() {
        val p = prefs()
        respNome = p.getString("resp_nome", "") ?: ""
        respCargo = p.getString("resp_cargo", "") ?: ""
        setor = p.getString("setor", "") ?: ""
        produtos.clear()
        contagens.clear()

        val txt = p.getString("produtos", null)
        if (txt != null) {
            val a = JSONArray(txt)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                produtos.add(
                    Produto(
                        o.getLong("id"), o.optString("n"), o.optString("u"), o.optInt("kg"),
                        o.optInt("m"), o.optString("o"), o.optInt("q"), o.optInt("e")
                    )
                )
            }
        } else {
            val antigo = p.getString("dados", null)
            if (antigo != null) { // migra da versão 1
                val a = JSONArray(antigo)
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    val pr = novoProduto(o.optString("n"), o.optInt("q"))
                    pr.minimo = o.optInt("m")
                    pr.obs = o.optString("o")
                    val u = o.optString("u")
                    if (u.isNotEmpty()) pr.unidade = u
                    produtos.add(pr)
                }
            } else {
                ctx!!.assets.open("produtos.txt").bufferedReader().useLines { linhas ->
                    for (l in linhas) {
                        val n = l.trim()
                        if (n.isNotEmpty()) produtos.add(novoProduto(n))
                    }
                }
            }
            salvar()
        }

        val ct = p.getString("contagens", null)
        if (ct != null) {
            val a = JSONArray(ct)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val la = o.getJSONArray("l")
                val ls = ArrayList<Linha>()
                for (j in 0 until la.length()) {
                    val x = la.getJSONObject(j)
                    ls.add(
                        Linha(
                            x.getLong("id"), x.optString("n"), x.optString("u"), x.optInt("kg"),
                            x.optInt("q"), x.optInt("e"), x.optInt("m")
                        )
                    )
                }
                contagens.add(
                    Contagem(
                        o.getLong("id"), o.getLong("t"), o.optString("r"),
                        o.optString("c"), o.optString("s"), ls
                    )
                )
            }
        }
    }

    fun salvar() {
        val a = JSONArray()
        for (p in produtos) {
            a.put(
                JSONObject().put("id", p.id).put("n", p.nome).put("u", p.unidade)
                    .put("kg", p.kg).put("m", p.minimo).put("o", p.obs)
                    .put("q", p.qtd).put("e", p.entrada)
            )
        }
        val c = JSONArray()
        for (k in contagens) {
            val la = JSONArray()
            for (l in k.linhas) {
                la.put(
                    JSONObject().put("id", l.id).put("n", l.nome).put("u", l.unidade)
                        .put("kg", l.kg).put("q", l.qtd).put("e", l.entrada).put("m", l.minimo)
                )
            }
            c.put(
                JSONObject().put("id", k.id).put("t", k.tempo).put("r", k.resp)
                    .put("c", k.cargo).put("s", k.setor).put("l", la)
            )
        }
        prefs().edit()
            .putString("produtos", a.toString())
            .putString("contagens", c.toString())
            .putString("resp_nome", respNome)
            .putString("resp_cargo", respCargo)
            .putString("setor", setor)
            .apply()
    }

    /** Fecha a conferência: guarda uma foto do estoque no histórico. */
    fun finalizar(): Contagem {
        val agora = System.currentTimeMillis()
        val ls = ordenados().map {
            Linha(it.id, it.nome, it.unidade, it.kg, it.qtd, it.entrada, it.minimo)
        }
        val c = Contagem(agora, agora, respNome, respCargo, setor, ls)
        contagens.add(c)
        for (p in produtos) p.entrada = 0
        salvar()
        return c
    }

    /**
     * Venda entre duas conferências = anterior + entradas - atual.
     * Semanas em que o resultado fica negativo (chegou mercadoria sem registro) são ignoradas.
     */
    fun stats(): List<Stat> {
        val cs = contagens.sortedBy { it.tempo }
        val venda = HashMap<Long, Double>()
        val dias = HashMap<Long, Double>()
        val ultimo = HashMap<Long, Linha>()
        for (i in 1 until cs.size) {
            val d = (cs[i].tempo - cs[i - 1].tempo) / 86400000.0
            if (d <= 0.0) continue
            val ant = cs[i - 1].linhas.associateBy { it.id }
            for (l in cs[i].linhas) {
                ultimo[l.id] = l
                val a = ant[l.id] ?: continue
                val v = a.qtd + l.entrada - l.qtd
                if (v < 0) continue
                venda[l.id] = (venda[l.id] ?: 0.0) + v
                dias[l.id] = (dias[l.id] ?: 0.0) + d
            }
        }
        val res = ArrayList<Stat>()
        for ((id, d) in dias) {
            val v = venda[id] ?: 0.0
            val l = ultimo[id] ?: continue
            res.add(Stat(id, l.nome, l.unidade, l.kg, v / d * 7.0, v / d * 30.0))
        }
        res.sortByDescending { it.semana }
        return res
    }
}
