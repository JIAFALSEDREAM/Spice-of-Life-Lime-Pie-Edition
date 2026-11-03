"""Generate Vue's configuration data. Run: python3 docs/config-prototype/build.py

Java executes the original declaration classes;
the recording builder supports the APIs currently used here, without Minecraft.
New unsupported declaration APIs fail compilation instead of silently disappearing.
"""
import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SOURCE = ROOT / "src/main/java/com/jia/sollimepie/SOLLimePieConfig.java"


def declaration_class(source, name):
    start = source.index(f"public static class {name} {{")
    depth = 0
    # Consume strings/comments as whole tokens so their braces don't affect nesting.
    tokens = re.finditer(r'"(?:\\.|[^"\\])*"|//[^\n]*|/\*.*?\*/|[{}]', source[start:], re.S)
    for token in tokens:
        if token[0] == "{":
            depth += 1
        elif token[0] == "}":
            depth -= 1
            if depth == 0:
                return source[start:start + token.end()]
    raise ValueError(f"Unclosed declaration class: {name}")


def translations(fields):
    languages = {"en": "en_us", "zh-CN": "zh_cn"}
    catalogs = {}
    required = set()
    for language, game_locale in languages.items():
        game_file = ROOT / f"src/main/resources/assets/sollimepie/lang/{game_locale}.json"
        game = json.loads(game_file.read_text(encoding="utf-8"))
        site = json.loads((HERE / f"locales/{language}.json").read_text(encoding="utf-8"))
        required.update(site)
        catalogs[language] = {key: text for key, text in game.items()
                              if key.startswith("config.sollimepie.")}
        catalogs[language].update(site)
    for field in fields:
        key = field["translation"]
        if not key:
            raise ValueError(f"Missing translation key: {field['scope']}.{field['key']}")
        required.update((key, key + ".description", "site.groups." + field["group"]))
        # English descriptions always come from Java; no second copy to maintain.
        catalogs["en"][key + ".description"] = field["comment"]
    for language, catalog in catalogs.items():
        if any(not isinstance(text, str) for text in catalog.values()):
            raise ValueError(f"Non-string translation in {language}")
        missing = sorted(key for key in required if not catalog.get(key, "").strip())
        if missing:
            print(f"Warning: {language} missing {len(missing)} translations:\n  "
                  + "\n  ".join(missing), file=sys.stderr)
    return catalogs


def build():
    source = SOURCE.read_text()
    classes = "\n".join(declaration_class(source, name) for name in ("Server", "Client"))
    recorder = (HERE / "ConfigCapture.java").read_text().replace("/* CONFIG_CLASSES */", classes)
    with tempfile.TemporaryDirectory(prefix="lime-pie-config-") as tmp:
        java = Path(tmp) / "ConfigCapture.java"
        java.write_text(recorder)
        subprocess.run(["javac", "--release", "21", "-d", tmp, str(java)], check=True)
        result = subprocess.run(["java", "-cp", tmp, "ConfigCapture"], check=True,
                                capture_output=True, text=True)
    fields = json.loads(result.stdout)
    if len({(f["scope"], *f["path"], f["key"]) for f in fields}) != len(fields):
        raise ValueError("Duplicate configuration paths")
    output = HERE / "src/generated/config.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps({"schema": fields, "locales": translations(fields)},
                                 ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Generated {len(fields)} fields → {output}")
    return output


if __name__ == "__main__":
    build()
