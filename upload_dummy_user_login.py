import requests
import json
from datetime import datetime, timedelta

# --- KONFIGURASI ---
API_KEY = "AIzaSyDxHR0KS7uP7CsVNXVY9UMwcqYYpHkbD2w" # Ganti dengan API Key dari google-services.json
EMAIL = "syahril@gmail.com"        # Ganti dengan email user
PASSWORD = "123456"          # Ganti dengan password user
PROJECT_ID = "calorilens-b223f"   # Ganti dengan Project ID Anda
# --- AKHIR KONFIGURASI ---

# URL Endpoints
AUTH_URL = f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={API_KEY}"
FIRESTORE_URL = f"https://firestore.googleapis.com/v1/projects/{PROJECT_ID}/databases/(default)/documents"

# 1. LOGIN (Dapatkan ID Token & User ID)
print(f"Mencoba login sebagai {EMAIL}...")
auth_response = requests.post(AUTH_URL, json={
    "email": EMAIL,
    "password": PASSWORD,
    "returnSecureToken": True
})

if auth_response.status_code != 200:
    print(f"Login Gagal! {auth_response.text}")
    exit()

auth_data = auth_response.json()
ID_TOKEN = auth_data['idToken']
USER_ID = auth_data['localId']
print(f"Login Berhasil! UID: {USER_ID}")

# 2. Baca Data Makanan (untuk referensi kalori)
try:
    with open("foods.json", "r") as f:
        foods_data_raw = json.load(f)
    
    available_foods = []
    for food_id, data in foods_data_raw.items():
        calories = (data['proteins'] * 4) + (data['fat'] * 9) + (data['carbs'] * 4)
        available_foods.append({'name': data['name'], 'calories': calories})
    print(f"Berhasil membaca {len(available_foods)} makanan dari file lokal.")
except Exception as e:
    print(f"Gagal baca foods.json: {e}")
    exit()

# 3. Fungsi Helper untuk Upload ke Firestore via REST
def upload_meal(date_obj, items):
    date_str = date_obj.strftime("%Y-%m-%d")
    total_cal = sum(item['calories'] for item in items)
    
    # Format Firestore REST API
    # Timestamp format: 2023-12-05T12:00:00Z
    timestamp_str = date_obj.isoformat() + "Z" 
    
    # Struktur Item Array
    items_array_values = []
    for item in items:
        items_array_values.append({
            "mapValue": {
                "fields": {
                    "name": {"stringValue": item['name']},
                    "calories": {"doubleValue": item['calories']}
                }
            }
        })

    payload = {
        "fields": {
            "date": {"stringValue": date_str},
            "totalCalories": {"doubleValue": total_cal},
            "timestamp": {"timestampValue": timestamp_str},
            "items": {
                "arrayValue": {
                    "values": items_array_values
                }
            }
        }
    }
    
    # URL Koleksi: users/{uid}/mealLogs
    url = f"{FIRESTORE_URL}/users/{USER_ID}/mealLogs"
    
    # Kirim Request (Butuh Authorization Header)
    headers = {"Authorization": f"Bearer {ID_TOKEN}"}
    response = requests.post(url, json=payload, headers=headers)
    
    if response.status_code == 200:
        print(f"  [OK] Upload meal {date_str} ({total_cal:.0f} kcal)")
    else:
        print(f"  [FAIL] {response.text}")

# 4. Generate Dummy Data (5 Hari Terakhir)
print("\nMulai upload data dummy...")
today = datetime.utcnow()

for i in range(5):
    current_date = today - timedelta(days=i)
    
    # Meal 1
    m1 = [available_foods[0], available_foods[18]] # Ayam + Nasi
    t1 = current_date.replace(hour=8, minute=0, second=0)
    upload_meal(t1, m1)
    
    # Meal 2
    m2 = [available_foods[1], available_foods[9]] # Bakso + Kerupuk
    t2 = current_date.replace(hour=13, minute=0, second=0)
    upload_meal(t2, m2)
    
    # Meal 3
    m3 = [available_foods[14]] # Mie Goreng
    t3 = current_date.replace(hour=19, minute=0, second=0)
    upload_meal(t3, m3)

print("\nSelesai! Cek aplikasi Android Anda.")
