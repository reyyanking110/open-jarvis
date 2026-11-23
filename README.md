<div align="center">

<!-- ========================================================================= -->
<!--                    3D ANIMATED HERO HEADER BANNER                         -->
<!-- ========================================================================= -->
<img src="./assets/hero-banner.svg" width="100%" alt="Open JARVIS Android 3D Wave Header" />

<br/>

<!-- ========================================================================= -->
<!--                   DYNAMIC MULTI-LINE ANIMATED SUBTITLE                    -->
<!-- ========================================================================= -->
<a href="https://github.com/reyyanking110/open-jarvis">
  <img src="./assets/typing-subtitle.svg" width="88%" alt="Dynamic Animated Subtitle" />
</a>

<br/>
<!-- ========================================================================= -->
<!--                     PREMIUM TECH STACK & STATUS BADGES                    -->
<!-- ========================================================================= -->
<p>
  <img src="https://img.shields.io/badge/PLATFORM-ANDROID_10%2B_(API_29%2B)-090D16?style=for-the-badge&logo=android&logoColor=3DDC84" alt="Android 10+" />
  <img src="https://img.shields.io/badge/LANGUAGE-KOTLIN_2.0-090D16?style=for-the-badge&logo=kotlin&logoColor=7F52FF" alt="Kotlin 2.0" />
  <img src="https://img.shields.io/badge/UI_TOOLKIT-JETPACK_COMPOSE-090D16?style=for-the-badge&logo=jetpackcompose&logoColor=4285F4" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/DI-DAGGER_HILT-090D16?style=for-the-badge&logo=android&logoColor=D4AF37" alt="Dagger Hilt" />
  <img src="https://img.shields.io/badge/DATABASE-ROOM_%2B_FTS4-090D16?style=for-the-badge&logo=sqlite&logoColor=003B57" alt="Room FTS4" />
</p>

<p>
  <img src="https://img.shields.io/badge/CLOUD_LLM-GEMINI_2.5_FLASH_%2F_PRO-090D16?style=for-the-badge&logo=googlegemini&logoColor=F59E0B" alt="Gemini 2.5" />
  <img src="https://img.shields.io/badge/EDGE_RUNTIME-LITERT--LM-090D16?style=for-the-badge&logo=tensorflow&logoColor=FF6F00" alt="LiteRT-LM" />
  <img src="https://img.shields.io/badge/SECURITY-AES--256--GCM_KEYSTORE-090D16?style=for-the-badge&logo=letsencrypt&logoColor=10B981" alt="AES-256-GCM" />
  <img src="https://img.shields.io/badge/BUILD-GRADLE_KTS-090D16?style=for-the-badge&logo=gradle&logoColor=02303A" alt="Gradle" />
</p>

<br/>

<!-- ========================================================================= -->
<!--                      PRIMARY 3D GOLD DOWNLOAD BUTTON                      -->
<!-- ========================================================================= -->
<p>
  <a href="https://github.com/reyyanking110/open-jarvis/actions/runs/45148566182">
    <img src="https://img.shields.io/badge/DOWNLOAD_DEBUG_APK-GITHUB_ACTIONS_CI_ARTIFACT-D4AF37?style=for-the-badge&logo=githubactions&logoColor=050811" height="44" alt="Download Debug APK" />
  </a>
</p>

<!-- ANIMATED DIVIDER LINE -->
<img src="https://user-images.githubusercontent.com/73097560/115834477-dbab4500-a447-11eb-908a-139a6edaec5c.gif" width="100%" alt="Animated Gradient Divider" />

<!-- QUICK NAVIGATION BAR -->
<p>
  <a href="#executive-overview"><b>Overview</b></a> &nbsp;&bull;&nbsp;
  <a href="#apk-installation-guide"><b>Download APK</b></a> &nbsp;&bull;&nbsp;
  <a href="#3d-hybrid-architecture"><b>System Architecture</b></a> &nbsp;&bull;&nbsp;
  <a href="#core-engineering-pillars"><b>Core Modules</b></a> &nbsp;&bull;&nbsp;
  <a href="#android-tool-registry"><b>Tool Matrix</b></a> &nbsp;&bull;&nbsp;
  <a href="#gemini-api-configuration"><b>API Setup</b></a> &nbsp;&bull;&nbsp;
  <a href="#offline-edge-models"><b>Offline Models</b></a> &nbsp;&bull;&nbsp;
  <a href="#build-and-verification"><b>Build &amp; Test</b></a>
</p>

</div>

---

<a id="executive-overview"></a>
## Executive Overview

