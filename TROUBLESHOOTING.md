# Помилки завантаження залежностей

## HTTP 403 Forbidden від Maven Central

Приклад: `Could not HEAD https://repo.maven.apache.org/maven2/... Received status code 403`.
Це відмова HTTP-доступу до залежностей, а не повідомлення компілятора про помилку коду. Сам лог не показує, хто відмовив: Maven Central, мережевий проксі, VPN-шлюз чи політика середовища. `plugins.gradle.org` також може перенаправити до Maven Central; зміна лише Gradle Plugin Portal не розв'яже цей випадок.

### 1. Перевірте адресу в тому самому середовищі

Windows PowerShell:

```powershell
curl.exe -I https://repo.maven.apache.org/maven2/org/jetbrains/annotations/23.0.0/annotations-23.0.0.pom
curl.exe -I https://repo1.maven.org/maven2/org/jetbrains/annotations/23.0.0/annotations-23.0.0.pom
```

Linux/macOS: використайте `curl -I` з тими самими адресами. Очікувана відповідь — HTTP 200. Браузер і Java/Gradle можуть використовувати різні проксі, тому результат curl є діагностикою, а не гарантією роботи Gradle.

### 2. Якщо repo1.maven.org доступний

У проєкті є перемикач на альтернативну офіційну адресу Maven Central. Він застосовується до залежностей Android Gradle Plugin і до залежностей застосунку; Google Maven залишається без змін.

Windows PowerShell:

```powershell
.\gradlew.bat -PuseAlternateMavenCentral=true --refresh-dependencies --no-daemon assembleDebug lintDebug testDebugUnitTest
```

Linux/macOS / GitHub Actions:

```sh
./gradlew -PuseAlternateMavenCentral=true --refresh-dependencies --no-daemon assembleDebug lintDebug testDebugUnitTest
```

Для Gradle Sync в Android Studio додайте у кореневий `gradle.properties`:

```properties
useAlternateMavenCentral=true
```

Потім натисніть **Sync Project with Gradle Files**. Приберіть цей рядок для повернення на стандартну адресу.

У GitHub Actions можна запустити **Run workflow** і ввімкнути параметр **Use alternate official Maven Central endpoint**. Новий workflow має бути доступний у default branch для ручного запуску; натискання Run workflow саме по собі не зливає гілки. Автоматична збірка гілки за замовчуванням використовує стандартну адресу.

### 3. Якщо обидві адреси повертають 403

Потрібно виправити мережевий доступ у середовищі, де працює збірка. Для Maven Central потрібні HTTPS-запити GET та HEAD до `repo.maven.apache.org` або `repo1.maven.org`. Для всього Android-проєкту також потрібні Google Maven (`dl.google.com`), Gradle Plugin Portal (`plugins.gradle.org`, `plugins-artifacts.gradle.org`) та Gradle distribution (`services.gradle.org`, `downloads.gradle.org` і фактичні адреси HTTPS-перенаправлень).

На ПК перевірте налаштування **HTTP Proxy** в Android Studio та `systemProp.http.*` / `systemProp.https.*` у користувацькому Gradle `gradle.properties`. Якщо проксі потрібен вашій мережі, використайте надані її адміністратором налаштування. Не додавайте логіни/паролі в Git і не надсилайте їх у чат.

У керованому хмарному середовищі перевірте allowlist і правила проксі. У GitHub Actions перевірте тип runner і мережеві правила саме цього runner. Після зміни доступу повторіть збірку з `--refresh-dependencies`.

Вимкнення TLS-перевірки, перехід на HTTP, видалення залежностей або довільні сторонні дзеркала не виправляють причину відмови доступу.
