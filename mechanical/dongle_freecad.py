import os
import FreeCAD as App
import Part
import MeshPart
from FreeCAD import Vector as V

OUT = os.path.dirname(os.path.abspath(__file__)) if "__file__" in globals() else os.getcwd()

P = dict(
    plug_w=39.0, plug_w_bot=33.0, plug_h=17.0, plug_r=2.5, plug_out=16.0,
    flange_t=3.0, flange_ext=1.2, plug_body=8.0,
    pcb_w=38.0, pcb_l=46.0, pcb_t=1.6, comp_top=7.0, under=2.0, ant_len=8.0,
    wall=1.5, gap=0.3, r_out=3.0,
    hook_w=5.0, hook_len=5.0, hook_t=1.0, barb=0.6,
)

p = type("P", (), P)
z_pcb_bot = p.wall + p.under
z_pcb_mid = z_pcb_bot + p.pcb_t / 2
hb_in = p.under + p.pcb_t + p.comp_top
wb_in = p.pcb_w + 2 * p.gap
ha_in = p.plug_h + 2 * p.flange_ext + 2 * p.gap
wa_in = p.plug_w + 2 * p.flange_ext + 2 * p.gap
la = p.wall + p.flange_t + p.plug_body
pcb_x0 = la + 3.0
l_tot = pcb_x0 + p.pcb_l + p.gap + p.wall
y_c = max(wa_in, wb_in) / 2 + p.wall
zA = z_pcb_mid
zB = p.wall + hb_in / 2


def rrect(x, w, h, zc, r):
    r = min(r, w / 2 - 0.01, h / 2 - 0.01)
    y0, y1, z0, z1 = y_c - w / 2, y_c + w / 2, zc - h / 2, zc + h / 2
    k = r * (1 - 0.70710678)
    e = [
        Part.LineSegment(V(x, y0 + r, z0), V(x, y1 - r, z0)).toShape(),
        Part.Arc(V(x, y1 - r, z0), V(x, y1 - k, z0 + k), V(x, y1, z0 + r)).toShape(),
        Part.LineSegment(V(x, y1, z0 + r), V(x, y1, z1 - r)).toShape(),
        Part.Arc(V(x, y1, z1 - r), V(x, y1 - k, z1 - k), V(x, y1 - r, z1)).toShape(),
        Part.LineSegment(V(x, y1 - r, z1), V(x, y0 + r, z1)).toShape(),
        Part.Arc(V(x, y0 + r, z1), V(x, y0 + k, z1 - k), V(x, y0, z1 - r)).toShape(),
        Part.LineSegment(V(x, y0, z1 - r), V(x, y0, z0 + r)).toShape(),
        Part.Arc(V(x, y0, z0 + r), V(x, y0 + k, z0 + k), V(x, y0 + r, z0)).toShape(),
    ]
    return Part.Wire(e)


def prism(x0, length, w, h, zc, r):
    return Part.Face(rrect(x0, w, h, zc, r)).extrude(V(length, 0, 0))


def body(xa, lA, wa, ha, xb, lB, wb, hb, r):
    a = prism(xa, lA, wa, ha, zA, r)
    b = prism(xb, lB, wb, hb, zB, r)
    t = Part.makeLoft([rrect(xa + lA, wa, ha, zA, r), rrect(xb, wb, hb, zB, r)], True, False)
    return a.fuse(t).fuse(b).removeSplitter()


def shroud(extra, x0, length):
    w1, w0, h = p.plug_w + 2 * extra, p.plug_w_bot + 2 * extra, p.plug_h + 2 * extra
    pts = [V(x0, y_c - w0 / 2, zA - h / 2), V(x0, y_c + w0 / 2, zA - h / 2),
           V(x0, y_c + w1 / 2, zA + h / 2), V(x0, y_c - w1 / 2, zA + h / 2)]
    s = Part.Face(Part.makePolygon(pts + [pts[0]])).extrude(V(length, 0, 0))
    edges = [e for e in s.Edges if abs((e.Vertexes[1].Point - e.Vertexes[0].Point).normalize().x) > 0.99]
    return s.makeFillet(p.plug_r, edges)


def box(l, w, h, x, y, z):
    return Part.makeBox(l, w, h, V(x, y, z))


outer = body(0, la, wa_in + 2 * p.wall, ha_in + 2 * p.wall, la + 4, l_tot - la - 4,
             wb_in + 2 * p.wall, hb_in + 2 * p.wall, p.r_out)
inner = body(p.wall, la - p.wall, wa_in, ha_in, la + 4, l_tot - la - 4 - p.wall,
             wb_in, hb_in, max(0.6, p.r_out - p.wall))

shell = outer.cut(inner)
for cut in (
    shroud(p.gap, -1, p.wall + 2),
    shroud(p.flange_ext + p.gap, p.wall, p.flange_t + p.gap),
    shroud(p.gap, p.wall + p.flange_t, p.plug_body + 1),
    box(10.0, p.wall + 2, 3.6, pcb_x0 + 15, y_c + wb_in / 2 - 1, z_pcb_bot + p.pcb_t - 0.2),
    Part.makeCylinder(1.55, 20, V(pcb_x0 + 28, y_c - 8, z_pcb_bot)),
    Part.makeCylinder(0.8, 20, V(pcb_x0 + 28, y_c + 8, z_pcb_bot)),
):
    shell = shell.cut(cut)

