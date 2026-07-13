# AWS 배포 가이드 (EC2 + Docker Compose)

이 프로젝트(Spring Boot + PostgreSQL + Redis)를 EC2 한 대에 Docker Compose로 올려서 웹에 공개하는 절차입니다.

사용하는 파일:
- `Dockerfile` — 앱 이미지 빌드
- `docker-compose.prod.yml` — 앱 + postgres + redis 실행
- `.env` — 실제 비밀값 (직접 만들어야 함, git에 커밋 금지)
- `application-prod.yml` — 운영 프로필 설정

---

## 0. 시작 전 꼭 확인할 것 — JWT secret 노출

`application.yml`에 JWT secret이 하드코딩되어 git에 커밋되어 있습니다. 이미 노출된 값이므로 운영에서 재사용하면 안 됩니다. 아래 3단계에서 새 값을 생성해 `.env`로만 관리하세요.

```bash
openssl rand -base64 64
```

---

## 1. EC2 인스턴스 생성

AWS 콘솔 → EC2 → **인스턴스 시작**

- AMI: **Ubuntu Server 22.04 LTS**
- 인스턴스 타입: **t3.small(2GB) 이상** 권장. Postgres+Redis+Spring Boot를 한 서버에서 같이 돌리므로 t3.micro(1GB)는 메모리 부족(OOM) 위험이 큽니다. 이미지 빌드(Gradle)까지 서버에서 할 거면 빌드 때만 t3.medium으로 올렸다가 배포 후 t3.small로 낮추는 것도 방법입니다.
- 키 페어: 새로 생성 후 `.pem` 파일 안전하게 보관 (SSH 접속에 필요)
- 네트워크 설정(보안 그룹) — 인바운드 규칙:
  | 유형 | 포트 | 소스 |
  |---|---|---|
  | SSH | 22 | 내 IP만 (Anywhere 금지) |
  | HTTP | 80 | Anywhere |
  | HTTPS | 443 | Anywhere |
- 스토리지: 20GB 이상 (gp3)

생성 후 **탄력적 IP(Elastic IP)** 를 할당해서 인스턴스에 연결하세요. 안 하면 인스턴스를 재시작할 때마다 공인 IP가 바뀝니다.

---

## 2. 서버 접속 & Docker 설치

```bash
chmod 400 your-key.pem
ssh -i your-key.pem ubuntu@<탄력적 IP>
```

서버 안에서:

```bash
sudo apt update && sudo apt upgrade -y

# Docker 설치
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
newgrp docker

# Docker Compose plugin은 위 스크립트에 포함되어 있음 (docker compose version 으로 확인)
```

---

## 3. 코드 배포 & 환경변수 설정

```bash
git clone <your-repo-url> shop-backend
cd shop-backend

cp .env.example .env
nano .env   # DB_PASSWORD, JWT_SECRET(1번에서 생성한 값)을 실제 값으로 채우기
```

---

## 4. 빌드 & 실행

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

확인:

```bash
docker compose -f docker-compose.prod.yml ps
curl localhost:8080/api/items   # 200 응답이면 정상
```

로그 확인: `docker compose -f docker-compose.prod.yml logs -f app`

---

## 5. 도메인 연결 (선택)

도메인이 있다면 DNS의 A 레코드를 Elastic IP로 연결합니다. (Route 53을 쓴다면 호스팅 영역에서 A 레코드 추가, 다른 등록기관이면 해당 DNS 관리 화면에서 동일하게 설정)

```
A   api.yourdomain.com   →   <탄력적 IP>
```

전파에는 몇 분~몇 시간이 걸릴 수 있습니다.

---

## 6. Nginx 리버스 프록시 + HTTPS

앱은 컨테이너 내부에서 8080으로만 떠 있으므로, 80/443을 받아 8080으로 넘겨주는 Nginx를 서버에 설치합니다.

```bash
sudo apt install -y nginx
sudo tee /etc/nginx/sites-available/shop-backend > /dev/null <<'EOF'
server {
    listen 80;
    server_name api.yourdomain.com;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
EOF

sudo ln -s /etc/nginx/sites-available/shop-backend /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
```

HTTPS 인증서 발급 (Let's Encrypt, 무료):

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d api.yourdomain.com
```

certbot이 nginx 설정을 자동으로 443으로 확장하고 인증서 자동 갱신도 등록해줍니다.

도메인이 없다면 `http://<탄력적 IP>` 로 바로 접속해도 되지만, 이 경우 HTTPS(certbot)는 도메인 없이는 불가능합니다.

---

## 7. 이후 업데이트 배포

```bash
cd shop-backend
git pull
docker compose -f docker-compose.prod.yml up -d --build
```

---

## 참고 — 비용

- EC2 t3.small: 시간당 약 $0.0208 (서울 리전 기준, 월 약 $15)
- Elastic IP: 인스턴스에 연결되어 있는 동안은 무료 (연결 안 하고 놀리면 과금)
- EBS 20GB(gp3): 월 약 $1.6
- 도메인/Route 53: 선택 사항, 도메인 자체 비용은 별도

가격은 변동될 수 있으니 실제 청구 전 [AWS 요금 계산기](https://calculator.aws)로 확인하세요.

## 참고 — 알아두면 좋은 점

- `application-prod.yml`은 `ddl-auto: update`로 설정했습니다 (local/loadtest의 `create`는 재시작마다 테이블을 지우므로 운영에서는 절대 사용 금지). 스키마가 안정되면 `validate`로 바꾸고 마이그레이션 도구(Flyway 등) 도입을 권장합니다.
- `data-postgresql.sql`은 부하테스트용 시드 데이터라 운영 프로필에서는 `sql.init.mode: never`로 꺼두었습니다.
- README의 "알려진 이슈" 목록에 있는 항목들(재고 off-by-one, PG 연동 없음 등)은 배포와 별개로 실제 서비스 전에는 처리가 필요합니다.
