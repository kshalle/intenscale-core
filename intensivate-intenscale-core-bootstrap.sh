#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2016-2026 Intensivate, Inc.
# SPDX-License-Identifier: LicenseRef-Intensivate-NC-1.0
#
# prepare_env.sh
#
# Single setup script shared by the "firesim" and "intenscore" (intenscale-core) repos.
# It detects which repo it is running in, checks host prerequisites,
# pulls the Docker image and (for FireSim) an OS disk image from
# Google Drive with gdown, loads the Docker image, and leaves the
# user with a ready-to-run container.
#
# Usage:
#   ./prepare_env.sh                       # interactive, checks + downloads
#   ./prepare_env.sh --check-only          # only run prerequisite checks
#   ./prepare_env.sh --yes                 # auto-install fixable apt/pip deps
#   ./prepare_env.sh --skip-download       # skip Drive pull (assets already local)
#   ./prepare_env.sh --force-download      # ignore any cached download, fetch fresh
#   ./prepare_env.sh --os-image=ubuntu     # FireSim only: buildroot|fedora|ubuntu (default: ubuntu)
#   ./prepare_env.sh --skip-uid-remap      # don't align the container's intens4 user to your host UID/GID
#   ./prepare_env.sh --skip-submodules     # don't run 'git submodule update --init --recursive'
#   ./prepare_env.sh --use-local-image[=NAME]  # skip Drive entirely for the docker image; use an
#                                               # already-loaded local image (default: the name
#                                               # configured in prepare_env.conf). For local
#                                               # Dockerfile development -- see the user guide.
#   ./prepare_env.sh --config=PATH         # use a config file other than ./prepare_env.conf
#   ./prepare_env.sh --no-login            # intenscale-core: print the docker run command instead of
#                                          # logging in (and building DRAMSim3) automatically
#   ./prepare_env.sh -h | --help
#
# Place this script at the root of EITHER repo. It works out which
# repo it's in and changes course accordingly (see detect_repo_type).
# All configurable values (Drive file IDs, image names, etc) live in
# prepare_env.conf next to this script -- edit that file, not this one.
#
# Assets are pulled straight from Google Drive with `gdown`, no
# Google login required -- the files are shared as "Anyone with the
# link". That's simpler for everyone but is a bearer-link trust model:
# anyone who has the ID can download, and there's no per-user
# revocation. Fine for open-source test assets; worth remembering if
# these folders' sharing settings are ever generalized to more
# sensitive content later.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR"
CONFIG_FILE="$SCRIPT_DIR/prepare_env.conf"

MISSING_DEPS=()
AUTO_FIX=false
CHECK_ONLY=false
SKIP_DOWNLOAD=false
FORCE_DOWNLOAD=false
SKIP_UID_REMAP=false
SKIP_SUBMODULES=false
USE_LOCAL_IMAGE=false
USE_LOCAL_IMAGE_NAME=""
NO_LOGIN=false
RUN_IMAGE=""
OS_IMAGE=""

# Pre-declared as associative BEFORE the config file is sourced. This
# matters: if prepare_env.conf's `FIRESIM_DISK_IMAGE_FILEIDS=([buildroot]=...)`
# assignment ran against a variable that was never declared -A, bash
# would silently treat `buildroot` as an arithmetic expression (== 0)
# instead of a string key, and every lookup would quietly break.
declare -A FIRESIM_DISK_IMAGE_FILEIDS=()
declare -A FIRESIM_DISK_IMAGE_SHA256S=()

RED="$(tput setaf 1 2>/dev/null || true)"
GREEN="$(tput setaf 2 2>/dev/null || true)"
YELLOW="$(tput setaf 3 2>/dev/null || true)"
BLUE="$(tput setaf 4 2>/dev/null || true)"
BOLD="$(tput bold 2>/dev/null || true)"
RESET="$(tput sgr0 2>/dev/null || true)"

log()   { echo -e "${BLUE}[*]${RESET} $*" >&2; }
ok()    { echo -e "${GREEN}[OK]${RESET} $*" >&2; }
warn()  { echo -e "${YELLOW}[WARN]${RESET} $*" >&2; }
err()   { echo -e "${RED}[FAIL]${RESET} $*" >&2; }
title() { echo -e "\n${BOLD}== $* ==${RESET}" >&2; }

for arg in "$@"; do
  case "$arg" in
    --yes|-y) AUTO_FIX=true ;;
    --check-only) CHECK_ONLY=true ;;
    --skip-download) SKIP_DOWNLOAD=true ;;
    --force-download) FORCE_DOWNLOAD=true ;;
    --skip-uid-remap) SKIP_UID_REMAP=true ;;
    --skip-submodules) SKIP_SUBMODULES=true ;;
    --use-local-image) USE_LOCAL_IMAGE=true ;;
    --use-local-image=*) USE_LOCAL_IMAGE=true; USE_LOCAL_IMAGE_NAME="${arg#*=}" ;;
    --os-image=*) OS_IMAGE="${arg#*=}" ;;
    --config=*) CONFIG_FILE="${arg#*=}" ;;
    --no-login) NO_LOGIN=true ;;
    -h|--help)
      sed -n '2,36p' "$0"
      exit 0
      ;;
    *) warn "Unknown argument: $arg" ;;
  esac
