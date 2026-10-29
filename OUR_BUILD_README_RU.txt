AppleStyleTV — рабочая копия исходников CleanYourTV Launcher 3.2.
Цель: Xiaomi Mi Box 4 / Android 9.
В custom-wallpapers/midnight-mountains.jpg лежит созданный для проекта тёмный фон 1920x1080.
Это исходный проект, НЕ готовый APK. Обои пока отдельный файл, не установлены в качестве фона по умолчанию.
Для сборки нужен Android SDK 34, JDK 17 и Gradle; из папки launcher запустить ./gradlew assembleDebug.
Прежде чем отключать системный launcher, установите и проверьте новый APK.
Исходный код предоставлен на условиях LICENSE оригинального проекта.

Версия подготовки 0.2: добавлен .github/workflows/build-applestyletv.yml.
GitHub Actions -> Build AppleStyleTV APK -> Run workflow; затем скачать артефакт AppleStyleTV-debug-apk.
Важно: это оригинальный лаунчер CleanYourTV с отдельным файлом обоев; обои ещё НЕ встроены в настройки лаунчера.
Нельзя обещать сборку без проверки GitHub Actions. Не отключайте системный лаунчер до тестирования.
