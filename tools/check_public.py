"""Fail when anything internal would be published: in the public repository's tree, or in the
artifacts a release uploads to Maven Central.

Customers read this SDK's source, tests and documents. Nothing in them may cite the team's internal
designs, decisions, work items or test cases, or name an internal repository or a private customer.

    python3 tools/check_public.py --tree <directory>      # every text file under a directory
    python3 tools/check_public.py --package <directory>   # the jars ./mvnw -P release wrote there

A release is also refused when it lacks what Maven Central requires of it: a sources jar and a
Javadoc jar beside the jar, and a POM (the one the jar carries) naming the project, its description
and URL, its licence, its developers and its source repository.

Two kinds of check:

* **Patterns** for internal identifiers whose *shape* is the giveaway: work-item, decision and
  architecture-record ids, test-case ids of both schemes, internal design paths, section
  references, tracker area paths, team lanes, private repository names and the name the team gives
  the SDK every other one derives from.
* **Names** of private customers, internal repositories and other products, read from
  ``tools/internal-names.txt``. A list of words a public repository must never contain cannot be
  published in it, so that file stays in the private repository and the export leaves it out; the
  public repository runs the pattern check alone, and every release is checked with both before it
  is exported. Hyphenated tokens are checked whole and part by part.

Standard library only: it runs on a bare runner.
"""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
import zipfile
from collections.abc import Iterable, Iterator
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]

#: The test-case area codes: a case id of the second scheme is ``TC-``, one of these, and four
#: digits. This closed list MUST agree with the one the test-results publisher holds, so nothing it
#: would record can be published. Longest code first, as the publisher builds its pattern.
_TC_AREAS = sorted(
    (
        "CP", "AGENT", "INFRA", "DOCS", "SDK", "ADMIN", "BILL", "SUP",
        "PORTAL", "RISK", "SEC", "COPY", "CTRD", "IDN", "TRADE", "CX",
    ),
    key=lambda code: (-len(code), code),
)  # fmt: skip

#: Identifiers that are internal by their shape.
INTERNAL = re.compile(
    # Written so that these lines do not match themselves when this file is scanned.
    r"design[/]|\bDEC-\d+|AB#\d+|\bADR-\d+|TC-S[1]-|§ ?\d"
    # A test-case id of the area scheme; the codes are joined in, so no id is spelt out here.
    r"|TC-(?:" + "|".join(_TC_AREAS) + r")-\d{4}"
    # A tracker area path is upper case - MFX, a backslash, a capital. A lower-case file path that
    # starts the same way is not one.
    r"|(?-i:MFX[\\]{1,2}[A-Z])|\bLane \d|-intern[a]l\b"
    # The team's name for the SDK the others derive from. Public text names that SDK by its
    # language, or states the behaviour itself.
    r"|\breferenc[e] SDK",
    re.IGNORECASE,
)

#: The SDK reference the docs site renders is held to more: every word in it is read as the
#: product's own, so it names no tracker or decision id even without a number, calls the trading
#: terminal "the terminal" and the platform's machines "hosts", and names no private repository.
#: Case matters for the terminal's short name only: ``mt5`` is a venue's value.
REFERENCE = "docs/sdkref.json"
REFERENCE_WORDS = re.compile(r"AB#|DEC-|(?-i:MT5)|\bcells?\b|-intern[a]l", re.IGNORECASE)

#: Where the names live: one lower-case name a line, ``#`` starts a comment. Absent in the public
#: repository, where the check runs on patterns alone.
NAMES_FILE = REPO / "tools" / "internal-names.txt"

#: Directories a tree scan never enters: git's own files, environments, caches and build output,
#: none of which is committed.
_SKIP = frozenset(
    {
        ".git", ".vs", ".idea", ".hub", ".venv", "bin", "obj", "target", "TestResults", "reports",
        "artifacts", "node_modules", "__pycache__", ".pytest_cache",
    }
)  # fmt: skip
_TOKEN = re.compile(r"[a-z0-9][a-z0-9-]*")


def load_names(path: Path = NAMES_FILE) -> frozenset[str]:
    """The names in ``path``, lower case; none when the file is absent."""
    if not path.is_file():
        return frozenset()
    lines = (line.split("#", 1)[0].strip().lower() for line in path.read_text("utf-8").splitlines())
    return frozenset(line for line in lines if line)


NAMES = load_names()


def _text(data: bytes) -> str | None:
    """``data`` as text, or None when it is binary (a NUL in its first 8 KiB)."""
    # DECISION: every file is scanned unless it is binary, rather than a list of text extensions as
    # the wheel and npm checks keep. This tree mixes Java, Maven, shell, PowerShell, Python, JSON
    # and Markdown, and a file type missing from a list would be published unread.
    if b"\0" in data[:8192]:
        return None
    return data.decode("utf-8", "replace")


def _tree(root: Path) -> Iterator[tuple[str, str]]:
    for path in sorted(root.rglob("*")):
        relative = path.relative_to(root)
        if _SKIP.intersection(relative.parts[:-1]) or not path.is_file():
            continue
        text = _text(path.read_bytes())
        if text is not None:
            yield relative.as_posix(), text


def match(line: str, names: frozenset[str] = NAMES) -> str | None:
    """What in ``line`` is internal, or None. A name is reported by the token that carried it."""
    found = INTERNAL.search(line)
    if found:
        return found.group(0)
    for token in _TOKEN.findall(line.lower()):
        for word in (token, *token.split("-")):
            if word in names:
                return token
    return None


