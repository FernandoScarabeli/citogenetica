#!/bin/sh
set -eu

usage() {
    printf 'Uso: %s [--force]\n' "$0" >&2
}

force=0
case "${1-}" in
    "") ;;
    --force) force=1 ;;
    *) usage; exit 2 ;;
esac
if [ "$#" -gt 1 ]; then
    usage
    exit 2
fi

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_root=$(CDPATH= cd -- "$script_dir/.." && pwd)
cd "$repo_root"

if [ ! -r .env.example ]; then
    printf 'Erro: não foi possível ler .env.example.\n' >&2
    exit 1
fi
if { [ -e .env ] || [ -L .env ]; } && [ "$force" -ne 1 ]; then
    printf 'Erro: .env já existe; nada foi alterado. Use --force para substituí-lo.\n' >&2
    exit 1
fi
if [ ! -r /dev/urandom ] || ! command -v od >/dev/null 2>&1 || ! command -v tr >/dev/null 2>&1; then
    printf 'Erro: geração segura indisponível; são necessários /dev/urandom, od e tr.\n' >&2
    exit 1
fi

umask 077
temporary_file=.env.tmp.$$
if ! (set -C; : > "$temporary_file") 2>/dev/null; then
    printf 'Erro: não foi possível criar o arquivo temporário com segurança.\n' >&2
    exit 1
fi
trap 'rm -f "$temporary_file"' 0
trap 'exit 1' HUP INT TERM

generate_secret() {
    generated=$(od -An -N32 -tx1 < /dev/urandom | tr -d '[:space:]')
    if [ "${#generated}" -ne 64 ]; then
        printf 'Erro: não foi possível gerar um segredo aleatório completo.\n' >&2
        exit 1
    fi
    printf '%s' "$generated"
}

datasource_password=$(generate_secret)
mail_password=$(generate_secret)
jwt_secret=$(generate_secret)
storage_access_key=$(generate_secret)
storage_secret_key=$(generate_secret)

while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in
        SPRING_DATASOURCE_PASSWORD=troque-isto) line="SPRING_DATASOURCE_PASSWORD=$datasource_password" ;;
        SPRING_MAIL_PASSWORD=troque-isto) line="SPRING_MAIL_PASSWORD=$mail_password" ;;
        API_SECURITY_JWT_SECRET=troque-isto) line="API_SECURITY_JWT_SECRET=$jwt_secret" ;;
        STORAGE_ACCESS_KEY=troque-isto) line="STORAGE_ACCESS_KEY=$storage_access_key" ;;
        STORAGE_SECRET_KEY=troque-isto) line="STORAGE_SECRET_KEY=$storage_secret_key" ;;
    esac
    printf '%s\n' "$line" >> "$temporary_file"
done < .env.example

for key in SPRING_DATASOURCE_PASSWORD SPRING_MAIL_PASSWORD API_SECURITY_JWT_SECRET STORAGE_ACCESS_KEY STORAGE_SECRET_KEY; do
    if ! grep -q "^${key}=[[:xdigit:]]\{64\}$" "$temporary_file"; then
        printf 'Erro: .env.example não contém um marcador válido para %s.\n' "$key" >&2
        exit 1
    fi
done

chmod 600 "$temporary_file"
if [ "$force" -eq 1 ]; then
    mv -f "$temporary_file" .env
else
    if ! ln "$temporary_file" .env 2>/dev/null; then
        printf 'Erro: .env já existe; nada foi sobrescrito. Use --force para substituí-lo.\n' >&2
        exit 1
    fi
    rm -f "$temporary_file"
fi
printf 'Arquivo .env criado com segredos aleatórios e permissão restrita.\n'
