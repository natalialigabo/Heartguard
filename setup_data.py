import os
import requests


FILES = {
    

}

DATA_DIR = os.path.join("Backend", "Data")

def download_file_from_google_drive(id, destination):
    URL = "https://docs.google.com/uc?export=download"
    session = requests.Session()
    response = session.get(URL, params={'id': id}, stream=True)
    token = get_confirm_token(response)

    if token:
        params = {'id': id, 'confirm': token}
        response = session.get(URL, params=params, stream=True)

    save_response_content(response, destination)

def get_confirm_token(response):
    for key, value in response.cookies.items():
        if key.startswith('download_warning'):
            return value
    return None

def save_response_content(response, destination):
    CHUNK_SIZE = 32768
    with open(destination, "wb") as f:
        for chunk in response.iter_content(CHUNK_SIZE):
            if chunk:
                f.write(chunk)

if __name__ == "__main__":
    if not os.path.exists(DATA_DIR):
        os.makedirs(DATA_DIR)
        print(f"📁 Pasta criada: {DATA_DIR}")

    print("⬇️ Iniciando download dos PDFs de referência...")
    
    for filename, file_id in FILES.items():
        dest_path = os.path.join(DATA_DIR, filename)
        if not os.path.exists(dest_path):
            print(f"Baixando {filename}...")
            try:
                download_file_from_google_drive(file_id, dest_path)
                print(f"✅ {filename} concluído.")
            except Exception as e:
                print(f"❌ Erro ao baixar {filename}: {e}")
        else:
            print(f"ℹ️ {filename} já existe. Pulando.")
    
    print("\n✨ Setup de dados concluído!")