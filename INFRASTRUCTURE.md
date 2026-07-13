## 🌐 인프라 구축 및 배포 기록 (Deployment Architecture)

본 프로젝트는 AWS(Amazon Web Services) 환경에 독립적인 서버를 구축하여 배포되었습니다.

### 1. 서버 인프라 사양 (AWS EC2)

* **운영체제(OS):** Ubuntu Server 22.04 LTS (HVM)
* **인스턴스 유형:** t3.small (vCPU 2 Cores, RAM 2 GiB)
* **스토리지:** EBS 20GB (General Purpose SSD, gp3)
* **네트워크:** AWS 탄력적 IP (Elastic IP) 연동을 통한 고정 IP 확보

### 2. 네트워크 및 보안 설정 (Security Group)

외부 자원의 안전한 접근을 위해 인바운드 규칙(Firewall)을 최소한으로 제한하여 설정했습니다.

* **HTTP (80):** 웹 서비스 접속용 (위치 무관 개방)
* **HTTPS (443):** 보안 웹 서비스 접속용 (위치 무관 개방)
* **SSH (22):** 서버 원격 제어용 (개발자 개인 IP만 허용)
* **PostgreSQL (5432) / Redis (6379):** 외부 인바운드 미개방. 컨테이너 간 내부 네트워크(`docker-compose` 내부 DNS)로만 통신하며, 외부에서 직접 접근할 필요가 없어 보안 그룹에 규칙 자체를 추가하지 않음

### 3. 데이터베이스 및 컨테이너 환경

서버 내 리소스 관리와 환경 격리를 위해 **Docker / Docker Compose**를 활용해 애플리케이션과 데이터스토어를 함께 구동했습니다.

* **Database:** Docker 컨테이너 환경의 PostgreSQL 16
* **Cache / 동시성 제어:** Docker 컨테이너 환경의 Redis 7 (쿠폰 발급 카운터, 상품 캐싱)
* **Application:** 멀티스테이지 Dockerfile로 빌드한 Spring Boot(Java 21) 앱 컨테이너
* **환경 구성:** `docker-compose.prod.yml` 하나로 app + postgres + redis 3개 컨테이너를 함께 기동, 비밀값은 `.env`로 분리 관리

### 4. 실제 배포 명령어 흐름 (Deployment Steps)

추후 서버 재구축 및 스케일 아웃을 위해 실제 사용된 명령어 핵심 요약을 기록합니다.

```bash
# 1. 시스템 업데이트 및 Docker 설치
sudo apt update && sudo apt upgrade -y
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER

# 2. 프로젝트 코드 배포 및 환경변수 설정
git clone <레포지토리-주소> shop-backend
cd shop-backend
cp .env.example .env
nano .env   # DB_PASSWORD, JWT_SECRET 등 운영 값으로 수정

# 3. 앱 + PostgreSQL + Redis 컨테이너 빌드 및 기동
docker compose -f docker-compose.prod.yml up -d --build

# 4. 헬스체크
curl localhost:8080/api/items

# 5. Nginx 리버스 프록시 + Let's Encrypt HTTPS 적용
sudo apt install -y nginx certbot python3-certbot-nginx
# (nginx site 설정 후)
sudo certbot --nginx -d your-domain.com
```

세부 절차 및 트러블슈팅은 [`DEPLOY.md`](./DEPLOY.md) 참고.
