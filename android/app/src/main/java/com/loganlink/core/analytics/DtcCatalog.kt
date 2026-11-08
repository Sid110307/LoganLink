package com.loganlink.core.analytics

import com.loganlink.ui.theme.Severity

data class DtcInfo(val title: String, val causes: String, val severity: Severity)

object DtcCatalog {
    private val S = Severity.StopSafely; private val C = Severity.CheckSoon; private val I = Severity.Info
    private val map = mapOf(
        "P0105" to DtcInfo("MAP sensor circuit", "MAP sensor connector, cracked vacuum hose to the sensor, sensor failure", C),
        "P0106" to DtcInfo("MAP sensor range/performance", "Vacuum leak, blocked MAP port, sensor drift", C),
        "P0107" to DtcInfo("MAP sensor circuit low", "Open/short in the 5 V or signal wire, faulty sensor", C),
        "P0108" to DtcInfo("MAP sensor circuit high", "Signal shorted to 5 V, poor ground, faulty sensor", C),
        "P0110" to DtcInfo("Intake air temp circuit", "IAT part of the MAP/IAT sensor, wiring", I),
        "P0112" to DtcInfo("Intake air temp low", "IAT signal shorted to ground", I),
        "P0113" to DtcInfo("Intake air temp high", "IAT open circuit, unplugged sensor", I),
        "P0115" to DtcInfo("Coolant temp sensor circuit", "ECU coolant sensor or wiring; fan may run constantly as a fail-safe", C),
        "P0117" to DtcInfo("Coolant temp sensor low", "Sensor signal shorted to ground", C),
        "P0118" to DtcInfo("Coolant temp sensor high", "Sensor open circuit, corroded connector", C),
        "P0120" to DtcInfo("Throttle position sensor circuit", "TPS on the throttle body, wiring", C),
        "P0122" to DtcInfo("Throttle position sensor low", "TPS signal shorted to ground, worn track", C),
        "P0123" to DtcInfo("Throttle position sensor high", "TPS signal shorted to 5 V", C),
        "P0125" to DtcInfo("Engine slow to reach temperature", "Thermostat stuck open (common on ageing K7M), coolant sensor", I),
        "P0128" to DtcInfo("Coolant below thermostat temperature", "Thermostat stuck open or opening early", I),
        "P0130" to DtcInfo("Oxygen sensor circuit (bank 1, sensor 1)", "Upstream O₂ sensor, wiring, exhaust leak", C),
        "P0131" to DtcInfo("Oxygen sensor low voltage", "Exhaust leak before the sensor, lean mixture, sensor failure", C),
        "P0132" to DtcInfo("Oxygen sensor high voltage", "Sensor shorted, rich mixture, contaminated sensor", C),
        "P0133" to DtcInfo("Oxygen sensor slow response", "Ageing O₂ sensor (typical after 80,000 km)", I),
        "P0134" to DtcInfo("Oxygen sensor no activity", "Sensor heater or signal open circuit, dead sensor", C),
        "P0135" to DtcInfo("Oxygen sensor heater circuit", "Heater element open, heater fuse/relay, wiring", C),
        "P0170" to DtcInfo("Fuel trim malfunction", "Vacuum leak, fuel pressure, injectors, MAP sensor", C),
        "P0171" to DtcInfo("System too lean", "Vacuum leak (intake gasket, brake servo hose), weak fuel pump, clogged injectors", C),
        "P0172" to DtcInfo("System too rich", "Leaking injector, high fuel pressure, faulty MAP/coolant sensor", C),
        "P0201" to DtcInfo("Injector 1 circuit", "Injector coil or wiring, ECU driver", C),
        "P0202" to DtcInfo("Injector 2 circuit", "Injector coil or wiring, ECU driver", C),
        "P0203" to DtcInfo("Injector 3 circuit", "Injector coil or wiring, ECU driver", C),
        "P0204" to DtcInfo("Injector 4 circuit", "Injector coil or wiring, ECU driver", C),
        "P0230" to DtcInfo("Fuel pump relay circuit", "Fuel pump relay, inertia switch, wiring", S),
        "P0300" to DtcInfo("Random misfire", "Worn spark plugs, ignition coil pack, vacuum leak, low fuel pressure", S),
        "P0301" to DtcInfo("Misfire cylinder 1", "Plug/HT lead/coil on cylinders 1–4 pair, injector 1", S),
        "P0302" to DtcInfo("Misfire cylinder 2", "Plug/HT lead/coil on cylinders 2–3 pair, injector 2", S),
        "P0303" to DtcInfo("Misfire cylinder 3", "Plug/HT lead/coil on cylinders 2–3 pair, injector 3", S),
        "P0304" to DtcInfo("Misfire cylinder 4", "Plug/HT lead/coil on cylinders 1–4 pair, injector 4", S),
        "P0325" to DtcInfo("Knock sensor circuit", "Knock sensor torque/connector, wiring", C),
        "P0335" to DtcInfo("Crankshaft position sensor", "Flywheel (TDC) sensor, air gap, wiring. Engine may cut out", S),
        "P0336" to DtcInfo("Crankshaft sensor range", "Damaged flywheel teeth, sensor gap, interference", S),
        "P0351" to DtcInfo("Ignition coil A circuit", "Coil pack (cyl 1–4), wiring", S),
        "P0352" to DtcInfo("Ignition coil B circuit", "Coil pack (cyl 2–3), wiring", S),
        "P0420" to DtcInfo("Catalyst efficiency below threshold", "Ageing catalytic converter, exhaust leak, O₂ sensor", I),
        "P0440" to DtcInfo("Evaporative system", "Canister purge valve, hoses, fuel cap", I),
        "P0443" to DtcInfo("Purge valve circuit", "Canister purge solenoid, wiring", I),
        "P0500" to DtcInfo("Vehicle speed sensor", "Gearbox speed sensor, wiring. Speedometer may also fail", C),
        "P0505" to DtcInfo("Idle control system", "Idle stepper motor (IAC), dirty throttle body, vacuum leak", C),
        "P0506" to DtcInfo("Idle speed lower than expected", "Dirty throttle body/IAC, vacuum leak", I),
        "P0507" to DtcInfo("Idle speed higher than expected", "Vacuum leak, IAC stuck open", I),
        "P0560" to DtcInfo("System voltage", "Battery, alternator, charging wiring", C),
        "P0562" to DtcInfo("System voltage low", "Weak battery, alternator not charging, loose battery terminal", C),
        "P0563" to DtcInfo("System voltage high", "Alternator regulator fault: overcharging can damage electronics", S),
        "P0601" to DtcInfo("ECU memory checksum", "Engine computer internal fault", S),
        "P0650" to DtcInfo("Warning lamp circuit", "Check-engine lamp bulb or wiring", I),
    )

