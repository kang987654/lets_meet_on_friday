"""exp74 자료 받기 — 국가법령정보센터 공개 법령 본문(웹 페이지)을 글자로 저장한다.

계정·API 키·이메일을 쓰지 않는다(공개 페이지 읽기). 결과는 scratch/lab/library_data/ (git 제외).
본문만 남기고 부칙은 뺀다. 줄은 페이지 구조(편·장·절·조 제목, 항·호)를 그대로 따른다.
"""
import html as H
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")
UA = {"User-Agent": "Mozilla/5.0 (KOSMOS lab research)"}
OUT = Path(__file__).parent / "library_data"
LAWS = ["민법", "상법", "형법", "민사소송법", "공무원 행동강령", "개인정보 보호법 시행령"]


def fetch(url, data=None):
    req = urllib.request.Request(url, data=data, headers=UA)
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read().decode("utf-8", "replace")


def body_lines(raw):
    text = re.sub(r"<script.*?</script>|<style.*?</style>", "", raw, flags=re.S)
    text = re.sub(r"<(?:br|/p|/div|/li|/tr)[^>]*>", "\n", text)
    text = H.unescape(re.sub(r"<[^>]+>", "", text))
    lines = [re.sub(r"\s+", " ", l).strip() for l in text.splitlines()]
    lines = [l for l in lines if l]
    start = next((i for i, l in enumerate(lines) if re.match(r"^제1조\s*\(", l) or re.match(r"^제1편|^제1장", l)), 0)
    # 부칙 머리줄은 앞에 HTML 잔여물이 붙어 온다("... 부 칙 <법률 제9650호, ...>").
    end = next((i for i, l in enumerate(lines) if i > start and re.search(r"부\s*칙\s*<", l)), len(lines))
    return lines[start:end]


OUT.mkdir(exist_ok=True)
for name in LAWS:
    page = fetch("https://www.law.go.kr/" + urllib.parse.quote("법령/" + name))
    seq = re.search(r"lsiSeq=(\d+)", page)
    ef = re.search(r"efYd=(\d+)", page)
    if not seq:
        print("skip (no lsiSeq):", name); continue
    data = urllib.parse.urlencode({"lsiSeq": seq.group(1), "chrClsCd": "010202", "efYd": ef.group(1) if ef else "",
                                   "ancYnChk": "0", "nwJoYnInfo": "Y", "efGubun": "Y"}).encode()
    lines = body_lines(fetch("https://www.law.go.kr/LSW/lsInfoR.do", data))
    path = OUT / f"{name.replace(' ', '_')}.txt"
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    arts = sum(1 for l in lines if re.match(r"^제\d+조(의\d+)?\s*\(", l))
    print(f"{name}: lsiSeq={seq.group(1)} lines={len(lines)} chars={sum(map(len, lines))} articles={arts} -> {path.name}", flush=True)
    time.sleep(2)
