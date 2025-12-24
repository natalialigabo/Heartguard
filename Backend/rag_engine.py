import os
from langchain_community.vectorstores import FAISS
from langchain_huggingface import HuggingFaceEmbeddings

class MedicalAssistant:
    def __init__(self):
        print("🔄 Carregando Cérebro Médico (RAG)...")
        try:
            # Carrega o modelo de embeddings (mesmo usado na criação)
            self.embeddings = HuggingFaceEmbeddings(model_name="sentence-transformers/all-MiniLM-L6-v2")
            
            # Carrega o banco vetorial salvo
            if os.path.exists("faiss_index_tcc"):
                self.vector_db = FAISS.load_local("faiss_index_tcc", self.embeddings, allow_dangerous_deserialization=True)
                print("✅ Banco FAISS carregado com sucesso!")
            else:
                print("⚠️ Banco FAISS não encontrado. O sistema usará respostas genéricas.")
                self.vector_db = None
        except Exception as e:
            print(f"❌ Erro ao carregar IA: {e}")
            self.vector_db = None

    def get_advice(self, ecg_class, bp, spo2, location_type):
        """
        Gera um conselho médico baseado nos dados vitais e nos PDFs.
        """
        # 1. Monta a Query para buscar nos livros/artigos
        query = f"Tratamento e protocolo para arritmia {ecg_class} com pressão {bp} e SpO2 {spo2} em contexto {location_type}"
        
        contexto = ""
        if self.vector_db:
            # Busca os 2 trechos mais relevantes nos seus PDFs
            docs = self.vector_db.similarity_search(query, k=2)
            contexto = "\n".join([doc.page_content for doc in docs])
        
        # 2. Monta a Resposta Final (Aqui simulamos o LLM final gerando o texto)
        # Num sistema real com GPT-4, passaríamos o 'contexto' para ele.
        # Como estamos rodando local sem API paga, vamos usar o contexto para estruturar a resposta.
        
        resposta_base = f"ANÁLISE IA (Baseada em Protocolos SBC/MS):\n"
        
        if "VENTRICULAR" in ecg_class.upper() or "PERIGO" in ecg_class.upper():
            resposta_base += f"🚨 ALERTA CRÍTICO: Padrão ventricular detectado.\n"
            if location_type == "RURAL_REMOTA":
                resposta_base += "📍 PROTOCOLO RURAL: Estabilização imediata necessária. Acionar telemedicina via satélite. Considerar uso de Amiodarona se disponível (conforme Consenso Chagas).\n"
            else:
                resposta_base += "📍 PROTOCOLO URBANO: Remoção imediata via SAMU (192) para centro com suporte de desfibrilação.\n"
            
            resposta_base += f"\n📚 EVIDÊNCIA ENCONTRADA NOS MANUAIS:\n{contexto[:2000]}..." # Mostra um trecho do PDF para provar que leu
            
        elif "ARRITMIA" in ecg_class.upper():
            resposta_base += "⚠️ ATENÇÃO: Arritmia Supraventricular/Extrassístole.\n"
            resposta_base += "Monitorar evolução. Se paciente chagásico, investigar progressão da cardiopatia."
            
        else:
            resposta_base += "✅ Sinais estáveis. Manter monitoramento de rotina."

        return resposta_base