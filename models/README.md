# Моделі українського мовлення

Ваги не включені до Git/ZIP/APK. На Windows із кореня проєкту:

```powershell
powershell -ExecutionPolicy Bypass -File .\DOWNLOAD_MODEL.ps1 -Model tiny
```

Або виберіть `-Model base` (більше пам’яті та обчислень). Скрипт завантажує тільки дані моделі з офіційного сховища, перевіряє опублікований SHA-1 і зберігає файл у цій папці. Політика виконання змінюється лише для цього запуску PowerShell.

[Whisper Tiny — багатомовна](https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin?download=true), близько 75 МіБ.
[Whisper Base — багатомовна](https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin?download=true), близько 142 МіБ.

Перенесіть повний файл на телефон, відкрийте локальний голос у ГолосКалендарі й натисніть «Вибрати файл моделі». Англомовні `.en`, квантовані та довільні інші моделі поки не підтримуються. Після успішного імпорту оригінал у «Завантаженнях» можна прибрати самостійно; застосунок має приватну копію.

Опубліковані контрольні суми: [whisper.cpp models/README.md](https://github.com/ggml-org/whisper.cpp/blob/d1be6fde11ac6e0407606b4e42fe72d34add8037/models/README.md). Ліцензія OpenAI Whisper — MIT; копія є в `app/src/main/assets/licenses/`.
