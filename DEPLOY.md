# Deploying to a Windows PC behind Cloudflare Tunnel

Goal: run the helpdesk on an always-on Windows PC and publish it at **https://helpdesk.manageone.me** through the existing Cloudflare Tunnel. In production, UI and API run as **one jar on one port (8080)**, so Cloudflare needs a single route.

```
Internet ──► https://helpdesk.manageone.me ──► Cloudflare Tunnel (cloudflared on the PC)
                                                     └──► localhost:8080  helpdesk.jar (UI + API)
                                                                └──► MySQL localhost:3306 (not exposed)
```

Time needed: about 45 minutes the first time.

---

## 1. Install the prerequisites

Open **PowerShell** on the PC and check what is already there:

```powershell
java -version      # need 17 or newer
node -v            # need 18 or newer (only used to build the UI)
git --version
netstat -ano | findstr ":8080"   # must print nothing (port free)
```

Install whatever is missing:

| Tool | Download | Notes |
|---|---|---|
| Java 21 | https://adoptium.net (Temurin 21 LTS, Windows .msi) | Tick **"Set JAVA_HOME"** and **"Add to PATH"** in the installer |
| Node.js 20 LTS | https://nodejs.org | Default options |
| MySQL 8 | https://dev.mysql.com/downloads/installer | "Server only"; remember the **root password**; keep "Start at system startup" on |
| Git | https://git-scm.com | Default options |

Close and reopen PowerShell after installing, then run the checks again.

## 2. Get the code

```powershell
cd C:\
git clone https://github.com/<your-account>/<your-repo>.git helpdesk
cd C:\helpdesk
```

## 3. Create the database user

Open the MySQL command line (`mysql -u root -p`, or "MySQL 8.0 Command Line Client") and run, with your own strong password:

```sql
CREATE DATABASE helpdesk CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'helpdesk'@'localhost' IDENTIFIED BY 'choose-a-strong-password';
GRANT ALL PRIVILEGES ON helpdesk.* TO 'helpdesk'@'localhost';
FLUSH PRIVILEGES;
```

The app creates its tables on first start.

## 4. Configure

```powershell
cd C:\helpdesk\deploy
copy helpdesk.env.example helpdesk.env
notepad helpdesk.env
```

Fill in:

- `DB_PASSWORD`: the password from step 3
- `JWT_SECRET`: a random value. Generate one in PowerShell and paste it:
  ```powershell
  -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 48 | ForEach-Object {[char]$_})
  ```
- Leave `SPRING_PROFILES_ACTIVE=prod`.

`helpdesk.env` holds secrets and is git-ignored, so it never gets pushed.

The `prod` profile **refuses to start** with the development JWT secret, an empty DB password, or demo data enabled, and the log says exactly which one. Demo accounts and demo inbound keys are never created in production.

## 5. Build

```powershell
cd C:\helpdesk
deploy\build.bat
```

This builds the UI, runs the backend tests, and produces `Backend_BE\target\helpdesk.jar` with the UI inside. Expect 2–4 minutes the first time (it downloads dependencies).

## 6. Start and check locally

```powershell
deploy\start-helpdesk.bat
```

Keep that window open. In a browser **on the PC**, open http://localhost:8080. The login page should appear. If not, read `deploy\logs\helpdesk.log`.

## 7. Start automatically on boot

Close the window from step 6 (or run `deploy\stop-helpdesk.bat`). Then right-click `deploy\register-autostart.bat` and choose **Run as administrator**. Start it now without rebooting:

```powershell
schtasks /run /tn "Helpdesk"
```

From now on the helpdesk starts one minute after every boot, even with nobody logged in.

## 8. Publish on Cloudflare

1. https://one.dash.cloudflare.com → **Networks → Tunnels** → the **feesone** tunnel → **Edit**.
2. **Public Hostname** (or "Published application routes") → **Add a public hostname**:
   - Subdomain: `helpdesk`
   - Domain: `manageone.me`
   - Path: *(empty)*
   - Service: `HTTP` → `localhost:8080`
3. **Save**. Cloudflare creates the DNS record itself.

If the tunnel has no Edit option, it is configured from a `config.yml` on the PC. Add this rule **above** the final catch-all `service: http_status:404` line, then restart the cloudflared service:

```yaml
  - hostname: helpdesk.manageone.me
    service: http://localhost:8080
```

## 9. Go live

1. Open **https://helpdesk.manageone.me**.
2. **Register organization**: the organization name (e.g. ManageOne) and the admin's name, email and password. You are now its Org Admin.
3. **Users** → add agents (and customers if needed).
4. **Email intake** → copy the inbound key for the mailbox script ([README section 4](README.md#4-email-intake-extra)); its `BACKEND_URL` is `https://helpdesk.manageone.me`.

---

## Updating to a new version

```powershell
cd C:\helpdesk
deploy\stop-helpdesk.bat
git pull
deploy\build.bat
schtasks /run /tn "Helpdesk"
```

## Backups

Run regularly (Task Scheduler can run it nightly). `mysqldump` is in MySQL's `bin` folder:

```powershell
New-Item -ItemType Directory -Force C:\backups | Out-Null
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe" -u helpdesk -p helpdesk > "C:\backups\helpdesk-$(Get-Date -Format yyyyMMdd).sql"
```

## Troubleshooting

| Symptom | Check |
|---|---|
| `Refusing to start in prod: ...` in the log | The message names the setting to fix in `helpdesk.env` |
| `Access denied for user 'helpdesk'` | `DB_PASSWORD` differs from step 3 |
| `Port 8080 was already in use` | Another app uses 8080: set `SERVER_PORT=8081` in `helpdesk.env` **and** in the Cloudflare route |
| Cloudflare shows error 502 / 1033 | The app is not running (`netstat -ano \| findstr ":8080"`), or the tunnel service is stopped |
| Page loads but login fails | Read `deploy\logs\helpdesk.log` |
