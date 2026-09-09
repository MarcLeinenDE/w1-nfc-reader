# W1 NFC Reader v2.1 — Next project chat prompt

Copy the following block into a new project chat.

```text
Wir setzen die Entwicklung von W1 NFC Reader v2.1.0 fort.

Öffentliches Repository:
MarcLeinenDE/w1-nfc-reader

Bitte zuerst strikt den kanonischen Handoff auf folgendem Branch lesen:
handoff/v2.1.0-current

Zuerst:
1. HANDOFF_LATEST.md
2. CURRENT_STATE.json

Danach die dort angegebene Required-Reading-Reihenfolge vollständig einhalten. Repository-, GitHub-Actions- und Real-Device-Evidence ist autoritativ gegenüber altem Chatwissen.

Aktiver Entwicklungsbranch:
dev/v2.1.0-real-time-timeline

Aktueller Entwicklungs-Head:
50852719e9e93e7a4f921e95156663920756fdc9

Aktueller funktionaler Code-/Physical-Validation-Checkpoint:
e4f458d9ff47a088926772a13d9bf19946f4bc48

Draft-PR:
#22 — WIP: add v2.1 real-time timeline foundation

Der v2.1-Zeitmodell-/Query-/UI-Slice ist implementiert und CI-grün. Nicht erneut von der Architekturplanung anfangen.

Besonders wichtig umgesetzt sind:
- global LOCAL / METER, LOCAL als Standard;
- stabile per-Zähler-IANA-Zeitzone mit Provenienz;
- verifizierte Live-Zeitanker;
- occurrence-safe Archividentität für wiederholte rohe Zeitstempel;
- kanonische UTC-Projektion ohne Überschreiben roher Zählerzeit;
- LOCAL-History/Statistics über die aufgelöste UTC-Zeitachse;
- LOCAL-Live verwendet für Zeitraum/Sortierung den realen Android-Akquisitionszeitpunkt;
- METER-Live verwendet für Zeitraum, Sortierung und Vorgänger tatsächlich `meter_time` und nicht nur eine andere Beschriftung;
- Live-Zeilen ohne nutzbare Zählerzeit werden im begrenzten METER-Zeitraum nicht über Telefonzeit hineingeraten;
- DST-sichere LOCAL-Bereiche;
- 23/24/25-Stunden-Statistiknenner;
- Zählerzeit als sekundäre Evidence in LOCAL;
- aufgelöste lokale Zeit als sekundäre Evidence in METER, wenn vertrauenswürdig;
- Zeitzonenanzeige/-korrektur in Zählerdetails;
- Live-Zählerzeit wird in Overview, History und Zählerdetails locale-aware formatiert; der gespeicherte Rohwert bleibt unverändert;
- bei deutscher UI wird z. B. `2026-09-09 14:05` als `09.09.2026 · 14:05` dargestellt;
- explizite LOCAL-Warnungen bei fehlender Zone, nicht sicher auflösbarer Zeitgrenze oder fehlender Archiv-Zeitevidence;
- schema-3 Backup/Restore mit Zone, Ankern, occurrence identity und globaler Zeitbasis;
- sechs Sprachen.

Die NFC-/Mailbox-/Archiv-Traversal-Pfade wurden dabei nicht verändert. QalcosonicReader.java, MbusParser.java und die physisch validierten Protokollpfade bleiben geschützt.

Der nächste Gate ist NICHT weitere Architekturarbeit, sondern die begrenzte physische Validierung aus:
docs/V2_1_REAL_DEVICE_VALIDATION.md

Exakter physischer Testkandidat:
- Commit e4f458d9ff47a088926772a13d9bf19946f4bc48
- CI 34320334896: SUCCESS
- 346 Tests: PASS
- i18n: 227 Keys / 6 Locales: PASS
- Artifact w1-nfc-reader-debug
- Artifact ID 10091656517
- Artifact digest sha256:a7ad4eb0c09a331836a508cd3e3e96210fa8966710502a0ab8a3d7feb1eae775
- APK SHA-256 44d8896f92b10c586eb4ae51a0c05ff7118f87c8656e43ba14393ca11487e95a

Der vorherige Kandidat 2191147a96db59b2857af7526fa07826bffc8967 / CI 34316796172 ist ÜBERHOLT und darf nicht mehr zur physischen v2.1-Akzeptanz verwendet werden.

Der aktuelle Dev-Head 50852719e9e93e7a4f921e95156663920756fdc9 ist nur Dokumentation auf dem funktionalen Kandidaten und hat ebenfalls vollständig grünes CI 34320664008.

Bitte beim Weiterarbeiten:
1. den bestehenden Stand verifizieren, nicht neu entwerfen;
2. PR #22 Draft lassen, solange der Real-Device-Gate offen ist;
3. VersionName 2.0.0 / VersionCode 43 noch NICHT auf 2.1.0 bumpen;
4. zuerst die physische A–F-Prüfung begleiten/auswerten;
5. dabei LOCAL/METER Live-Semantik und deutsche locale-aware Zählerzeit ausdrücklich mitprüfen;
6. erst nach erfolgreicher physischer Akzeptanz Version, Changelog/Release Notes und signed Release Candidate vorbereiten;
7. v2.0.0 und v1.0.0 Tags/Releases niemals verschieben, neu erzeugen oder ersetzen;
8. keine privaten Meter-IDs, Verbrauchsdaten, Captures oder NFC-Rohdaten öffentlich committen.

Wenn der Nutzer die physische Prüfung noch nicht durchführen kann, darf Release-/Handoff-Dokumentation weiter vorbereitet werden, aber kein stabiler v2.1.0 Release-Kandidat vorgetäuscht oder veröffentlicht werden.
```
