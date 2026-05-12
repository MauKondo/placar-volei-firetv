# Placar de Vôlei — Fire TV

Aplicativo Android nativo para **Amazon Fire TV Stick** que exibe um placar de
vôlei em tela cheia, controlado remotamente por qualquer celular via navegador
web (sem instalar app no celular).

A comunicação é feita por um servidor HTTP embutido no próprio app
(NanoHTTPD), rodando em rede local. Sem internet, sem nuvem.

---

## Features

- **Placar broadcast-grade** em 10-foot UI (otimizado pra TV)
- **Controle via celular** — qualquer navegador na mesma Wi-Fi
- **Modos de pontuação:**
  - `FIVB_DEFAULT` — sets até 25, decisivo até 15
  - `ALL_25` — todos os sets até 25
  - `ALL_15` — todos os sets até 15
- **Melhor de** 3, 5, 7, 9 ou 11 sets
- **Confirmação manual de fim de set** (evita clique acidental)
- **Indicador de saque** com rally point automático
- **Histórico de sets** anteriores
- **Cronômetro do set atual** + relógio
- **Dark / Light mode** com toggle pelo celular
- **PIN auth** nos endpoints de mutação (GETs ficam livres)
- **Persistência** automática — fecha app, reabre, volta tudo
- **Foreground Service** mantém servidor vivo
- **Nomes de times editáveis** a qualquer momento

---

## Requisitos

- **Hardware:** Amazon Fire TV Stick (Fire OS 5+ / Android 5.1+, API 22+)
- **Dev:** Android Studio Hedgehog ou superior, JDK 17
- **Build:** Gradle 8.x, Android Gradle Plugin 8.x
- **Rede:** Fire Stick e celular na mesma Wi-Fi

---

## Arquitetura

```
┌────────────────────────────────────────┐
│ PlacarActivity (UI da TV)              │
│ - Exibe placar, sets, hora, cronômetro │
│ - Observa GameState                    │
└────────────────────────────────────────┘
          ↑ observa via listener
┌────────────────────────────────────────┐
│ GameState (singleton)                  │
│ - Estado completo da partida           │
│ - Lógica de regras de vôlei            │
│ - Persiste em SharedPreferences        │
└────────────────────────────────────────┘
          ↑ modificado por
┌────────────────────────────────────────┐
│ PlacarService (Foreground Service)     │
│ - NanoHTTPD na porta 8080              │
│ - Endpoints REST                       │
│ - Serve HTML/CSS/JS do controle        │
└────────────────────────────────────────┘
          ↑ recebe HTTP de
┌────────────────────────────────────────┐
│ Celular (browser, rede local)          │
│ - HTML servido por /                   │
│ - Polling em /state a cada 1s          │
└────────────────────────────────────────┘
```

**Stack:**
- Java (não Kotlin)
- NanoHTTPD 2.3.1
- Gson 2.10.1
- AndroidX AppCompat
- HTML + CSS + JS puro no controle

---

## Setup

### 1. Clone

```bash
git clone https://github.com/MauKondo/placar-volei-firetv.git
cd placar-volei-firetv
```

### 2. Configure o PIN

O PIN protege os endpoints de mutação. Sem PIN, qualquer pessoa na Wi-Fi
poderia bagunçar o placar.

```bash
cp secrets.properties.example secrets.properties
```

Edite `secrets.properties` e troque para um PIN só você sabe:

```properties
PIN=8421
```

`secrets.properties` está no `.gitignore` — nunca vai pro GitHub.

### 3. Abre no Android Studio

`File → Open` → seleciona pasta. Aguarda Gradle sync (5–10 min na primeira vez).

Verifica `File → Settings → Build → Build Tools → Gradle → Gradle JDK` está em
17 (embutido).

### 4. Build

```bash
./gradlew assembleDebug
```

APK em `app/build/outputs/apk/debug/app-debug.apk`.

---

## Instalação no Fire TV Stick

### 1. Habilita ADB no Stick

- `Settings → My Fire TV → About` → clica "Fire TV Stick" 7x → vira developer
- Volta → `Developer Options` → **ADB debugging: ON**
- `Settings → My Fire TV → About → Network` → anota IP (ex `192.168.0.20`)

