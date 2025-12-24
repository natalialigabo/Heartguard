from langchain_community.document_loaders import PyPDFLoader
import os
from langchain_community.document_loaders import WebBaseLoader
from langchain_community.document_loaders import WebBaseLoader, PyPDFLoader
from langchain_text_splitters import RecursiveCharacterTextSplitter
from langchain_community.vectorstores import FAISS
from langchain_openai import OpenAIEmbeddings
from langchain_huggingface import HuggingFaceEmbeddings

# Configurar User Agent para não ser bloqueado nos sites
os.environ["USER_AGENT"] = "Estudante_TCC/1.0"

print("--- 1. CARREGANDO DADOS ---")
documentos_totais = []
# A. Carregar da Web
urls = [
    "https://www.scielo.br/j/abc/a/pZnfbJzqhGcBgVHmyjRgDkb/?format=html&lang=pt"
]

try:
    loader_web = WebBaseLoader(urls)
    docs_web = loader_web.load()
    documentos_totais.extend(docs_web)
    print(f"Web carregada: {len(docs_web)} documentos.")
except Exception as e:
    print(f"Erro na Web: {e}")


lista_pdfs = [
    "ECG-Manual-Prático-de-Eletrocardiograma-HCor.pdf",
    "download (1).pdf",
    "download (2).pdf",
    "rdt_v22n4_166-168.pdf",
    "38050006.pdf",
    "0066-782X-abc-119-04-0638.x55156.pdf",
    "download (3).pdf",
    "a2004_v17_n04_art03.pdf",
    "kumar-et-al-2023-tropical-cardiovascular-diseases.pdf",
    "download (4).pdf",
    "download (5).pdf"

]

for pdf in lista_pdfs:
    if os.path.exists(pdf):
        try:
            loader = PyPDFLoader(pdf)
            docs = loader.load()
            documentos_totais.extend(docs)
            print(f"✅ PDF carregado: '{pdf}' ({len(docs)} páginas).")
        except Exception as e:
            print(f"❌ Erro ao ler '{pdf}': {e}")
    else:
        print(f"⚠️ Aviso: Arquivo '{pdf}' não encontrado.")

if not documentos_totais:
    print("Erro: Nenhum documento foi carregado. Verifique os links ou o PDF.")
    exit() # Para o código se não tiver nada

print(f"Total de documentos brutos: {len(documentos_totais)}")

print("--- 2. DIVIDINDO O TEXTO (CHUNKING) ---")
# Corta o texto em pedaços de 1000 caracteres
text_splitter = RecursiveCharacterTextSplitter(chunk_size=1000, chunk_overlap=200)
splits = text_splitter.split_documents(documentos_totais)
print(f"Texto dividido em {len(splits)} pedaços (chunks).")

print("--- 3. CRIANDO O BANCO VETORIAL (Local/Grátis) ---")
try:
    print("Baixando modelo de Embeddings gratuito (pode demorar um pouquinho na 1ª vez)...")
    # Este modelo roda local no seu PC (CPU)
    embedding_model = HuggingFaceEmbeddings(model_name="sentence-transformers/all-MiniLM-L6-v2")
    
    print("Criando banco de dados FAISS...")
    vectorstore = FAISS.from_documents(documents=splits, embedding=embedding_model)
    
    # Salva na pasta do projeto
    vectorstore.save_local("faiss_index_tcc")
    print("Sucesso! Banco vetorial criado e salvo como 'faiss_index_tcc'.")
    
except Exception as e:
    print(f"Erro ao criar Embeddings: {e}")