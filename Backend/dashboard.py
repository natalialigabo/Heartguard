import streamlit as st
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
import bcrypt
import os
import time
import plotly.graph_objects as go # Importante para o gráfico bonito

# --- CONFIGURAÇÃO INICIAL DA PÁGINA ---
st.set_page_config(page_title="HeartGuard | Sistema de Gestão", layout="wide", page_icon="❤️")

# Arquivos de banco de dados (CSV)
FILES = {
    "history": "historico_pacientes.csv",
    "users": "usuarios_sistema.csv",
    "patients": "cadastro_pacientes.csv"
}

# --- FUNÇÕES DE BANCO DE DADOS E SEGURANÇA ---
def load_csv(key, columns):
    if not os.path.exists(FILES[key]):
        pd.DataFrame(columns=columns).to_csv(FILES[key], index=False)
    return pd.read_csv(FILES[key])

def save_user(username, password, role="medico"):
    df = load_csv("users", ["Username", "Password_Hash", "Role"])
    if username in df["Username"].values:
        return False
    hashed = bcrypt.hashpw(password.encode('utf-8'), bcrypt.gensalt()).decode('utf-8')
    new_user = pd.DataFrame([[username, hashed, role]], columns=["Username", "Password_Hash", "Role"])
    pd.concat([df, new_user], ignore_index=True).to_csv(FILES["users"], index=False)
    return True

def save_patient(name, phone):
    df = load_csv("patients", ["Nome", "Telefone_Emergencia"])
    df = df[df["Nome"] != name] 
    new_pat = pd.DataFrame([[name, phone]], columns=["Nome", "Telefone_Emergencia"])
    pd.concat([df, new_pat], ignore_index=True).to_csv(FILES["patients"], index=False)

def verify_login(username, password):
    df = load_csv("users", ["Username", "Password_Hash", "Role"])
    user_row = df[df["Username"] == username]
    if not user_row.empty:
        stored_hash = user_row.iloc[0]["Password_Hash"]
        if bcrypt.checkpw(password.encode('utf-8'), stored_hash.encode('utf-8')):
            return user_row.iloc[0]["Role"]
    return None

# --- INICIALIZAÇÃO AUTOMÁTICA ---
if load_csv("users", ["Username", "Password_Hash", "Role"]).empty:
    save_user("admin", "admin123", "admin")

# --- TELA DE LOGIN ---
if "logged_in" not in st.session_state:
    st.session_state.update({"logged_in": False, "user": "", "role": ""})

if not st.session_state["logged_in"]:
    col1, col2, col3 = st.columns([1,1,1])
    with col2:
        st.title("🔒 HeartGuard Login")
        u = st.text_input("Usuário")
        p = st.text_input("Senha", type="password")
        if st.button("Entrar", type="primary"):
            role = verify_login(u, p)
            if role:
                st.session_state.update({"logged_in": True, "user": u, "role": role})
                st.rerun()
            else:
                st.error("Acesso Negado.")
    st.stop()

# --- ÁREA LOGADA ---
st.sidebar.success(f"👤 {st.session_state['user'].upper()} ({st.session_state['role']})")

if st.sidebar.button("Sair"):
    st.session_state.update({"logged_in": False, "user": "", "role": ""})
    st.rerun()

st.sidebar.markdown("---")
menu = st.sidebar.radio("Navegação", ["Monitoramento (Tempo Real)", "Cadastro de Pacientes", "Gestão de Usuários"])

