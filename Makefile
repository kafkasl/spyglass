SHELL := /bin/bash

# Load .env if present
-include .env
PHONE_HOST ?= a3
SSH_PORT   ?= 8022
APK        := app/build/outputs/apk/debug/app-debug.apk

export JAVA_HOME := /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME := $(HOME)/Library/Android/sdk
export PATH := $(JAVA_HOME)/bin:$(PATH)

.PHONY: build install test clean status snap

## Build debug APK
build:
	./gradlew assembleDebug
	@echo "APK: $(APK)"

## Run unit tests
test:
	./gradlew testDebugUnitTest

## Build + deploy to phone over SSH
install: build
	scp -P $(SSH_PORT) $(APK) $(PHONE_HOST):~/storage/downloads/spyglass.apk
	ssh -p $(SSH_PORT) $(PHONE_HOST) "termux-open ~/storage/downloads/spyglass.apk"
	@echo "APK sent — approve install on phone"

## Install via adb (if connected)
adb-install: build
	$(ANDROID_HOME)/platform-tools/adb install -r $(APK)

## Check phone status
status:
	@curl -s http://$(PHONE_HOST):4747/status | python3 -m json.tool

## Take a snapshot
snap:
	@curl -s http://$(PHONE_HOST):4747/snap > snap.jpg && echo "Saved: snap.jpg"

## Clean build artifacts
clean:
	./gradlew clean
