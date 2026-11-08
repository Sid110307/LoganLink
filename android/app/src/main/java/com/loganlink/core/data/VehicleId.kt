package com.loganlink.core.data

object VehicleId {
    private val states = mapOf(
        "AN" to "Andaman & Nicobar", "AP" to "Andhra Pradesh", "AR" to "Arunachal Pradesh", "AS" to "Assam", "BR" to "Bihar",
        "CG" to "Chhattisgarh", "CH" to "Chandigarh", "DD" to "Daman & Diu", "DL" to "Delhi", "DN" to "Dadra & Nagar Haveli",
        "GA" to "Goa", "GJ" to "Gujarat", "HP" to "Himachal Pradesh", "HR" to "Haryana", "JH" to "Jharkhand", "JK" to "Jammu & Kashmir",
        "KA" to "Karnataka", "KL" to "Kerala", "LA" to "Ladakh", "LD" to "Lakshadweep", "MH" to "Maharashtra", "ML" to "Meghalaya",
        "MN" to "Manipur", "MP" to "Madhya Pradesh", "MZ" to "Mizoram", "NL" to "Nagaland", "OD" to "Odisha", "OR" to "Odisha",
        "PB" to "Punjab", "PY" to "Puducherry", "RJ" to "Rajasthan", "SK" to "Sikkim", "TN" to "Tamil Nadu", "TR" to "Tripura",
        "TS" to "Telangana", "TG" to "Telangana", "UK" to "Uttarakhand", "UA" to "Uttarakhand", "UP" to "Uttar Pradesh", "WB" to "West Bengal",
    )

    private val rtos = mapOf(
        "KA01" to "Bengaluru (Koramangala)", "KA02" to "Bengaluru (Rajajinagar)", "KA03" to "Bengaluru (Indiranagar)",
        "KA04" to "Bengaluru (Yeshwanthpur)", "KA05" to "Bengaluru (Jayanagar)", "KA41" to "Bengaluru (Jnanabharathi)",
        "KA50" to "Bengaluru (Yelahanka)", "KA51" to "Bengaluru (Electronic City)", "KA53" to "Bengaluru (KR Puram)",
        "KA09" to "Mysuru", "KA19" to "Mangaluru", "KA25" to "Dharwad", "KA22" to "Belagavi",
        "MH01" to "Mumbai (Tardeo)", "MH02" to "Mumbai (Andheri)", "MH03" to "Mumbai (Worli)", "MH12" to "Pune", "MH14" to "Pimpri-Chinchwad",
        "MH15" to "Nashik", "MH31" to "Nagpur", "DL01" to "Delhi (Mall Road)", "DL03" to "Delhi (Sheikh Sarai)",
        "TN01" to "Chennai (Central)", "TN09" to "Chennai (West)", "TN22" to "Chennai (Meenambakkam)", "TN37" to "Coimbatore",
        "TS09" to "Hyderabad (Central)", "TS07" to "Rangareddy", "TS08" to "Medchal", "AP39" to "Andhra Pradesh",
        "KL07" to "Kochi", "KL01" to "Thiruvananthapuram", "GJ01" to "Ahmedabad", "WB01" to "Kolkata", "RJ14" to "Jaipur",
        "HR26" to "Gurugram", "UP16" to "Noida", "GA01" to "Panaji",
    )

    private val stdPlate = Regex("""^([A-Z]{2})\s*(\d{1,2})\s*([A-Z]{0,3})\s*(\d{1,4})$""")
    private val bhPlate = Regex("""^(\d{2})\s*BH\s*(\d{4})\s*([A-Z]{1,2})$""")

    fun formatPlate(input: String): String {
        val s = input.uppercase().filter { it.isLetterOrDigit() }
        bhPlate.matchEntire(s)?.let { m -> return "${m.groupValues[1]} BH ${m.groupValues[2]} ${m.groupValues[3]}" }
        val m = stdPlate.matchEntire(s) ?: return input.uppercase()
        val (st, rto, series, num) = m.destructured
        return listOf(st, rto.padStart(2, '0'), series, num).filter { it.isNotEmpty() }.joinToString(" ")
    }

    fun registration(plate: String): String? {
        val s = plate.uppercase().filter { it.isLetterOrDigit() }
        bhPlate.matchEntire(s)?.let { return "Bharat series, registered in 20${it.groupValues[1]}" }
        val m = stdPlate.matchEntire(s) ?: return null
        val st = m.groupValues[1]; val code = st + m.groupValues[2].padStart(2, '0')
        val state = states[st] ?: return null
        return rtos[code]?.let { "$it, $state" } ?: "$state, RTO ${m.groupValues[2].padStart(2, '0')}"
    }

    data class VinInfo(val vin: String, val maker: String?, val year: Int?)

    private val wmi = mapOf(
        "VF1" to "Renault", "VF3" to "Peugeot", "VF7" to "Citroën", "UU1" to "Dacia", "MA1" to "Mahindra", "MAT" to "Tata",
        "MA3" to "Maruti Suzuki", "MAL" to "Hyundai", "MAK" to "Honda", "MBH" to "Suzuki", "MBJ" to "Toyota", "MA6" to "Chevrolet",
        "MAJ" to "Ford", "MEE" to "Renault (India)", "MDH" to "Nissan", "MZB" to "Kia", "WVW" to "Volkswagen", "WAU" to "Audi",
        "WBA" to "BMW", "WDD" to "Mercedes-Benz", "SAL" to "Land Rover", "JTD" to "Toyota", "KMH" to "Hyundai",
    )
    private const val yearCodes = "ABCDEFGHJKLMNPRSTVWXY123456789"

    fun decodeVin(raw: String?): VinInfo? {
        val vin = raw?.uppercase()?.filter { it.isLetterOrDigit() }?.takeIf { it.length == 17 && it.none { c -> c in "IOQ" } } ?: return null
        val idx = yearCodes.indexOf(vin[9])
        val year = if (idx < 0) null else {
            val y = 1980 + idx
            if (y + 30 <= java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) + 1) y + 30 else y
        }
        return VinInfo(vin, wmi[vin.substring(0, 3)], year)
    }
}
