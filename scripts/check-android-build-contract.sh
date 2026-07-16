#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"

scan_gradle_files() {
  while IFS= read -r -d '' file; do
    awk '
      function append_char(character, is_string) {
        code = code character
        string_mask = string_mask (is_string ? "1" : "0")
      }

      function has_unmasked_match(text, mask, pattern, remaining, offset, position, length_) {
        remaining = text
        offset = 1

        while (match(remaining, pattern)) {
          position = offset + RSTART - 1
          if (substr(mask, position, 1) != "1") {
            return 1
          }

          length_ = RLENGTH
          offset = position + length_
          remaining = substr(text, offset)
        }

        return 0
      }

      BEGIN {
        android_target = "androidTarget[[:space:]]*\\("
        kotlin_android_plugin = "id\\(\"org\\.jetbrains\\.kotlin\\.android\"\\)|kotlin\\(\"android\"\\)"
      }

      {
        original = $0
        code = ""
        string_mask = ""
        escaped = 0
        char_escaped = 0

        for (column = 1; column <= length(original);) {
          character = substr(original, column, 1)
          pair = substr(original, column, 2)
          triple = substr(original, column, 3)

          if (block_comment_depth > 0) {
            if (pair == "/*") {
              append_char(" ", 0)
              append_char(" ", 0)
              block_comment_depth++
              column += 2
            } else if (pair == "*/") {
              append_char(" ", 0)
              append_char(" ", 0)
              block_comment_depth--
              column += 2
            } else {
              append_char(" ", 0)
              column++
            }
          } else if (raw_string) {
            if (triple == "\"\"\"") {
              append_char("\"", 1)
              append_char("\"", 1)
              append_char("\"", 1)
              raw_string = 0
              column += 3
            } else {
              append_char(character, 1)
              column++
            }
          } else if (quoted_string) {
            append_char(character, 1)
            if (escaped) {
              escaped = 0
            } else if (character == "\\") {
              escaped = 1
            } else if (character == "\"") {
              quoted_string = 0
            }
            column++
          } else if (quoted_char) {
            append_char(character, 1)
            if (char_escaped) {
              char_escaped = 0
            } else if (character == "\\") {
              char_escaped = 1
            } else if (character == "\047") {
              quoted_char = 0
            }
            column++
          } else if (pair == "//") {
            while (column <= length(original)) {
              append_char(" ", 0)
              column++
            }
          } else if (pair == "/*") {
            append_char(" ", 0)
            append_char(" ", 0)
            block_comment_depth = 1
            column += 2
          } else if (triple == "\"\"\"") {
            append_char("\"", 1)
            append_char("\"", 1)
            append_char("\"", 1)
            raw_string = 1
            column += 3
          } else if (character == "\"") {
            append_char(character, 1)
            quoted_string = 1
            column++
          } else if (character == "\047") {
            append_char(character, 1)
            quoted_char = 1
            column++
          } else {
            append_char(character, 0)
            column++
          }
        }

        quoted_string = 0
        quoted_char = 0

        if (has_unmasked_match(code, string_mask, android_target) ||
            has_unmasked_match(code, string_mask, kotlin_android_plugin)) {
          print FILENAME ":" FNR ":" original
        }
      }
    ' "$file"
  done < <(rg --files --null "$root" --glob '*.gradle.kts' --glob '!docs/**')
}

legacy="$({
  scan_gradle_files
  rg -n '^android\.(builtInKotlin|newDsl)=false|^systemProp\..*android\.(builtInKotlin|newDsl)=false' "$root/gradle.properties" || true
})"

if [[ -n "$legacy" ]]; then
  printf '%s\n' "$legacy" >&2
  exit 1
fi
