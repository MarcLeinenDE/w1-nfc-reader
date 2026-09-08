# W1 NFC Reader v2.1 — Next project chat prompt

Copy the following block into a new project chat.

```text
Wir setzen die Entwicklung von W1 NFC Reader mit Version 2.1.0 fort.

Öffentliches Repository:
MarcLeinenDE/w1-nfc-reader

Bitte zuerst strikt den kanonischen Handoff auf folgendem Branch lesen:

handoff/v2.1.0-current

Zuerst:
1. HANDOFF_LATEST.md
2. CURRENT_STATE.json

Danach die dort angegebene Required-Reading-Reihenfolge vollständig einhalten.

Repository-, GitHub-Actions-, Dokumentations- und Real-Device-Evidence ist autoritativ gegenüber altem Chatwissen.

Aktueller main-Stand:
841630bc7ea8dea2f1fb8d25a4fa41a56d83a6c8

Der öffentliche Release v2.0.0 ist abgeschlossen und unveränderlich.
Tag v2.0.0 niemals verschieben, neu erzeugen oder ersetzen.

Für v2.1.0 ist die kanonische Produktentscheidung:

docs/product-decisions/v2.1-real-time-timeline-and-coverage.md

Wesentliche neue Produktentscheidung:
Die Android-App bekommt global eine auswählbare Zeitbasis:

1. LOCAL / Lokale Zeit (empfohlen und Standard)
   - reale/lokale Zeit ist primär;
   - Zählerzeit bleibt sekundäre technische Information;
   - History-/Statistik-Navigator arbeitet in der dem Zähler zugeordneten lokalen Zeitzone und wird intern auf UTC abgebildet;
   - History verwendet UTC-Overlap-Semantik;
   - Statistik verwendet explizite Coverage-/Full-Containment-Semantik ohne erfundene Teilperioden.

2. METER / Zählerzeit
   - rohe Zähler-/Loggerzeit ist primär;
   - aufgelöste lokale Zeit ist sekundär, sofern zuverlässig verfügbar;
   - History-/Statistik-Navigator interpretiert Eingaben direkt als rohe Zählerzeit;
   - keine LOCAL-Partial-Edge-Warnung nur wegen Zeitversatz;
   - echte Datenlücken, Zählerwechsel und Granularitätsregeln bleiben unverändert relevant.

Die Auswahl ist nur eine Android-Darstellungs-/Navigationspräferenz. Sie darf weder rohe Zählerdaten noch die kanonische UTC-Zeitachse verändern.

Lokale Zeit bedeutet nicht dauerhaft die jeweils aktuelle Handy-Zeitzone.
Stattdessen bekommt jeder physische Zähler eine eigene zugeordnete IANA-Zeitzone, z. B. Europe/Berlin.

Beim ersten erfolgreichen verifizierten Live-/Default-Read eines Zählers ohne bestehende Zeitzonen-Zuordnung soll grundsätzlich die aktuelle Android-IANA-Zeitzone übernommen und mit Provenienz gespeichert werden, z. B. DEVICE_AT_FIRST_VERIFIED_LIVE.
Dafür soll keine GPS-/Standortberechtigung eingeführt werden.

Die Zähler-Zeitzone darf später nicht automatisch wechseln, nur weil der Nutzer reist oder das Handy eine andere Zeitzone nutzt. Sie muss in Einstellungen/Zählerdetails einsehbar und manuell änderbar sein. Backup/Restore soll diese per-Zähler-Zeitzone erhalten und darf sie nicht durch die Zeitzone des Restore-Geräts ersetzen.

Home Assistant ist vom Android LOCAL/METER-Schalter unabhängig:
Ein späterer Home-Assistant-Connector muss immer dieselbe kanonische UTC-Zeitachse verwenden, die auch LOCAL zugrunde liegt. Rohe Zählerzeit darf dort höchstens Diagnose-Metadatum sein, nicht primärer Statistikzeitpunkt.

Besonders wichtig:
Die v2.1-Arbeit darf nicht mit Änderungen am NFC-Transport beginnen.
QalcosonicReader.java, MbusParser.java sowie die physisch validierten NFC/Mailbox/Archive-Traversal-Pfade sind geschützt.

Als ersten Arbeitsschritt bitte anhand des aktuellen öffentlichen Codes und der im Handoff genannten eingefrorenen privaten Research-Evidence exakt ermitteln:

- welche Type-F-Zeitinformationen derzeit dekodiert werden;
- welche raw meter time / Logger-Zeit tatsächlich persistiert wird;
- ob und wo SU/Summer-Time gespeichert wird;
- ob und wo On-Time pro Live- bzw. Archivbeobachtung gespeichert wird;
- welche Android-Acquisition-Time/UTC-Evidence als Real-Time-Anchor vorhanden ist;
- ob heute bereits irgendeine ZoneId-/Timezone-Evidence persistiert wird;
- welche dieser Informationen heute in DB, .qw1backup und CSV erhalten bleiben;
- welche Evidence für einen belastbaren UTC-TimeResolver noch fehlt.

Danach bitte zusätzlich prüfen:

- wie eine per-Zähler-IANA-Zeitzone sauber persistiert und in Backup/Restore erhalten werden sollte;
- wie die globale LOCAL/METER-Präferenz technisch gekapselt werden sollte;
- welche bestehenden History-/Statistics-Abfragen auf rohe Loggerzeit fest verdrahtet sind;
- wie LOCAL- und METER-Navigator-Semantik ohne doppelte oder widersprüchliche Logik umgesetzt werden kann;
- wie bestehende v2.0-Daten migriert bzw. nach einem neuen validen Live-Anker nachträglich zeitlich aufgelöst werden können;
- welche deterministischen Tests für DST, Drift, On-Time-Reset, Zählerwechsel, Zeitzonenänderung, Backup/Restore und Coverage nötig sind.

Erst danach einen konkreten v2.1-Datenmodell-, Migrations-, Backup-/Export-, TimeResolver- und Navigator-Plan festlegen.

Für die Implementierung später einen eigenen Entwicklungsbranch vom aktuellen main anlegen, bevorzugt:

dev/v2.1.0-real-time-timeline

Nicht direkt auf dem Handoff-Branch entwickeln.

Bitte nach dem Einlesen zunächst den vorgefundenen Zustand, die relevante Evidence, offene Risiken/Unklarheiten und den vorgeschlagenen ersten v2.1-Implementierungsschnitt zusammenfassen, bevor Repository-Änderungen vorgenommen werden.
```
