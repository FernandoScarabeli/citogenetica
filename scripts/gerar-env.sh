#!/bin/sh
set -eu

usage() {
    printf 'Uso: %s [--force | --add-missing]\n' "$0" >&2
}

mode=${1-}
case "$mode" in
    "") mode=create ;;
    --force) mode=force ;;
    --add-missing) mode=add-missing ;;
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
if [ "$mode" = create ] && { [ -e .env ] || [ -L .env ]; }; then
    printf 'Erro: .env já existe; nada foi alterado. Use --add-missing para acrescentar apenas chaves ausentes ou --force para substituí-lo.\n' >&2
    exit 1
fi
if [ "$mode" = add-missing ] && [ -L .env ]; then
    printf 'Erro: .env é um link simbólico; nada foi alterado.\n' >&2
    exit 1
fi
if [ "$mode" = add-missing ] && [ -e .env ] && [ ! -f .env ]; then
    printf 'Erro: .env não é um arquivo regular; nada foi alterado.\n' >&2
    exit 1
fi
if [ ! -r /dev/urandom ] || ! command -v od >/dev/null 2>&1 || ! command -v tr >/dev/null 2>&1; then
    printf 'Erro: geração segura indisponível; são necessários /dev/urandom, od e tr.\n' >&2
    exit 1
fi

umask 077
temporary_file=.env.tmp.$$
original_file=.env.original.$$
lock_directory=.env.gerar-env.lock
if ! mkdir "$lock_directory" 2>/dev/null; then
    printf 'Erro: outra atualização do .env está em andamento.\n' >&2
    exit 1
fi
if ! (set -C; : > "$temporary_file") 2>/dev/null; then
    rmdir "$lock_directory"
    printf 'Erro: não foi possível criar o arquivo temporário com segurança.\n' >&2
    exit 1
fi
trap 'rm -f "$temporary_file" "$original_file"; rmdir "$lock_directory" 2>/dev/null || true' 0
trap 'exit 1' HUP INT TERM

generate_secret() {
    generated=$(od -An -N32 -tx1 < /dev/urandom | tr -d '[:space:]')
    if [ "${#generated}" -ne 64 ]; then
        printf 'Erro: não foi possível gerar um segredo aleatório completo.\n' >&2
        exit 1
    fi
    printf '%s' "$generated"
}

is_secret_key() {
    case "$1" in
        SPRING_DATASOURCE_PASSWORD|SPRING_MAIL_PASSWORD|API_SECURITY_RECOVERY_HMAC_SECRET|STORAGE_ACCESS_KEY|STORAGE_SECRET_KEY)
            return 0 ;;
        *) return 1 ;;
    esac
}

value_for() {
    key=$1
    value=$2
    if [ "$value" = troque-isto ] && is_secret_key "$key"; then
        generate_secret
    else
        printf '%s' "$value"
    fi
}

if [ "$mode" = add-missing ] && [ -f .env ]; then
    cp .env "$original_file"
    cat "$original_file" > "$temporary_file"
    if [ -s "$original_file" ]; then
        last_byte=$(tail -c 1 "$original_file" | od -An -t x1 | tr -d '[:space:]')
        if [ "$last_byte" != 0a ]; then
            printf '\n' >> "$temporary_file"
        fi
    fi

    added_keys=0
    while IFS= read -r template_line || [ -n "$template_line" ]; do
        case "$template_line" in
            ""|\#*) continue ;;
        esac
        case "$template_line" in
            *=*) key=${template_line%%=*}; value=${template_line#*=} ;;
            *) continue ;;
        esac
        case "$key" in
            ""|*[!A-Za-z0-9_]*) printf 'Erro: chave inválida encontrada em .env.example.\n' >&2; exit 1 ;;
        esac
        if grep -q "^${key}=" "$original_file"; then
            continue
        fi
        value=$(value_for "$key" "$value")
        printf '%s=%s\n' "$key" "$value" >> "$temporary_file"
        added_keys=$((added_keys + 1))
    done < .env.example

    if [ "$added_keys" -eq 0 ]; then
        printf 'Nenhuma chave ausente; .env não foi alterado.\n'
        exit 0
    fi
    if ! cmp -s .env "$original_file"; then
        printf 'Erro: .env mudou durante a atualização; nada foi substituído.\n' >&2
        exit 1
    fi
else
    while IFS= read -r template_line || [ -n "$template_line" ]; do
        case "$template_line" in
            *=*) key=${template_line%%=*}; value=${template_line#*=}; value=$(value_for "$key" "$value"); template_line="$key=$value" ;;
        esac
        printf '%s\n' "$template_line" >> "$temporary_file"
    done < .env.example
fi

for key in SPRING_DATASOURCE_PASSWORD SPRING_MAIL_PASSWORD API_SECURITY_RECOVERY_HMAC_SECRET STORAGE_ACCESS_KEY STORAGE_SECRET_KEY; do
    if [ "$mode" = add-missing ] && [ -f "$original_file" ] && grep -q "^${key}=" "$original_file"; then
        continue
    fi
    if ! grep -q "^${key}=[[:xdigit:]]\{64\}$" "$temporary_file"; then
        printf 'Erro: .env.example não contém um marcador válido para %s.\n' "$key" >&2
        exit 1
    fi
done

chmod 600 "$temporary_file"
if [ "$mode" = force ]; then
    mv -f "$temporary_file" .env
    printf 'Arquivo .env substituído com segredos aleatórios e permissão restrita.\n'
elif [ "$mode" = add-missing ] && [ -f "$original_file" ]; then
    mv -f "$temporary_file" .env
    printf 'Chaves ausentes adicionadas ao .env; conteúdo existente preservado.\n'
else
    if ! ln "$temporary_file" .env 2>/dev/null; then
        printf 'Erro: .env já existe; nada foi sobrescrito. Use --add-missing para acrescentar chaves ausentes.\n' >&2
        exit 1
    fi
    rm -f "$temporary_file"
    printf 'Arquivo .env criado com segredos aleatórios e permissão restrita.\n'
fi
