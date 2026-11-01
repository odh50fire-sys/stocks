import http.server
import socketserver
import urllib.request
import urllib.parse
import json
import time
import os
import sys
import threading
from collector import collect_all_12_indicators, fetch_url, HEADERS

sys.stdout.reconfigure(encoding='utf-8')

PORT = 8080
CACHE_TTL = 5  # 5 seconds cache
cached_indicators = None
last_cache_time = 0
cache_lock = threading.Lock()

def get_cached_indicators():
    global cached_indicators, last_cache_time
    now = time.time()
    with cache_lock:
        if cached_indicators and (now - last_cache_time < CACHE_TTL):
            return cached_indicators
        try:
            data = collect_all_12_indicators()
            cached_indicators = {
                "timestamp": int(now),
                "items": data
            }
            last_cache_time = now
            return cached_indicators
        except Exception as e:
            print(f"Error updating cache: {e}")
            if cached_indicators:
                return cached_indicators
            return {"timestamp": int(now), "items": []}

class CustomHTTPHandler(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        # Enable CORS and disable aggressive caching for API
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        super().end_headers()

    def do_OPTIONS(self):
        self.send_response(200)
        self.end_headers()

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        query = urllib.parse.parse_qs(parsed.query)

        # API: /api/indicators
        if path == "/api/indicators":
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Cache-Control", "no-cache")
            self.end_headers()
            data = get_cached_indicators()
            self.wfile.write(json.dumps(data, ensure_ascii=False).encode('utf-8'))
            return

        # API: /api/candles?symbol=MU&interval=5m
        if path == "/api/candles":
            symbol = query.get("symbol", ["MU"])[0]
            interval = query.get("interval", ["5m"])[0]
            target_url = f"https://hlkr.co.kr/api/candles/{symbol}?interval={interval}"
            try:
                content = fetch_url(target_url, timeout=5)
                self.send_response(200)
                self.send_header("Content-Type", "application/json; charset=utf-8")
                self.send_header("Cache-Control", "no-cache")
                self.end_headers()
                self.wfile.write(content.encode('utf-8'))
            except Exception as e:
                self.send_response(500)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(json.dumps({"error": str(e), "candles": []}).encode('utf-8'))
            return

        # Serve static files from workspace root
        return super().do_GET()

def run_server():
    # Pre-fetch cache in background once
    threading.Thread(target=get_cached_indicators, daemon=True).start()
    
    server_address = ('', PORT)
    socketserver.TCPServer.allow_reuse_address = True
    with socketserver.TCPServer(server_address, CustomHTTPHandler) as httpd:
        print(f"Server started at http://localhost:{PORT}")
        httpd.serve_forever()

if __name__ == "__main__":
    run_server()
