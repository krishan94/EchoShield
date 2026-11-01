"""
EchoShield — Firestore & Firebase Admin Verification Script
============================================================
Tests server-side Firebase Admin initialization and performs
a real read-write-delete roundtrip test on Cloud Firestore.
NEVER prints or leaks private keys.
"""

import sys
import os
from pathlib import Path

# Ensure backend directory is in sys.path
backend_dir = Path(__file__).resolve().parents[1]
if str(backend_dir) not in sys.path:
    sys.path.insert(0, str(backend_dir))

from app.core.config import settings
from app.core.firebase import (
    initialize_firebase_admin,
    is_service_account_configured,
    get_credentials_path,
    verify_firestore_connection,
)


def run_verification():
    print("=" * 60)
    print("EchoShield — Firebase Admin & Firestore Verification Check")
    print("=" * 60)

    print(f"[*] Firebase Project ID: {settings.FIREBASE_PROJECT_ID}")

    cred_path = settings.get_firebase_credentials_path()
    if cred_path:
        print(f"[*] Resolved Credentials Path: {cred_path}")
        print(f"    - File exists on disk: {os.path.exists(cred_path)}")
        print(f"    - File size: {os.path.getsize(cred_path)} bytes")
    else:
        print("[-] No service account credentials file found.")
        print("    Checked:")
        print("    1. FIREBASE_CREDENTIALS_PATH environment variable")
        print("    2. GOOGLE_APPLICATION_CREDENTIALS environment variable")
        print(f"    3. {backend_dir / 'secrets' / 'serviceAccountKey.json'}")

    print("\n[*] Initializing Firebase Admin SDK...")
    try:
        app = initialize_firebase_admin()
        if is_service_account_configured():
            print("[+] Firebase Admin initialized with service account certificate!")
        else:
            print("[!] Firebase Admin initialized using Application Default Credentials / Project ID.")
    except Exception as e:
        print(f"[x] Firebase Admin initialization failed: {str(e)}")
        sys.exit(1)

    print("\n[*] Testing Firestore Connectivity and Read/Write Permissions...")
    try:
        result = verify_firestore_connection()
        print("[+] Firestore Read/Write verification PASSED!")
        print(f"    - Status: {result.get('status')}")
        print(f"    - Timestamp: {result.get('timestamp')}")
        print(f"    - Message: {result.get('message')}")
        print("\n" + "=" * 60)
        print("RESULT: SUCCESS - Firebase Admin & Firestore are fully operational.")
        print("=" * 60)
        sys.exit(0)
    except Exception as e:
        print(f"[x] Firestore Read/Write test FAILED: {str(e)}")
        print("\n" + "=" * 60)
        print("RESULT: FAILED / INCOMPLETE")
        print("Action Required:")
        print("1. Place your service account JSON file into:")
        print(f"   {backend_dir / 'secrets' / 'serviceAccountKey.json'}")
        print("2. Or set the FIREBASE_CREDENTIALS_PATH environment variable to its location.")
        print("3. Re-run this script: python backend/scripts/verify_firestore.py")
        print("=" * 60)
        sys.exit(1)


if __name__ == "__main__":
    run_verification()

