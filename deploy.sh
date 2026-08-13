#!/bin/bash
set -e
cd "$(dirname "$0")"

REPO=MeshiHQApp
OWNER=Sekiguchi-Takashi
MSG="${1:-update}"
TOKEN=$(git config --global github.token)

curl -s -o /dev/null -X POST \
  -H "Authorization: token $TOKEN" \
  -H "Accept: application/vnd.github+json" \
  https://api.github.com/user/repos \
  -d "{\"name\":\"$REPO\",\"private\":true}"

if [ ! -d .git ]; then
  git init -b main
fi

git config user.name "$OWNER"
git config user.email "$OWNER@users.noreply.github.com"

git remote remove origin 2>/dev/null || true
git remote add origin "https://$TOKEN@github.com/$OWNER/$REPO.git"

git add -A
git commit -m "$MSG" || true
git push -u origin main
