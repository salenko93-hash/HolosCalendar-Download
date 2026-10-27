# HolosCalendar-Download

Готовий **ГолосКалендар 4.0.0** для Android 8.0+ та повний проєкт Android Studio.

## Завантажити на ПК

### [⬇ Повний проєкт ZIP — APK та PDF всередині](https://github.com/salenko93-hash/HolosCalendar-Download/raw/refs/heads/main/HolosCalendar-4.0.0-project.zip)

Натисніть посилання й збережіть ZIP у «Завантаження». Розпакуйте його, потім відкрийте папку **HolosCalendar** в Android Studio. Потрібні JDK 17 і Android SDK 35. Усі вихідні файли, Gradle Wrapper, документація й тести включені.

## Окремі файли

- [APK для телефона — HolosCalendar 4.0.0](https://github.com/salenko93-hash/HolosCalendar-Download/raw/refs/heads/main/HolosCalendar-4.0.0-debug.apk)
- [PDF — 87 українських фраз та правила уточнення](https://github.com/salenko93-hash/HolosCalendar-Download/raw/refs/heads/main/HolosCalendar-Ukrainian-4.0.0.pdf)
- [Контрольні суми SHA-256](https://github.com/salenko93-hash/HolosCalendar-Download/raw/refs/heads/main/SHA256SUMS.txt)
- [Звіт 50 Android-тестів на API 26](https://github.com/salenko93-hash/HolosCalendar-Download/raw/refs/heads/main/Android-API26.xml)

ZIP містить інсталятор у `HolosCalendar/APK/` і PDF у `HolosCalendar/Docs/`.

## Що оновлено

Природні українські формулювання часу, дат і тривалості; часові проміжки; повторення; цілоденні події та нагадування. «За пів години» задає початок через 30 хвилин; «на пів години» — тривалість. Якщо тривалість не названо, поле залишається порожнім. Неоднозначні фрази потребують уточнення. Зміна та видалення стосуються всієї серії.

## Установлення APK

Це debug-збірка, versionCode 10. Підпис збережений від хмарної 3.2.0: установлюйте поверх неї без видалення застосунку. Оновлення зі збереженням старої події та нагадування перевірене. Для APK, зібраного іншим ключем на вашому ПК, потрібен той самий ключ підпису.

Події зберігаються локально. Видалення застосунку стирає їх. Автоматичної синхронізації із зовнішнім календарем немає. Голос розпізнає системний сервіс Android українською; текстове введення та власний календар працюють без мережі.

## Перевірки

**182 JVM-тести, 50 Android-тестів на API 26, без помилок і пропусків; lint без зауважень.** Підпис APK v2 і zipalign перевірені. Окремо перевірене встановлення 4.0.0 поверх 3.2.0 зі збереженням даних. API 35 налаштовано в CI вихідного проєкту, його результат у цій перевірці не підтверджений. Google-розпізнавач, звук та налаштування Redmi додатково перевіряються на телефоні за TESTING.md у ZIP.

Вихідний Git commit збірки: `3e12c3cdb861a051b0b6484f51dbe6db6d112313`. Файли APK, ZIP і PDF перенесено без зміни байтів; контрольні суми — у SHA256SUMS.txt.
