#!/usr/bin/env bash
# Phase 1: tools, depot_tools, Chromium Android checkout, build deps, hooks.
# Runs inside the Ubuntu-24.04 WSL distro as user "builder". Log: ~/logs/01-fetch.log
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
step() { echo; echo "=== [$(date -u +%FT%TZ)] $* ==="; }

step "apt packages"
sudo apt-get update -y
sudo apt-get install -y git python3 curl lsb-release file

step "depot_tools"
if [ ! -d ~/depot_tools ]; then
  git clone https://chromium.googlesource.com/chromium/tools/depot_tools.git ~/depot_tools
fi
grep -q 'depot_tools' ~/.bashrc || echo 'export PATH="$HOME/depot_tools:$PATH"' >> ~/.bashrc
export PATH="$HOME/depot_tools:$PATH"
git config --global core.autocrlf false
git config --global core.filemode false
git config --global core.preloadindex true
git config --global user.name >/dev/null || git config --global user.name "builder"
git config --global user.email >/dev/null || git config --global user.email "builder@localhost"

step "fetch android"
mkdir -p ~/chromium && cd ~/chromium
if [ ! -d src ]; then
  fetch --nohooks --no-history android
else
  echo "src exists; running gclient sync instead"
  gclient sync --nohooks --no-history
fi

step "install-build-deps --android"
cd ~/chromium/src
sudo -E build/install-build-deps.sh --android --no-prompt

step "gclient runhooks"
gclient runhooks

step "DONE"
du -sh ~/chromium
