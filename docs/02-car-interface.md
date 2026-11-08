# 02. Car Interface

## OBD pins (SAE J1962)

| Pin | Function | Use |
|-----|----------|-----|
| 4 | Chassis ground | Power and TVS return |
| 5 | Signal ground | Joined to GND on the PCB |
| 6 / 14 | CAN-H / CAN-L | Unpopulated CAN footprint |
| 7 | K-line | K-line interface |
| 16 | +12 V permanent | Supply, fused 2 A on the board |

Only these six cavities are populated. Ground returns only through pins 4 and 5; never add a separate chassis ground.

## K-line electrical (ISO 9141-2 / ISO 14230-1)

| Parameter | Value |
|-----------|-------|
| Topology | Single wire, open collector to ground, pulled up to VBAT |
| Tester pull-up | 510 Ω to VBAT |
| Driver | 2N7002K, 47 Ω series; VOL < 0.15 V (limit 0.2 x VBAT) |
| Receiver | LMV331 comparator, threshold 0.488 x VBAT, hysteresis about ±3 % VBAT |
| Bit rate | 10,400 bit/s, 8N1, bit time 96.15 µs |
| Echo | Every transmitted byte is read back; a mismatch aborts the request |

Discrete, not a LIN transceiver: their 6 to 20 ms TXD-dominant timeout cuts the 25 ms fast-init pulse short.

## Timings

| Symbol | Meaning | Value |
|--------|---------|-------|
| W5 | Bus idle before init | ≥ 300 ms |
| TiniL | Fast-init low pulse | 25 ms ± 1 ms |
| TWuP | Fast-init wake-up pattern | 50 ms ± 1 ms |
| 5-baud init | Address byte at 5 bit/s | 2.0 s |
| W1 | ECU sync byte 0x55 after address | 60 to 300 ms |
| W4 | Tester inverts KB2 after | 25 to 50 ms |
| P1 | ECU inter-byte | 0 to 20 ms |
| P2 | ECU response start | 25 to 50 ms; 5 s after 7F xx 78 (response pending) |
| P3 | Tester new request after response | 60 ms (min 55 ms) |
| P3max | Session timeout | 5 s; TesterPresent (3E 01) after 2 s of silence |
| P4 | Tester inter-byte | 5 ms |

## Message format (ISO 14230-2)

```
[FMT][TGT][SRC][LEN?][SID][DATA...][CS]
FMT  0x80 | len  physical addressing, 0xC0 | len  functional
TGT  0x33 functional OBD, or the ECU physical address found by the probe
SRC  0xF1 tester
CS   8-bit sum of all preceding bytes, modulo 256
```

Fast-init StartCommunication: `C1 33 F1 81 66`, positive response `83 F1 xx C1 KB1 KB2 CS`. RPM request: `C2 33 F1 01 0C F3`.

## Protocol probe

Runs on the first engine start and on demand (app: Garage, LoganLink module, "Detect again"). 3 s between attempts so the ECU session clears:

1. ISO 14230 fast init, functional address 0x33.
2. ISO 14230 5-baud init, address 0x33.
3. ISO 9141-2 5-baud init, address 0x33 (key bytes 08 08 or 94 94).
4. ISO 14230 fast init to each candidate physical ECU address, confirmed with `1A 80` (ReadEcuIdentification).

On success it reads PID support bitmaps (01 00, 20, 40, 60), VIN (mode 09 PID 02, if supported), ECU ID (`1A 80`), and dumps
local-ID blocks `21 01` to `21 0F` raw into the log. The profile is stored in NVS. Re-probe only on command or after repeated
connection failures. CAN is not probed.

## J1979 PIDs

The scheduler requests the most overdue supported PID. One request takes 70 to 110 ms, so RPM and speed update at 4 to 5 Hz.

| PID | Signal | Decoding | Period |
|-----|--------|----------|--------|
| 0C | RPM | ((A x 256) + B) / 4 | 200 ms |
| 0D | Vehicle speed | A km/h | 200 ms |
| 0B | MAP | A kPa | 500 ms |
| 11 | Throttle | A x 100 / 255 % | 500 ms |
| 04 | Engine load | A x 100 / 255 % | 1 s |
| 0E | Ignition advance | A / 2 - 64 ° BTDC | 1 s |
| 05 | Coolant | A - 40 °C | 2 s |
| 0F | Intake air | A - 40 °C | 2 s |
| 06 | Short-term fuel trim | (A - 128) x 100 / 128 % | 2 s |
| 07 | Long-term fuel trim | (A - 128) x 100 / 128 % | 5 s |
| 01 | MIL and DTC count | bit 7 of A = MIL, bits 0 to 6 = count | 30 s |

Fault codes: mode 03 (stored) and 07 (pending), or KWP 0x18. Clear: mode 04 or KWP 0x14, guarded (below).

## Local IDs (`lidmap`)

Many K-line ECUs of this era answer few J1979 PIDs but expose data blocks through `21 xx` (ReadDataByLocalIdentifier). The config
holds a `lidmap` table; each entry: local ID, byte offset, size, signed flag, scale, offset, target field (RPM, speed, coolant, IAT,
MAP, TPS, advance, STFT, LTFT). Mapped blocks are polled at 4 Hz and fill the same live fields as the PIDs.

To build a map: record the probe dumps during known actions (idle then 2,000 rpm; cold then warm), find the byte that tracks each
signal, derive the scale against the cluster or a scan tool. No community definitions are shipped (accuracy unverified).

## Vehicle guard (read-only)

`vguard_allow()` sits between all firmware code and the K-line transmitter. Default deny; no command path sends arbitrary bytes.

| Service | Status |
|---------|--------|
| J1979 modes 01, 02, 03, 07, 09 | Allowed |
| KWP 0x1A, 0x21, 0x17, 0x18, 0x3E, 0x81, 0x82 | Allowed |
| KWP 0x10 StartDiagnosticSession | Default session (0x81) only |
| Mode 04 / KWP 0x14 clear DTCs | Only when armed by an owner two-step command, valid 5 s, engine off (RPM 0, VBAT < 13.0 V) |
| Everything else, including 0x27, 0x2E, 0x3B, 0x2F, 0x30, 0x31, 0x34 to 0x37, 0x11, mode 08 | Denied, counted, reported in GET_INFO |

## Errors and other testers

- Three consecutive timeouts, checksum or echo errors mark the ECU offline: back off 2 s and reconnect; after 3 failed connects back
  off 30 s.
- A negative response other than 78 marks that PID unsupported for the session.
- K-line bytes the dongle did not cause, outside its reply window, mean another tester is connected: the dongle stays silent for
  10 min. It occupies the only socket, so unplug it when a workshop needs the port.

## Measure on each car

| Item | How |
|------|-----|
| Protocol, init type, ECU address, PIDs, VIN | Probe result in the app (Garage, LoganLink module). Older calibrations support few PIDs and no VIN |
| Local-ID layout and scaling | Raw probe dumps, then build the `lidmap` |
| Pin 16 is permanent +12 V | Multimeter with the key out ([09](09-install-and-test.md)) |
| Socket location and clearance | Measure ([05](05-enclosure.md)) |
