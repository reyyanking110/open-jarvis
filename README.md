<div align="center">

<!-- ANIMATED 3D WAVE HEADER -->
<img src="https://capsule-render.vercel.app/api?type=waving&color=0:090D16,50:0F2942,100:D4AF37&height=240&section=header&text=OPEN%20JARVIS%20ANDROID&fontSize=44&fontColor=F8FAFC&fontAlignY=38&animation=fadeIn&desc=Autonomous%20Hybrid%20Edge%20%26%20Cloud%20AI%20Agent&descAlignY=58&descSize=18" width="100%" alt="Open JARVIS Android Banner" />

<!-- DYNAMIC TYPING SUBTITLE -->
<a href="https://github.com/reyyanking110/open-jarvis">
  <img src="https://readme-typing-svg.demolab.com?font=JetBrains+Mono&weight=700&size=19&duration=3000&pause=1000&color=F59E0B&center=true&vCenter=true&multiline=false&width=780&height=36&lines=Native+Kotlin+%26+Jetpack+Compose+Autonomous+Agent;Cloud+Gemini+2.5+%2B+Offline+LiteRT-LM+Edge+Inference;Hardware-Aware+Thermal%2C+RAM+%26+Battery+Routing;AES-256-GCM+Hardware+Keystore+%26+Action+Safety+Gates" alt="Typing Animation" />
</a>

<br/>

<!-- TECH & STATUS BADGES -->
<p>
  <img src="https://img.shields.io/badge/Platform-Android_10%2B-0F172A?style=for-the-badge&logo=android&logoColor=3DDC84" alt="Android" />
  <img src="https://img.shields.io/badge/Language-Kotlin_2.0-0F172A?style=for-the-badge&logo=kotlin&logoColor=7F52FF" alt="Kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack_Compose-0F172A?style=for-the-badge&logo=jetpackcompose&logoColor=4285F4" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Cloud-Gemini_2.5-0F172A?style=for-the-badge&logo=googlegemini&logoColor=F59E0B" alt="Gemini" />
  <img src="https://img.shields.io/badge/Edge-LiteRT--LM-0F172A?style=for-the-badge&logo=tensorflow&logoColor=FF6F00" alt="LiteRT-LM" />
</p>

<!-- PRIMARY DOWNLOAD CTA -->
<p>
  <a href="https://github.com/reyyanking110/open-jarvis/actions/runs/45148566182">
    <img src="https://img.shields.io/badge/DOWNLOAD_DEBUG_APK-GITHUB_CI_ARTIFACT-D4AF37?style=for-the-badge&logo=githubactions&logoColor=090D16" height="40" alt="Download Debug APK" />
  </a>
</p>

</div>

---

## Overview

**Open JARVIS Android** is a native, autonomous, hybrid AI agent engineered for Android smartphones—inspired by [OpenJarvis](https://github.com/open-jarvis/OpenJarvis). Designed with **Kotlin**, **Jetpack Compose**, **Hilt**, **Room**, **Gemini REST API**, and **LiteRT-LM** (`com.google.ai.edge.litertlm`), it seamlessly orchestrates cloud intelligence and on-device neural inference while executing real-world Android system actions under strict security gates.

---

## Direct APK Installation

<table>
  <tr>
    <td width="62%" valign="top">
      <h3>Quick Build Artifact Steps</h3>
      <ol>
        <li>Open the <a href="https://github.com/reyyanking110/open-jarvis/actions/runs/45148566182"><b>Linked Successful Android CI Run</b></a>.</li>
        <li>Scroll down to the <code>Artifacts</code> section at the bottom of the workflow summary.</li>
        <li>Download <code>open-jarvis-debug-apk</code>.</li>
        <li>Extract the downloaded ZIP archive and install <code>app-debug.apk</code> on your device.</li>
      </ol>
    </td>
    <td width="38%" valign="top">
      <h3>Artifact Retention Notice</h3>
      <blockquote>
        <b>Note:</b> GitHub Actions build artifacts are temporary and automatically expire according to repository retention policies. Always pull from the latest green CI run.
      </blockquote>
    </td>
  </tr>
</table>

---

## Hybrid System Architecture

```mermaid
%%{init: {'theme': 'dark', 'themeVariables': { 'primaryColor': '#0F2942', 'primaryTextColor': '#F8FAFC', 'primaryBorderColor': '#D4AF37', 'lineColor': '#F59E0B', 'secondaryColor': '#090D16', 'tertiaryColor': '#1E293B'}}}%%
flowchart TB
    User([User Voice / Text Input]) --> Agent[AutonomousAgentEngine]
    
    subgraph AgentLoop [Bounded Execution Loop]
        direction LR
        Plan[Plan] --> Tool[Tool Call] --> Obs[Observation] --> Ver[Verification] --> Rec[Recovery]
    end
    
    Agent --> AgentLoop
    AgentLoop --> Router{HybridInferenceRouter}
    
    Router -->|Online + Optimal| Cloud[GeminiCloudEngine<br/>Gemini 2.5 Flash / Pro<br/>OkHttp SSE Streaming]
    Router -->|Offline / Privacy / Thermal| Edge[LiteRtLmEngine<br/>Gemma 3 1B / Gemma 3n E2B / Qwen 2.5<br/>NPU / GPU / CPU]
    
    AgentLoop --> Gate{Confirmation Gate}
    Gate -->|Sensitive Action| Approve[User Approval Dialog]
    Gate -->|Safe Action| Exec[DeviceCommandExecutor & ToolRegistry]
    Approve --> Exec
    
    Exec --> OS[Android System APIs & SAF]
    AgentLoop <--> DB[(Room SQLite + FTS4<br/>Memory & Adaptive Learning)]
