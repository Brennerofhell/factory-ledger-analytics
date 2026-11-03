# Versionierung

Schema: **`MAJOR.MINOR.PATCH`** (SemVer), Git-Tag `vMAJOR.MINOR.PATCH`.

| Änderung | Erhöht | Beispiel | Ältere Notation |
|---|---|---|---|
| Fehlerbehebung, kleine Korrektur, kein neues Feature | PATCH | 1.0.0 -> **1.0.1** | "v1.01" |
| Neues Feature, abwärtskompatibel (Config/Speicherformat/Netzwerk bleiben lesbar) | MINOR | 1.0.1 -> **1.1.0** | "v1.1" |
| Inkompatible Änderung (Speicherformat ohne Migration, Netzwerkprotokoll, entfernte/umbenannte Config-Schlüssel) | MAJOR | 1.1.0 -> **2.0.0** | "v2.0" |

Die Version steht an **einer** Stelle: `mod_version` in `gradle.properties`. Daraus entstehen Dateiname der JAR, `neoforge.mods.toml` und Anzeige im Spiel.

## Ablauf pro Release
1. `CHANGELOG.md`: Abschnitt `[Unreleased]` in `[x.y.z] - Datum` umbenennen, neuen leeren `[Unreleased]` darüber setzen.
2. `mod_version` in `gradle.properties` erhöhen.
3. `./gradlew build` (Tests müssen grün sein).
4. Commit `Release x.y.z`, dann `git tag -a vx.y.z -m "..."`, `git push --follow-tags`.
5. Die gebaute JAR lokal nach `releases/` kopieren (JARs liegen nicht im Git; `releases/` ist die lokale Archivablage).

## Kompatibilität
- Speicherformat hat eine eigene Nummer (`HistoryCodec.VERSION`). Eine Mod-Änderung am Format erhöht MINOR (mit Migration) oder MAJOR (ohne), und alte Dateien werden nie überschrieben, wenn sie nicht lesbar sind.
- Das Netzwerkprotokoll (`registrar("1")`) wird bei inkompatiblen Paketänderungen mitgezählt (MAJOR).