lower = box(200, 200, 50 + z_pcb_bot, -50, -50, -50)
upper = box(200, 200, 200, -50, -50, z_pcb_bot)
hook_x = [la + 6, l_tot - p.ant_len - 8]

extras = []
for x in (pcb_x0 + 10, l_tot - p.ant_len - 4):
    for s in (-1, 1):
        extras.append(box(4, 3, p.under + 0.01, x - 2, y_c + s * (p.pcb_w / 2 - 1.5) - 1.5, p.wall - 0.01))
eye = box(7, 10, 3.2, l_tot - 9, y_c - 5, -3).cut(box(4, 12, 1.6, l_tot - 7.5, y_c - 6, -2.2))
extras.append(eye)
for x in hook_x:
    for s in (-1, 1):
        yi = y_c + s * (wb_in / 2 - p.hook_t / 2 - 0.05)
        extras.append(box(p.hook_w, p.hook_t, p.hook_len, x - p.hook_w / 2, yi - p.hook_t / 2, z_pcb_bot - 0.01))
        yb = yi + p.hook_t / 2 if s > 0 else yi - p.hook_t / 2 - p.barb
        extras.append(box(p.hook_w, p.barb, 1.0, x - p.hook_w / 2, yb, z_pcb_bot + p.hook_len - 1.5))

bottom = shell.common(lower)
for e in extras:
    bottom = bottom.fuse(e)
bottom = bottom.removeSplitter()

top = shell.common(upper)
for x in (pcb_x0 + 10, l_tot - p.ant_len - 4):
    top = top.fuse(box(3, 3, p.comp_top + 0.2, x - 1.5, y_c - 1.5, z_pcb_bot + p.pcb_t - 0.15))
for x in hook_x:
    for s in (-1, 1):
        top = top.cut(box(p.hook_w + 0.4, 2 * p.wall, 1.4, x - (p.hook_w + 0.4) / 2,
                          y_c + s * (wb_in / 2 + p.wall / 2) - p.wall, z_pcb_bot + p.hook_len - 1.7))
top = top.removeSplitter()

pipe = Part.makeCylinder(1.45, p.wall + p.comp_top - 1.0).fuse(Part.makeCylinder(2.0, 0.8))
pcb = box(p.pcb_l, p.pcb_w, p.pcb_t, pcb_x0, y_c - p.pcb_w / 2, z_pcb_bot)
plug = shroud(0, -p.plug_out, p.plug_out + p.wall + p.flange_t + p.plug_body)

name = "LoganLink_Dongle"
if name in App.listDocuments():
    App.closeDocument(name)
doc = App.newDocument(name)
sheet = doc.addObject("Spreadsheet::Sheet", "Params")
for i, (k, v) in enumerate(P.items(), start=1):
    sheet.set(f"A{i}", k)
    sheet.set(f"B{i}", str(v))
parts = {}
for label, shp, col, tr in (("Bottom", bottom, (0.17, 0.18, 0.21), 0), ("Top", top, (0.23, 0.25, 0.29), 45),
                            ("LightPipe", pipe.translated(V(pcb_x0 + 28, y_c - 8, z_pcb_bot + p.pcb_t + 0.5)), (0.95, 0.95, 0.95), 30),
                            ("PCB_ref", pcb, (0.1, 0.37, 0.13), 0), ("OBDPlug_ref", plug, (0.55, 0.55, 0.55), 60)):
    o = doc.addObject("Part::Feature", label)
    o.Shape = shp
    if App.GuiUp:
        o.ViewObject.ShapeColor = col
        o.ViewObject.Transparency = tr
    parts[label] = o
doc.recompute()
doc.saveAs(os.path.join(OUT, "LoganLink-dongle.FCStd"))

import Import
Import.export([parts["Bottom"], parts["Top"], parts["LightPipe"]], os.path.join(OUT, "dongle.step"))
top_print = top.copy()
top_print.rotate(V(0, 0, 0), V(1, 0, 0), 180)
for fname, shp in (("dongle-bottom.stl", bottom), ("dongle-top.stl", top_print), ("dongle-pipe.stl", pipe)):
    MeshPart.meshFromShape(Shape=shp, LinearDeflection=0.05, AngularDeflection=0.3).write(os.path.join(OUT, fname))

if App.GuiUp:
    import FreeCADGui
    FreeCADGui.ActiveDocument.ActiveView.viewIsometric()
    FreeCADGui.SendMsgToActiveView("ViewFit")

bb = bottom.fuse(top).BoundBox
print(f"Shell {bb.XLength:.1f} x {bb.YLength:.1f} x {bb.ZLength:.1f} mm, valid={bottom.isValid() and top.isValid()}, "
      f"bottom {bottom.Volume / 1000:.1f} cm3, top {top.Volume / 1000:.1f} cm3")
