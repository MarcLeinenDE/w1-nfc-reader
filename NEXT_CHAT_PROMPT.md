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
2128ef5946d1a7c4886be68758f01113a96dc36e

Aktueller funktionaler Code-Checkpoint:
2191147a96db59b2857af7526fa07826bffc8967

Draft-PR:
#22 — WIP: add v2.1 real-time timeline foundation

Der v2.1-Zeitmodell-/Query-/UI-Slice ist inzwischen implementiert und CI-grün. Nicht erneut von der Architekturplanung anfangen.

Besonders wichtig umgesetzt sind:
- global LOCAL / METER, LOCAL als Standard;
- stabile per-Zähler-IANA-Zeitzone mit Provenienz;
- verifizierte Live-Zeitanker;
- occurrence-safe Archividentität für wiederholte rohe Zeitstempel;
- kanonische UTC-Projektion ohne Überschreiben roher Zählerzeit;
- LOCAL-History/Statistics über die aufgelöste UTC-Zeitachse;
- METER als unveränderter raw/floating Kompatibilitätspfad;
- DST-sichere LOCAL-Bereiche;
- 23/24/25-Stunden-Statistiknenner;
- Zählerzeit als sekundäre Evidence in LOCAL;
- Zeitzonenanzeige/-korrektur in Zählerdetails;
- explizite LOCAL-Warnungen bei fehlender Zone, nicht sicher auflösbarer Zeitgrenze oder fehlender Archiv-Zeitevidence;
- schema-3 Backup/Restore mit Zone, Ankern, occurrence identity und globaler Zeitbasis;
- sechs Sprachen.

Die NFC-/Mailbox-/Archiv-Traversal-Pfade wurden dabei nicht verändert. QalcosonicReader.java, MbusParser.java und die physisch validierten Protokollpfade bleiben geschützt.

Der nächste Gate ist NICHT weitere Architekturarbeit, sondern die begrenzte physische Validierung aus:
docs/V2_1_REAL_DEVICE_VALIDATION.md

Exakter physischer Testkandidat:
- Commit 2191147a96db59b2857af7526fa07826bffc8967
- CI 34316796172: SUCCESS
- Artifact w1-nfc-reader-debug
- Artifact ID 10090392521
- Artifact digest sha256:2f9f873bd426f3d38e9eb67939e52208d96eaa2d99bac8170d61a032d3192441
- APK SHA-256 773ef13392dc1eb6bb707138d7fb4f11087ad42ab3c08dff3e31430b459262e7

Bitte beim Weiterarbeiten:
1. den bestehenden Stand verifizieren, nicht neu entwerfen;
2. PR #22 Draft lassen, solange der Real-Device-Gate offen ist;
3. VersionName 2.0.0 / VersionCode 43 noch NICHT auf 2.1.0 bumpen;
4. zuerst die physische A–F-Prüfung begleiten/auswerten;
5. erst nach erfolgreicher physischer Akzeptanz Version, Changelog/Release Notes und signed Release Candidate vorbereiten;
6. v2.0.0 und v1.0.0 Tags/Releases niemals verschieben, neu erzeugen oder ersetzen;
7. keine privaten Meter-IDs, Verbrauchsdaten, Captures oder NFC-Rohdaten öffentlich committen.

Wenn der Nutzer die physische Prüfung noch nicht durchführen kann, darf Release-/Handoff-Dokumentation weiter vorbereitet werden, aber kein stabiler v2.1.0 Release-Kandidat vorgetäuscht oder veröffentlicht werden.
```