### 2. Conecta via ADB do PC

```bash
adb connect 192.168.0.20:5555
adb devices
```

Primeira conexão: Fire Stick mostra popup "Allow ADB?" → **Always allow**.

### 3. Instala

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Ou pelo Android Studio: dropdown de devices → seleciona Fire Stick → Run ▶.

### 4. Abre no Stick

Carrossel `Your Apps & Channels` → **Placar Vôlei**.

Ou por ADB:

```bash
adb shell am start -n com.mauricio.placarvolei/.PlacarActivity
```

---

## Uso

1. **Liga o app no Fire Stick.** A TV mostra o placar zerado e o endereço de
   controle no rodapé, ex: `Controle: http://192.168.0.42:8080`
2. **No celular**, abre o navegador na mesma Wi-Fi e acessa essa URL.
3. **Primeiro clique** o controle pede o PIN. Digita o que configurou em
   `secrets.properties`. Salva em localStorage — não pede de novo.
4. Comparte verbalmente o PIN com quem deve poder controlar.

### Endpoints (caso queira automatizar)

Todos os POSTs exigem header `X-Pin: <seu_pin>`.

```
GET  /state                   estado completo em JSON (livre)
POST /score/A/plus            +1 Time A
POST /score/A/minus           -1
POST /score/B/plus            +1 Time B
POST /score/B/minus           -1
POST /serve/toggle            alterna quem saca
POST /set/confirm-close       confirma fim de set
POST /set/reject-close        desfaz (clique acidental)
POST /teams/rename            body: {"teamA":"Brasil","teamB":"Argentina"}
POST /match/best-of           body: {"bestOf":5}
POST /match/scoring-mode      body: {"mode":"FIVB_DEFAULT"}
POST /theme                   body: {"theme":"light"}
POST /match/new               nova partida (mantém config)
POST /match/reset             reset total
```

---

## Segurança

- **Wi-Fi local apenas** — servidor só escuta em 0.0.0.0:8080, sem exposição
  pra internet. Não use atrás de NAT aberto.
- **PIN obrigatório em POST** — protege contra trote casual. Não é uma
  fortaleza, é uma barreira mínima. Suficiente pra jogo entre amigos.
- **PIN nunca commitado** — fica em `secrets.properties` local.
- **GET livre** — qualquer um na Wi-Fi pode ver placar, ninguém pode mexer
  sem PIN.

### Quero trocar o PIN

Edita `secrets.properties`, rebuild, reinstala. Celulares conectados pegam
401 no próximo POST → re-prompt automático do PIN.

---

## Troubleshooting

**App não aparece no carrossel do Fire TV**
Verifica que o manifest tem `LEANBACK_LAUNCHER` (já configurado). Reinstala
APK.

**Celular não acessa a URL**
- Confirma que está na mesma Wi-Fi que o Fire Stick.
- Wi-Fi com "isolamento de clientes" bloqueia comunicação peer-to-peer —
  desliga no roteador ou usa outra rede.
- Testa pelo PC primeiro: `curl http://<ip>:8080/state`

**Servidor cai quando saio do app**
Não deveria — é Foreground Service. Confirma que a notificação "Placar de
Vôlei" continua aparecendo no Fire Stick após mudar de app. Se sumiu,
reinicia o app.

**Emulador não roda** (dev)
Precisa virtualização habilitada na BIOS (SVM Mode em AMD, VT-x em Intel) +
Windows Hypervisor Platform / WHPX. Veja docs Android Studio.

---

## Licença

Use à vontade pessoal. Sem garantia. Não foi feito pra Play Store ou Amazon
Appstore — só instalação manual.

---

## Stack

| Camada | Tecnologia |
|---|---|
| App nativo | Android Java, AppCompat, min API 22 |
| Servidor HTTP | NanoHTTPD 2.3.1 |
| Serialização | Gson 2.10.1 |
| Persistência | SharedPreferences |
| Frontend controle | HTML + CSS + JS puro |
| Tipografia TV | sans-serif-condensed (Roboto Condensed sistema) |
| Tipografia controle | Saira Condensed + Saira + JetBrains Mono (Google Fonts) |
| Build | Gradle 8.x + AGP 8.x |
