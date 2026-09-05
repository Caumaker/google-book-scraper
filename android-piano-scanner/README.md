# PianoScan

App Android que fotografa uma partitura, interpreta as notas com IA e toca a musica
em piano, com arranjo, dentro do proprio aparelho.

## Como funciona

1. **Captura** (`ui/CameraCapture.kt`, `ui/CaptureScreen.kt`): camera via CameraX ou
   galeria, uma ou varias paginas. A imagem e normalizada (EXIF, escala max 1800 px)
   em `data/ImageUtils.kt`.
2. **Leitura** (`data/ClaudeVisionClient.kt`): as paginas vao para a Messages API da
   Anthropic com um system prompt de OMR. O modelo devolve JSON estrito com armadura,
   formula de compasso, andamento, cifras e cada nota (MIDI, inicio em batidas,
   duracao, mao, dinamica). O prefill `{` obriga a resposta a comecar no JSON.
3. **Arranjo** (`music/Arranger.kt`, `music/Chords.kt`): a harmonia sai da cifra escrita
   ou e deduzida por peso de duracao das classes de altura. Seis estilos: Original,
   Piano solo, Balada, Arpejo, Jazz (swing 2:1 e baixo caminhante) e Valsa. Micro
   variacoes de tempo e dinamica evitam o som mecanico.
4. **Som** (`music/PianoSynth.kt`, `music/PlaybackEngine.kt`): sintese aditiva com 14
   parciais inarmonicos por nota, decaimento por parcial, ataque dependente da
   dinamica, panorama por altura e reverb de placa. Sai por AudioTrack em float,
   blocos de 256 quadros, ate 28 vozes. Sem samples e sem SoundFont.
5. **Visual** (`ui/StaffView.kt`): pauta dupla rolavel com clave de sol e de fa
   desenhadas em vetor, linhas suplementares, hastes, colchetes, cifras e cursor de
   execucao. Abaixo, um teclado que acende as teclas tocadas.
6. **Exportacao**: `.mid` (SMF formato 1, uma trilha por mao) e `.wav` (render offline
   16 bits estereo), compartilhados via FileProvider.

## Rodar

```bash
export ANDROID_HOME=/caminho/do/android-sdk
./gradlew assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

Requisitos: JDK 17, Android SDK 35, minSdk 26.

Na primeira execucao, abra o icone de engrenagem e cole a chave da API da Anthropic.
Ela e guardada com EncryptedSharedPreferences e so trafega para `api.anthropic.com`.
Sem chave o app ainda toca a peca de demonstracao embutida (Hino a Alegria).

## Limites conhecidos

- A qualidade da transcricao depende do enquadramento: pauta reta, boa luz, sem sombra.
- Repeticoes, quialteras e ornamentos sao transcritos expandidos, nem sempre com fidelidade.
- A renderizacao da pauta e uma leitura visual do resultado, nao um editor de partitura.
