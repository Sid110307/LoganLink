import os
import FreeCAD as App
import Part
import MeshPart
from FreeCAD import Vector as V

OUT = os.path.dirname(os.path.abspath(__file__)) if "__file__" in globals() else os.getcwd()

P = dict(
    plug_w=39.0, plug_w_bot=33.0, plug_h=17.0, plug_r=2.5, plug_out=16.0,
    flange_t=3.0, flange_ext=1.2, plug_body=8.0, wire_gap=5.0,
    pcb_w=54.0, pcb_h=42.0, pcb_t=1.6, comp_top=6.0, hole_inset=3.0,
    face_t=2.0, ret_t=2.0, wall=1.6, roof=1.5, gap=0.3, r_out=3.0,
    boss_d=5.0, pilot_d=1.6, clear_d=2.4, lip_h=3.0, lip_t=0.8,
    led_x=21.0, led_y=9.0, btn_x=22.0, btn_y=3.0,
)
p = type("P", (), P)

z_flange = p.face_t
z_ret = z_flange + p.flange_t
z_body_end = z_ret + p.plug_body
z_pcb = z_body_end + p.wire_gap
z_pcb_top = z_pcb + p.pcb_t
z_roof_in = z_pcb_top + p.comp_top + 0.5
z_top = z_roof_in + p.roof

in_w, in_h = p.pcb_w + 2 * p.gap, p.pcb_h + 2 * p.gap
out_w, out_h = in_w + 2 * p.wall, in_h + 2 * p.wall
hx, hy = p.pcb_w / 2 - p.hole_inset, p.pcb_h / 2 - p.hole_inset
holes = [(sx * hx, sy * hy) for sx in (-1, 1) for sy in (-1, 1)]


def rbox(w, h, z0, dz, r):
    b = Part.makeBox(w, h, dz, V(-w / 2, -h / 2, z0))
    if r <= 0:
        return b
    e = [x for x in b.Edges if abs((x.Vertexes[1].Point - x.Vertexes[0].Point).normalize().z) > 0.99]
    return b.makeFillet(min(r, w / 2 - 0.01, h / 2 - 0.01), e)


def trap(extra, z0, dz):
    w1, w0, h = p.plug_w + 2 * extra, p.plug_w_bot + 2 * extra, p.plug_h + 2 * extra
    pts = [V(-w0 / 2, -h / 2, z0), V(w0 / 2, -h / 2, z0), V(w1 / 2, h / 2, z0), V(-w1 / 2, h / 2, z0)]
    s = Part.Face(Part.makePolygon(pts + [pts[0]])).extrude(V(0, 0, dz))
    e = [x for x in s.Edges if abs((x.Vertexes[1].Point - x.Vertexes[0].Point).normalize().z) > 0.99]
    return s.makeFillet(p.plug_r + extra, e)


def cyl(d, x, y, z0, dz):
    return Part.makeCylinder(d / 2, dz, V(x, y, z0))


front = rbox(out_w, out_h, 0, z_pcb_top, p.r_out).cut(rbox(in_w, in_h, p.face_t, z_pcb_top, p.r_out - p.wall))
front = front.cut(trap(p.gap, -1, p.face_t + 2))
for x, y in holes:
    front = front.fuse(cyl(p.boss_d, x, y, p.face_t - 0.01, p.flange_t + 0.01))
rab = rbox(out_w + 2, out_h + 2, z_pcb_top - p.lip_h, p.lip_h + 1, 0).cut(
    rbox(out_w - 2 * p.lip_t, out_h - 2 * p.lip_t, z_pcb_top - p.lip_h - 1, p.lip_h + 3, p.r_out - p.lip_t))
front = front.cut(rab)
for s in (-1, 1):
    y = s * (out_h / 2 - p.lip_t)
    front = front.fuse(Part.makeBox(12, 0.35, 0.8, V(-6, y if s > 0 else y - 0.35, z_pcb_top - p.lip_h / 2 - 0.4)))
for x, y in holes:
    front = front.cut(cyl(p.pilot_d, x, y, 0.6, 10))
front = front.removeSplitter()

ret = rbox(in_w - 0.4, in_h - 0.4, z_ret, p.ret_t, p.r_out - p.wall).cut(trap(p.gap, z_ret - 1, p.ret_t + 2))
for x, y in holes:
    ret = ret.fuse(cyl(p.boss_d, x, y, z_ret + p.ret_t - 0.01, z_pcb - z_ret - p.ret_t + 0.01))
