#!/usr/bin/env bash
#
# Publishes textgame-client to the course Maven repo, which is what students'
# poms resolve against.
#
#   deploy/publish-maven.sh            # publish to prodesk
#   deploy/publish-maven.sh --force    # overwrite a version that is already there
#
# Three artifacts go up: textgame-client, textgame-protocol (it needs it, and
# Maven fetches it transitively) and the parent pom (Maven reads it to resolve
# the other two).
#
# A published version is immutable on purpose. Students pin a version in their
# pom; if the same number ever came back with different bytes, a project that
# built in September would fail in November for no visible reason. Bump the
# version instead — that is what --force exists to make you think about.

set -euo pipefail

HOST="${TEXTGAME_MAVEN_HOST:-prodesk-ubuntu}"
REMOTE_DIR="/var/www/maven"
FORCE=no
[ "${1:-}" = "--force" ] && FORCE=yes

cd "$(dirname "$0")/.."
VERSION=$(mvn -q -o -Dexec.executable=echo -Dexec.args='${project.version}' \
              --non-recursive exec:exec 2>/dev/null | tail -1 || true)
if [ -z "$VERSION" ]; then
    VERSION=$(grep -m1 '<version>' pom.xml | sed 's/.*<version>\(.*\)<\/version>.*/\1/')
fi
echo "==> publishing version $VERSION to $HOST:$REMOTE_DIR"

if [ "$FORCE" = no ] && ssh "$HOST" "test -d $REMOTE_DIR/textgame/textgame-client/$VERSION"; then
    echo
    echo "Version $VERSION is already published, and published versions do not change."
    echo "Bump the version in pom.xml, or re-run with --force if you are certain."
    exit 1
fi

echo "==> full build with tests (a red build must not be published)"
mvn -q clean install

STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT

echo "==> staging the artifacts"
mvn -q deploy -pl .,textgame-protocol,textgame-client -DskipTests \
    -DaltDeploymentRepository="course::default::file://$STAGE"

echo "==> uploading"
# --chmod, not -a: the staging directory is a mktemp 700, and plain "rsync -a"
# copies that mode onto the web root, which makes Caddy answer 403 for a tree
# that looks perfectly fine over SSH. Set web-readable modes explicitly instead
# of inheriting whatever the build host's umask happened to be.
rsync -rlt --chmod=D755,F644 --info=NAME "$STAGE/" "$HOST:$REMOTE_DIR/"

echo
echo "Published. A student's pom needs:"
echo
echo "  <repositories><repository>"
echo "    <id>tobiasgrundtvig</id>"
echo "    <url>https://maven.tobiasgrundtvig.dk</url>"
echo "  </repository></repositories>"
echo
echo "  <dependency>"
echo "    <groupId>textgame</groupId>"
echo "    <artifactId>textgame-client</artifactId>"
echo "    <version>$VERSION</version>"
echo "  </dependency>"