    fun lookup(code: String): DtcInfo = map[code.uppercase()] ?: when {
        code.length == 5 && code[1] == '1' -> DtcInfo("Manufacturer-specific fault", "Renault-specific code: needs dealer documentation (Clip/DDT)", Severity.CheckSoon)
        code.startsWith("P03") -> DtcInfo("Ignition or misfire fault", "Ignition system: plugs, coil pack, leads", Severity.CheckSoon)
        code.startsWith("P01") || code.startsWith("P02") -> DtcInfo("Fuel and air metering fault", "Sensors or fuel delivery", Severity.CheckSoon)
        code.startsWith("P04") -> DtcInfo("Emissions control fault", "Catalyst, EVAP or EGR system", Severity.Info)
        code.startsWith("P05") -> DtcInfo("Speed/idle control fault", "Speed sensor or idle control", Severity.CheckSoon)
        code.startsWith("P06") -> DtcInfo("Engine computer or output circuit fault", "ECU or its outputs", Severity.CheckSoon)
        code.startsWith("B") -> DtcInfo("Body system fault", "Body electronics (UCH, lighting, locking)", Severity.Info)
        code.startsWith("C") -> DtcInfo("Chassis system fault", "ABS or chassis electronics", Severity.CheckSoon)
        else -> DtcInfo("Unrecognised fault", "No description available", Severity.CheckSoon)
    }
}
