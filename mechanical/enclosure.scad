part = "assembly";

pcb_w = 80; pcb_d = 55; pcb_t = 1.6; pcb_r = 3;
hole_xy = [[4,4],[76,4],[4,51],[76,51]];
top_h = 10.0;
bot_h = 3.5;

wall = 2.4; floor_t = 2.2; lid_t = 2.2; gap = 0.6;
stand_h = bot_h; stand_d = 6.5;
in_w = pcb_w + 2*gap; in_d = pcb_d + 2*gap;
out_w = in_w + 2*wall; out_d = in_d + 2*wall;
base_h = floor_t + stand_h + pcb_t + 3.0;
lid_h  = lid_t + (top_h - 3.0);
r_out = 4;
insert_d = 3.6; insert_h = 5.0;
screw_d = 2.8; screw_head_d = 5.2;
pcb_z = floor_t + stand_h;

j1_x = 6;  j1_w = 32; j1_h = 11;
usb_y = 27; usb_w = 10; usb_h = 4.2;
ufl_slot = [62, 55, 12, 6];
led_xy = [70, 20]; btn_xy = [74, 20];
vent_xy = [44, 8]; vent_size = [26, 14];
sht_xy = [12, 14];
ant_x0 = 26; ant_x1 = 54;

$fn = 48;

module rrect(w, d, h, r) {
    translate([r, r, 0]) linear_extrude(h) offset(r) square([w - 2*r, d - 2*r]);
}
module at_pcb(xy, z = 0) { translate([wall + gap + xy[0], wall + gap + xy[1], z]) children(); }

module base() {
    difference() {
        union() {
            difference() {
                rrect(out_w, out_d, base_h, r_out);
                translate([wall, wall, floor_t]) rrect(in_w, in_d, base_h, max(0.5, r_out - wall));
            }
            for (p = hole_xy) at_pcb(p) cylinder(d = stand_d, h = pcb_z);
            for (c = [[wall+3, wall+3], [out_w-wall-3, wall+3], [wall+3, out_d-wall-3], [out_w-wall-3, out_d-wall-3]])
                translate([c[0], c[1], 0]) cylinder(d = 7, h = base_h);
            for (sx = [-1, 1]) translate([sx < 0 ? -12 : out_w, out_d/2 - 12, 0]) difference() {
                rrect(12, 24, 3.5, 3);
                translate([6, 12, -1]) cylinder(d = 4.4, h = 6);
                translate([sx < 0 ? 1.5 : 6.5, 3, -1]) cube([4, 5, 6]);
                translate([sx < 0 ? 1.5 : 6.5, 16, -1]) cube([4, 5, 6]);
            }
        }
        for (p = hole_xy) at_pcb(p, pcb_z - insert_h) cylinder(d = insert_d, h = insert_h + 1);
        for (c = [[wall+3, wall+3], [out_w-wall-3, wall+3], [wall+3, out_d-wall-3], [out_w-wall-3, out_d-wall-3]])
            translate([c[0], c[1], base_h - insert_h]) cylinder(d = insert_d, h = insert_h + 1);
        at_pcb([j1_x, -gap - wall - 1, pcb_z + pcb_t - 0.5]) cube([j1_w, wall + 2, j1_h]);
        at_pcb([pcb_w - 1, usb_y - usb_w/2, pcb_z + pcb_t - 0.3]) cube([wall + gap + 2, usb_w, usb_h]);
        for (i = [0:3]) at_pcb([-gap - wall - 1, sht_xy[1] - 6 + i*4, pcb_z + 0.5]) cube([wall + 2, 2, 6]);
        at_pcb([pcb_w - 8, -gap - wall - 1, floor_t + 0.8]) rotate([-90, 0, 0]) cylinder(d = 2, h = wall + 2);
        translate([out_w/2 - 25, out_d/2 - 15, -0.01]) cube([50, 30, 0.4]);
    }
}

module lid() {
    difference() {
        union() {
            rrect(out_w, out_d, lid_h, r_out);
        }
        translate([wall, wall, -0.01]) rrect(in_w, in_d, lid_h - lid_t, max(0.5, r_out - wall));
        for (c = [[wall+3, wall+3], [out_w-wall-3, wall+3], [wall+3, out_d-wall-3], [out_w-wall-3, out_d-wall-3]]) {
            translate([c[0], c[1], -1]) cylinder(d = screw_d, h = lid_h + 2);
            translate([c[0], c[1], lid_h - 1.6]) cylinder(d = screw_head_d, h = 2);
        }
        for (i = [0:5]) at_pcb([vent_xy[0] + i * 4.4, vent_xy[1], lid_h - lid_t - 1]) cube([2.2, vent_size[1], lid_t + 2]);
        at_pcb(led_xy, lid_h - lid_t - 1) cylinder(d = 3.2, h = lid_t + 2);
        at_pcb(btn_xy, lid_h - lid_t - 1) cylinder(d = 1.6, h = lid_t + 2);
        at_pcb([ufl_slot[0], pcb_d - 1, -1]) cube([ufl_slot[2], wall + gap + 2, 4]);
        at_pcb([j1_x, -gap - wall - 1, -1]) cube([j1_w, wall + 2, j1_h - 3 + 1]);
    }
    for (x = [8, 66]) at_pcb([x, 20, -3.2]) cube([2, 15, lid_h - lid_t + 3.2]);
}

module pipe() {
    cylinder(d = 3.0, h = lid_t + 7.5);
    cylinder(d = 4.2, h = 0.8);
}

if (part == "base") base();
else if (part == "lid") translate([0, 0, lid_h]) rotate([180, 0, 0]) lid();
else if (part == "pipe") pipe();
else {
    color("#2a2d33") base();
    color("#3a3f47", 0.6) translate([0, 0, base_h]) lid();
    color("#1b5e20") at_pcb([0, 0], pcb_z) rrect(pcb_w, pcb_d, pcb_t, pcb_r);
}

echo(str("Outer body: ", out_w, " x ", out_d, " x ", base_h + lid_h, " mm"));
