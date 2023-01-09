
PACKAGE = xdsopl.robot36
GRADLE = JAVA_HOME=$(HOME)/Android/Studio/jre/ ./gradlew
ADB = adb

.PHONY: all

all:
	$(GRADLE) installDebug
	$(ADB) shell am start -n $(PACKAGE)/$(PACKAGE).MainActivity

.PHONY: build

build:
	$(GRADLE) assembleDebug

.PHONY: install

install:
	$(ADB) -d install app/build/outputs/apk/debug/app-debug.apk

.PHONY: remove

remove:
	$(ADB) uninstall $(PACKAGE)

.PHONY: start

start:
	$(ADB) shell am start -n $(PACKAGE)/$(PACKAGE).MainActivity

