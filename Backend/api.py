from fastapi import FastAPI
from pydantic import BaseModel
from datetime import datetime
import pandas as pd
import os
from rag_engine import MedicalAssistant

# Tenta importar o Twilio, se não tiver instalado, não quebra
try:
    from twilio.rest import Client
    TWILIO_INSTALLED = True
except ImportError:
    TWILIO_INSTALLED = False
    print("Aviso: Biblioteca Twilio não instalada via pip.")

app = FastAPI()
assistant = MedicalAssistant()

HISTORY_FILE = "historico_pacientes.csv"
PATIENTS_FILE = "cadastro_pacientes.csv"

# --- CONFIG TWILIO (SE NÃO TIVER CONTA, DEIXE ASSIM) ---
TWILIO_SID = "SEU_SID_AQUI" 
TWILIO_TOKEN = "SEU_TOKEN_AQUI"
TWILIO_PHONE = "SEU_NUMERO_TWILIO" 

# Modelo de dados
class VitalSigns(BaseModel):
    patient_name: str
    ecg_status: str
    bp_value: str
    spo2_value: str
    location_type: str
    timestamp: str

# Função de SMS Blindada (Não trava o app se falhar)
def enviar_sms_dinamico(nome_paciente, status, link):
    print(f"📧 Tentando enviar SMS para {nome_paciente}...")
    
    if not TWILIO_INSTALLED:
        print("❌ Erro SMS: Biblioteca Twilio não instalada.")
        return

    if "SEU_SID" in TWILIO_SID: # Verifica se configurou as chaves
        print("⚠️ Aviso SMS: Credenciais do Twilio não configuradas no api.py. Pulei o envio.")
        return

    try:
        if os.path.exists(PATIENTS_FILE):
            df_pacientes = pd.read_csv(PATIENTS_FILE)
            paciente = df_pacientes[df_pacientes["Nome"] == nome_paciente]
            
            if not paciente.empty:
                telefone_destino = str(paciente.iloc[0]["Telefone_Emergencia"])
                
                client = Client(TWILIO_SID, TWILIO_TOKEN)
                msg = f"🚨 ALERTA HEARTGUARD: {nome_paciente} apresenta {status}. Acompanhe: {link}"
                
                message = client.messages.create(body=msg, from_=TWILIO_PHONE, to=telefone_destino)
                print(f"✅ SMS enviado com sucesso! ID: {message.sid}")
            else:
                print(f"❌ Erro SMS: Paciente '{nome_paciente}' não encontrado no cadastro_pacientes.csv")
        else:
            print("❌ Erro SMS: Arquivo cadastro_pacientes.csv ainda não existe.")
            
    except Exception as e:
        # AQUI ESTÁ A PROTEÇÃO: Se der erro, só imprime e continua a vida
        print(f"❌ FALHA NO ENVIO DO SMS (Mas o sistema continua rodando): {e}")

# Inicializa CSV
# ... (Mantenha os imports anteriores)

# 1. ATUALIZE O ARQUIVO DE PACIENTES PARA TER MAIS COLUNAS
if not os.path.exists(PATIENTS_FILE):
    # Criamos com colunas extras para os limites e remédios
    df = pd.DataFrame(columns=["Nome", "Telefone", "Target_Sys", "Target_Dia", "Target_SpO2", "Medicacao"])
    df.to_csv(PATIENTS_FILE, index=False)

# 2. MODELO DE DADOS PARA CADASTRO (Novo)
class PatientConfig(BaseModel):
    name: str
    phone: str
    target_sys: int
    target_dia: int
    target_spo2: int
    medication: str

# 3. ROTA DE CADASTRO (POST)
@app.post("/register_patient")
async def register_patient(config: PatientConfig):
    print(f"📝 Cadastrando: {config.name} | Med: {config.medication}")
    try:
        df = pd.read_csv(PATIENTS_FILE)
        # Remove se já existir (atualização)
        df = df[df["Nome"] != config.name]
        
        novo_paciente = {
            "Nome": config.name,
            "Telefone": config.phone,
            "Target_Sys": config.target_sys,
            "Target_Dia": config.target_dia,
            "Target_SpO2": config.target_spo2,
            "Medicacao": config.medication
        }
        
        df = pd.concat([df, pd.DataFrame([novo_paciente])], ignore_index=True)
        df.to_csv(PATIENTS_FILE, index=False)
        return {"status": "success", "message": "Paciente cadastrado com sucesso!"}
    except Exception as e:
        return {"status": "error", "message": str(e)}

# 4. ROTA DE LEITURA (Atualize a 'get_latest_status' para retornar a medicação também!)
@app.get("/latest_status")
async def get_latest_status():
    # ... (código igual ao anterior, mas vamos tentar pegar o nome do remédio do CSV)
    try:
        if os.path.exists(HISTORY_FILE):
            df_hist = pd.read_csv(HISTORY_FILE)
            if not df_hist.empty:
                ultimo = df_hist.iloc[-1].to_dict()
                
                # Tenta achar o remédio desse paciente no outro arquivo
                medication = "o medicamento prescrito" # Padrão
                if os.path.exists(PATIENTS_FILE):
                    df_pat = pd.read_csv(PATIENTS_FILE)
                    patient_row = df_pat[df_pat["Nome"] == ultimo["Paciente"]]
                    if not patient_row.empty:
                        medication = str(patient_row.iloc[0]["Medicacao"])

                response = {k: (v if pd.notna(v) else "") for k, v in ultimo.items()}
                response["Medicacao_Cadastrada"] = medication # Adiciona no retorno
                return response
    except Exception as e:
        print(f"Erro: {e}")
    # ... (retorno de erro igual antes)
@app.post("/analyze")
async def analyze_vitals(data: VitalSigns):
    print(f"📲 Recebido: {data.ecg_status} | Paciente: {data.patient_name}")
    
    # 1. RAG
    try:
        analise_medica = assistant.get_advice(
            data.ecg_status, data.bp_value, data.spo2_value, data.location_type
        )
    except Exception as e:
        analise_medica = "Erro na IA. Consulte médico imediatamente."
        print(f"Erro no RAG: {e}")
    
    # 2. Salvar Histórico
    try:
        novo_evento = {
            "Data": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            "Paciente": data.patient_name,
            "ECG": data.ecg_status,
            "PA": data.bp_value,
            "SpO2": data.spo2_value,
            "Local": data.location_type,
            "Analise_IA": analise_medica
        }
        df = pd.read_csv(HISTORY_FILE)
        df = pd.concat([df, pd.DataFrame([novo_evento])], ignore_index=True)
        df.to_csv(HISTORY_FILE, index=False)
    except Exception as e:
        print(f"Erro ao salvar CSV: {e}")
    
    # 3. Tentar Enviar SMS (Se for perigo)
    if "PERIGO" in data.ecg_status or "VENTRICULAR" in data.ecg_status:
        # Link fictício para demonstração se não tiver ngrok configurado
        link = "https://hospital-dashboard.com" 
        enviar_sms_dinamico(data.patient_name, data.ecg_status, link)
    
    return {
        "status": "received",
        "medical_advice": analise_medica
    }
# ... (todo o seu código anterior continua igual) ...

#