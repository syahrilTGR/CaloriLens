import firebase_admin
from firebase_admin import credentials, firestore
import json
import os

# Nama file service account key yang Anda download
# Pastikan file ini ada di folder yang sama dengan script ini
SERVICE_ACCOUNT_KEY_FILE = "calorilens-b223f-firebase-adminsdk-fbsvc-ec4103c380.json" 

# Cek apakah file key ada (mencoba nama umum jika spesifik tidak ada)
if not os.path.exists(SERVICE_ACCOUNT_KEY_FILE):
    # Coba cari file json apapun yang terlihat seperti service account key
    potential_keys = [f for f in os.listdir('.') if f.endswith('.json') and 'firebase-adminsdk' in f]
    if potential_keys:
        SERVICE_ACCOUNT_KEY_FILE = potential_keys[0]
        print(f"Menggunakan service account key: {SERVICE_ACCOUNT_KEY_FILE}")
    else:
        # Fallback ke nama standar
        SERVICE_ACCOUNT_KEY_FILE = "serviceAccountKey.json"

if not os.path.exists(SERVICE_ACCOUNT_KEY_FILE):
    print(f"Error: File service account key tidak ditemukan.")
    print("Pastikan Anda sudah mendownload key dari Firebase Console dan menyimpannya di folder ini.")
    exit()

# Inisialisasi Firebase
try:
    cred = credentials.Certificate(SERVICE_ACCOUNT_KEY_FILE)
    firebase_admin.initialize_app(cred)
    db = firestore.client()
    print("Berhasil terhubung ke Firebase Firestore.")
except Exception as e:
    print(f"Gagal menginisialisasi Firebase: {e}")
    exit()

# Baca file foods.json
try:
    with open("foods.json", "r") as f:
        foods_data = json.load(f)
    print(f"Berhasil membaca foods.json ({len(foods_data)} items).")
except FileNotFoundError:
    print("Error: File 'foods.json' tidak ditemukan.")
    exit()
except json.JSONDecodeError:
    print("Error: Format JSON di 'foods.json' tidak valid.")
    exit()

# Upload ke Firestore (Satu per Satu - Lebih aman koneksi lambat)
print("Mulai proses upload (Mode Satu-per-Satu)...")

collection_ref = db.collection("foods")
count = 0
total = len(foods_data)

for food_id, data in foods_data.items():
    try:
        doc_ref = collection_ref.document(food_id)
        doc_ref.set(data) # Upload langsung, tanpa batch
        count += 1
        print(f"  [{count}/{total}] Berhasil upload: {food_id}")
    except Exception as e:
        print(f"  ❌ Gagal upload {food_id}: {e}")

print(f"SUKSES! Total {count} makanan telah diupload ke koleksi 'foods'.")
print("Silakan cek Firebase Console Anda.")
