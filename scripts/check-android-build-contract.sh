#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"

scan_gradle_files() {
  while IFS= read -r -d '' file; do
    relative="${file#"$root"/}"
    enforce_kmp_namespace=0
    case "$relative" in
      kadre/build.gradle.kts | \
        kadre-core/build.gradle.kts | \
        kadre-android/build.gradle.kts | \
        kadre-test/build.gradle.kts | \
        samples/hello-touch/build.gradle.kts | \
        samples/pong/build.gradle.kts)
        enforce_kmp_namespace=1
        ;;
    esac

    awk -v enforce_kmp_namespace="$enforce_kmp_namespace" '
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

      function is_identifier_start(character) {
        return character ~ /[[:alpha:]_]/
      }

      function is_identifier_part(character) {
        return character ~ /[[:alnum:]_]/
      }

      function is_inside_kmp_android_scope(level, found_android) {
        found_android = 0

        for (level = scope_depth; level >= 1; level--) {
          if (!found_android && scope[level] == "android") {
            found_android = 1
          } else if (found_android && scope[level] == "kotlin") {
            return 1
          }
        }

        return 0
      }

      function inspect_scopes(text, original, character, cursor, end_, word) {
        for (cursor = 1; cursor <= length(text);) {
          character = substr(text, cursor, 1)

          if (is_identifier_start(character)) {
            end_ = cursor + 1
            while (end_ <= length(text) && is_identifier_part(substr(text, end_, 1))) {
              end_++
            }
            word = substr(text, cursor, end_ - cursor)
            pending_identifier = word
            cursor = end_
          } else if (character == "{") {
            scope_depth++
            scope[scope_depth] = pending_identifier
            pending_identifier = ""
            cursor++
          } else if (character == "}") {
            delete scope[scope_depth]
            if (scope_depth > 0) {
              scope_depth--
            }
            pending_identifier = ""
            cursor++
          } else if (character == "=") {
            if (pending_identifier == "namespace") {
              found_namespace = 1
              if (is_inside_kmp_android_scope()) {
                found_nested_namespace = 1
              } else if (enforce_kmp_namespace) {
                invalid_namespace = 1
                print FILENAME ":" FNR ":" original
              }
            }
            pending_identifier = ""
            cursor++
          } else if (character ~ /[[:space:]]/) {
            cursor++
          } else {
            pending_identifier = ""
            cursor++
          }
        }
      }

      BEGIN {
        android_target = "androidTarget[[:space:]]*\\("
        kotlin_android_plugin = "id\\(\"org\\.jetbrains\\.kotlin\\.android\"\\)|kotlin\\(\"android\"\\)"
        android_library_plugin = "id[[:space:]]*\\([[:space:]]*\"com\\.android\\.library\"[[:space:]]*\\)|apply[[:space:]]*\\([[:space:]]*plugin[[:space:]]*=[[:space:]]*\"com\\.android\\.library\"[[:space:]]*\\)"
        kotlin_multiplatform_plugin = "id[[:space:]]*\\([[:space:]]*\"org\\.jetbrains\\.kotlin\\.multiplatform\"[[:space:]]*\\)|kotlin[[:space:]]*\\([[:space:]]*\"multiplatform\"[[:space:]]*\\)|apply[[:space:]]*\\([[:space:]]*plugin[[:space:]]*=[[:space:]]*\"org\\.jetbrains\\.kotlin\\.multiplatform\"[[:space:]]*\\)"
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

        if (has_unmasked_match(code, string_mask, android_library_plugin)) {
          android_library_plugin_count++
          android_library_plugin_line[android_library_plugin_count] = FNR
          android_library_plugin_source[android_library_plugin_count] = original
        }
        if (has_unmasked_match(code, string_mask, kotlin_multiplatform_plugin)) {
          found_kotlin_multiplatform_plugin = 1
        }

        structure = ""
        for (column = 1; column <= length(code); column++) {
          if (substr(string_mask, column, 1) == "1") {
            structure = structure " "
          } else {
            structure = structure substr(code, column, 1)
          }
        }
        inspect_scopes(structure, original)
      }

      END {
        if (found_kotlin_multiplatform_plugin && android_library_plugin_count > 0) {
          for (plugin_index = 1; plugin_index <= android_library_plugin_count; plugin_index++) {
            print FILENAME ":" android_library_plugin_line[plugin_index] ":" android_library_plugin_source[plugin_index]
          }
        }

        if (enforce_kmp_namespace && !found_nested_namespace && !invalid_namespace) {
          print FILENAME ":1:<missing namespace inside kotlin { android { ... } }>"
        }
      }
    ' "$file" || return 1
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