# ==============================================================================
# ABA 1: MONITORAMENTO 
# ==============================================================================
if menu == "Monitoramento (Tempo Real)":
    st.title("❤️ Central de Telemetria (SUS)")
    
    auto_refresh = st.sidebar.checkbox("🟢 Atualização Automática", value=True)
    
    placeholder = st.empty()
    
    with placeholder.container():
        try:
            df = pd.read_csv(FILES["history"])
        except:
            st.info("Aguardando dados do servidor...")
            st.stop()
            
        if not df.empty:
            # Filtros
            pacientes_lista = df["Paciente"].unique()
            if "paciente_selecionado" not in st.session_state:
                st.session_state["paciente_selecionado"] = pacientes_lista[0]
            
            st.session_state["paciente_selecionado"] = st.sidebar.selectbox(
                "📂 Selecione o Prontuário:", 
                pacientes_lista,
                index=list(pacientes_lista).index(st.session_state["paciente_selecionado"]) if st.session_state["paciente_selecionado"] in pacientes_lista else 0
            )
            
            # Dados
            df_paciente = df[df["Paciente"] == st.session_state["paciente_selecionado"]].copy()
            df_paciente['Data'] = pd.to_datetime(df_paciente['Data'])
            df_paciente = df_paciente.sort_values(by='Data')

            # Tratamento numérico
            try:
                df_paciente[['PA_Sys', 'PA_Dia']] = df_paciente['PA'].str.split('/', expand=True).astype(int)
                df_paciente['SpO2_Num'] = df_paciente['SpO2'].str.replace('%', '').astype(int)
            except:
                pass 

            # --- GRÁFICOS DE TENDÊNCIA (ZOOM) ---
            janela_zoom = 50  

            col1, col2 = st.columns(2)
            with col1:
                st.markdown("##### 🩸 Pressão Arterial (mmHg)")
                # Gráfico de Linha com Zoom
                st.line_chart(
                    df_paciente[['Data', 'PA_Sys', 'PA_Dia']].tail(janela_zoom).set_index('Data'), 
                    height=200, 
                    color=["#FF5733", "#33FF57"]
                )
            with col2:
                st.markdown("##### 🫁 Saturação de O₂ (%)")
                # Gráfico de Linha com Zoom
                st.line_chart(
                    df_paciente[['Data', 'SpO2_Num']].tail(janela_zoom).set_index('Data'), 
                    height=200, 
                    color="#33C1FF"
                )

            st.markdown("---")

            # --- ECG RECONSTRUÍDO (PLOTLY) ---
            st.subheader("💓 Análise do Último Evento (ECG)")
            
            ultimo = df_paciente.iloc[-1]
            status_ecg = ultimo['ECG']

            col_info, col_img = st.columns([1, 2])
            
            with col_info:
                st.info(f"🕒 **Data:** {ultimo['Data'].strftime('%d/%m/%Y %H:%M:%S')}")
                
                # ... (seus ifs de status continuam aqui) ...
                if "PERIGO" in status_ecg or "VENTRICULAR" in status_ecg:
                    st.error(f"🚨 **STATUS:** {status_ecg}")
                elif "Arritmia" in status_ecg:
                    st.warning(f"⚠️ **STATUS:** {status_ecg}")
                else:
                    st.success(f"✅ **STATUS:** {status_ecg}")
                
                # --- AQUI É A MUDANÇA VISUAL ---
                st.markdown("---")
                st.markdown("**🧠 Análise Inteligente (RAG):**")
                
                # Usamos um 'expander' para o texto longo não poluir o layout
                # expanded=True deixa ele aberto por padrão se for Perigo
                abrir_automaticamente = ("PERIGO" in status_ecg)
                
                with st.expander("Ler Parecer Clínico Completo", expanded=abrir_automaticamente):
                    # st.write interpreta as quebras de linha melhor que st.caption
                    st.write(ultimo['Analise_IA'])

            with col_img:
                # --- LÓGICA PLOTLY (INTERATIVA) ---
                t = np.linspace(0, 4, 1000)
                sinal = np.zeros_like(t)
                descricoes = ["Linha de Base"] * 1000

                def adicionar_batimento(t_local, inicio_idx):
                    if t_local < 0 or t_local > 0.8: return 0.0
                    y = 0.0
                    if 0.05 < t_local < 0.15: 
                        y += 0.15 * np.exp(-(t_local - 0.1)**2 / (2 * 0.03**2))
                        if 0 <= inicio_idx + int(t_local*250) < 1000: descricoes[inicio_idx + int(t_local*250)] = "Onda P"
                    elif 0.3 < t_local < 0.4:
                        y -= 0.15 * np.exp(-(t_local - 0.33)**2 / (2 * 0.008**2))
                        y += 1.2 * np.exp(-(t_local - 0.35)**2 / (2 * 0.01**2))
                        y -= 0.25 * np.exp(-(t_local - 0.37)**2 / (2 * 0.008**2))
                        if 0 <= inicio_idx + int(t_local*250) < 1000: descricoes[inicio_idx + int(t_local*250)] = "Complexo QRS"
                    elif 0.5 < t_local < 0.7:
                        y += 0.3 * np.exp(-(t_local - 0.6)**2 / (2 * 0.05**2))
                        if 0 <= inicio_idx + int(t_local*250) < 1000: descricoes[inicio_idx + int(t_local*250)] = "Onda T"
                    return y

                cor_linha = "#00FF00"
                
                if "PERIGO" in status_ecg or "VENTRICULAR" in status_ecg:
                    sinal = np.sin(2*np.pi*5*t) + np.sin(2*np.pi*12*t)*0.5 + np.random.normal(0, 0.1, 1000)
                    descricoes = ["Fibrilação Ventricular"] * 1000
                    cor_linha = "#FF0000"
                elif "Arritmia" in status_ecg:
                    batimentos = [0.2, 1.2, 3.2]
                    for b in batimentos:
                        for i, time_val in enumerate(t):
                            sinal[i] += adicionar_batimento(time_val - b, i)
                    cor_linha = "#FFA500"
                else:
                    batimentos = [0.2, 1.2, 2.2, 3.2]
                    for b in batimentos:
                        for i, time_val in enumerate(t):
                            sinal[i] += adicionar_batimento(time_val - b, i)

                fig = go.Figure()
                fig.add_trace(go.Scatter(
                    x=t, y=sinal, mode='lines', name='ECG',
                    line=dict(color=cor_linha, width=2),
                    text=descricoes,
                    hovertemplate='%{text}<br>Voltagem: %{y:.2f}mV<extra></extra>'
                ))
                fig.update_layout(
                    title="Monitoramento Eletrocardiográfico (Lead II)",
                    plot_bgcolor="black", paper_bgcolor="#0e1117",
                    xaxis=dict(showgrid=True, gridcolor='green', range=[0, 4]),
                    yaxis=dict(showgrid=True, gridcolor='green', range=[-1.5, 2.0]),
                    margin=dict(l=40, r=20, t=40, b=40), height=350
                )
                st.plotly_chart(fig, use_container_width=True)

    if auto_refresh:
        time.sleep(2)
        st.rerun()

