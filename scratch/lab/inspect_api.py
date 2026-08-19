# litert-lm 0.16.0 파이썬 API 표면 확인용 (일회성)
import importlib
import pkgutil

candidates = ["litert_lm", "litertlm", "litert"]
mod = None
for name in candidates:
    try:
        mod = importlib.import_module(name)
        print("imported:", name, "->", mod.__file__)
        break
    except ImportError as e:
        print("no:", name, e)

if mod is None:
    import pip._internal.metadata as md  # fallback: list installed dists
    raise SystemExit("no module found")

print("version:", getattr(mod, "__version__", "?"))
print("top-level:", [n for n in dir(mod) if not n.startswith("_")])

if hasattr(mod, "__path__"):
    print("submodules:", [m.name for m in pkgutil.iter_modules(mod.__path__)])
