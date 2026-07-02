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
