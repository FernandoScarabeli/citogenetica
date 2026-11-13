#!/bin/sh
# Acompanha o endereço do túnel temporário e, quando ele muda, grava o novo endereço na
# variável API_ORIGIN do site no Netlify e pede um build novo (o _redirects é gerado no build).
set -u

API=https://api.netlify.com/api/v1
ESTADO=/estado/ultimo-endereco
VAR=API_ORIGIN

log() { printf '%s netlify-sync: %s\n' "$(date -u +%FT%TZ)" "$*"; }

netlify() {
    # netlify MÉTODO CAMINHO [CORPO] -> imprime o corpo da resposta; retorna erro se não for 2xx.
    metodo=$1; caminho=$2; corpo=${3-}
    if [ -n "$corpo" ]; then
        curl -sS --fail-with-body --max-time 30 -X "$metodo" \
            -H "Authorization: Bearer $NETLIFY_AUTH_TOKEN" -H 'Content-Type: application/json' \
            --data "$corpo" "$API$caminho"
    else
        curl -sS --fail-with-body --max-time 30 -X "$metodo" \
            -H "Authorization: Bearer $NETLIFY_AUTH_TOKEN" "$API$caminho"
    fi
}

conta=
while [ -z "$conta" ]; do
    conta=$(netlify GET "/sites/$NETLIFY_SITE_ID" | jq -r '.account_id // .account_slug // empty') || conta=
    [ -n "$conta" ] || { log 'não consegui ler o site no Netlify (token ou NETLIFY_SITE_ID errados?); tento de novo em 60 s'; sleep 60; }
done
log "site $NETLIFY_SITE_ID, conta $conta"

atualizar() {
    endereco=$1
    valor=$(jq -nc --arg v "$endereco" '{context: "all", value: $v}')
    if ! netlify PATCH "/accounts/$conta/env/$VAR?site_id=$NETLIFY_SITE_ID" "$valor" >/dev/null 2>&1; then
        novo=$(jq -nc --arg k "$VAR" --arg v "$endereco" '[{key: $k, values: [{context: "all", value: $v}]}]')
        netlify POST "/accounts/$conta/env?site_id=$NETLIFY_SITE_ID" "$novo" >/dev/null || return 1
    fi
    netlify POST "/sites/$NETLIFY_SITE_ID/builds" '{}' >/dev/null || return 1
}

while :; do
    host=$(curl -fsS --max-time 5 http://cloudflared:2000/quicktunnel 2>/dev/null | jq -r '.hostname // empty' 2>/dev/null)
    if [ -n "$host" ]; then
        endereco="https://$host"
        ultimo=$(cat "$ESTADO" 2>/dev/null || true)
        if [ "$endereco" != "$ultimo" ]; then
            # O DNS do trycloudflare leva alguns segundos para responder.
            if curl -fsS --max-time 10 -o /dev/null "$endereco/actuator/health"; then
                if atualizar "$endereco"; then
                    printf '%s' "$endereco" > "$ESTADO"
                    log "túnel em $endereco; build do Netlify disparado"
                else
                    log "falha ao atualizar o Netlify para $endereco; tento de novo"
                fi
            else
                log "túnel $endereco ainda não responde"
            fi
        fi
    fi
    sleep 15
done
