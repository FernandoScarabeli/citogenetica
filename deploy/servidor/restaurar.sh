#!/bin/sh
# Restaura o banco e as fotos de um dia de backup. APAGA o estado atual do banco e das fotos.
# Uso: deploy/servidor/restaurar.sh AAAA-MM-DD
set -eu
cd "$(dirname -- "$0")"

dia=${1-}
case "$dia" in
    [0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]) ;;
    *) printf 'Uso: %s AAAA-MM-DD   (veja os dias em %s)\n' "$0" "${BACKUP_DIR:-./backups}" >&2; exit 2 ;;
esac

docker compose exec -T backup test -f "/backups/banco-$dia.dump" </dev/null || { echo "Falta banco-$dia.dump" >&2; exit 1; }
docker compose exec -T backup test -f "/backups/fotos-$dia.tar.gz" </dev/null || { echo "Falta fotos-$dia.tar.gz" >&2; exit 1; }

printf 'Isto substitui o banco e as fotos atuais pelo backup de %s. Digite RESTAURAR para seguir: ' "$dia"
read -r resposta
[ "$resposta" = RESTAURAR ] || { echo 'Cancelado.'; exit 1; }

docker compose stop api cloudflared
docker compose exec -T backup sh -c "pg_restore --clean --if-exists --no-owner --single-transaction -d \"\$PGDATABASE\" /backups/banco-$dia.dump"
echo 'Banco restaurado.'

docker compose stop rustfs
docker compose run --rm --no-deps -T -v "citogenetica-servidor_rustfs-data:/destino" --entrypoint sh backup \
    -c "find /destino -mindepth 1 -delete && tar -xzf /backups/fotos-$dia.tar.gz -C /destino"
echo 'Fotos restauradas.'

docker compose up -d
echo 'Pronto. O túnel volta com endereço novo; o netlify-sync atualiza o Netlify sozinho.'
