#!/bin/sh
# Uma vez por dia, na hora BACKUP_HORA (UTC), grava em /backups:
#   banco-AAAA-MM-DD.dump  (pg_dump -Fc)   e   fotos-AAAA-MM-DD.tar.gz  (volume do RustFS)
# e mantém só os BACKUP_MANTER mais recentes de cada tipo. Também faz um ao subir, se o do dia faltar.
set -u
umask 077

log() { printf '%s backup: %s\n' "$(date -u +%FT%TZ)" "$*"; }

fazer_backup() {
    dia=$(date -u +%F)
    if pg_dump -Fc -f "/backups/banco-$dia.dump.tmp" && mv "/backups/banco-$dia.dump.tmp" "/backups/banco-$dia.dump"; then
        log "banco-$dia.dump gravado"
    else
        rm -f "/backups/banco-$dia.dump.tmp"; log 'FALHA no pg_dump'
    fi
    if tar -czf "/backups/fotos-$dia.tar.gz.tmp" -C /rustfs . && mv "/backups/fotos-$dia.tar.gz.tmp" "/backups/fotos-$dia.tar.gz"; then
        log "fotos-$dia.tar.gz gravado"
    else
        rm -f "/backups/fotos-$dia.tar.gz.tmp"; log 'FALHA ao copiar as fotos'
    fi
    for tipo in banco fotos; do
        ls -1 /backups/"$tipo"-*.* 2>/dev/null | grep -v '\.tmp$' | sort -r | tail -n +"$((BACKUP_MANTER + 1))" | xargs -r rm -f
    done
}

[ -f "/backups/banco-$(date -u +%F).dump" ] || fazer_backup
while :; do
    sleep 600
    if [ "$(date -u +%H)" = "$BACKUP_HORA" ] && [ ! -f "/backups/banco-$(date -u +%F).dump" ]; then
        fazer_backup
    fi
done
