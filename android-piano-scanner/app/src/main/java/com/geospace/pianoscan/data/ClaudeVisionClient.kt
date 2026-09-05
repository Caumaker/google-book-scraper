package com.geospace.pianoscan.data

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Transcreve a foto de uma partitura em [Score] usando a Messages API da Anthropic.
 */
class ClaudeVisionClient(private val apiKey: String) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun transcribe(pages: List<Bitmap>, hint: String = ""): Result<Score> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(apiKey.isNotBlank()) { "Configure a chave da API nas Preferencias." }
                require(pages.isNotEmpty()) { "Nenhuma imagem para transcrever." }

                val content = buildJsonArray {
                    pages.forEachIndexed { index, bitmap ->
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", "Pagina ${index + 1} de ${pages.size}:")
                        })
                        add(buildJsonObject {
                            put("type", "image")
                            putJsonObject("source") {
                                put("type", "base64")
                                put("media_type", "image/jpeg")
                                put("data", ImageUtils.toBase64Jpeg(bitmap))
                            }
                        })
                    }
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", userPrompt(hint))
                    })
                }

                val body = buildJsonObject {
                    put("model", MODEL)
                    put("max_tokens", 16000)
                    put("temperature", 0)
                    put("system", SYSTEM_PROMPT)
                    put("messages", buildJsonArray {
                        add(buildJsonObject {
                            put("role", "user")
                            put("content", content)
                        })
                        // Prefill: forca a resposta a comecar direto no JSON.
                        add(buildJsonObject {
                            put("role", "assistant")
                            put("content", "{")
                        })
                    })
                }

                val request = Request.Builder()
                    .url(ENDPOINT)
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", "2023-06-01")
                    .addHeader("content-type", "application/json")
                    .post(body.toString().toRequestBody(JSON_MEDIA))
                    .build()

                http.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IllegalStateException("API ${response.code}: ${errorMessage(raw)}")
                    }
                    val text = extractText(raw)
                    val payload = repairJson(text)
                    json.decodeFromString(Score.serializer(), payload)
                }
            }
        }

    private fun errorMessage(raw: String): String = runCatching {
        json.parseToJsonElement(raw).jsonObject["error"]?.jsonObject
            ?.get("message")?.jsonPrimitive?.content ?: raw.take(300)
    }.getOrDefault(raw.take(300))

    private fun extractText(raw: String): String {
        val blocks = json.parseToJsonElement(raw).jsonObject["content"] as? JsonArray
            ?: throw IllegalStateException("Resposta sem conteudo.")
        return blocks.mapNotNull { block ->
            val obj = block as? JsonObject ?: return@mapNotNull null
            if (obj["type"]?.jsonPrimitive?.content == "text") {
                obj["text"]?.jsonPrimitive?.content
            } else null
        }.joinToString("")
    }

    /** O prefill remove a primeira chave; devolve o objeto completo e limpo. */
    private fun repairJson(text: String): String {
        val restored = if (text.trimStart().startsWith("{")) text else "{$text"
        val start = restored.indexOf('{')
        val end = restored.lastIndexOf('}')
        if (start < 0 || end <= start) throw IllegalStateException("O modelo nao devolveu JSON valido.")
        return restored.substring(start, end + 1)
    }

    private fun userPrompt(hint: String): String = buildString {
        append(
            """
            Transcreva a partitura das imagens acima para o JSON descrito no system prompt.
            Leia as duas pautas (clave de sol na mao direita, clave de fa na mao esquerda),
            respeite a armadura de clave, acidentes ocorrentes, ligaduras de valor, pontos de
            aumento, quialteras, repeticoes (escreva os compassos repetidos de forma expandida)
            e a indicacao de andamento. Se a foto cortar parte da musica, transcreva apenas o
            que estiver legivel. Responda somente com o JSON.
            """.trimIndent()
        )
        if (hint.isNotBlank()) {
            append("\n\nContexto dado pelo usuario: ")
            append(hint.take(500))
        }
    }

    companion object {
        const val MODEL = "claude-opus-5"
        private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        private val SYSTEM_PROMPT = """
            Voce e um sistema de OMR (Optical Music Recognition) especializado em partituras
            de piano. Recebe fotos de partituras e devolve a musica em JSON estrito.

            Formato obrigatorio da resposta (apenas o objeto JSON, sem markdown, sem comentarios):

            {
              "title": "string",
              "composer": "string",
              "keySignature": "ex.: G major, D minor",
              "timeSignature": "ex.: 4/4, 3/4, 6/8",
              "tempoBpm": 96,
              "notes": "observacoes curtas sobre a leitura, trechos ilegiveis, etc",
              "measures": [
                {
                  "number": 1,
                  "chordSymbol": "cifra do compasso, ex.: Cmaj7. Deduza da harmonia se nao estiver escrita",
                  "notes": [
                    {
                      "pitch": "C4",
                      "midi": 60,
                      "startBeat": 0.0,
                      "durationBeats": 1.0,
                      "hand": "right",
                      "velocity": 82
                    }
                  ]
                }
              ]
            }

            Regras:
            - "midi" e o numero MIDI da nota. C4 (do central) = 60. Sempre coerente com "pitch".
            - "startBeat" e o inicio da nota DENTRO do compasso, em semininas, comecando em 0.
            - "durationBeats": semibreve 4.0, minima 2.0, seminima 1.0, colcheia 0.5,
              semicolcheia 0.25, ponto de aumento multiplica por 1.5. Em compassos X/8 uma
              colcheia continua valendo 0.5.
            - "hand" e "right" para a pauta superior e "left" para a inferior.
            - Notas simultaneas (acordes) sao entradas separadas com o mesmo "startBeat".
            - Ligaduras de valor: some as duracoes na primeira nota e nao repita a segunda.
            - "velocity" de 1 a 127, seguindo as dinamicas escritas (pp 35, p 50, mf 75, f 100, ff 115).
            - Compassos numerados em sequencia a partir de 1, sem buracos.
            - Se o andamento nao estiver escrito, estime pelo carater da peca.
            - Nunca invente compassos que nao estao na foto.
        """.trimIndent()
    }
}
