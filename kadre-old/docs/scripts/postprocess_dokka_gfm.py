#!/usr/bin/env python3
r"""
Post-traitement de la sortie GFM de Dokka pour la rendre amicale à MkDocs.

Ce script nettoie la sortie Dokka GFM (GitHub-Flavored Markdown) des spécificités
non-standard de Dokka afin qu'il soit parfaitement rendu par MkDocs Material.
Il s'adapte de manière dynamique et générique à tous les packages et classes.

Usage :
    python3 postprocess_dokka_gfm.py docs/api/shared
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

# -- patterns --

# Breadcrumb : `//[name](url)/[name](url)/...` sur la première ligne
RE_BREADCRUMB = re.compile(r"^//(?:\[[^\]]+\]\([^)]+\)/?)+\s*$", re.MULTILINE)

# Tag plateforme : `[jvm]\` (suivi de \n) ou `[jvm]<br>` ou `[android]` etc.
RE_PLATFORM_LINE = re.compile(r"^\[(?:jvm|android|iosX64|iosArm64|iosSimulatorArm64|common)\]\\?\s*$", re.MULTILINE)
RE_PLATFORM_BR = re.compile(r"\[(?:jvm|android|iosX64|iosArm64|iosSimulatorArm64|common)\]<br>")
RE_PLATFORM_INLINE = re.compile(r"\[(?:jvm|android|iosX64|iosArm64|iosSimulatorArm64|common)\]\\?")

# Liste avec espaces parasites : `- \n   content` → `- content`
RE_LIST_INDENT = re.compile(r"^(- )\s*\n\s{3,}(\S)", re.MULTILINE)

# Lignes "signature" qui commencent par mots-clés Kotlin et ne sont pas DÉJÀ
# dans un code block. On les emballe.
SIG_KEYWORDS = r"(?:data class|sealed class|enum class|abstract class|open class|inner class|annotation class|interface|object|class|fun|val|var|const val|operator fun|infix fun|inline fun|suspend fun|tailrec fun|external fun|abstract fun|open fun|override fun|typealias|constructor)"
RE_SIGNATURE_LINE = re.compile(rf"^(?P<sig>(?:{SIG_KEYWORDS})\s+\[[^\]]+\]\([^)]+\).*)$", re.MULTILINE)


def strip_md_links(text: str) -> str:
    """`[Float](url)` → `Float` — pour les signatures en code block."""
    return re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", text)


def wrap_signatures_in_code(content: str) -> str:
    """
    Emballe les blocs consécutifs de lignes-signatures dans un ```kotlin … ```.
    """
    lines = content.splitlines(keepends=False)
    out: list[str] = []
    in_codefence = False
    i = 0
    while i < len(lines):
        line = lines[i]

        # Suivre les code fences existants
        if line.lstrip().startswith("```"):
            in_codefence = not in_codefence
            out.append(line)
            i += 1
            continue

        if not in_codefence and RE_SIGNATURE_LINE.match(line):
            # Collecter le bloc contigu de signatures + lignes-vides
            block: list[str] = [line]
            j = i + 1
            while j < len(lines):
                nxt = lines[j]
                if RE_SIGNATURE_LINE.match(nxt) or nxt.strip() == "":
                    block.append(nxt)
                    j += 1
                else:
                    break
            # Retire les lignes vides terminales
            while block and block[-1].strip() == "":
                block.pop()
            # Strip les liens markdown des signatures
            cleaned = [strip_md_links(b) for b in block]
            out.append("```kotlin")
            out.extend(cleaned)
            out.append("```")
            i = j
            continue

        out.append(line)
        i += 1
    return "\n".join(out) + ("\n" if content.endswith("\n") else "")


def process(text: str) -> str:
    # 1. Supprime le breadcrumb
    text = RE_BREADCRUMB.sub("", text)
    # 2. Supprime les markers plateformes
    text = RE_PLATFORM_LINE.sub("", text)
    text = RE_PLATFORM_BR.sub("", text)
    text = RE_PLATFORM_INLINE.sub("", text)
    # 3. Compacte les listes mal indentées
    text = RE_LIST_INDENT.sub(r"\1\2", text)
    # 4. Emballe les signatures dans des blocs de code
    text = wrap_signatures_in_code(text)
    # 5. Réduit les sauts de lignes successifs multiples
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text


# -- Pages Navigation Generation (.pages files) -------------------------------

# Nom de package Kotlin valide
RE_KOTLIN_PACKAGE = re.compile(r"^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$")
RE_H1 = re.compile(r"^#\s+(.+?)\s*$", re.MULTILINE)


def extract_h1(index_md: Path) -> str | None:
    """Renvoie le premier H1 d'un index.md, ou None si introuvable."""
    if not index_md.is_file():
        return None
    m = RE_H1.search(index_md.read_text(encoding="utf-8"))
    return m.group(1) if m else None


def write_pages_files(api_root: Path) -> int:
    """Écrit les `.pages` aux niveaux pertinents pour awesome-pages."""
    n = 0

    # Racine API : titre simple.
    (api_root / ".pages").write_text("title: API Reference\n", encoding="utf-8")
    n += 1

    # Niveau module (ex. `docs/api/shared/`) : titre = `:shared`.
    for module_dir in sorted(api_root.iterdir()):
        if not module_dir.is_dir():
            continue
        (module_dir / ".pages").write_text(f"title: ':{module_dir.name}'\n", encoding="utf-8")
        n += 1

        # Package roots & classes : extrait H1 pour renommer élégamment
        for class_dir in module_dir.rglob("*"):
            if not class_dir.is_dir():
                continue
            index_file = class_dir / "index.md"
            if index_file.is_file():
                title = extract_h1(index_file)
                if title:
                    # Enlever les éventuels liens de l'en-tête
                    clean_title = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", title)
                    (class_dir / ".pages").write_text(f"title: {clean_title}\n", encoding="utf-8")
                    n += 1
    return n


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(f"usage: {argv[0]} <dir>", file=sys.stderr)
        return 2
    root = Path(argv[1])
    if not root.is_dir():
        print(f"error: not a directory: {root}", file=sys.stderr)
        return 2

    n_processed = 0
    n_changed = 0
    for md in root.rglob("*.md"):
        original = md.read_text(encoding="utf-8")
        cleaned = process(original)

        # Patch H1 des constructeurs
        if md.stem == md.parent.name and md.parent.name.startswith("-"):
            cleaned = re.sub(r"^#\s+.+$", "# constructor", cleaned, count=1, flags=re.MULTILINE)

        n_processed += 1
        if cleaned != original:
            md.write_text(cleaned, encoding="utf-8")
            n_changed += 1

    print(f"Processed {n_processed} files, modified {n_changed}.")

    # Extraction et résolution du répertoire racine API
    api_root = root
    for ancestor in [root, *root.parents]:
        if ancestor.name == "api":
            api_root = ancestor
            break
    n_pages = write_pages_files(api_root)
    print(f"Wrote {n_pages} .pages files under {api_root}.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
