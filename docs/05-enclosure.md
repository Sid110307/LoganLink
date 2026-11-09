# 05. Enclosure

Plug nose into the car, board flat behind it: 54.8 x 43.8 mm, 27.6 mm out from the socket face, so a knee or glovebox lid pushes
it straight in.

`mechanical/dongle_freecad.py` generates everything. Run it in FreeCAD (Macro > Execute, or the MCP bridge); it writes
`LoganLink-dongle.FCStd`, `dongle.step` and the STLs next to itself. Sizes are in the `P` dict at the top of the script (copied into the
`Params` sheet for reference).

## Parts

| Part | File | Print |
|------|------|-------|
| Front shell | `dongle-front.stl` | Face down, no supports |
| Retainer | `dongle-retainer.stl` | Plate down, no supports |
| Rear shell | `dongle-rear.stl` | Roof down (already flipped), no supports |
| Light pipe | `dongle-pipe.stl` | Clear PETG, upright |
| Screws | 4 x M2 x 18 pan head self-tapping for plastic | |

Shells and retainer in ASA (a parked car's glovebox passes 70 °C; never PLA). 0.2 mm layers, 4 walls, 30 % infill.

## Stack (from the car outwards)

| z (mm) | Part |
|--------|------|
| 0 to 2 | Front shell face; the plug nose passes through it |
| 2 to 5 | Plug flange, on the inside of the face and the four boss stubs |
| 5 to 7 | Retainer plate, clamping the flange |
| 5 to 13 | Plug body behind the flange, through the retainer opening |
| 7 to 18 | Retainer corner posts |
| 13 to 18 | Wire space, crimp pins to board |
| 18 to 19.6 | PCB |
| 19.6 to 26.1 | Parts (6 mm max) |
| 26.1 to 27.6 | Rear shell roof |

Plug-in force goes plug, retainer, posts, screws, front shell; pull-out force goes flange to front face. Neither passes through the
board or wires.

## Assembly

1. Solder six 25 mm lengths of 22 AWG wire into J1 from the bottom.
2. Crimp a 57964-9702 pin on each free end and insert: J1.1 to cavity 16, J1.2 to 4, J1.3 to 5, J1.4 to 7. J1.5 and J1.6
   (cavities 6 and 14) only if CAN is fitted.
3. Push the plug nose out through the front shell face from inside, flange against the face.
4. Drop the retainer over the plug body onto the flange.
5. Lay the board on the retainer posts; drive the four screws through board and posts into the front shell bosses. Snug, not tight.
6. Press the light pipe into the rear shell from inside.
7. Snap the rear shell on (a groove on each long side of the lip catches a bump on the front shell).

Pop the rear shell off to flash over USB. Normal updates go over the air.

## Fit check

Print the front shell and retainer first and measure the real Molex housing:

| Param | Default | Measure |
|-------|---------|---------|
| `plug_w`, `plug_w_bot`, `plug_h` | 39, 33, 17 | Nose trapezoid: top width, bottom width, height |
| `flange_t`, `flange_ext` | 3, 1.2 | Flange thickness and overhang past the nose |
| `plug_body` | 8 | Body depth behind the flange |
| `gap` | 0.3 | Increase if the plug is tight in the face opening |

Edit `P`, rerun, and check the screws reach the bosses (`pcb z` in the output).