def scan(sources: Iterable[tuple[str, str]], names: frozenset[str] = NAMES) -> list[str]:
    """Every line of ``sources`` that is internal, as ``where:line: match``."""
    out: list[str] = []
    for where, text in sources:
        reference = where == REFERENCE or where.endswith("/" + REFERENCE)
        for number, line in enumerate(text.splitlines(), 1):
            hit = match(line, names)
            if hit is None and reference:
                found = REFERENCE_WORDS.search(line)
                hit = found.group(0) if found else None
            if hit:
                out.append(f"{where}:{number}: {hit}")
    return out


def tree_findings(root: Path, names: frozenset[str] = NAMES) -> list[str]:
    """Every line under ``root`` that would publish something internal."""
    return scan(_tree(root), names)


#: What Maven Central requires a release's POM to state, as (path under ``<project>``, what it is).
POM_NEEDS = (
    ("name", "the project's name"),
    ("description", "a description"),
    ("url", "the project's URL"),
    ("licenses/license/name", "a licence"),
    ("developers/developer/name", "a developer"),
    ("scm/url", "the source repository"),
    ("scm/connection", "the source repository's connection"),
)
#: The licence and the public repository the POM must name: what Central shows, and where a reader
#: of the sources jar goes.
LICENCE = "Apache-2.0"
SCM_URL = "https://github.com/mfx-platform/mfx-sdk-java"
_POM = {"m": "http://maven.apache.org/POM/4.0.0"}
_EMBEDDED_POM = re.compile(r"^META-INF/maven/[^/]+/[^/]+/pom\.xml$")
#: The two jars Central requires beside each jar.
CLASSIFIERS = ("sources", "javadoc")


def _jar(path: Path) -> tuple[list[tuple[str, str]], str | None]:
    """The text entries of one jar, as scan sources, and the POM it carries, if any."""
    sources: list[tuple[str, str]] = []
    pom = None
    with zipfile.ZipFile(path) as jar:
        for name in jar.namelist():
            text = _text(jar.read(name))
            if text is not None:
                sources.append((f"{path.name}:{name}", text))
                if _EMBEDDED_POM.match(name):
                    pom = text
    return sources, pom


def _pom_lacks(where: str, pom: str) -> list[str]:
    """What ``pom`` does not state that Central requires, or a licence or repository it gets wrong."""
    project = ET.fromstring(pom)

    def value(path: str) -> str:
        node = project.find("/".join(f"m:{part}" for part in path.split("/")), _POM)
        return (node.text or "").strip() if node is not None else ""

    out = [f"{where}: the POM lacks {what}" for path, what in POM_NEEDS if not value(path)]
    if value("licenses/license/name") not in {"", LICENCE}:
        out.append(f"{where}: the POM's licence is not {LICENCE}")
    if value("scm/url") not in {"", SCM_URL}:
        out.append(f"{where}: the POM's source repository is not {SCM_URL}")
    return out


def package_findings(dist: Path, names: frozenset[str] = NAMES) -> list[str]:
    """Every line the jars in ``dist`` would publish that is internal, and everything Central would
    refuse them for."""
    jars = sorted(path for path in dist.iterdir() if path.suffix == ".jar")
    sources: list[tuple[str, str]] = []
    missing: list[str] = []
    for path in jars:
        found, pom = _jar(path)
        sources.extend(found)
        if path.stem.endswith(tuple(f"-{classifier}" for classifier in CLASSIFIERS)):
            continue
        for classifier in CLASSIFIERS:
            if not (dist / f"{path.stem}-{classifier}.jar").is_file():
                missing.append(f"{path.name}: no {path.stem}-{classifier}.jar beside it")
        if pom is None:
            missing.append(f"{path.name}: carries no POM")
        else:
            missing.extend(_pom_lacks(path.name, pom))
    return missing + scan(sources, names)


def _main_jars(dist: Path) -> list[Path]:
    return [
        path
        for path in dist.iterdir()
        if path.suffix == ".jar"
        and not path.stem.endswith(tuple(f"-{classifier}" for classifier in CLASSIFIERS))
    ]


def main(argv: list[str] | None = None) -> int:
    """Run the check; 0 when clean, 1 when something internal was found, 2 on bad usage."""
    args = sys.argv[1:] if argv is None else argv
    if args[:1] not in (["--tree"], ["--package"]) or len(args) > 2:
        sys.stderr.write("usage: check_public.py --tree [<directory>] | --package <directory>\n")
        return 2
    root = Path(args[1]) if len(args) > 1 else REPO
    if not root.is_dir():
        sys.stderr.write(f"no directory at {root}\n")
        return 2
    if args[0] == "--package":
        if not _main_jars(root):
            sys.stderr.write(f"no jar in {root}: run ./mvnw -P release package first\n")
            return 2
        found, what = package_findings(root), f"the jars in {root}"
    else:
        found, what = tree_findings(root), f"the tree at {root}"
    for line in found:
        sys.stdout.write(line + "\n")
    if found:
        sys.stderr.write(f"{len(found)} internal reference(s) or missing part(s) in {what}\n")
        return 1
    sys.stdout.write(f"nothing internal in {what}\n")
    return 0


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(main())
