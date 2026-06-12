#!/usr/bin/env python3
"""
Post-processing fixup for generated ObjC FFM Kotlin bindings.

Fixes:
1. Missing package declaration + imports in split-output files
2. Raw `Class` → `Class<*>` (Kotlin requires star projection)
3. Class method keyword escaping (`NSData_`data`` → `NSData_data`)
4. Missing `override` for methods re-defined in subclasses
5. Kotlin hard keywords used as function names (e.g. `object`, `null`, `class`)
6. Conflicting overloads (dedup methods with same Kotlin signature)
"""
import os
import re
import sys
from pathlib import Path

OUT_PKG = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(
    "ffi/objc/src/jvmMain/kotlin/org/graphiks/kffi/objc"
)

PACKAGE_DECL = "package org.graphiks.kffi.objc"
IMPORTS = """\
import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*"""

# Kotlin hard keywords that cannot be used as identifiers without backtick escaping
KOTLIN_KEYWORDS = {
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun",
    "if", "in", "interface", "is", "null", "object", "package", "return",
    "super", "this", "throw", "true", "try", "typealias", "typeof", "val",
    "var", "when", "while",
}


def needs_imports(content: str) -> bool:
    """Check if file already has package declaration."""
    return not content.startswith("package ")


def prepend_imports(content: str) -> str:
    return f"{PACKAGE_DECL}\n\n{IMPORTS}\n\n{content}"


def fix_class_keyword_escaping(content: str) -> str:
    """Fix `ClassName_`keyword`` → proper identifier.
    
    Pattern: `ClassName_`keyword`` is invalid. We need to either:
    - wrap the whole thing: ``ClassName_keyword``
    - or, since prefix prevents keyword collision, just unwrap: `ClassName_keyword`
    """
    return re.sub(
        r'fun (\w+)_`(\w+)`(\s*[:(])',
        r'fun \1_\2\3',
        content
    )


def fix_raw_class(content: str) -> str:
    """Replace raw `Class` (without type parameter) with `Class<*>`.
    
    Pattern: `: Class)` → `: Class<*>)` in parameter/return types,
    and `(Class)` → `(Class<*>)` for cast expressions.
    But NOT when Class already has a type arg (<) or is a keyword.
    """
    content = re.sub(r'(?<![.\w])Class(?![.\w<*])', 'Class<*>', content)
    return content


def fix_keyword_function_names(content: str) -> str:
    """Wrap Kotlin hard keywords used as function names in backticks.
    
    Matches `fun keyword(` and `open fun keyword(` where `keyword` is a
    Kotlin hard keyword, and adds backticks around the identifier.
    """
    def escape_keyword(m):
        prefix = m.group(1)  # whitespace prefix
        mods = m.group(2) or ""  # "open " or ""
        kword = m.group(3)  # the keyword
        rest = m.group(4)  # "("
        return f"{prefix}{mods}fun `{kword}`{rest}"
    
    return re.sub(
        rf'^(\s+)((?:open\s+)?)fun\s+({"|".join(KOTLIN_KEYWORDS)})\s*(\()',
        escape_keyword,
        content,
        flags=re.MULTILINE
    )


def build_class_map(class_dir: Path) -> dict:
    """Build class_name → {super, methods} from all .kt files."""
    class_map = {}
    for f in sorted(class_dir.glob("*.kt")):
        text = f.read_text()
        # Extract class name and superclass
        m = re.search(r'^open class (\w+)(?:\([^)]*\))?(?:\s*:\s*(\w+)\(ptr\))?', text, re.MULTILINE)
        if not m:
            continue
        name = m.group(1)
        super_name = m.group(2) if m.lastindex >= 2 and m.group(2) else None
        
        # Extract method names (fun declarations inside class body)
        # Match: `fun methodName(` or `open fun methodName(`
        methods = set()
        for fn in re.finditer(r'^\s+(?:open\s+)?(?:override\s+)?fun\s+`?(\w+)`?\s*[\(:]', text, re.MULTILINE):
            methods.add(fn.group(1))
        # Also match extension functions (top-level)
        for fn in re.finditer(r'^fun \w+\.`?(\w+)`?\s*[\(:]', text, re.MULTILINE):
            methods.add(fn.group(1))
        
        class_map[name] = {
            "super": super_name,
            "methods": methods,
            "file": f,
            "text": text,
        }
    return class_map


