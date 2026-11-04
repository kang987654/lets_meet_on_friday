"""exp78c — 바꿔 말한 질문 10개의 정답 순위: 원문 dense / 모델 검색어 dense / 지금 구조(검색어 dense + 0.05 sparse) / 원문·검색어 평균 섞기.

exp78b 에서 섞기가 나아지지 않은 이유를 문항별로 본다(결과: 주된 손실은 툴 미호출 3/10, 나쁜 검색어 1건 — 섞어도 구제 안 됨).
사용: python exp78c_rank_detail.py <scratch/lab 경로> (exp78_out.txt 를 읽는다)
"""
import sys, pickle, re
from pathlib import Path
LAB=Path(sys.argv[1]); sys.path.insert(0,str(LAB))
import litert_lm.embedding_engine as EE
import exp74_library_index as E, exp76_library_dense as D
sys.stdout.reconfigure(encoding="utf-8")
laws=E.load_laws(); chunks=[c for n,ls in laws.items() for c in E.chunk_law(n,ls,500)]
pre=[(c,E.bigrams(c["path"]),E.bigrams(c["text"])) for c in chunks]
docs=pickle.loads(D.CACHE.read_bytes())
emb=EE.EmbeddingEngine(str(D.MODEL),max_input_length=512)
o=EE.EmbeddingOptions(normalize=True,input_overflow_strategy=EE.InputOverflowStrategy.TRUNCATE)
def dense(q):
    v=list(emb.compute_embedding(D.query_input(q),o).embedding); return [D.dot(d,v) for d in docs]
def sparse(q):
    t=E.terms_of(q); out=[1.5*E.score(t,pb)+E.score(t,tb) for c,pb,tb in pre]; m=max(out) or 1; return [x/m for x in out]
def rank(sc,law,gold):
    o=sorted(range(len(sc)),key=lambda i:-sc[i])
    for r,i in enumerate(o[:20]):
        c=chunks[i]
        if (c["law"]==law and c["article"] in gold) or f"{c['law']}:{c['article']}" in gold: return r+1
    return ">20"
gold_of={q:(law,gold) for k,(q,law,gold) in E.QUESTIONS if k=="바꿔말함"}
text=(LAB/"exp78_out.txt").read_text(encoding="utf-8")
qs={m.group(1):m.group(2) for m in re.finditer(r"^\[바꿔말함\] (.+)\n   B: search_documents '(.+?)'",text,re.M)}
print("질문 | 모델 검색어 | 정답 순위: 원문dense / 검색어dense / 검색어 Q(w.05) / 섞기 M(w.05) / 원문만 1위 조각")
for q,(law,gold) in gold_of.items():
    du=dense(q); top=chunks[max(range(len(du)),key=lambda i:du[i])]
    qq=qs.get(q)
    if qq:
        dq,sq=dense(qq),sparse(qq)
        r=[rank(du,law,gold),rank(dq,law,gold),rank([a+0.05*b for a,b in zip(dq,sq)],law,gold),rank([(x+a)/2+0.05*b for x,a,b in zip(du,dq,sq)],law,gold)]
    else: r=[rank(du,law,gold),"-","-","-"]
    print(f"{q} | {qq or '(호출 안 함)'} | {' / '.join(map(str,r))} | {top['law']} {top['path'].split(' > ')[-1][:20]}")
