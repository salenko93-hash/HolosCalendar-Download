# Vendored whisper.cpp

Upstream: https://github.com/ggml-org/whisper.cpp
Tag: v1.9.5
Commit: d1be6fde11ac6e0407606b4e42fe72d34add8037
License: MIT (LICENSE)

Unmodified CMakeLists.txt, cmake/, include/, src/, ggml/ copied from the pinned commit. Android builds use only the CPU backend; examples, downloads and network backends are disabled in app/src/main/cpp/CMakeLists.txt. No model weights or recordings are included here.
