#!/bin/sh
# Config Server's git backend needs its config-repo directory to be a real git working copy (it does
# `git show`/`git log` to resolve a label), not just a directory of files. The source of truth for those
# files is this repository's own config-repo/ - tracked normally there so every clone and every PR diff sees
# real content - so this script gives it real git history the first time this container ever sees it.
#
# Two different paths can be in play depending on how this container is run, so both are handled rather than
# assuming one: /app/config-repo is what the image bakes in via the Dockerfile COPY (Kubernetes, and the
# classpath default git.uri) - /config-repo is what docker-compose.yml bind-mounts instead, overriding the
# git URI to match. Either way: a restart finds .git already there and skips straight to exec, no manual
# host-side setup step.
set -eu

init_if_needed() {
  dir="$1"
  [ -d "$dir" ] || return 0
  [ -d "$dir/.git" ] && return 0
  git init -q -b main "$dir"
  git -C "$dir" config user.email "config-server@ecommerce.local"
  git -C "$dir" config user.name "config-server"
  git -C "$dir" add -A
  git -C "$dir" commit -q -m "Initial configuration"
}

init_if_needed /app/config-repo
init_if_needed /config-repo

exec "$@"
