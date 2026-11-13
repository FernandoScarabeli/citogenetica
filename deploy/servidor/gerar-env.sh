#!/bin/sh
# Cria deploy/servidor/.env a partir do .env.example, com segredos aleatórios e o endereço do site.
# Uso: deploy/servidor/gerar-env.sh https://<site>.netlify.app
set -eu
cd "$(dirname -- "$0")"

site=${1-}
case "$site" in
    https://*) site=${site%/} ;;
    *) printf 'Uso: %s https://<site>.netlify.app\n' "$0" >&2; exit 2 ;;
esac
if [ -e .env ]; then
    printf 'Erro: deploy/servidor/.env já existe; nada foi alterado.\n' >&2
    exit 1
fi

umask 077
tmp=.env.tmp.$$
trap 'rm -f "$tmp"' 0
while IFS= read -r linha || [ -n "$linha" ]; do
    case "$linha" in
        *=gerar) linha="${linha%=gerar}=$(od -An -N32 -tx1 /dev/urandom | tr -d '[:space:]')" ;;
        SITE_URL=*) linha="SITE_URL=$site" ;;
    esac
    printf '%s\n' "$linha" | sed "s|\${SITE_URL}|$site|g" >> "$tmp"
done < .env.example
mv "$tmp" .env
printf '.env criado. Complete os campos "preencher" (e-mail e Netlify) antes de subir.\n'
