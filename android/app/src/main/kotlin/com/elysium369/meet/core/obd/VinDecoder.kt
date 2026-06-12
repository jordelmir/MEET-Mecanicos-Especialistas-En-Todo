package com.elysium369.meet.core.obd

/**
 * VinDecoder — Decodifica VIN según ISO 3779.
 * Posiciones: 1-3=WMI(fabricante), 4-8=VDS(modelo), 9=check, 10=año, 11=planta, 12-17=serial
 */
object VinDecoder {

    data class VinInfo(
        val vin: String,
        val country: String,
        val manufacturer: String,
        val modelYear: String,
        val assemblyPlant: String,
        val serialNumber: String,
        val isValid: Boolean,
        val summary: String
    )

    fun decode(vin: String): VinInfo? {
        val clean = vin.uppercase().replace(" ", "").replace("-", "")
        if (clean.length != 17) return null

        val wmi = clean.substring(0, 3)
        val country = decodeCountry(clean[0])
        val manufacturer = decodeManufacturer(wmi)
        val yearChar = clean[9]
        val modelYear = decodeYear(yearChar, clean[6])
        val plant = clean[10].toString()
        val serial = clean.substring(11, 17)

        val summary = buildString {
            append("🏭 $manufacturer")
            if (modelYear.isNotEmpty()) append(" | 📅 $modelYear")
            append(" | 🌍 $country")
            append(" | 🏭 Planta: $plant")
            append(" | #$serial")
        }

        return VinInfo(
            vin = clean, country = country, manufacturer = manufacturer,
            modelYear = modelYear, assemblyPlant = plant,
            serialNumber = serial, isValid = true, summary = summary
        )
    }

    private fun decodeCountry(c: Char): String = when (c) {
        '1', '4', '5' -> "Estados Unidos"
        '2' -> "Canadá"
        '3' -> "México"
        '6', '7' -> "Australia"
        '8' -> "Argentina"
        '9' -> "Brasil"
        'J' -> "Japón"
        'K' -> "Corea del Sur"
        'L' -> "China"
        'M' -> "India"
        'S' -> "Reino Unido"
        'V' -> "Francia/España"
        'W' -> "Alemania"
        'Y' -> "Suecia/Finlandia"
        'Z' -> "Italia"
        else -> "Otro ($c)"
    }

    private fun decodeManufacturer(wmi: String): String = when {
        wmi.startsWith("1G") || wmi.startsWith("3G") -> when (wmi[2]) {
            '1' -> "Chevrolet"; '2' -> "Pontiac"; '4' -> "Buick"
            '6' -> "Cadillac"; '8' -> "Saturn"; else -> "General Motors"
        }
        wmi.startsWith("1F") || wmi.startsWith("3F") -> "Ford"
        wmi.startsWith("1C") || wmi.startsWith("2C") || wmi.startsWith("3C") -> "Chrysler/RAM"
        wmi.startsWith("1N") -> "Nissan USA"
        wmi.startsWith("1H") -> "Honda USA"
        wmi.startsWith("2T") -> "Toyota Canadá"
        wmi.startsWith("3V") -> "Volkswagen México"
        wmi.startsWith("3N") -> "Nissan México"
        wmi.startsWith("JT") -> "Toyota"
        wmi.startsWith("JH") -> "Honda"
        wmi.startsWith("JN") -> "Nissan"
        wmi.startsWith("JM") -> when (wmi[2]) {
            '1' -> "Mazda"; 'Z' -> "Mazda"; else -> "Mitsubishi"
        }
        wmi.startsWith("JS") -> "Suzuki"
        wmi.startsWith("KM") -> "Hyundai"
        wmi.startsWith("KN") -> "Kia"
        wmi.startsWith("LF") -> "FAW (China)"
        wmi.startsWith("LJ") -> "Changan"
        wmi.startsWith("LV") -> "Chery"
        wmi.startsWith("MA") -> "Mahindra"
        wmi.startsWith("SAL") -> "Land Rover"
        wmi.startsWith("SAJ") -> "Jaguar"
        wmi.startsWith("SCC") -> "Lotus"
        wmi.startsWith("VF") -> "Renault/Peugeot"
        wmi.startsWith("VS") -> "SEAT"
        wmi.startsWith("WA") -> "Audi"
        wmi.startsWith("WB") -> "BMW"
        wmi.startsWith("WD") || wmi.startsWith("WF") -> "Mercedes-Benz"
        wmi.startsWith("WP") -> "Porsche"
        wmi.startsWith("WV") || wmi.startsWith("WVW") -> "Volkswagen"
        wmi.startsWith("W0") -> "Opel"
        wmi.startsWith("YV") -> "Volvo"
        wmi.startsWith("YS") -> "Saab"
        wmi.startsWith("ZA") -> "Alfa Romeo"
        wmi.startsWith("ZAR") -> "Alfa Romeo"
        wmi.startsWith("ZCF") -> "Iveco"
        wmi.startsWith("ZF") -> "Ferrari"
        wmi.startsWith("ZFF") -> "Ferrari"
        wmi.startsWith("ZHW") -> "Lamborghini"
        wmi.startsWith("ZLA") -> "Lancia"
        wmi.startsWith("5Y") -> "Toyota USA"
        wmi.startsWith("5T") -> "Hyundai USA"
        wmi.startsWith("5N") -> "Hyundai/Kia USA"
        wmi.startsWith("5X") -> "Kia USA"
        else -> "Fabricante ($wmi)"
    }

    private fun decodeYear(c: Char, seventhChar: Char): String {
        val isPre2010 = seventhChar.isDigit()
        return if (isPre2010) {
            when (c) {
                'A' -> "1980"; 'B' -> "1981"; 'C' -> "1982"; 'D' -> "1983"
                'E' -> "1984"; 'F' -> "1985"; 'G' -> "1986"; 'H' -> "1987"
                'J' -> "1988"; 'K' -> "1989"; 'L' -> "1990"; 'M' -> "1991"
                'N' -> "1992"; 'P' -> "1993"; 'R' -> "1994"; 'S' -> "1995"
                'T' -> "1996"; 'V' -> "1997"; 'W' -> "1998"; 'X' -> "1999"
                'Y' -> "2000"; '1' -> "2001"; '2' -> "2002"; '3' -> "2003"
                '4' -> "2004"; '5' -> "2005"; '6' -> "2006"; '7' -> "2007"
                '8' -> "2008"; '9' -> "2009"
                else -> c.toString()
            }
        } else {
            when (c) {
                'A' -> "2010"; 'B' -> "2011"; 'C' -> "2012"; 'D' -> "2013"
                'E' -> "2014"; 'F' -> "2015"; 'G' -> "2016"; 'H' -> "2017"
                'J' -> "2018"; 'K' -> "2019"; 'L' -> "2020"; 'M' -> "2021"
                'N' -> "2022"; 'P' -> "2023"; 'R' -> "2024"; 'S' -> "2025"
                'T' -> "2026"; 'V' -> "2027"; 'W' -> "2028"; 'X' -> "2029"
                'Y' -> "2030"; '1' -> "2031"; '2' -> "2032"; '3' -> "2033"
                '4' -> "2034"; '5' -> "2035"; '6' -> "2036"; '7' -> "2037"
                '8' -> "2038"; '9' -> "2039"
                else -> c.toString()
            }
        }
    }
}
