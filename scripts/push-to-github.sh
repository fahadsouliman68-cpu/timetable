#!/usr/bin/env bash
# Pushes this repository to a new GitHub repository.
# Usage: ./scripts/push-to-github.sh <owner>/<repo>
# Requires an authenticated GitHub CLI (gh auth login) or a configured git remote.
set -euo pipefail
if [[ $# -ne 1 ]]; then echo "Usage: $0 <owner>/<repo>"; exit 1; fi
REPO="$1"
cd "$(dirname "$0")/.."
if git remote get-url origin >/dev/null 2>&1; then
  git push -u origin HEAD:main
else
  gh repo create "$REPO" --private --source=. --remote=origin --push
fi
echo "Pushed. The domain-tests and android-build workflows start automatically."
