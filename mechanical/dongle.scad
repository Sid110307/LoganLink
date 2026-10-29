part = "assembly";

plug_w      = 39.0;
plug_w_bot  = 33.0;
plug_h      = 17.0;
plug_r      = 2.5;
plug_out    = 16.0;
flange_t    = 3.0;
flange_ext  = 1.2;
plug_body   = 8.0;

pcb_w = 38.0; pcb_l = 46.0; pcb_t = 1.6;
comp_top = 7.0;
under    = 2.0;
ant_len  = 8.0;

wall = 1.5; gap = 0.3; r_out = 3.0;
$fn = 40;

z_pcb_bot = wall + under;
z_pcb_mid = z_pcb_bot + pcb_t / 2;
hb_in = under + pcb_t + comp_top;
wb_in = pcb_w + 2 * gap;
ha_in = plug_h + 2 * flange_ext + 2 * gap;
wa_in = plug_w + 2 * flange_ext + 2 * gap;
la    = wall + flange_t + plug_body;
pcb_x0 = la + 3.0;
l_tot = pcb_x0 + pcb_l + gap + wall;
y_c = max(wa_in, wb_in) / 2 + wall;

module rbox(x0, l, w, h, zc, r) {
    translate([x0, y_c - w/2, zc - h/2])
        hull() for (yy = [r, w - r], zz = [r, h - r]) translate([0, yy, zz]) rotate([0, 90, 0]) cylinder(r = r, h = l);
}

zA = z_pcb_mid;
zB = wall + hb_in / 2;

module outer() {
    hull() {
        rbox(0, la, wa_in + 2*wall, ha_in + 2*wall, zA, r_out);
        rbox(la + 4, l_tot - la - 4, wb_in + 2*wall, hb_in + 2*wall, zB, r_out);
    }
}
module inner() {
    hull() {
        rbox(wall, la - wall, wa_in, ha_in, zA, max(0.6, r_out - wall));
        rbox(la + 4, l_tot - la - 4 - wall, wb_in, hb_in, zB, max(0.6, r_out - wall));
    }
}

module shroud(extra = 0, len = 1) {
    w1 = plug_w + 2*extra; w0 = plug_w_bot + 2*extra; h = plug_h + 2*extra;
    translate([0, y_c, zA]) rotate([90, 0, 90]) linear_extrude(len)
        offset(r = plug_r) offset(delta = -plug_r)
            polygon([[-w0/2, -h/2], [w0/2, -h/2], [w1/2, h/2], [-w1/2, h/2]]);
}

hook_x = [la + 6, l_tot - ant_len - 8];
hook_w = 5; hook_len = 5.0; hook_t = 1.0; barb = 0.6;

module window_cuts() {
    for (x = hook_x, s = [-1, 1])
        translate([x - (hook_w + 0.4)/2, y_c + s * (wb_in/2 + wall/2) - wall, z_pcb_bot + hook_len - 1.7])
            cube([hook_w + 0.4, 2 * wall, 1.4]);
}
module hooks() {
    for (x = hook_x, s = [-1, 1]) {
        yi = y_c + s * (wb_in/2 - hook_t/2 - 0.05);
        translate([x - hook_w/2, yi - hook_t/2, z_pcb_bot - 0.01]) cube([hook_w, hook_t, hook_len]);
        translate([x - hook_w/2, yi + (s > 0 ? hook_t/2 : -hook_t/2 - barb), z_pcb_bot + hook_len - 1.5])
            cube([hook_w, barb, 1.0]);
    }
}
module shell() {
    difference() {
        outer();
        inner();
        translate([-1, 0, 0]) shroud(gap, wall + 2);
        translate([wall, 0, 0]) shroud(flange_ext + gap, flange_t + gap);
        translate([wall + flange_t, 0, 0]) shroud(gap, plug_body + 1);
        translate([pcb_x0 + 20 - 5, y_c + wb_in/2 - 1, z_pcb_bot + pcb_t - 0.2]) cube([10.0, wall + 2, 3.6]);
        translate([pcb_x0 + 28, y_c - 8, z_pcb_bot]) cylinder(d = 3.1, h = 20);
        translate([pcb_x0 + 28, y_c + 8, z_pcb_bot]) cylinder(d = 1.6, h = 20);
    }
}

module pcb_supports() {
    for (x = [pcb_x0 + 10, l_tot - ant_len - 4], s = [-1, 1])
        translate([x - 2, y_c + s * (pcb_w/2 - 1.5) - 1.5, wall - 0.01]) cube([4, 3, under + 0.01]);
}
module hold_downs() {
    for (x = [pcb_x0 + 10, l_tot - ant_len - 4])
        translate([x - 1.5, y_c - 1.5, z_pcb_bot + pcb_t - 0.15]) cube([3, 3, comp_top + 0.2]);
}
module tie_eye() {
    translate([l_tot - 9, y_c - 5, -3]) difference() {
        cube([7, 10, 3.2]);
        translate([1.5, -1, 0.8]) cube([4, 12, 1.6]);
    }
}

module split_box(lower) {
    big = 200;
    if (lower) translate([-50, -50, -50]) cube([big, big, 50 + z_pcb_bot]);
    else       translate([-50, -50, z_pcb_bot]) cube([big, big, big]);
}

module bottom() {
    union() { intersection() { shell(); split_box(true); } pcb_supports(); tie_eye(); hooks(); }
}
module top() {
    difference() {
        union() { intersection() { shell(); split_box(false); } hold_downs(); }
        window_cuts();
    }
}
module pipe() { cylinder(d = 2.9, h = wall + comp_top - 1.0); cylinder(d = 4.0, h = 0.8); }

if (part == "bottom") bottom();
else if (part == "top") translate([0, 0, 0]) rotate([180, 0, 0]) translate([0, 0, -(zB + hb_in/2 + wall)]) top();
else if (part == "pipe") pipe();
else {
    color("#2b2e35") bottom();
    color("#3b404a", 0.55) top();
    color("#1b5e20") translate([pcb_x0, y_c - pcb_w/2, z_pcb_bot]) cube([pcb_l, pcb_w, pcb_t]);
    color("#888") translate([-plug_out, 0, 0]) shroud(0, plug_out + wall + flange_t + plug_body);
}

echo(str("Shell ", l_tot, " (+", plug_out, " plug) x ", 2*y_c, " x ", ha_in + 2*wall, " mm max; PCB zone height ",
         hb_in + 2*wall, " mm"));
