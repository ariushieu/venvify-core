# Deploy venvify-core lên VPS

Dựa trên [vps-setup-kit](https://github.com/ariushieu/vps-setup-kit) (VPS đã chạy `setup_vps.sh`: Docker, network `backend-network`, UFW, Nginx, Certbot, backup cron).

## Lần đầu (một lần duy nhất)

```bash
# 1. Trên VPS — tạo thư mục project và copy 3 file trong deploy/ này lên:
mkdir -p ~/vps-setup-kit/projects/venvify-core
scp deploy/docker-compose.yml deploy/.env.example deploy/nginx.conf \
    root@<vps-ip>:~/vps-setup-kit/projects/venvify-core/

# 2. Điền secret thật
cd ~/vps-setup-kit/projects/venvify-core
cp .env.example .env && nano .env        # SECRET_KEY: openssl rand -hex 32

# 3. Domain: trỏ DNS A record về VPS, sửa api.your-domain.com trong nginx.conf,
#    rồi chạy lại setup_vps.sh để symlink nginx + tạo /opt/data/venvify-core:
sudo bash ~/vps-setup-kit/scripts/setup_vps.sh
sudo certbot --nginx -d api.your-domain.com

# 4. Kéo image + chạy
docker compose pull && docker compose up -d
curl -s https://api.your-domain.com/api/v1/health   # {"status":"UP"}
```

## GitHub Secrets (Settings → Secrets and variables → Actions)

| Secret | Giá trị |
|---|---|
| `DOCKERHUB_USERNAME` | username Docker Hub (image: `<username>/venvify-core`) |
| `DOCKERHUB_TOKEN` | Docker Hub **Access Token** (không dùng password) |
| `VPS_HOST` | IP/hostname VPS |
| `VPS_USERNAME` | user SSH (vd `root`) |
| `VPS_SSH_KEY` | private key SSH |

> Lưu ý: image trong `docker-compose.yml` đang là `ariushieu/venvify-core` — nếu Docker Hub username khác thì sửa cả file này lẫn không cần sửa workflow (workflow đọc từ secret).

## Sau đó

Mỗi lần push `master` → CD tự: test (MySQL service) → build image → push Docker Hub → SSH vào VPS `docker compose pull app && up -d app` → health check → prune image cũ. DB không bị restart khi deploy (`--no-deps`).

---

# Runbook backup / restore / rollback (T7)

Platform giữ tiền thật — sự cố KHÔNG được ứng biến. Làm đúng theo thứ tự dưới đây; drill ít nhất 1 lần trên VPS trước khi mở paid flow, và chỉ tin runbook đã được drill.

## 1. Backup (cài một lần)

```bash
# Trên VPS — copy script lên cạnh docker-compose.yml rồi cài cron 03:05 UTC hằng ngày:
scp deploy/backup.sh root@<vps-ip>:~/vps-setup-kit/projects/venvify-core/
ssh root@<vps-ip> 'chmod +x ~/vps-setup-kit/projects/venvify-core/backup.sh'
ssh root@<vps-ip> '(crontab -l 2>/dev/null; echo "5 3 * * * bash ~/vps-setup-kit/projects/venvify-core/backup.sh >> /var/log/venvify-backup.log 2>&1") | crontab -'
```

- Dump vào `/opt/backups/venvify-core/venvify-<UTC-timestamp>.sql.gz`, giữ 14 ngày.
- Script tự fail (exit ≠ 0) nếu dump rỗng/gzip cụt — kiểm tra `/var/log/venvify-backup.log` khi nghi ngờ.
- `--triggers` là bắt buộc: V5 có trigger chặn UPDATE/DELETE trên `ledger_entries`.
- **Khuyến nghị:** đồng bộ định kỳ thư mục backup ra NGOÀI VPS (rclone → object storage, hoặc `scp` về máy khác). VPS chết = mất cả app lẫn backup nếu chỉ để một chỗ.

## 2. Restore DB (khi dữ liệu hỏng / mất)

> Nguyên tắc: dừng app trước (chặn ghi mới đè lên dữ liệu đang cứu), MySQL giữ nguyên.

```bash
cd ~/vps-setup-kit/projects/venvify-core

# B1. Dừng app — user thấy downtime, chấp nhận; KHÔNG dừng mysql.
docker compose stop app

# B2. (Nếu DB hiện tại còn đọc được) chụp thêm 1 bản trước khi đè — cứu được gì hay nấy:
bash backup.sh || true

# B3. Chọn bản backup muốn khôi phục:
ls -lh /opt/backups/venvify-core/

# B4. Nạp đè vào DB (mysqldump chứa DROP TABLE + CREATE + INSERT + TRIGGER):
gunzip -c /opt/backups/venvify-core/venvify-<stamp>.sql.gz | \
  docker compose exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"'

# B5. Kiểm tra trigger ledger còn sống (phải thấy 2 dòng trg_ledger_no_update/no_delete):
docker compose exec -T mysql sh -c \
  'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SHOW TRIGGERS IN $MYSQL_DATABASE" ' | grep trg_ledger

# B6. Bật lại app + verify:
docker compose up -d app
curl -s https://api.<domain>/api/v1/health          # {"status":"UP"}
docker compose logs --tail=100 app                   # Flyway "up to date", không lỗi validate
```

- Sau restore, dữ liệu từ thời điểm backup → thời điểm sự cố là MẤT (RPO tối đa 24h). Đối chiếu với email/notification đã gửi nếu cần xử lý tay.
- Job reconcile 03:17 sẽ tự đối soát lại sổ; hoặc ép chạy tay ngay bằng cách restart app rồi theo dõi log `Ledger reconciliation`.

## 3. Rollback app version (deploy hỏng, DB vẫn lành)

Image được push 2 tag: `latest` và `<git-sha>` — rollback = chạy lại đúng sha cũ.

```bash
# B1. Xem sha của commit lành gần nhất (trên máy dev):
git log --oneline -5

# B2. Trên VPS — ghim image về sha đó rồi recreate app (không đụng DB):
cd ~/vps-setup-kit/projects/venvify-core
sed -i 's|venvify-core:.*|venvify-core:<sha>|' docker-compose.yml
docker compose pull app
docker compose up -d --no-deps --force-recreate app
curl -s https://api.<domain>/api/v1/health

# B3. Sau khi push bản fix lên master (CD deploy lại), trả compose về latest:
sed -i 's|venvify-core:.*|venvify-core:latest|' docker-compose.yml
```

> ⚠ **Migration đi kèm bản hỏng thì KHÔNG rollback được bằng cách đổi image** — Flyway không tự undo. Bản cũ + schema mới thường vẫn chạy nếu migration chỉ THÊM (cột/bảng mới); nếu migration đổi/xóa cột thì phải restore DB (mục 2) về bản backup TRƯỚC khi deploy, rồi mới ghim image cũ. Vì vậy: **prod migrate luôn backup trước** (architecture §10) — CD deploy có đổi migration thì tự tay chạy `bash backup.sh` trước khi push.

## 4. Drill (bắt buộc trước khi mở paid flow)

1. Trên VPS (hoặc VPS staging), tạo vài user/event/booking test.
2. Chạy `bash backup.sh` → thấy file mới trong `/opt/backups/venvify-core/`.
3. Phá dữ liệu có chủ đích (xóa 1 bảng test) → làm đúng mục 2 từ B1 đến B6.
4. Xác nhận: dữ liệu về đúng thời điểm backup, trigger còn, health UP, log reconcile sạch.
5. Ghi ngày drill + kết quả vào cuối file này.

**Lịch sử drill:** _(chưa chạy — điền sau lần drill đầu)_
