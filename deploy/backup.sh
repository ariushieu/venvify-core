#!/usr/bin/env bash
# ==========================================================
# Backup MySQL của venvify-core (T7 — trước khi có tiền thật).
# Chạy bằng cron trên VPS (xem runbook trong README.md):
#   5 3 * * * bash ~/vps-setup-kit/projects/venvify-core/backup.sh >> /var/log/venvify-backup.log 2>&1
# ==========================================================
set -euo pipefail

BACKUP_DIR=/opt/backups/venvify-core
RETENTION_DAYS=14
STAMP=$(date -u +%Y%m%d-%H%M%S)
COMPOSE_DIR="$(cd "$(dirname "$0")" && pwd)"
OUT="$BACKUP_DIR/venvify-$STAMP.sql.gz"

mkdir -p "$BACKUP_DIR"
cd "$COMPOSE_DIR"

# --single-transaction: dump nhất quán InnoDB, không khóa bảng đang chạy.
# --triggers BẮT BUỘC: V5 có trigger append-only trên ledger_entries — mất trigger là mất lớp chặn DB.
# --routines để dự phòng nếu sau này có stored procedure.
docker compose exec -T mysql sh -c \
    'exec mysqldump --single-transaction --triggers --routines -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' \
    | gzip > "$OUT"

# Dump rỗng hoặc gzip cụt = backup hỏng → exit khác 0 để cron log báo đỏ.
[ -s "$OUT" ]
gunzip -t "$OUT"

find "$BACKUP_DIR" -name 'venvify-*.sql.gz' -mtime +"$RETENTION_DAYS" -delete

echo "OK $(date -u +%FT%TZ): $OUT ($(du -h "$OUT" | cut -f1))"
