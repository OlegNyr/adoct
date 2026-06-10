---
apply: always
mode: all
---

# Java Code Generation Style Guide for AI Coding Agent

This document defines the Java code style to be strictly followed by the AI coding agent. These rules align with the `smart.xml` Checkstyle configuration and ensure consistent, maintainable, and readable code.

## 1. General Requirements
- **Encoding:** UTF-8.
- **Indentation:** 2 spaces (no tabs).
- **Line Length:** Maximum 120 characters.
- **Line Wrapping:**
    - Wrap before binary operators (except assignment).
    - Do not wrap after commas or before opening parentheses.
    - Continuation lines indented +4 spaces.

## 2. File Structure
- **Order:** License (if any), `package`, imports, exactly one public class.
- **Sections:** Separated by a single blank line.

## 3. Imports
- **No Wildcards:** `import java.util.*;` is prohibited.
- **Group Order:**
    1. Third-party packages (not `java.*` or `javax.*`)
    2. `javax.*`
    3. `java.*`
    4. Static imports
- **Sorting:** Lexicographic ASCII order within each group.
- **Static Imports:** Do not use for nested classes.

## 4. Code Formatting

### 4.1 Braces
- Always use braces `{}` for `if`, `for`, `while`, `do`, even for single-line bodies.
- **K&R Style:**
    - Opening brace on the same line, no space before.
    - Closing brace on a new line.
- **Empty Block:** Use `{}` on one line, except for multi-block constructs (`if/else`, `try/catch/finally`).

### 4.2 Spacing
- **One Space:**
    - After keywords (`if`, `for`, `catch`) before `(`
    - Before `{` in class/method declarations
    - Around binary operators (`+`, `-`, `&&`, `|`, but not `.` or `::`)
    - After commas, semicolons
    - Between type and variable name
- **No Spaces:**
    - Before `:` in `case`
    - Inside method call parentheses (`method(arg)`)
    - Between unary operator and operand
    - In casts: `(String)object`

### 4.3 Line Wrapping
- Wrap operators (except assignment) before the symbol (e.g., before `.`, not after).
- Assignment can wrap after `=`.
- Method name always with opening parenthesis on the same line.
- Lambda: If body is an expression, wrap after `->`.

### 4.4 Annotations
- Each annotation on its own line, except `@Override` (can be on the same line as method declaration).
- Multiple field annotations can be on one line.

### 4.5 Language Constructs
- **Enum:** Constants can be in a single line or column; methods formatted as regular classes.
- **Switch:**
    - Cases indented +2.
    - If no `break`/`return`, add comment: `// fall through`.
    - No space before colon.
- **Arrays:**
    - Declaration: `String[] args` (brackets with type).
    - Initializers can be block-style with line breaks.
- **Modifiers Order:** `public protected private abstract default static final transient volatile synchronized native strictfp`.

## 5. Naming Conventions
- **Packages:** All lowercase, words concatenated (`com.example.myapp`).
- **Classes:** UpperCamelCase; digits/underscores only for number separation (e.g., `MyClass`, `Test12`). Test classes end with `Test`.
- **Methods:** lowerCamelCase; do not start with a single lowercase letter. Underscores allowed in tests.
- **Constants** (`static final`): UPPER_SNAKE_CASE.
- **Fields, Parameters, Locals:** lowerCamelCase. Avoid single-letter names.
- **Type Variables:**
    - Single uppercase letter (T, E) or class name + T (RequestT).
- **No Hungarian Notation** (mVar, sVar) or trailing underscores.

## 6. Programming Practices
- Always use `@Override` for overridden/implemented methods (unless parent is `@Deprecated`).
- Empty `catch` allowed only if exception variable is named `expected` or a comment explains why.
- Call static members via class name, not instance.
- Do not use `finalize()`.
- Use uppercase `L` suffix for long literals (`100L`).

## 7. Javadoc
- Public classes and methods must have Javadoc, except obvious getters/setters.
- **Format:**
  ```java
  /**
   * Brief description.
   *
   * <p>Detailed description if needed.
   *
   * @param paramName description
   * @return description
   */
  ```
- Single-line Javadoc allowed if it fits on one line.
- Tags (`@param`, `@return`) indented +4 for multi-line descriptions.

## 8. Comments
- Use `TODO:` (uppercase, with colon).
- Comment indentation matches surrounding code.

## 9. Special Characters
- Prefer escape sequences (`\n`, `\t`) over Unicode escapes where possible.
- For non-ASCII characters, prefer the actual character (e.g., `μ`), not `\u03bc`, unless readability is affected.