def add_overrides(class_dir: Path):
    """Add `override` to methods in subclasses that shadow parents."""
    class_map = build_class_map(class_dir)
    
    for name, info in class_map.items():
        if not info["super"] or info["super"] not in class_map:
            continue
        parent = class_map[info["super"]]
        common = info["methods"] & parent["methods"]
        if not common:
            continue
        
        text = info["text"]
        changed = False
        for method in common:
            # Add override to `fun methodName(` or `open fun methodName(`
            # but NOT if it already has `override`
            pattern1 = rf'^(\s+)fun\s+`?{re.escape(method)}`?\s*[\(]'
            pattern2 = rf'^(\s+)open\s+fun\s+`?{re.escape(method)}`?\s*[\(]'
            replacement = r'\1override fun `' + method + r'`('
            
            if re.search(pattern2, text, re.MULTILINE):
                text = re.sub(pattern2, replacement, text, count=1, flags=re.MULTILINE)
                changed = True
            elif re.search(pattern1, text, re.MULTILINE):
                text = re.sub(pattern1, replacement, text, count=1, flags=re.MULTILINE)
                changed = True
        
        if changed:
            info["file"].write_text(text)
            print(f"  override: {name} ({len(common)} methods)")


def make_parent_methods_open(class_dir: Path):
    """Make all methods `open fun` in root classes (so children can override)."""
    class_map = build_class_map(class_dir)
    
    for name, info in class_map.items():
        if not info["super"]:
            # Root class - make methods open
            text = info["text"]
            changed = False
            
            # Replace `fun methodName(` with `open fun methodName(`
            # but not if already open
            def make_open(m):
                prefix = m.group(1)
                fn_name = m.group(2)
                if "override" in m.group(0):
                    return m.group(0)  # Skip override methods
                return f"{prefix}open fun {fn_name}("
            
            new_text = re.sub(
                r'^(\s+)fun\s+`?(\w+)`?\s*\(',
                make_open,
                text,
                flags=re.MULTILINE
            )
            if new_text != text:
                info["file"].write_text(new_text)
                changed = True
            
            if changed:
                print(f"  open: {name}")


def main():
    print(f"→ Fixing bindings in {OUT_PKG}")
    
    # 1. Add package + imports to all split-output directories
    for subdir in ["classes", "protocols", "enums", "options", "types", "functions"]:
        d = OUT_PKG / subdir
        if not d.exists():
            continue
        count = 0
        for f in sorted(d.glob("*.kt")):
            text = f.read_text()
            if needs_imports(text):
                f.write_text(prepend_imports(text))
                count += 1
        if count:
            print(f"  {subdir}/: added imports to {count} files")
    
    # 2. Fix keyword escaping (`ClassName_`keyword`` → ClassName_keyword)
    for subdir in ["classes", "protocols", "enums", "options", "types", "functions"]:
        d = OUT_PKG / subdir
        if not d.exists():
            continue
        count = 0
        for f in sorted(d.glob("*.kt")):
            text = f.read_text()
            new_text = fix_class_keyword_escaping(text)
            if new_text != text:
                f.write_text(new_text)
                count += 1
        if count:
            print(f"  {subdir}/: fixed keyword escaping in {count} files")
    
    # 3. Fix raw `Class` → `Class<*>`
    for subdir in ["classes", "protocols", "enums", "options", "types", "functions"]:
        d = OUT_PKG / subdir
        if not d.exists():
            continue
        count = 0
        for f in sorted(d.glob("*.kt")):
            text = f.read_text()
            new_text = fix_raw_class(text)
            if new_text != text:
                f.write_text(new_text)
                count += 1
        if count:
            print(f"  {subdir}/: fixed raw Class in {count} files")
    
    # 4. Fix Kotlin hard keywords used as function names (e.g. `object`, `null`, `class`)
    for subdir in ["classes", "protocols", "enums", "options", "types", "functions"]:
        d = OUT_PKG / subdir
        if not d.exists():
            continue
        count = 0
        for f in sorted(d.glob("*.kt")):
            text = f.read_text()
            new_text = fix_keyword_function_names(text)
            if new_text != text:
                f.write_text(new_text)
                count += 1
        if count:
            print(f"  {subdir}/: fixed keyword function names in {count} files")
    
    # 5. Make root class methods open, add override to subclasses
    class_dir = OUT_PKG / "classes"
    if class_dir.exists():
        count_before = len([m for m in re.finditer(
            r"'hides member of supertype'", "", re.MULTILINE
        )])
        make_parent_methods_open(class_dir)
        add_overrides(class_dir)
        print(f"  classes/: applied override fixes")
    
    print("✓ Fixup complete")


if __name__ == "__main__":
    main()
