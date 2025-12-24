
# 💓 HeartGuard: Sistema de Monitoramento Cardíaco com Edge AI e IoT

> **Projeto de Trabalho de Conclusão de Curso (TCC)** > **Curso:** Engenharia de Computação - UNIVESP  
> **Autora:** Natália Ligabo dos Santos

## 📌 Sobre o Projeto

O **HeartGuard** é uma solução completa de *Internet of Medical Things* (IoMT) focada em monitoramento cardíaco para regiões remotas (como comunidades ribeirinhas). O sistema integra:

1.  **Wearable (Smartwatch):** Rede Neural (CNN) rodando offline para detecção de arritmias e Chagas.
2.  **Mobile App (Android):** Interface para a família com alertas e controle de medicação.
3.  **Backend & IA Generativa:** API Python com arquitetura RAG (Retrieval-Augmented Generation) para explicar diagnósticos baseados em protocolos médicos.

## 🛠️ Tecnologias Utilizadas

### 📱 Mobile & Wearable

* **Linguagem:** Kotlin (Android Nativo)
* **Interface:** Jetpack Compose
* **IA Embarcada:** TensorFlow Lite (TFLite)

### 🧠 Backend & Inteligência Artificial

* **Linguagem:** Python 3.10
* **API:** FastAPI
* **Dashboard:** Streamlit
* **Machine Learning:** TensorFlow/Keras (CNN), Scikit-Learn
* **GenAI:** LangChain + FAISS (RAG)

## 📂 Estrutura do Repositório

* `/backend`: Código da API, Motor RAG e Dashboard Web.
* `/mobile_app`: Projeto Android Studio (Módulos `app` para relógio e `heartguardmobile` para celular).
* `/notebooks`: Jupyter Notebooks usados para treinar a CNN com os datasets MIT-BIH e CODE.

## 🚀 Como Executar

### Pré-requisitos

* Android Studio Koala ou superior.
* Python 3.10+.

### Passos

1.  Clone este repositório.
2.  No diretório `backend`, instale as dependências: `pip install -r requirements.txt`.
3.  Execute a API: `uvicorn api:app --reload`.
4.  Abra a pasta `mobile_app` no Android Studio e execute nos emuladores (Watch e Phone).

---
*Desenvolvido como requisito parcial para obtenção do grau de Engenheiro de Computação.*
