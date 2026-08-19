"""aespa ko-위키 도입부를 1회 fetch 해 동결한다 (재현성 — 실험 중 라이브 fetch 금지).

WikipediaSearchToolImpl 과 동일한 엔드포인트·파라미터·출력 형식("Title: ...\n--- SUMMARY ---\n...").
"""

import json
import pathlib
import urllib.parse
import urllib.request

FIXTURES = pathlib.Path(__file__).parent / "fixtures"

params = {
    "action": "query",
    "format": "json",
    "generator": "search",
    "gsrsearch": "에스파",
    "gsrlimit": "1",
    "prop": "extracts",
    "explaintext": "1",
    "exintro": "1",
}
url = "https://ko.wikipedia.org/w/api.php?" + urllib.parse.urlencode(params)
req = urllib.request.Request(url, headers={"User-Agent": "KOSMOS/0.8.0 (on-device personal assistant; personal project)"})
body = json.loads(urllib.request.urlopen(req, timeout=30).read().decode("utf-8"))

pages = body["query"]["pages"]
page = pages[next(iter(pages))]
title = page.get("title", "")
extract = page.get("extract", "")
result = f"Title: {title}\n--- SUMMARY ---\n{extract}"

out = FIXTURES / "wiki_aespa_intro.txt"
out.write_text(result, encoding="utf-8")
print(f"frozen: {out} ({len(result)} chars)")
print(result[:500])
print("...")
print("데뷔일 포함 여부:", "데뷔" in result)