**Open JARVIS Android** is a native, autonomous, hybrid AI agent engineered from the ground up for Android smartphones—inspired by [OpenJarvis](https://github.com/open-jarvis/OpenJarvis). Built with **Kotlin**, **Jetpack Compose**, **Hilt**, **Room**, **Gemini REST API**, and **LiteRT-LM** (`com.google.ai.edge.litertlm`), it unifies frontier cloud reasoning with true zero-network on-device neural execution.

Unlike passive chat wrappers, Open JARVIS operates as a **closed-loop autonomous system**: it plans multi-step workflows, inspects live device telemetry (thermal state, available RAM, battery level, network quality), dynamically routes inference between Gemini 2.5 and local quantized Gemma/Qwen models, and executes native Android OS commands behind strict user confirmation gates.

<table>
  <tr>
    <td width="33%" align="center" valign="top">
      <br/>
      <h3>Hybrid Neural Core</h3>
      <p>Seamless switching between <b>Gemini 2.5 Flash/Pro</b> (SSE streaming) and offline <b>LiteRT-LM</b> binaries on NPU, GPU, or CPU.</p>
      <br/>
    </td>
    <td width="33%" align="center" valign="top">
      <br/>
      <h3>Autonomous Loop</h3>
      <p>Deterministic <b>Plan &rarr; Tool Call &rarr; Observation &rarr; Verification &rarr; Recovery</b> engine with Room task state persistence.</p>
      <br/>
    </td>
    <td width="33%" align="center" valign="top">
      <br/>
      <h3>Hardware-Grade Security</h3>
      <p>API keys sealed in hardware <b>AndroidKeyStore</b> via <b>AES-256-GCM</b> plus mandatory approval gates for sensitive OS actions.</p>
      <br/>
    </td>
  </tr>
</table>

<img src="https://user-images.githubusercontent.com/73097560/115834477-dbab4500-a447-11eb-908a-139a6edaec5c.gif" width="100%" alt="Animated Gradient Divider" />

<a id="apk-installation-guide"></a>
## APK Installation Guide

Get the latest compiled debug build directly from the automated GitHub Actions CI pipeline without setting up Android Studio locally.

<table>
  <tr>
    <td width="60%" valign="top">
      <h3>Step-by-Step Artifact Retrieval</h3>
      <ol>
        <li>Navigate to the <a href="https://github.com/reyyanking110/open-jarvis/actions/runs/45148566182"><b>Successful Android CI Run (#45148566182)</b></a>.</li>
        <li>Scroll to the bottom of the workflow summary page to locate the <b><code>Artifacts</code></b> panel.</li>
        <li>Click <b><code>open-jarvis-debug-apk</code></b> to download the compressed build bundle.</li>
        <li>Extract the downloaded ZIP file on your PC or Android device.</li>
        <li>Install <b><code>app-debug.apk</code></b> (enable <i>Install from Unknown Sources</i> if prompted by Android Package Installer).</li>
      </ol>
    </td>
    <td width="40%" valign="top">
      <h3>CI Retention &amp; Build Integrity</h3>
      <blockquote>
        <b>Important Notice:</b> GitHub Actions build artifacts are ephemeral and automatically expire based on repository retention policies. If the direct run link expires, open the <b>Actions</b> tab in this repository and select the newest green workflow build.
      </blockquote>
      <p>
        <b>Target Package:</b> <code>app-debug.apk</code><br/>
        <b>CI Artifact Name:</b> <code>open-jarvis-debug-apk</code><br/>
        <b>Verification:</b> Automated unit tests + debug assembly
      </p>
    </td>
  </tr>
</table>

<img src="https://user-images.githubusercontent.com/73097560/115834477-dbab4500-a447-11eb-908a-139a6edaec5c.gif" width="100%" alt="Animated Gradient Divider" />

<a id="3d-hybrid-architecture"></a>
## 3D Hybrid System Architecture

The system follows a clean, reactive, dependency-injected architecture powered by **Dagger Hilt** and **Kotlin Coroutines/Flow**, isolating UI rendering from inference routing, tool execution, and persistent storage.

```mermaid
%%{init: {'theme': 'dark', 'themeVariables': { 'primaryColor': '#0B192C', 'primaryTextColor': '#F8FAFC', 'primaryBorderColor': '#D4AF37', 'lineColor': '#F59E0B', 'secondaryColor': '#050811', 'tertiaryColor': '#1E293B', 'clusterBkg': '#090D16', 'clusterBorder': '#3B82F6'}}}%%
flowchart TB
    subgraph Presentation ["UI & Voice Interaction Layer (Jetpack Compose)"]
        direction LR
        UI[Compose Screens & Telemetry HUD]
        STT[SpeechRecognizerManager]
        TTS[TextToSpeechManager]
    end

    subgraph Orchestration ["Autonomous Agent Core"]
        Agent[AutonomousAgentEngine]
        subgraph Loop ["Bounded Execution State Machine"]
            direction LR
            P[1. Plan] --> TC[2. Tool Call] --> O[3. Observation] --> V[4. Verification] --> R[5. Recovery]
        end
        Agent --> Loop
    end

    subgraph Inference ["Hybrid Inference & Telemetry Router"]
        Router{HybridInferenceRouter<br/>Network • Privacy • RAM • Thermal • Battery}
        Catalog[(ModelCatalog)]
        Cloud[GeminiCloudEngine<br/>Gemini 2.5 Flash / Pro<br/>OkHttp SSE + Function Calling]
        Edge[LiteRtLmEngine<br/>Gemma 3 1B • Gemma 3n E2B • Qwen 2.5<br/>NPU / GPU / CPU Accelerators]
        Catalog --> Router
        Router -->|Cloud Route| Cloud
        Router -->|Edge / Offline Route| Edge
    end

    subgraph Execution ["Security Gates & Android OS Execution"]
        Gate{Confirmation Gate<br/>Zero-Trust Policy}
        Registry[ToolRegistry & DeviceCommandExecutor]
        AndroidOS[Android System APIs • Intents • SAF • Hardware]
        Gate -->|Sensitive Tool| UserPrompt[Explicit User Approval Dialog]
        Gate -->|Safe Tool| Registry
        UserPrompt -->|Approved| Registry
        Registry --> AndroidOS
    end

    subgraph Persistence ["Local Persistence, Vault & Background Workers"]
        direction LR
        DB[(Room SQLite + FTS4<br/>Memory • Conversations • Learning)]
        Vault[SecretVault<br/>AndroidKeyStore AES-256-GCM]
        FG[AgentForegroundService<br/>specialUse]
        WM[ModelDownloadWorker<br/>WorkManager dataSync]
    end

    UI <--> Agent
    STT --> Agent
    Agent --> TTS
    Loop <--> Router
    Loop --> Gate
    Agent <--> DB
    Cloud -.-> Vault
    Agent <--> FG
    Edge <--> WM
