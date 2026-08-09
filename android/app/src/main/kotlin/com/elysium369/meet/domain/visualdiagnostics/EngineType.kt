package com.elysium369.meet.domain.visualdiagnostics

enum class EngineType {
    UNKNOWN, // Educational generic view; exact powertrain architecture is not established
    // Inline/Straight
    L3,     // 3 cylinders (Smart, Mitsubishi Mirage, Ford EcoBoost 1.0)
    L4,     // 4 cylinders (most common)
    L5,     // 5 cylinders (Volvo, Audi)
    L6,     // 6 cylinders inline (BMW, Mercedes, Toyota Supra)
    // V-Configuration
    V6,     // V6 (very common)
    V8,     // V8 (muscle cars, trucks)
    V10,    // V10 (Dodge Viper, Lamborghini)
    V12,    // V12 (Ferrari, Mercedes AMG)
    // Boxer/Flat (Horizontally Opposed)
    H4,     // Boxer 4 (Subaru, Porsche 718)
    H6,     // Boxer 6 (Porsche 911)
    // Specialty
    ROTARY, // Wankel rotary (Mazda RX-7, RX-8)
    // Diesel variants
    DIESEL_L4,  // 4-cyl diesel (most common diesel)
    DIESEL_V6,  // V6 diesel (RAM EcoDiesel, Mercedes)
    DIESEL_V8,  // V8 diesel (Ford PowerStroke, Duramax)
    // Electrified
    HYBRID,     // Parallel/Series hybrid (Toyota Prius, Honda Insight)
    PHEV,       // Plug-in hybrid (Mitsubishi Outlander, RAV4 Prime)
    EV          // Full electric (Tesla, Nissan Leaf, Hyundai Ioniq)
}