done

# -----------------------------------------------------------------------
# 0. Load and validate config
# -----------------------------------------------------------------------

title "Loading config"
if [[ ! -f "$CONFIG_FILE" ]]; then
  err "Config file not found: $CONFIG_FILE"
  echo "    This repo should ship with prepare_env.conf right next to this"
  echo "    script, already filled in. If it's missing, restore it from the"
  echo "    repo, or point at one elsewhere with --config=/path/to/file."
  exit 1
fi
if ! source "$CONFIG_FILE"; then
  err "Failed to load $CONFIG_FILE (bash syntax error inside it?)"
  exit 1
fi
ok "Loaded $CONFIG_FILE"

# -----------------------------------------------------------------------
# 1. Detect which repo we're running in
# -----------------------------------------------------------------------
detect_repo_type() {
  # Preferred: an explicit marker file each repo ships with.
  #   echo "REPO_TYPE=firesim" > .repo-manifest
  if [[ -f "$REPO_ROOT/.repo-manifest" ]]; then
    local val
    val="$(grep -E '^REPO_TYPE=' "$REPO_ROOT/.repo-manifest" | cut -d= -f2 | tr -d '[:space:]')"
    if [[ -n "$val" ]]; then
      echo "$val"
      return
    fi
  fi

  # Fallback heuristics based on known repo layout / git remote.
  if git -C "$REPO_ROOT" remote get-url origin 2>/dev/null | grep -qi "firesim"; then
    echo "firesim"; return
  fi
  if git -C "$REPO_ROOT" remote get-url origin 2>/dev/null | grep -qEi "intenscale-core|intenscore"; then
    echo "intenscore"; return
  fi
  if [[ -d "$REPO_ROOT/deploy" && -d "$REPO_ROOT/sim" ]]; then
    echo "firesim"; return
  fi
  if [[ -d "$REPO_ROOT/src/main/scala" && -d "$REPO_ROOT/emulator" ]]; then
    echo "intenscore"; return
  fi

  echo "unknown"
}

REPO_TYPE="$(detect_repo_type)"

title "Repo detection"
case "$REPO_TYPE" in
  firesim)     ok "Detected repo: FireSim (FPGA-based RTL testing, tested on U250)" ;;
  intenscore)  ok "Detected repo: intenscale-core (Verilator bare-metal testing)" ;;
  *)
    err "Could not determine repo type."
    echo "  Add a file named .repo-manifest at the repo root containing:"
    echo "    REPO_TYPE=firesim"
    echo "  or"
    echo "    REPO_TYPE=intenscore"
    exit 1
    ;;
esac

