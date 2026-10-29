# HolosCalendar-Download

**ГолосКалендар 4.1.0 — локальний український голос.** Android 8+, 64-бітний arm64/x86_64.

## Завантажити на ПК

### [⬇ Повний проєкт ZIP — APK та PDF всередині](https://github.com/salenko93-hash/HolosCalendar-Download/raw/refs/heads/main/HolosCalendar-4.1.0-project.zip)

Розпакуйте архів і відкрийте папку HolosCalendar в Android Studio. Потрібні JDK 17, SDK 35, NDK 27.2.12479018, CMake 3.22.1. C++-джерела Whisper включені; ваги завантажуються окремо.

- [APK 4.1.0 для телефона](https://github.com/salenko93-hash/HolosCalendar-Download/raw/refs/heads/main/HolosCalendar-4.1.0-debug.apk)
- [PDF: встановлення локального голосу й 87 українських фраз](https://github.com/salenko93-hash/HolosCalendar-Download/raw/refs/heads/main/HolosCalendar-Ukrainian-4.1.0.pdf)
- [Скрипт завантаження моделі на Windows](https://github.com/salenko93-hash/HolosCalendar-Download/raw/refs/heads/main/DOWNLOAD_MODEL.ps1)
- [Контрольні суми SHA-256 4.1.0](https://github.com/salenko93-hash/HolosCalendar-Download/raw/refs/heads/main/SHA256SUMS-4.1.0.txt)
- [Звіт Android API26](https://github.com/salenko93-hash/HolosCalendar-Download/raw/refs/heads/main/Android-API26-4.1.0.xml)
- [Вихідний код 4.1.0](https://github.com/salenko93-hash/HolosCalendar-Download/tree/source-4.1.0), commit `910d37c9f4d03a12f3d98fcf90decf382e61ed15`.

## Як увімкнути власне локальне розпізнавання

1. Відкрийте голос у формі події → «Завантажити модель у браузері». Виберіть багатомовну Tiny (близько 75 МіБ) або Base (близько 142 МіБ). Можна завантажити на ПК скриптом із ZIP і перенести файл на телефон.
2. Натисніть «Вибрати файл моделі», дочекайтеся перевірки й дозвольте мікрофон.
3. «Почати запис» → одна команда українською → «Завершити запис». Перевірте текст і поля перед збереженням. Межа запису — 30 секунд.

Після імпорту цей режим працює без інтернету й без Google. Аудіо перебуває тільки в RAM, не зберігається в аудіофайл і не надсилається на сервер. Системний сервіс доступний як інший явний режим. Це готова Whisper; власного донавчання ваг ще немає. Якість на різних голосах і швидкість Redmi треба перевірити.

## Оновлення та дані

VersionCode 11. Підпис хмарних APK збережений. Якщо поточну 4.0.0 ви зібрали й установили зі свого ПК, зберіть нову з ZIP на тому самому ПК з тим самим debug-ключем. Не видаляйте застосунок для звичайного оновлення: події зберігаються тільки локально. Схема календаря у 4.1.0 не змінюється.

Тривалість без вказівки залишається порожньою; неоднозначні фрази уточнюються. Зміна/видалення стосується всієї серії. Автоматичної синхронізації з зовнішнім календарем немає.

## Перевірено

190 JVM-тестів та 56 Android-тестів на API26. Справжня Base та український WAV, таймаут і конкурентне скасування/закриття перевірені через той самий JNI-код на Linux-хості. Повну модельну транскрипцію на повільному програмному AVD зупинено через тривалий час; це не успішний модельний тест Android. Відмова для некоректного імпорту, завантаження JNI, повернення тексту, скасування та попередні функції календаря перевірені; lint без зауважень. Підпис APK v2 й вирівнювання native ELF/ZIP 16 КіБ перевірені. API35 CI налаштовано, результат тут не підтверджено. Реальний мікрофон, дозволи нових Android і якість на Redmi потребують перевірки на телефоні. Деталі в TESTING.md та docs/OFFLINE_SPEECH.md усередині ZIP.

## Попередня 4.0.0

[ZIP 4.0.0](https://github.com/salenko93-hash/HolosCalendar-Download/raw/refs/heads/main/HolosCalendar-4.0.0-project.zip) · [APK 4.0.0](https://github.com/salenko93-hash/HolosCalendar-Download/raw/refs/heads/main/HolosCalendar-4.0.0-debug.apk). Старі файли збережено.