# ==============================================================================
# ABA 2: CADASTRO
# ==============================================================================
elif menu == "Cadastro de Pacientes":
    st.title("📋 Cadastro de Contatos")
    with st.form("paciente_form"):
        col_nome, col_tel = st.columns(2)
        nome_pac = col_nome.text_input("Nome (Ex: Sr. João (Ribeirinho))")
        tel_pac = col_tel.text_input("Telefone (Ex: +5511999999999)")
        if st.form_submit_button("💾 Salvar"):
            if nome_pac and tel_pac:
                save_patient(nome_pac, tel_pac)
                st.success("Salvo!")
    if os.path.exists(FILES["patients"]):
        st.dataframe(pd.read_csv(FILES["patients"]), use_container_width=True)

# ==============================================================================
# ABA 3: GESTÃO
# ==============================================================================
elif menu == "Gestão de Usuários":
    st.title("👥 Gestão de Médicos")
    if st.session_state["role"] == "admin":
        with st.form("new_user"):
            new_u = st.text_input("Usuário")
            new_p = st.text_input("Senha", type="password")
            new_r = st.selectbox("Permissão", ["medico", "admin"])
            if st.form_submit_button("Criar"):
                if save_user(new_u, new_p, new_r):
                    st.success("Criado!")
                else:
                    st.error("Já existe.")
        st.dataframe(load_csv("users", ["Username", "Role"])[["Username", "Role"]], use_container_width=True)
    else:
        st.warning("Acesso restrito.")