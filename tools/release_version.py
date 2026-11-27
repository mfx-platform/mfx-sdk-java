"""The version a release tag names, checked against every place the version is written.

    python3 tools/release_version.py v1.2.3

prints ``1.2.3`` when the tag is ``v`` and the POM's version, that version is a release (Maven
Central refuses a ``-SNAPSHOT``), and the version the SDK states in its ``User-Agent`` is the same;
otherwise it says which differs and exits 1. The release workflows run it first, so a tag that does
not match what would be built publishes nothing.

    python3 tools/release_version.py --current

checks only that the ``User-Agent`` states the POM's version, ``-SNAPSHOT`` aside, as CI does on
every pull request.

The ``User-Agent`` names ``SdkVersion.VALUE``, a constant in the SDK's source.

Standard library only: it runs on a bare runner.
"""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
POM = Path("pom.xml")
AGENT = Path("src/main/java/com/mfxhub/sdk/SdkVersion.java")
SNAPSHOT = "-SNAPSHOT"

_NS = {"m": "http://maven.apache.org/POM/4.0.0"}
_VALUE = re.compile(r"\bVALUE\s*=\s*\"([^\"]+)\"")


def versions(repo: Path = REPO) -> tuple[str | None, str | None]:
    """The POM's version and the ``User-Agent``'s, each None when it cannot be found."""
    node = ET.parse(repo / POM).getroot().find("m:version", _NS)
    pom = ((node.text or "").strip() or None) if node is not None else None
    agent = _VALUE.search((repo / AGENT).read_text(encoding="utf-8"))
    return pom, agent.group(1) if agent else None


def problems(tag: str | None, repo: Path = REPO) -> list[str]:
    """What makes ``tag`` the wrong tag for what ``repo`` would build; empty when it is right.

    With no tag, only that the ``User-Agent`` states the POM's version, ``-SNAPSHOT`` aside.
    """
    pom, agent = versions(repo)
    if pom is None:
        return [f"no <version> in {POM.as_posix()}"]
    release = pom.removesuffix(SNAPSHOT)
    out = []
    if tag is not None:
        if tag != f"v{pom}":
            out.append(f"tag {tag} does not match {POM.as_posix()}'s version {pom}")
        if pom.endswith(SNAPSHOT):
            out.append(f"{pom} is a snapshot: Maven Central takes releases only")
    if agent != release:
        out.append(f"the User-Agent names {agent} ({AGENT.as_posix()}), the POM {release}")
    return out


def main(argv: list[str] | None = None, repo: Path = REPO) -> int:
    """Print the version; 0 when it is right everywhere, 1 when not, 2 on bad usage."""
    args = sys.argv[1:] if argv is None else argv
    if len(args) != 1:
        sys.stderr.write("usage: release_version.py <tag> | --current\n")
        return 2
    tag = None if args[0] == "--current" else args[0]
    found = problems(tag, repo)
    for problem in found:
        sys.stderr.write(f"::error::{problem}\n")
    if found:
        return 1
    pom, _ = versions(repo)
    sys.stdout.write(f"{pom}\n")
    return 0


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(main())
