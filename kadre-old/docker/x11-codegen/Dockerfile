# X11 FFM binding codegen environment for Kadre.
#
# Builds the kextract submodule and generates Kotlin Panama FFM bindings
# from X11 system headers, inside a Linux container.
#
# Build:  docker build -t kadre-x11-codegen docker/x11-codegen
# Run:    see scripts/gen-x11-bindings.sh
FROM eclipse-temurin:25-jdk-noble

# LLVM/libclang (for kextract) + X11 development headers + C toolchain
RUN apt-get update && apt-get install -y --no-install-recommends \
        clang-18 llvm-18-dev libclang-18-dev \
        libx11-dev libxext-dev libxcomposite-dev libxfixes-dev \
        libxrender-dev libxrandr-dev libxi-dev libxtst-dev \
        libxcursor-dev libxdamage-dev libxinerama-dev \
        gcc libc6-dev \
        patch \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/*

ENV JDK_HOME=/opt/java/openjdk \
    LLVM_HOME=/usr/lib/llvm-18

WORKDIR /work