# -----------------------------------------------------------------------
# 1b. Validate the config values relevant to this repo
# -----------------------------------------------------------------------
validate_config() {
  local errors=()
  local v

  for v in CONTAINER_USER ASSET_DIR; do
    if [[ -z "${!v}" ]]; then errors+=("$v"); fi
  done

  if [[ "$REPO_TYPE" == "firesim" ]]; then
    [[ -z "$FIRESIM_DOCKER_IMAGE_FILEID" || "$FIRESIM_DOCKER_IMAGE_FILEID" == REPLACE_WITH_* ]] && errors+=("FIRESIM_DOCKER_IMAGE_FILEID")
    for v in FIRESIM_DOCKER_IMAGE_FILE FIRESIM_DOCKER_IMAGE_NAME FIRESIM_MIN_FREE_GB FIRESIM_DEFAULT_OS_IMAGE; do
      [[ -z "${!v}" ]] && errors+=("$v")
    done
    if [[ ${#FIRESIM_DISK_IMAGE_FILEIDS[@]} -eq 0 ]]; then
      errors+=("FIRESIM_DISK_IMAGE_FILEIDS (empty -- no OS disk images configured)")
    fi
  else
    [[ -z "$INTENSCORE_DOCKER_IMAGE_FILEID" || "$INTENSCORE_DOCKER_IMAGE_FILEID" == REPLACE_WITH_* ]] && errors+=("INTENSCORE_DOCKER_IMAGE_FILEID")
    for v in INTENSCORE_DOCKER_IMAGE_FILE INTENSCORE_DOCKER_IMAGE_NAME INTENSCORE_MIN_FREE_GB; do
      [[ -z "${!v}" ]] && errors+=("$v")
    done
  fi

  if [[ ${#errors[@]} -gt 0 ]]; then
    err "$CONFIG_FILE is missing values needed for the $REPO_TYPE repo:"
    local e
    for e in "${errors[@]}"; do echo "    - $e" >&2; done
    exit 1
  fi
  ok "Config has everything needed for $REPO_TYPE."
}

validate_config

# -----------------------------------------------------------------------
# 2. Prerequisite checks
# -----------------------------------------------------------------------

require_cmd() {
  local cmd="$1" fix_hint="$2" apt_pkg="$3"
  if command -v "$cmd" >/dev/null 2>&1; then
    ok "$cmd found"
    return 0
  fi
  err "$cmd not found"
  echo "    Fix: $fix_hint"
  MISSING_DEPS+=("$apt_pkg")
  return 1
}

check_os() {
  title "Operating system"
  if [[ -f /etc/os-release ]]; then
    . /etc/os-release
    if [[ "$ID" == "ubuntu" ]]; then
      ok "Ubuntu $VERSION_ID detected (supported)"
    else
      warn "Detected $NAME $VERSION_ID. This environment is only validated on Ubuntu."
      warn "It may still work, but you're on your own for host-OS issues."
    fi
  else
    warn "Could not read /etc/os-release; assuming a non-standard Linux distro."
  fi
}

check_commands() {
  title "Required tools"
  require_cmd git    "sudo apt-get install -y git" "git"
  require_cmd curl   "sudo apt-get install -y curl" "curl"
  require_cmd unzip  "sudo apt-get install -y unzip" "unzip"
  require_cmd tar    "sudo apt-get install -y tar" "tar"
  require_cmd python3 "sudo apt-get install -y python3 python3-pip" "python3"
  require_cmd pip3   "sudo apt-get install -y python3-pip" "python3-pip"

  # Docker
  if command -v docker >/dev/null 2>&1; then
    ok "docker found"
  else
    err "docker not found"
    echo "    Fix: install Docker Engine for Ubuntu:"
    echo "      curl -fsSL https://get.docker.com | sh"
    echo "    Then log out/in (or reboot) so group membership takes effect."
    MISSING_DEPS+=("docker")
  fi

  # gdown (unauthenticated Google Drive downloads by file ID)
  if command -v gdown >/dev/null 2>&1; then
    ok "gdown found"
  else
    err "gdown not found"
    echo "    Fix: pip install --user gdown   (or: sudo pip install gdown)"
    echo "    If 'gdown' isn't on your PATH afterward, either open a new shell"
    echo "    or add ~/.local/bin to PATH."
    MISSING_DEPS+=("gdown")
  fi
}

check_docker_daemon() {
  title "Docker daemon"
  if ! command -v docker >/dev/null 2>&1; then
    warn "Skipping (docker not installed yet)"
    return
  fi
  if docker info >/dev/null 2>&1; then
    ok "Docker daemon is reachable"
    return
  fi
  err "Docker daemon not reachable"
  if ! systemctl is-active --quiet docker 2>/dev/null; then
    echo "    Fix: sudo systemctl enable --now docker"
  fi
  if ! groups "$USER" | grep -q docker; then
    echo "    Fix: sudo usermod -aG docker \$USER   (then log out and back in)"
  fi
  MISSING_DEPS+=("docker-daemon")
}

check_disk_space() {
  title "Disk space"
  local min_gb
  if [[ "$REPO_TYPE" == "firesim" ]]; then min_gb=$FIRESIM_MIN_FREE_GB; else min_gb=$INTENSCORE_MIN_FREE_GB; fi
  local avail_kb avail_gb
  avail_kb=$(df -Pk "$REPO_ROOT" | awk 'NR==2 {print $4}')
  avail_gb=$(( avail_kb / 1024 / 1024 ))
  if (( avail_gb >= min_gb )); then
    ok "Free space: ${avail_gb}GB (need ~${min_gb}GB)"
  else
    err "Only ${avail_gb}GB free, need at least ${min_gb}GB for $REPO_TYPE assets"
    echo "    Fix: free up space, or set ASSET_DIR in prepare_env.conf to a path on a larger disk."
    MISSING_DEPS+=("disk-space")
  fi
}

check_internet() {
  title "Internet connectivity"
  if curl -fsSL --max-time 5 https://www.google.com -o /dev/null 2>/dev/null; then
    ok "Internet reachable"
  else
    err "No internet connectivity detected"
    echo "    Fix: check network/proxy settings."
    MISSING_DEPS+=("internet")
  fi
}

# FireSim only. This is a best-effort check, not a full hardware
# readiness gate -- it can't verify Vivado/Vitis licensing, XDMA
# driver version compatibility, or that the card is actually seated
# correctly, only that *something* Xilinx-shaped and *some* XDMA
# device nodes are visible. Treated as a warning, never blocking:
# you may legitimately be running this before the FPGA is installed
# (e.g. to prep the Docker/software side first), and JTAG-based
# bitstream loading/debugging is entirely outside this script's scope
# -- follow the internal Vivado/driver setup docs for the rest.
check_fpga_hardware() {
  [[ "$REPO_TYPE" == "firesim" ]] || return
  title "FPGA hardware (best-effort check, U250)"

  if command -v lspci >/dev/null 2>&1; then
    if lspci 2>/dev/null | grep -qiE "xilinx|alveo|10ee:"; then
      ok "Xilinx/Alveo PCIe device detected"
    else
      warn "No Xilinx/Alveo PCIe device found in 'lspci' output."
      echo "    This is only a heads-up, not a blocker -- fine if you're" >&2
      echo "    preparing the environment before the card is installed." >&2
    fi
  else
    warn "'lspci' not found -- can't check for the FPGA card (sudo apt-get install pciutils)."
  fi

  if compgen -G "/dev/xdma*" >/dev/null 2>&1; then
    ok "/dev/xdma* device nodes present"
  else
    warn "No /dev/xdma* device nodes found."
    echo "    The docker run command this script prints uses --device=/dev/xdma*," >&2
    echo "    which will fail to start without them. This usually means the XDMA" >&2
    echo "    kernel driver isn't loaded yet -- see the internal FPGA setup docs." >&2
  fi
}

auto_fix_apt() {
  $AUTO_FIX || return
  local pkgs=()
  for d in "${MISSING_DEPS[@]}"; do
    case "$d" in
      git|curl|unzip|tar|python3|python3-pip) pkgs+=("$d") ;;
    esac
  done
  if [[ ${#pkgs[@]} -gt 0 ]]; then
    title "Auto-fixing apt packages: ${pkgs[*]}"
    sudo apt-get update && sudo apt-get install -y "${pkgs[@]}"
  fi
  if printf '%s\n' "${MISSING_DEPS[@]}" | grep -q '^docker$'; then
    title "Auto-installing Docker"
    curl -fsSL https://get.docker.com | sh
    sudo usermod -aG docker "$USER"
    warn "You must log out/in for the docker group change to take effect."
  fi
  if printf '%s\n' "${MISSING_DEPS[@]}" | grep -q '^gdown$'; then
    title "Auto-installing gdown"
    pip3 install --user gdown
  fi
}

run_checks() {
  check_os
  check_commands
  if $AUTO_FIX; then
    auto_fix_apt
    # re-check only after an actual auto-fix attempt, since that's the
    # only case where the first check_commands result could be stale.
    MISSING_DEPS=()
    check_commands
  fi
  check_docker_daemon
  check_disk_space
  check_internet
  check_fpga_hardware
}

run_checks

title "Prerequisite summary"
if [[ ${#MISSING_DEPS[@]} -eq 0 ]]; then
  ok "All checks passed."
else
  err "The following need to be fixed before continuing: ${MISSING_DEPS[*]}"
  echo "Re-run this script (optionally with --yes to auto-fix apt packages/Docker/gdown) after addressing the items above."
  exit 1
fi

if $CHECK_ONLY; then
  ok "Check-only mode: stopping here."
  exit 0
fi

# -----------------------------------------------------------------------
# 2b. Initialize git submodules
# -----------------------------------------------------------------------
# Runs on the host, before the repo is mounted into a container, so the
# environment is fully ready to go without needing this run again
# inside every fresh container.
init_submodules() {
  title "Git submodules"
  if [[ ! -f "$REPO_ROOT/.gitmodules" ]]; then
    log "No .gitmodules found in $REPO_ROOT -- nothing to initialize."
    return
  fi
  log "Running: git submodule update --init --recursive"
  if ! git -C "$REPO_ROOT" submodule update --init --recursive; then
    err "git submodule update failed -- see output above."
    echo "    Fix: check network access and repo permissions, then re-run."
    echo "    this script. To proceed without submodules for now, use"
    echo "    --skip-submodules (the environment likely won't fully work"
    echo "    until they're initialized, though)."
    exit 1
  fi
  ok "Submodules initialized."
}

if $SKIP_SUBMODULES; then
  warn "Skipping git submodules (--skip-submodules). The environment may not"
  warn "fully work until 'git submodule update --init --recursive' is run."
else
  init_submodules
fi

# -----------------------------------------------------------------------
# 2c. Download the sbt launcher (not redistributed in this repository)
# -----------------------------------------------------------------------
# sbt-launch.jar is sbt's BSD-3-Clause launcher (it bundles Apache Ivy).
# Makefrag runs it from the repo root. It only bootstraps the sbt version
# pinned in project/build.properties, so any 1.x launcher works; we pin
# 1.3.4 (matching project/build.properties) and verify its checksum.
SBT_LAUNCHER_URL="${SBT_LAUNCHER_URL:-https://repo1.maven.org/maven2/org/scala-sbt/sbt-launch/1.3.4/sbt-launch-1.3.4.jar}"
SBT_LAUNCHER_SHA256="ca7d842462f70d9e919b7e1fbc50660929b666f09c561401da3b6eedc56a67d3"

download_sbt_launcher() {
  title "sbt launcher"
  local dest="$REPO_ROOT/sbt-launch.jar" actual
  if [[ -f "$dest" ]]; then
    actual="$(sha256sum "$dest" | awk '{print $1}')"
    if [[ "$actual" == "$SBT_LAUNCHER_SHA256" ]]; then
      ok "sbt-launch.jar already present and verified."
      return
    fi
    warn "sbt-launch.jar present but checksum differs -- leaving it alone."
    return
  fi
  log "Downloading $SBT_LAUNCHER_URL"
  if ! curl -fsSL --retry 3 -o "$dest.part" "$SBT_LAUNCHER_URL"; then
    rm -f "$dest.part"
    err "Could not download the sbt launcher."
    echo "    Fix: check network access, or download sbt-launch.jar yourself"
    echo "    into $REPO_ROOT and re-run."
    exit 1
  fi
  actual="$(sha256sum "$dest.part" | awk '{print $1}')"
  if [[ "$actual" != "$SBT_LAUNCHER_SHA256" ]]; then
    rm -f "$dest.part"
    err "sbt-launch.jar checksum mismatch (got $actual)."
    exit 1
  fi
  mv "$dest.part" "$dest"
  ok "sbt-launch.jar downloaded and verified."
}
download_sbt_launcher

# -----------------------------------------------------------------------
# 3. Pull assets from Google Drive (gdown, no auth -- files are shared
#    "Anyone with the link")
# -----------------------------------------------------------------------

# Resolves --os-image (short name) to one of the keys in
# FIRESIM_DISK_IMAGE_FILEIDS, defaulting if unset.
resolve_os_image_choice() {
  local choice="${OS_IMAGE:-$FIRESIM_DEFAULT_OS_IMAGE}"
  if [[ -n "${FIRESIM_DISK_IMAGE_FILEIDS[$choice]+set}" ]]; then
    echo "$choice"
    return
  fi
  err "Unknown --os-image value: '$choice'"
  echo "    Available: ${!FIRESIM_DISK_IMAGE_FILEIDS[*]}" >&2
  exit 1
}

# Compares a file's sha256 against an expected value. Returns 1 (and
# prints nothing itself -- callers decide how loud to be) if they
# don't match or the file can't be hashed.
verify_checksum() {
  local file="$1" expected="$2"
  local actual
  actual="$(sha256sum "$file" 2>/dev/null | awk '{print $1}')"
  [[ -n "$actual" && "${actual,,}" == "${expected,,}" ]]
}

# Fetches a single file from Drive by ID to $ASSET_DIR, skipping if
# already present, with friendly diagnosis of the common failure
# modes (quota throttling, permission/sharing misconfiguration, and
# corrupted transfers are the ones you'll actually hit with large
# unauthenticated downloads). If expected_sha256 is set (and not the
# placeholder), the file is verified after download -- and a cached
# file is re-verified on every run too, so a corrupted download from
# an earlier run doesn't sit there silently trusted forever.
fetch_drive_file() {
  local fileid="$1" filename="$2" label="$3" expected_sha256="${4:-}"
  local dest="$REPO_ROOT/$ASSET_DIR/$filename"
  local have_checksum=false
  [[ -n "$expected_sha256" && "$expected_sha256" != REPLACE_WITH_* ]] && have_checksum=true
  mkdir -p "$REPO_ROOT/$ASSET_DIR"

  if $FORCE_DOWNLOAD && [[ -f "$dest" ]]; then
    log "--force-download set: removing cached $label ($filename) to fetch fresh."
    rm -f "$dest"
  fi

  if [[ -f "$dest" ]]; then
    if $have_checksum; then
      if verify_checksum "$dest" "$expected_sha256"; then
        log "$label already present and checksum-verified ($filename), skipping re-download."
        return
      fi
      warn "$label is present but failed checksum verification -- re-downloading."
      rm -f "$dest"
    else
      log "$label already present ($filename), skipping re-download."
      return
    fi
  fi

  if [[ -z "$fileid" || "$fileid" == REPLACE_WITH_* ]]; then
    err "No Drive file ID configured for $label."
    echo "    Fix: fill in prepare_env.conf with the real file ID from that"
    echo "    file's share link."
    exit 1
  fi
  log "Fetching $label: $filename"
  local gdown_log
  gdown_log="$(mktemp)"
  if gdown "$fileid" -O "$dest" 2>&1 | tee "$gdown_log" >&2; then
    if [[ ! -s "$dest" ]]; then
      err "$label download reported success but the file is empty/missing."
      rm -f "$gdown_log"
      exit 1
    fi
    if $have_checksum; then
      if ! verify_checksum "$dest" "$expected_sha256"; then
        err "$label downloaded but failed checksum verification -- the file"
        echo "    is corrupted (this can happen silently with large transfers"
        echo "    over Drive's unauthenticated download path)."
        echo "    Fix: the corrupted file has been removed; just re-run this"
        echo "    script to try the download again."
        rm -f "$dest" "$gdown_log"
        exit 1
      fi
      ok "$label downloaded and checksum-verified."
    else
      ok "$label downloaded."
    fi
  else
    if grep -qiE "Too many users have viewed or downloaded this file|exceeded.*quota" "$gdown_log"; then
      err "Google Drive's download quota for this file was hit (a rolling"
      echo "    24-hour limit on unauthenticated 'anyone with the link' files,"
      echo "    unrelated to your machine or account)."
      echo "    Fix: wait a while and re-run, or ask us to check the file's quota."
    elif grep -qiE "Permission denied|Access denied|cannot retrieve|400|403" "$gdown_log"; then
      err "Google Drive denied access to file ID $fileid ($filename)."
      echo "    Fix: confirm this specific file's sharing is set to 'Anyone with"
      echo "    the link' (not just its parent folder), and that the file ID in"
      echo "    prepare_env.conf is correct."
    else
      err "Download of $filename failed. See details above."
    fi
    rm -f "$gdown_log"
    exit 1
  fi
  rm -f "$gdown_log"
}

download_assets() {
  title "Downloading assets from Google Drive"

  if $USE_LOCAL_IMAGE; then
    log "--use-local-image set: skipping the Docker image download (using an"
    log "already-loaded local image instead -- see the 'Loading Docker image' step)."
  else
    local docker_fileid docker_file docker_sha256
    if [[ "$REPO_TYPE" == "firesim" ]]; then
      docker_fileid="$FIRESIM_DOCKER_IMAGE_FILEID"
      docker_file="$FIRESIM_DOCKER_IMAGE_FILE"
      docker_sha256="${FIRESIM_DOCKER_IMAGE_SHA256:-}"
    else
      docker_fileid="$INTENSCORE_DOCKER_IMAGE_FILEID"
      docker_file="$INTENSCORE_DOCKER_IMAGE_FILE"
      docker_sha256="${INTENSCORE_DOCKER_IMAGE_SHA256:-}"
    fi
    fetch_drive_file "$docker_fileid" "$docker_file" "Docker image" "$docker_sha256"
  fi

  if [[ "$REPO_TYPE" == "firesim" ]]; then
    local os_choice disk_fileid disk_file disk_sha256
    os_choice="$(resolve_os_image_choice)"
    if [[ -z "$os_choice" ]]; then
      # resolve_os_image_choice already printed the error -- exit inside
      # it only kills its own subshell, so bail out explicitly here.
      exit 1
    fi
    disk_fileid="${FIRESIM_DISK_IMAGE_FILEIDS[$os_choice]}"
    disk_file="${os_choice}-disk.img.gz"
    disk_sha256="${FIRESIM_DISK_IMAGE_SHA256S[$os_choice]:-}"
    fetch_drive_file "$disk_fileid" "$disk_file" "OS disk image ($os_choice)" "$disk_sha256"
  fi

  ok "All required assets are in $ASSET_DIR"
}

if $SKIP_DOWNLOAD; then
  warn "Skipping download (--skip-download). Assuming assets already exist in $ASSET_DIR."
else
  download_assets
fi

# -----------------------------------------------------------------------
# 4. Load Docker image
# -----------------------------------------------------------------------

# Set by load_docker_image() to whatever name:tag docker load actually
# reports -- this is the source of truth for every later docker
# command (remap, the printed `docker run`), NOT the configured
# *_DOCKER_IMAGE_NAME in prepare_env.conf. docker load can't rename an
# image; it only ever loads whatever repo:tag was baked in at `docker
# save` time. If the conf value has drifted out of sync with that (a
# rename, a version bump, a copy-paste typo), trusting the conf value
# instead would silently point every downstream docker command at an
# image that doesn't exist locally.
LOADED_IMAGE_NAME=""

load_docker_image() {
  title "Loading Docker image"
  local filename
  if [[ "$REPO_TYPE" == "firesim" ]]; then
    filename="$FIRESIM_DOCKER_IMAGE_FILE"
  else
    filename="$INTENSCORE_DOCKER_IMAGE_FILE"
  fi
  local imgfile="$REPO_ROOT/$ASSET_DIR/$filename"
  if [[ ! -f "$imgfile" ]]; then
    err "Expected Docker image not found: $imgfile"
    exit 1
  fi
  log "Loading $imgfile ..."
  # docker load auto-detects and decompresses gzip input, no need to
  # gunzip first.
  local load_log load_status
  load_log="$(mktemp)"
  docker load -i "$imgfile" 2>&1 | tee "$load_log"
  load_status=${PIPESTATUS[0]}

  # docker load can exit 0 even when a layer's content doesn't match
  # its declared hash ("invalid diffID") -- that's Docker being lenient
  # about a corrupted image, not confirmation it's fine. Treat it the
  # same as a hard failure: this is exactly the class of problem
  # checksum verification (above) is meant to catch earlier, but check
  # here too in case no checksum was configured for this file.
  if [[ $load_status -ne 0 ]] || grep -qi "invalid diffID" "$load_log"; then
    err "Docker image did not load cleanly -- this means the downloaded"
    echo "    file is corrupted, even though the download itself reported"
    echo "    success (this class of corruption isn't always caught by a"
    echo "    plain byte-count check)."
    echo "    Fix: delete the file and re-run so it downloads fresh:"
    echo "      rm \"$imgfile\""
    echo "      ./prepare_env.sh"
    rm -f "$load_log"
    exit 1
  fi

  local configured_name
  if [[ "$REPO_TYPE" == "firesim" ]]; then
    configured_name="$FIRESIM_DOCKER_IMAGE_NAME"
  else
    configured_name="$INTENSCORE_DOCKER_IMAGE_NAME"
  fi

  LOADED_IMAGE_NAME="$(grep -oE 'Loaded image: .*' "$load_log" | tail -1 | sed 's/^Loaded image: //')"
  rm -f "$load_log"

  if [[ -z "$LOADED_IMAGE_NAME" ]]; then
    warn "Couldn't parse an image name/tag out of docker load's output"
    warn "(the tar may have been saved untagged, as an image ID only)."
    warn "Falling back to the configured name: $configured_name -- if"
    warn "that doesn't exist locally, the next steps will fail."
    LOADED_IMAGE_NAME="$configured_name"
  elif [[ "$LOADED_IMAGE_NAME" != "$configured_name" ]]; then
    # The image carries whatever name it was saved under; give it the
    # configured name so every later step (and the printed docker run
    # command) uses that.
    if docker tag "$LOADED_IMAGE_NAME" "$configured_name"; then
      log "Tagged '$LOADED_IMAGE_NAME' as '$configured_name'."
      LOADED_IMAGE_NAME="$configured_name"
    else
      warn "Couldn't tag '$LOADED_IMAGE_NAME' as '$configured_name';"
      warn "using '$LOADED_IMAGE_NAME' for this run."
    fi
  fi

  ok "Docker image loaded: $LOADED_IMAGE_NAME"
}

if $USE_LOCAL_IMAGE; then
  title "Using local Docker image"
  target_name="$USE_LOCAL_IMAGE_NAME"
  if [[ -z "$target_name" ]]; then
    if [[ "$REPO_TYPE" == "firesim" ]]; then
      target_name="$FIRESIM_DOCKER_IMAGE_NAME"
    else
      target_name="$INTENSCORE_DOCKER_IMAGE_NAME"
    fi
  fi
  if ! docker image inspect "$target_name" >/dev/null 2>&1; then
    err "--use-local-image: no local image found named '$target_name'."
    echo "    Fix: build/tag it first (docker build -t $target_name ...), or"
    echo "    pass the exact tag with --use-local-image=<name:tag>."
    exit 1
  fi
  LOADED_IMAGE_NAME="$target_name"
  ok "Using local image: $LOADED_IMAGE_NAME (skipped Drive download and docker load,"
  echo "    so this won't touch or retag anything downloaded from Drive)." >&2
else
  load_docker_image
fi


# -----------------------------------------------------------------------
# 4b. Align the image's default user (intens4) with the host account
# -----------------------------------------------------------------------
#
# The repo you mount at /work is owned by whoever runs this
# script (a host UID/GID). CONTAINER_USER inside the image owns
# everything else (toolchains, build dirs, etc) under a DIFFERENT,
# image-baked UID/GID. Without reconciling the two, files created
# inside the container under /work end up owned by an ID the
# host user can't touch, and vice versa for anything the host user
# copies into image-owned directories.
#
# Fix: build a thin derivative image (via `docker build`, FROM the
# already-loaded base image) that renumbers CONTAINER_USER to match
# the host and rescopes what it owns, then reuse that tag on every
# later run. Two reasons this goes through `docker build` rather than
# `docker create` + manual commands + `docker commit`:
#   1. A Dockerfile's RUN steps never invoke the base image's own
#      ENTRYPOINT -- ENTRYPOINT only fires when a *container* actually
#      starts via `docker run`/`docker create`. Some images (this one
#      included) run a banner/diagnostic script on every start
#      regardless of the command given, which previously got its
#      output spliced into ours and corrupted the remap entirely.
#      Building sidesteps that completely rather than working around
#      it with `docker run --entrypoint ...` overrides everywhere.
#   2. It mirrors the org's own approach (Dockerfile.eval takes
#      ARG UID/GID and does groupadd/useradd at build time) instead of
#      reinventing a different mechanism at runtime.
#
# Only CONTAINER_USER_HOME (default: /home/$CONTAINER_USER, override
# in prepare_env.conf if an image puts it elsewhere) gets its
# ownership rescoped -- not the whole image -- both because that's
# normally the only thing the account exclusively owns (build-time
# COPY steps are typically root:root) and because scoping it keeps
# this fast and avoids inflating disk usage across the whole image via
# overlayfs's copy-on-ownership-change behavior.
remap_container_user() {
  local base_image="$1"
  local host_uid host_gid remapped_tag
  local user_home="${CONTAINER_USER_HOME:-/home/$CONTAINER_USER}"

  host_uid="$(id -u)"
  host_gid="$(id -g)"
  remapped_tag="${base_image}-remapped-${host_uid}-${host_gid}"

  title "Aligning container user '$CONTAINER_USER' with your host account ($host_uid:$host_gid)"

  if docker image inspect "$remapped_tag" >/dev/null 2>&1; then
    ok "Already prepared for this account: $remapped_tag"
    echo "$remapped_tag"
    return
  fi

  # --entrypoint overrides the image's own ENTRYPOINT so this stays a
  # single clean line of output instead of a banner/diagnostic script
  # running first and getting captured along with it.
  local old_uid old_gid
  old_uid="$(docker run --rm --entrypoint id "$base_image" -u "$CONTAINER_USER" 2>/dev/null || true)"
  old_gid="$(docker run --rm --entrypoint id "$base_image" -g "$CONTAINER_USER" 2>/dev/null || true)"

  if [[ -z "$old_uid" || -z "$old_gid" ]]; then
    warn "User '$CONTAINER_USER' not found in $base_image -- skipping remap."
    echo "$base_image"
    return
  fi

  if [[ "$old_uid" == "$host_uid" && "$old_gid" == "$host_gid" ]]; then
    ok "'$CONTAINER_USER' already matches your host UID/GID -- nothing to do."
    echo "$base_image"
    return
  fi

  log "Rebuilding a thin layer on $base_image to move $CONTAINER_USER from"
  log "${old_uid}:${old_gid} to ${host_uid}:${host_gid} (cached as $remapped_tag)."

  local build_dir
  build_dir="$(mktemp -d)"
  cat > "$build_dir/Dockerfile" <<DOCKERFILE
FROM ${base_image}
USER root
RUN groupmod -g ${host_gid} ${CONTAINER_USER} \\
 && usermod -u ${host_uid} -g ${host_gid} ${CONTAINER_USER} \\
 && find ${user_home} -xdev -exec chown -h ${host_uid}:${host_gid} {} + 2>/dev/null || true
USER ${CONTAINER_USER}
DOCKERFILE

  if ! docker build -t "$remapped_tag" -f "$build_dir/Dockerfile" "$build_dir" >&2; then
    err "Remap build failed -- see output above. Falling back to the original image"
    echo "    (you'll likely hit ownership issues under /work)." >&2
    rm -rf "$build_dir"
    echo "$base_image"
    return
  fi
  rm -rf "$build_dir"

  ok "Remapped image ready: $remapped_tag"
  echo "$remapped_tag"
}

# -----------------------------------------------------------------------
# 5. Repo-specific finishing steps
# -----------------------------------------------------------------------

setup_firesim() {
  title "FireSim-specific setup"
  local disk_file
  disk_file="$REPO_ROOT/$ASSET_DIR/$(resolve_os_image_choice)-disk.img.gz"
  if [[ -f "$disk_file" ]]; then
    ok "OS disk image ready: $disk_file"
    # TODO: adjust destination path to match your firesim workload layout,
    # e.g. deploy/workloads/<workload-name>/
    warn "Place/symlink this disk image where your firesim workload config expects it."
  else
    warn "Expected OS disk image not found at $disk_file -- check the Drive file ID or --os-image value."
  fi

  local run_image="$LOADED_IMAGE_NAME"
  if ! $SKIP_UID_REMAP; then
    run_image="$(remap_container_user "$LOADED_IMAGE_NAME")"
  fi

  cat <<EOF

Next steps for FireSim:
  0. Booting a different OS? Re-run with --os-image=buildroot or --os-image=fedora
     (default is ubuntu). Each is downloaded on demand, so switching pulls just
     the one new image.
  1. Confirm your Vivado/FPGA driver setup for the U250 card (outside this script's scope).
  2. Start the container with the repo mounted:
       docker run -it --rm \\
         -v "$REPO_ROOT":/work \\
         --device=/dev/xdma* \\
         $run_image bash
  3. Inside the container: cd /work && source sourceme-f1-manager.sh (or your equivalent).
  4. See the repo README for build/deploy commands.

A commercial license is available if you want to fabricate a chip based on this RTL --
contact us for details.
EOF
}

setup_intenscore() {
  title "intenscale-core setup"

  local run_image="$LOADED_IMAGE_NAME"
  if ! $SKIP_UID_REMAP; then
    run_image="$(remap_container_user "$LOADED_IMAGE_NAME")"
  fi

  RUN_IMAGE="$run_image"

  cat <<EOF

To log in to the container (again) later, run:
       docker run -it --rm \\
         -v "$REPO_ROOT":/work \\
         $run_image bash
EOF
}

# intenscale-core: log the user in to the container, building DRAMSim3 the first
# time (its library is not shipped in the repository), then leave them at a
# clean shell in /work. The build goes to DRAMSIM3/build/, which DRAMSIM3's
# .gitignore already excludes.
login_intenscore() {
  local inner='
cd /work || exit 1
if [ ! -f DRAMSIM3/build/libdramsim3.a ]; then
  echo "Building DRAMSim3 (first time only, about a minute)..."
  mkdir -p DRAMSIM3/build
  if ( cd DRAMSIM3/build && cmake -DCOSIM=1 .. && make -j8 ) > DRAMSIM3/build/bootstrap-build.log 2>&1 \
     && [ -f DRAMSIM3/build/libdramsim3.a ]; then
    clear
  else
    echo
    echo "DRAMSim3 build FAILED -- last lines of DRAMSIM3/build/bootstrap-build.log:"
    tail -20 DRAMSIM3/build/bootstrap-build.log
    echo
    echo "Fix the problem above, then run: cd /work/DRAMSIM3/build && cmake -DCOSIM=1 .. && make"
  fi
else
  clear
fi
echo "intenscale-core environment ready. See README.md, step 2, to build and run the simulator."
exec bash'
  exec docker run -it --rm -v "$REPO_ROOT":/work "$RUN_IMAGE" bash -c "$inner"
}

if [[ "$REPO_TYPE" == "firesim" ]]; then
  setup_firesim
else
  setup_intenscore
fi

title "Done"
ok "$REPO_TYPE environment is ready."

if [[ "$REPO_TYPE" != "firesim" ]]; then
  if $NO_LOGIN; then
    log "--no-login: not logging in. Inside the container, build DRAMSim3 once with:"
    log "  cd /work/DRAMSIM3 && mkdir -p build && cd build && cmake -DCOSIM=1 .. && make"
  elif [[ ! -t 0 || ! -t 1 ]]; then
    warn "Not running in an interactive terminal, so not logging in to the container."
    log "Run the docker run command above from a terminal; inside it, build DRAMSim3 once with:"
    log "  cd /work/DRAMSIM3 && mkdir -p build && cd build && cmake -DCOSIM=1 .. && make"
  else
    log "Logging you in to the container..."
    login_intenscore
  fi
fi
