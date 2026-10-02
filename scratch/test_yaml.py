import yaml

with open('.github/workflows/docker-build.yml', 'r', encoding='utf-8') as f:
    text = f.read()

tokens = []
try:
    for t in yaml.scan(text):
        tokens.append(t)
except Exception as e:
    print("Failed with:", e)
    for t in tokens[-10:]:
        print(t)
