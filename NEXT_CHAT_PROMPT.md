# W1 NFC Reader v2.1 — Next project chat prompt

Copy the following block into a new project chat.

```text
Wir setzen die physische Validierung von W1 NFC Reader v2.1.0 fort.

Öffentliches Repository:
MarcLeinenDE/w1-nfc-reader

Bitte zuerst strikt den kanonischen Handoff lesen:
Branch: handoff/v2.1.0-current
1. HANDOFF_LATEST.md
2. CURRENT_STATE.json

Danach die dort angegebene Required-Reading-Reihenfolge einhalten. Repository-, GitHub-Actions- und Real-Device-Evidence ist autoritativ gegenüber altem Chatwissen.

Aktiver Entwicklungsbranch:
dev/v2.1.0-real-time-timeline

Aktueller Dev-Head:
404dc90c1f974fb145d80cce23508825d8de16b9
CI 34362370166: SUCCESS
Dieser Head ist Dokumentation-only auf dem funktionalen Kandidaten.

Exakter physischer Testkandidat:
- Commit e4f458d9ff47a088926772a13d9bf19946f4bc48
- CI 34320334896: SUCCESS
- 346 Tests: PASS
- i18n: 227 Keys / 6 Locales: PASS
- Artifact w1-nfc-reader-debug
- Artifact ID 10091656517
- Artifact digest sha256:a7ad4eb0c09a331836a508cd3e3e96210fa8966710502a0ab8a3d7feb1eae775
- APK SHA-256 44d8896f92b10c586eb4ae51a0c05ff7118f87c8656e43ba14393ca11487e95a

Draft-PR:
#22 — WIP: add v2.1 real-time timeline foundation
PR bleibt Draft bis der Real-Device-Gate abgeschlossen ist.

WICHTIG: Wir sind bereits mitten in der physischen A–F-Prüfung aus docs/V2_1_REAL_DEVICE_VALIDATION.md. Nicht wieder bei Architektur oder Installation anfangen.

Bisheriger Real-Device-Stand vom 10.09.2026:

Abschnitt A:
- Debug-Kandidat ist installiert und nutzbar.
- LOCAL wird verwendet.
- Kein Installations-/Koexistenzproblem gemeldet.
- Nur ein Punkt wurde im Chat nicht ausdrücklich bestätigt: ob bei frischem Debug-App-State wirklich automatisch 'Lokale Zeit' vorausgewählt war. Das kann ohne weiteren NFC-Kontakt nachgeprüft werden.

Abschnitt B:
- normaler geschützter Live-NFC-Read am echten W1: PASS
- deutsche Formatierung der Live-Zählerzeit: PASS
- Zählerdetails zeigt Europe/Berlin: PASS
- Provenienz der Zeitzone = automatische Zuweisung beim ersten verifizierten Live-/Gerätekontext: PASS
- Zählerzeit in Zählerdetails deutsch locale-aware: PASS
- kein unbeabsichtigter automatischer History-Sync nach normalem Live-Read gemeldet: PASS

Kleiner UI-Fund:
- Bei 'Am Handy ausgelesen' fehlt in Zählerdetails der mittige Punkt zwischen Datum und Uhrzeit.
- Zeitinhalt/Locale sind korrekt.
- Als kosmetischen UI-Konsistenzfehler notieren, NICHT den physischen Kandidaten mitten im Test allein deswegen austauschen.

NÄCHSTER SCHRITT IST ABSCHNITT C — LOCAL HISTORY / STATISTICS.
Der Nutzer möchte im neuen Chat Screenshots vom Handy schicken können. Screenshots direkt analysieren und den Test anhand der sichtbaren UI begleiten.

Bitte in LOCAL-Modus prüfen:
1. History → Live: reale Auslesezeit primär, Zählerzeit sekundär wenn vorhanden.
2. History → Stunde / Tag / Monat: aufgelöste lokale Zeit primär, rohe Zähler-/Loggerzeit sekundär.
3. Reihenfolge chronologisch plausibel; keine falsche Warnung wegen fehlender Zeitzone.
4. Falls im Debug-App-State noch keine Archivdaten vorhanden sind, nur den normalen expliziten History-Sync verwenden; keine experimentellen NFC-Optionen.
5. Danach Statistics für einen begrenzten Zeitraum mit Daten: Werte/Coverage plausibel, kein künstlicher Nullverbrauch wegen fehlender LOCAL-Projektion.

Danach weiter mit:
D. LOCAL ↔ METER inklusive Overview/Live-History/Archiv und echter METER-Zeitbasis;
E. ungültige Zeitzone im Edit-Dialog testen, ohne produktive Zone erfolgreich zu verändern;
F. abschließender normaler Live-NFC-Regressionstest.

Geschützte Regeln:
- normaler NFC-Kontakt = schneller Live/default read only;
- History nur nach expliziter Benutzeraktion;
- keine neuen NFC-Kommandos;
- QalcosonicReader.java, MbusParser.java und validierte Mailbox-/Archiv-Traversierung nicht verändern;
- Terminal-/secure-overlap-Logik bleibt autoritativ, keine festen 1480/1130/36 Sync-Grenzen;
- keine privaten Meter-IDs, Verbrauchsdaten oder Captures öffentlich committen.

Release-Regel:
- VersionName/VersionCode noch nicht auf 2.1.0 bumpen;
- PR #22 Draft lassen;
- erst A–F physisch abschließen, dann Version/Changelog/signed RC vorbereiten.
```
