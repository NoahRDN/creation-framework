#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

load_env() {
  local env_file="$1"
  [[ -f "$env_file" ]] || return 0

  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    [[ -z "$line" || "$line" == \#* ]] && continue
    [[ "$line" == *"="* ]] || continue

    local key="${line%%=*}"
    local value="${line#*=}"
    key="$(printf '%s' "$key" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
    value="$(printf '%s' "$value" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"

    if [[ "$value" == \"*\" && "$value" == *\" ]]; then value="${value:1:-1}"; fi
    if [[ "$value" == \'*\' && "$value" == *\' ]]; then value="${value:1:-1}"; fi

    export "$key=$value"
  done <"$env_file"
}

load_env ".env"

: "${work_dir:=$SCRIPT_DIR}"
: "${jar_output_path:=$SCRIPT_DIR/lib}"

if [[ "$work_dir" =~ ^[A-Za-z]:\\\\? ]] || [[ "$jar_output_path" =~ ^[A-Za-z]:\\\\? ]]; then
  echo "ERREUR: .env contient des chemins Windows (ex: H:\\...). Sur Linux, utilisez des chemins POSIX (ex: /home/...)."
  exit 1
fi

echo "Dossier de travail: $work_dir"
echo "Chemin de sortie JAR: $jar_output_path"
echo "=========================="

if [[ -f "$work_dir/pom.xml" ]]; then
  echo "Maven project detected in $work_dir - running build and copying dependencies"
  (
    cd "$work_dir"
    echo "Running: mvn clean package"
    mvn clean package || echo "ATTENTION: mvn clean package a echoue"

    echo "Running: mvn dependency:copy-dependencies -DoutputDirectory=target/dependency"
    mvn dependency:copy-dependencies -DoutputDirectory=target/dependency || echo "ATTENTION: mvn dependency:copy-dependencies a echoue"
  )
else
  echo "Aucun pom.xml trouve dans $work_dir - saut des etapes Maven"
fi

echo "Recherche du JAR Maven dans \"$work_dir/target\"..."
jar_found=""
if compgen -G "$work_dir/target/*.jar" >/dev/null; then
  jar_found="$(
    ls -t "$work_dir"/target/*.jar 2>/dev/null \
      | grep -Ev -- '-(sources|javadoc)\\.jar$' \
      | head -n 1 || true
  )"
fi

if [[ -z "$jar_found" ]]; then
  echo "ERREUR: aucun JAR trouve dans $work_dir/target"
  exit 1
fi

echo "JAR Maven trouve : $jar_found"
mkdir -p "$jar_output_path"
cp -f "$jar_found" "$jar_output_path/"
echo "JAR copie vers: $jar_output_path/"

if [[ -d "$work_dir/target/dependency" ]]; then
  echo "Copie des dependances depuis: $work_dir/target/dependency"
  cp -a "$work_dir/target/dependency/." "$jar_output_path/"
else
  echo "Aucune dependance trouvee dans $work_dir/target/dependency"
fi

echo "Operation terminee."
