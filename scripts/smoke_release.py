#!/usr/bin/env python3
"""Exercise the installed release, including signed-cookie ownership and score persistence."""
import http.cookiejar
import json
import os
from pathlib import Path
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request

root = Path(__file__).resolve().parents[1]
with socket.socket() as sock:
    sock.bind(("127.0.0.1", 0))
    port = sock.getsockname()[1]
base = f"http://127.0.0.1:{port}"
with tempfile.TemporaryDirectory(prefix="tunegame-smoke-") as work:
    env = {k: v for k, v in os.environ.items() if not k.startswith("SPOTIFY_")}
    env.update(PORT=str(port), PUBLIC_URL=base, DATABASE_URL=f"jdbc:sqlite:{work}/game.db")
    log_path = Path(work) / "server.log"
    with log_path.open("w") as log:
        process = subprocess.Popen([str(root / "server/build/install/server/bin/server")], cwd=work, env=env,
                                   stdout=log, stderr=subprocess.STDOUT)
        try:
            for _ in range(100):
                try:
                    with urllib.request.urlopen(base + "/", timeout=1) as response:
                        assert response.status == 200
                    break
                except (urllib.error.URLError, TimeoutError):
                    if process.poll() is not None:
                        raise RuntimeError(log_path.read_text())
                    time.sleep(0.1)
            else:
                raise RuntimeError("Server did not start: " + log_path.read_text())
            with urllib.request.urlopen(base + "/play/") as response:
                assert b'<script src="shared.js"' in response.read()
            with urllib.request.urlopen(base + "/play/shared.js") as response:
                assert response.status == 200 and len(response.read()) > 1000
            player = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
            def request(path, data=None):
                req = urllib.request.Request(base + path, data=None if data is None else json.dumps(data).encode(),
                                             headers={"Content-Type": "application/json"})
                with player.open(req) as response:
                    return json.load(response)
            game = request("/new-game", {})["gameId"]
            try:
                urllib.request.urlopen(base + "/question?gameId=" + game)
                raise AssertionError("A stranger accessed the game")
            except urllib.error.HTTPError as error:
                assert error.code == 404
            for lives in (2, 1, 0):
                assert request("/question?gameId=" + game)["question"]["choices"]
                result = request("/answer", {"gameId": game, "answer": "wrong-smoke-answer"})
                assert result["lives"] == lives
            assert result["gameOver"]
            saved = request("/scores", {"gameId": game, "playerName": "<b>Smoke</b>"})
            assert saved["playerName"] == "<b>Smoke</b>"
            assert any(score["id"] == saved["id"] for score in request("/scores"))
            print("PASS: packaged assets, cookie isolation, three rounds, game over and persisted score")
        finally:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
