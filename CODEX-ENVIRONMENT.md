# Облачная сборка HomeBudget

Целевой репозиторий: https://github.com/koshkinaiuliia-arch/homebudget

## Версии

| Компонент | Версия | Основание |
| --- | --- | --- |
| JDK | 17 | JavaVersion.VERSION_17 в app/build.gradle, требования AGP |
| Android Gradle Plugin | 8.7.3 | build.gradle |
| Gradle | 8.9 | Требование AGP 8.7; зафиксировано в gradle-wrapper.properties |
| Android SDK Platform / compileSdk | android-35 / 35 | app/build.gradle |
| targetSdk / minSdk | 35 / 26 | app/build.gradle |
| SDK Build Tools | 34.0.0 | Значение по умолчанию AGP 8.7, теперь явно закреплено в app/build.gradle |
| SDK Command-line Tools | 12.0, архив 11076708 | Фиксированная версия загрузчика sdkmanager, совместимая с JDK 17 |

У SDK нет единой версии: устанавливаются отдельные пакеты Platform и Build Tools.
NDK, CMake, Android Studio, эмулятор и system images этому проекту не нужны.

## Настройка Codex Environment

Сначала поместите содержимое HomeBudget в корень указанного репозитория, включая
скрытые каталоги `.codex`, `.github` и файлы Gradle Wrapper.
Создайте или откройте Environment для этого репозитория в настройках Codex.
Используйте Linux x86_64 на Ubuntu/Debian (стандартное облачное окружение).

**Setup script:**

```bash
bash .codex/setup.sh
```

**Maintenance script:**

```bash
bash .codex/maintenance.sh
```

**Environment variables** — задайте в настройках окружения, чтобы они сохранялись
между setup и фазой выполнения агента:

```text
JAVA_HOME=/opt/homebudget/jdk17
ANDROID_HOME=/opt/homebudget/android-sdk
ANDROID_SDK_ROOT=/opt/homebudget/android-sdk
```

Setup также создаёт `/etc/profile.d/homebudget-android.sh` для login shell и
локальный `.codex/android-env.sh`. В обычном терминале можно выполнить:

```bash
source /etc/profile.d/homebudget-android.sh
./gradlew assembleDebug
```

Один `export` в setup не передаёт переменные в отдельную сессию агента.
Поэтому переменные в настройках Environment важны даже при наличии profile.d.

## Что делает setup

1. Устанавливает OpenJDK 17 headless, curl, unzip, CA-сертификаты и Python.
2. Скачивает фиксированную версию Command-line Tools с dl.google.com и проверяет
   контрольную сумму из официального repository2-1.xml.
3. Выполняет `sdkmanager --licenses` с ответом yes и проверяет код возврата sdkmanager.
4. Через sdkmanager устанавливает `platforms;android-35` и `build-tools;34.0.0`.
5. Настраивает пути, sdk.dir в local.properties и исполняемый бит gradlew.
6. Запускает `./gradlew assembleDebug --no-daemon --stacktrace --console=plain`.
7. Проверяет наличие APK и его подпись через apksigner, записывает SHA-256.

Gradle Wrapper проверяет SHA-256 дистрибутива Gradle 8.9. Сам JAR Wrapper
сверен с опубликованной Gradle контрольной суммой.

## Сеть и повторные запуски

Setup требует доступа к репозиториям Ubuntu/Debian, dl.google.com,
services.gradle.org и его адресам перенаправления (включая GitHub),
plugins.gradle.org и repo.maven.apache.org.
В Codex сеть доступна на этапе setup; настройка доступа агента отдельная.
Первичная сборка выполняется прямо в setup, чтобы загрузить зависимости в кэш.
Если при последующих изменениях появятся новые зависимости, потребуется сетевой
доступ или повторный setup. Секреты и токены для публичных зависимостей не нужны.

При несовместимом старом кэше используйте Reset cache в настройках Environment.
Для повторной сборки после восстановления кэша предусмотрен maintenance script.

## Результаты

- APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Лог сборки: `.codex/logs/assembleDebug.log`.
- Версии: `.codex/logs/gradle-version.log`.
- Контрольная сумма APK: `.codex/logs/apk.sha256`.

GitHub Actions использует этот же setup и публикует артефакт HomeBudget-APK.
Сборка отладочная; для публикации в магазине понадобится релизная подпись.

## Источники

- [Требования AGP 8.7](https://developer.android.com/build/releases/agp-8-7-0-release-notes).
- [Codex cloud environments: setup, переменные и кэш](https://learn.chatgpt.com/docs/environments/cloud-environment).
- [sdkmanager](https://developer.android.com/tools/sdkmanager).

## Статус проверки

Проверены Bash-синтаксис скриптов и контрольная сумма Gradle Wrapper.
Облачный setup ещё не выполнен: требуется авторизованный доступ к Codex Environment
и загрузка проекта в пока пустой репозиторий. Наличие workflow не означает успешную сборку.
