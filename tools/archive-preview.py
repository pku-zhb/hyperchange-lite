#!/usr/bin/env python3
"""Archive a verified, installable APK without replacing an existing version."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
from datetime import datetime, timezone


PROJECT = Path(__file__).resolve().parent.parent
DEFAULT_APK = Path("/Volumes/Harbor/DevCache/hyperchange-lite/build/app/outputs/apk/debug/app-debug.apk")
DEFAULT_ARCHIVE = Path.home() / "Harbor Drive/02 Personal Files/Miduo"


def digest(path):
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def ensure_destination(path, expected):
    if path.exists() and (not path.is_file() or digest(path) != expected):
        raise ValueError(f"Different content already exists: {path}. Use a new app version.")


def copy_once(source, destination, expected):
    if destination.exists():
        ensure_destination(destination, expected)
        return
    # Exclusive creation preserves every existing trial version, including concurrent writes.
    try:
        with destination.open("xb") as target, source.open("rb") as original:
            shutil.copyfileobj(original, target)
            target.flush()
            os.fsync(target.fileno())
        if digest(destination) != expected:
            raise ValueError(f"Archive verification failed: {destination}")
    except FileExistsError:
        ensure_destination(destination, expected)
    except Exception:
        destination.unlink(missing_ok=True)
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, default=DEFAULT_APK)
    parser.add_argument("--archive-dir", type=Path, default=DEFAULT_ARCHIVE)
    args = parser.parse_args()
    source = args.apk.expanduser().resolve(strict=True)
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=PROJECT, text=True).strip()
    if subprocess.check_output(["git", "status", "--porcelain"], cwd=PROJECT, text=True).strip():
        raise ValueError("Commit and push the source before archiving a delivery.")
    if subprocess.run(["git", "merge-base", "--is-ancestor", commit, "refs/remotes/origin/main"],
                      cwd=PROJECT, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode:
        raise ValueError("The delivery source is not recorded on origin/main. Push and verify it first.")
    archive = args.archive_dir.expanduser()
    if not archive.is_dir():
        raise ValueError(f"Existing personal archive is unavailable: {archive}")

    sdk = Path(os.environ.get("ANDROID_HOME", "/Volumes/Harbor/DevCache/hypermusiccover-enhanced/android-sdk"))
    build_tools = sdk / "build-tools/37.0.0"
    env = os.environ.copy()
    if not env.get("JAVA_HOME"):
        candidates = sorted((Path.home() / ".local/share/rea-cli").glob("jdk-*/Contents/Home"))
        if not candidates:
            raise ValueError("Set JAVA_HOME to a JDK before verifying the APK.")
        env["JAVA_HOME"] = str(candidates[-1])
    env["PATH"] = str(Path(env["JAVA_HOME"]) / "bin") + os.pathsep + env.get("PATH", "")
    verification = subprocess.check_output(
        [str(build_tools / "apksigner"), "verify", "--print-certs", str(source)], text=True, env=env
    )
    certificates = {value.lower() for value in re.findall(
        r"certificate SHA-256 digest: ([0-9a-fA-F]{64})", verification)}
    if len(certificates) != 1:
        raise ValueError("Expected one verified signing identity for the APK.")
    certificate = certificates.pop()
    badging = subprocess.check_output([str(build_tools / "aapt2"), "dump", "badging", str(source)], text=True)
    package_line = next(line for line in badging.splitlines() if line.startswith("package: "))
    package = dict(re.findall(r"(\w+)='([^']*)'", package_line))
    if package.get("name") != "com.zhuhuibin.hyperchange.lite":
        raise ValueError("This APK is not HyperChange Lite.")
    version = package["versionName"]
    code = int(package["versionCode"])
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,70}", version) or code < 1:
        raise ValueError("Invalid archive version.")
    manifest = subprocess.check_output(
        [str(build_tools / "aapt2"), "dump", "xmltree", str(source), "--file", "AndroidManifest.xml"], text=True)
    metadata = re.search(
        r'com\.zhuhuibin\.hyperchange\.lite\.SOURCE_REVISION[^\n]*\n[^\n]*android:value[^\n]*"([0-9a-f]{40})"',
        manifest)
    if not metadata or metadata.group(1) != commit:
        raise ValueError("APK embedded source revision differs from the committed delivery source.")

    checksum = digest(source)
    filename = f"hyperchange-lite-{version}-{commit[:12]}.apk"
    local = PROJECT / "releases/local"
    local.mkdir(parents=True, exist_ok=True)
    retained, delivered = local / filename, archive / filename
    receipt = retained.with_suffix(".json")
    already_archived = False
    if receipt.exists():
        record = json.loads(receipt.read_text())
        identity = dict(package=package["name"], version=version, version_code=code,
                        certificate_sha256=certificate, apk_sha256=checksum)
        if any(record.get(key) != value for key, value in identity.items()):
            raise ValueError("An existing version receipt belongs to a different APK.")
        already_archived = True
    for previous in local.glob("hyperchange-lite-*.json"):
        record = json.loads(previous.read_text())
        if record["package"] != package["name"]:
            raise ValueError("The local archive contains a receipt for another package.")
        if record["certificate_sha256"] != certificate:
            raise ValueError("Signing identity changed. Verify an explicit migration before delivering.")
        if not already_archived and code <= record["version_code"]:
            raise ValueError("Each new APK requires a larger versionCode and a new versionName.")
    for destination in (retained, delivered):
        ensure_destination(destination, checksum)
    copy_once(source, retained, checksum)
    # Import the retained, versioned file under its original filename; no vault rename.
    copy_once(retained, delivered, checksum)
    if not receipt.exists():
        record = dict(version=version, version_code=code, package=package["name"],
                      apk_sha256=checksum, certificate_sha256=certificate,
                      debuggable="application-debuggable" in badging,
                      archived_from_commit=commit, release_name=commit, source_dirty=False,
                      archived_at=datetime.now(timezone.utc).isoformat())
        with receipt.open("x") as target:
            json.dump(record, target, indent=2)
            target.write("\n")
    print(f"Retained: {retained}")
    print(f"Archived and hash-verified: {delivered}")
    print(f"Version {version} ({code}); SHA-256 {checksum}")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        sys.exit(str(error))