for x, y in holes:
    ret = ret.cut(cyl(p.clear_d, x, y, z_ret - 1, 30))
ret = ret.removeSplitter()

rear = rbox(out_w, out_h, z_pcb_top, z_top - z_pcb_top, p.r_out).cut(
    rbox(in_w, in_h, z_pcb_top - 1, z_roof_in - z_pcb_top + 1, p.r_out - p.wall))
lip = rbox(out_w, out_h, z_pcb_top - p.lip_h, p.lip_h + 0.01, p.r_out).cut(
    rbox(out_w - 2 * p.lip_t + 0.2, out_h - 2 * p.lip_t + 0.2, z_pcb_top - p.lip_h - 1, p.lip_h + 2, p.r_out - p.lip_t))
rear = rear.fuse(lip)
for s in (-1, 1):
    y = s * (out_h / 2 - p.lip_t + 0.1)
    rear = rear.cut(Part.makeBox(13, 0.5, 1.0, V(-6.5, y if s > 0 else y - 0.5, z_pcb_top - p.lip_h / 2 - 0.5)))
rear = rear.cut(cyl(3.0, p.led_x, p.led_y, z_roof_in - 1, 5))
rear = rear.cut(cyl(1.6, p.btn_x, p.btn_y, z_roof_in - 1, 5))
rear = rear.removeSplitter()

pipe_len = z_top - (z_pcb_top + 1.0)
pipe = Part.makeCylinder(1.45, pipe_len).fuse(Part.makeCylinder(2.0, 0.8, V(0, 0, z_roof_in - (z_pcb_top + 1.0) - 0.8)))

pcb = rbox(p.pcb_w, p.pcb_h, z_pcb, p.pcb_t, 3.0)
for x, y in holes:
    pcb = pcb.cut(cyl(p.clear_d, x, y, z_pcb - 1, 4))
plug = trap(0, -p.plug_out, p.plug_out + p.face_t).fuse(trap(p.flange_ext, z_flange, p.flange_t)).fuse(
    trap(0, z_ret, p.plug_body))

name = "LoganLink_Dongle"
if name in App.listDocuments():
    App.closeDocument(name)
doc = App.newDocument(name)
sheet = doc.addObject("Spreadsheet::Sheet", "Params")
for i, (k, v) in enumerate(P.items(), start=1):
    sheet.set(f"A{i}", k)
    sheet.set(f"B{i}", str(v))
parts = {}
for label, shp, col, tr in (("Front", front, (0.17, 0.18, 0.21), 0), ("Retainer", ret, (0.85, 0.65, 0.1), 0),
                            ("Rear", rear, (0.23, 0.25, 0.29), 45),
                            ("LightPipe", pipe.translated(V(p.led_x, p.led_y, z_pcb_top + 1.0)), (0.95, 0.95, 0.95), 30),
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
Import.export([parts["Front"], parts["Retainer"], parts["Rear"], parts["LightPipe"]], os.path.join(OUT, "dongle.step"))
rear_print = rear.copy()
rear_print.rotate(V(0, 0, 0), V(1, 0, 0), 180)
for fname, shp in (("dongle-front.stl", front), ("dongle-retainer.stl", ret), ("dongle-rear.stl", rear_print),
                   ("dongle-pipe.stl", pipe)):
    MeshPart.meshFromShape(Shape=shp, LinearDeflection=0.05, AngularDeflection=0.3).write(os.path.join(OUT, fname))

if App.GuiUp:
    import FreeCADGui
    FreeCADGui.ActiveDocument.ActiveView.viewIsometric()
    FreeCADGui.SendMsgToActiveView("ViewFit")

bb = front.fuse(rear).BoundBox
print(f"Shell {bb.XLength:.1f} x {bb.YLength:.1f} x {bb.ZLength:.1f} mm, protrusion {z_top:.1f} mm, "
      f"valid={front.isValid() and rear.isValid() and ret.isValid()}, "
      f"front {front.Volume / 1000:.1f} cm3, retainer {ret.Volume / 1000:.1f} cm3, rear {rear.Volume / 1000:.1f} cm3, "
      f"pcb z {z_pcb:.1f}..{z_pcb_top:.1f}")
