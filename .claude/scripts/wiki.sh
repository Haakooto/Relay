#!/usr/bin/env bash
# Clones the GitHub wiki (a separate repo) into .wiki/ at the repo root, or fast-forwards it if it
# is already there, and prints its path. .wiki/ is git-ignored, so each dev device keeps its own.
#
#   .claude/scripts/wiki.sh          clone or update, print the path
#
# Edits to the wiki are committed and pushed from inside .wiki/, never from this repo.
set -euo pipefail

root="$(git rev-parse --show-toplevel)"
wiki="$root/.wiki"
remote="git@github.com:Haakooto/Relay.wiki.git"

if [ -d "$wiki/.git" ]; then
    if [ -n "$(git -C "$wiki" status --porcelain)" ]; then
        echo "wiki.sh: $wiki has uncommitted changes, not pulling" >&2
    else
        git -C "$wiki" pull --ff-only --quiet
    fi
else
    git clone --quiet "$remote" "$wiki"
fi
echo "$wiki"
