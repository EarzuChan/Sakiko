# Sakiko - JVM-Hook-Lösung der nächsten Generation

[![Maven](https://img.shields.io/badge/Maven-EarzuChan-blue)](https://earzuchan.github.io/maven/)
[![Lizenz](https://img.shields.io/badge/License-MIT-green.svg)](https://opensource.org/license/MIT)
[![Release](https://img.shields.io/github/v/release/earzuchan/sakiko.svg)](https://github.com/earzuchan/sakiko/releases)

[English](README_EN.md) | [中文](README.md)

> **Hinweis**
>
> **Nicht geeignet** für den Einsatz in einer **Produktionsumgebung**, potenziell **langsam** oder verursacht **undefiniertes Verhalten**
>
> Der Projektname ist inspiriert von der Figur **Sakiko Togawa** aus **BanG Dream! It's MyGO!!!!!**
>
> **Still GOing, still GOing** (Ein **Meme** über MyGO. Im **chinesischen Kontext** klingt **go** ähnlich wie **hook** (钩), daher kann **ein Wortspiel** gemacht werden)

**Sakiko** ist **ein plattformübergreifendes Hook-Framework**, das für **Kotlin-Entwickler** entwickelt wurde. Es zielt darauf ab, **konsistente plattformübergreifende Hook-Funktionen** durch **eine einheitliche API** bereitzustellen (`HookAPI` + `ModuleAPI`)

## ✨ Funktionen

*   **Für Kotlin entwickelt**: Speziell für Kotlin konzipiert, nutzt vollständig dessen Sprachfunktionen
*   **Multiplattform**: Eine Codebasis unterstützt sowohl Android- als auch Desktop-JVM-Plattformen
*   **Einheitliche API**: Einfache und benutzerfreundliche `HookAPI` und `ModuleAPI`
*   **Automatische Erkennung**: Erkennt und lädt automatisch Moduleinstiegspunkte durch Annotationen, vereinfacht die Konfiguration
*   **Klassenisolierung**: Bietet unabhängige ClassLoader für Module, um Konflikte zu vermeiden

## 🔩 Projektstruktur

*   `api`: Kern-API, enthält `HookAPI` und `ModuleAPI`
*   `core-*`: Plattformspezifische Kernimplementierungen
    *   `core-android`: Verwendet derzeit AliuHook (LSPlant-Wrapper) für die Android-Hook-Implementierung
    *   `core-jvm`: Implementiert Desktop-JVM-Hook, inspiriert von JvmXposed
*   `launcher-*`: Plattformspezifische Launcher, verantwortlich für das Laden von Modulen
    *   `launcher-android`: Android-Bibliothek
    *   `launcher-jvm`: Java-Agent
*   `native-*`: Plattformspezifische native Implementierungen
    *   `native-android`: Noch nicht bereitgestellt
    *   `native-jvm`: Implementiert mit `Kotlin/Native`
*   `plugin`: Gradle-Plugin, noch nicht bereitgestellt
*   `test*`: Test- und Beispielprojekte
    *  `test`: Fungiert als Hook-Ziel (entspricht der `Zielanwendung`)
    *  `test-*`: Dient sowohl als Tests als auch als Beispiele und zeigt, wie der Launcher zum Laden von Modulen auf entsprechenden Plattformen verwendet wird
    *  `test-module`: Beispielmodul (enthält drei Einstiegspunkte: zwei zum Hooken von `test` und einen Unit-Test)

## 🚀 Schnellstart

### 1. Maven-Repository hinzufügen

Fügen Sie die folgenden Maven-Repository-URLs zu Ihrem Projekt hinzu:

```kotlin
maven("https://earzuchan.github.io/Maven/") // Achtung: Das M von Maven muss großgeschrieben werden
maven("https://maven.aliucord.com/releases/") // Dies ist notwendig, um die von `core-api` abhängige AliuHook zu finden
```

Für Bibliotheksversionen siehe bitte die [Release-Seite](https://github.com/Earzuchan/Sakiko/releases)

### 2. Ein Hook-Modul erstellen

1.  Erstellen Sie ein neues Kotlin/JVM-Projekt
2.  Fügen Sie das `api`-Modul als `compileOnly`-Abhängigkeit hinzu: `compileOnly("me.earzuchan.sakiko:api:<version>")`
3.  Implementieren Sie die `SakikoModuleEntry`-Schnittstelle und markieren Sie die Einstiegsklasse mit der `@ExposedSakikoModuleEntry`-Annotation

Sie müssen möglicherweise [diese Dokumentation](https://highcapable.github.io/KavaRef/) lesen, um zu erfahren, wie Sie die KavaRef API verwenden

Beispielcode:

```kotlin
package your.module

import me.earzuchan.sakiko.api.annotations.ExposedSakikoModuleEntry
import me.earzuchan.sakiko.api.hook.hook
import me.earzuchan.sakiko.api.module.SakikoModuleEntry
import me.earzuchan.sakiko.api.utils.SLog
import com.highcapable.kavaref.KavaRef.Companion.resolve

@ExposedSakikoModuleEntry
class TestModuleEntry : SakikoModuleEntry {
    private val TAG = "TestModuleEntry"

    override fun onHook() = encase {
        // `encase` injiziert HookContext
        // Dadurch können Sie auf appClassLoader, String.toClass(cl=appClassLoader) usw. zugreifen
        SLog.info("Beispielmodul-Einstiegspunkt: Beginne mit Hook StaticMethods", TAG)

        val targetClass = "your.target.app.StaticMethods".toClass().resolve()

        // Methode finden und hooken, Rückgabewert ersetzen
        targetClass.firstMethod { name = "method1" }.hook {
            replaceTo(1919810) // Hinweis: Sie können replaceTo nicht zusammen mit before/after verwenden
        }

        // Methode finden (Parametertypen für präziseres Matching angeben; optional, siehe KavaRef-Dokumentation) und hooken, Parameter oder Ergebnisse vor der Ausführung ändern
        targetClass.firstMethod {
            name = "method2"
            parameters(String::class)
        }.hook {
            before {
                SLog.info("Vor method2: ursprüngliches arg[0] = ${args[0]}", TAG)
                val thizObj = instance // Das this-Objekt abrufen
                val a = args[0] as String // Parameter abrufen, bequemere APIs werden in Zukunft bereitgestellt
                
                args[0] = "Von Sakiko geändert" // Parameter ändern, wird wirksam, wenn die ursprüngliche Methode ausgeführt wird
                
                // Hinweis: die folgenden beiden überschreiben sich gegenseitig
                result = "Geänderter Rückgabewert" // Manuelles Setzen des Ergebnisses kehrt früh zurück und überspringt die Ausführung der ursprünglichen Methode
                throwable = Exception() // Exception setzen, diese API wird in Zukunft geändert
                
            }
            
            after {
                SLog.info("Nach method2: result = $result", TAG)
            }
        }
    }
}
```

### 3. Das Modul laden

Verpacken Sie Ihr Modul als Jar (für JVM) oder Dex (für Android)

#### Auf der Android-Plattform

1.  Fügen Sie in Ihrem Android-Projekt `launcher-android` als `implementation`-Abhängigkeit hinzu: `implementation("me.earzuchan.sakiko:launcher-android:<version>")`
2.  Sie können den Code des Moduls in eine Dex-Datei kompilieren (mithilfe des `D8`-Tools, das im Android SDK enthalten ist; wie es verwendet wird, können Sie [hier in der Aufgabenkonfiguration](test-module/build.gradle.kts) nachlesen). Alternativ können Sie das Modul auch direkt in Ihr Android-Projekt kompilieren, wir bieten verschiedene Lademethoden an
3.  Rufen Sie zu einem geeigneten Zeitpunkt (z.B. in `Application.onCreate`) den `Launcher` auf, um das Modul zu laden

```kotlin
import me.earzuchan.sakiko.launcher.Launcher

// In Application oder Activity's onCreate (oder zu einem anderen Zeitpunkt; vorzugsweise bevor gehookter Code ausgeführt wird, um die Hook-Effektivität zu maximieren)
// Angenommen, Ihr `dex`-Dateipfad ist /data/data/your.app/files/module.dex
Launcher.findAndLoadModuleFromDexByPath("/path/to/your/module.dex") // Hinweis: Auf neueren Systemen kann der Zugriff auf beschreibbare Dex vom System eingeschränkt werden
// Unterstützt auch das Laden aus ClassLoader- oder Class-Objekten
// Launcher.findAndLoadModuleFromClassLoader(classLoader)
// Launcher.loadModuleFromClass(YourModuleEntry::class.java)
```

#### Auf der JVM-Plattform

1.  Laden Sie `core-jvm.jar` und `launcher-jvm.jar` herunter (über unser Maven-Repository oder die Release-Seite)
2.  Setzen Sie die Umgebungsvariable `SAKICORE` auf den absoluten Pfad von `core-jvm.jar`
3.  Fügen Sie beim Start der Zielanwendung die folgenden JVM-Parameter hinzu:

```bash
# SAKICORE=/path/to/core-jvm.jar
java -noverify -javaagent:"/path/to/launcher-jvm.jar=/path/to/module1.jar;/path/to/module2.jar" -jar /path/to/target-app.jar
```
*   `-noverify`: Derzeit noch erforderlich; zukünftige Versionen werden eine integrierte Verifikations-Bypass-Funktion enthalten
*   `-javaagent`: Trennen Sie mehrere Modul-Jar-Pfade mit `;`

## 🛠️ Dieses Projekt entwickeln

1.  Klonen Sie dieses Repository
2.  Bauen Sie das Projekt mit Gradle

Häufige Gradle-Aufgaben:
*   `api` verpacken: `./gradlew :api:jar` (möglicherweise muss zuerst `:api:generateBuildConstants` ausgeführt werden, um Build-Konstanten zu generieren)
*   `core-jvm` verpacken: `./gradlew :core-jvm:packageCore`
*   `launcher-jvm` verpacken: `./gradlew :launcher-jvm:packageLauncher`
*   `native-jvm` bauen und integrieren: `./gradlew :core-jvm:buildAndCopyNativeLibs`

## 💬 Community & Support

Kontakt-E-Mail: huascq@gmail.com
Wir freuen uns über jede Form von Beiträgen und Kommunikation:

*   **Stern** für dieses Projekt
*   **Issues** einreichen, um Fehler zu melden oder Vorschläge zu machen
*   **Pull Requests** erstellen, um Code beizutragen
*   **Dem Entwicklungsteam beitreten** (bitte informieren Sie uns per Issue oder kontaktieren Sie uns per E-Mail)
*   **Sponsoring** bereitstellen

## 🙏 Danksagungen

*   [LSPosed](https://github.com/LSPosed/LSPosed): Lernen von dessen Hook-Ausführungsfluss und Funktionsumfang
*   [LSPlant](https://github.com/LSPosed/LSPlant) & [AliuHook](https://github.com/AliuCord/Hook): Aktuelle zugrunde liegende Hook-Implementierung für Android
*   [JvmXposed](https://github.com/cinit/JvmXposed): Lernen von dessen JVM-Hook-Implementierungsansatz
*   [YukiHookAPI](https://github.com/HighCapable/YukiHookAPI): Das API-Design dieses Projekts wurde davon inspiriert
*   [KavaRef](https://github.com/HighCapable/KavaRef): Ausgezeichnete Kotlin-Reflexionsbibliothek, integriert im `api`-Modul, kein zusätzlicher Import erforderlich
*   [ClassGraph](https://github.com/ClassGraph/ClassGraph) & [DexKit](https://github.com/LuckyPray/DexKit): Wird zur Ermittlung von Moduleinträgen auf jeder Plattform verwendet
*   [ASM](https://gitlab.ow2.org/Asm/Asm): Eine leistungsstarke JVM-Bytecode-Manipulationsbibliothek, die verwendet wird, um Hook-Prologe auf der JVM in Zielklassen einzuweben

## 📄 Lizenz

Dieses Projekt ist unter der **MIT**-Lizenz lizenziert