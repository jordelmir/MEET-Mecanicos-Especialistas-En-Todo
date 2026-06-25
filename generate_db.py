import argparse
import json
import sqlite3
from pathlib import Path


SOURCE_JSON = Path("android/app/src/main/assets/dtc_database_es.json")
DEFAULT_OUTPUT = Path("android/app/src/main/assets/databases/meet_dtc.db")

DEFINITION_COLUMNS = [
    "code",
    "manufacturer",
    "isGeneric",
    "descriptionEs",
    "descriptionEn",
    "obd2StandardNameEn",
    "system",
    "subSystem",
    "severity",
    "urgency",
    "dtcCategory",
    "faultType",
    "monitorType",
    "readinessMonitor",
    "faultPersistence",
    "possibleCauses",
    "symptoms",
    "affectedComponents",
    "diagnosticSteps",
    "relatedCodes",
    "freezeFramePIDs",
    "liveDataThresholds",
    "repairComplexity",
    "drivabilityImpact",
    "repairCostUSD",
    "laborHoursEstimate",
    "diyFriendly",
    "specialToolsRequired",
    "repairVerification",
    "preventiveMaintenance",
    "milBehavior",
    "emissionsImpact",
    "warrantyNote",
    "cascadeRisk",
    "frequencyRank",
    "safeToResetWithoutRepair",
    "vehicleYearRange",
    "obd2Protocol",
    "countryRegulation",
    "obd2DiagnosticMode",
    "tsbBulletins",
]

OFFICIAL_SOURCE_AUDIT = [
    (
        "ISO 15031-6:2015",
        "https://www.iso.org/es/contents/data/standard/06/63/66369.html",
        "Standardized DTC definitions and text descriptor guidance.",
    ),
    (
        "EPA OBD readiness best practices",
        "https://www.epa.gov/system/files/documents/2022-08/diesel-obd-im-readiness-14k-pounds-gwr-best-practices.pdf",
        "DTCs identify malfunctioning components or systems; permanent DTC context.",
    ),
    (
        "CARB OBD II regulation order",
        "https://ww2.arb.ca.gov/sites/default/files/barcu/regact/2021/obd2021/fro-obdii.pdf",
        "Freeze-frame and monitoring requirements tied to fault codes.",
    ),
    (
        "CARB J1979-2 data record attachment",
        "https://ww2.arb.ca.gov/sites/default/files/barcu/regact/2021/obd2021/15dayattc.pdf",
        "J1979-2 data fields, scaling, monitor performance, and supported fault code reporting.",
    ),
    (
        "NHTSA vPIC",
        "https://vpic.nhtsa.dot.gov/",
        "Manufacturer-reported VIN and make data used for manufacturer normalization checks.",
    ),
    (
        "EPA emissions warranty FAQ",
        "https://www.epa.gov/transportation-air-pollution-and-climate-change/frequent-questions-related-transportation-air",
        "Federal emissions warranty context for catalyst, ECU, and OBD components.",
    ),
]

MECHANICAL_SEED = {
    "safety_protocols": [
        {
            "protocolId": "hot_engine_fluids",
            "system": "engine",
            "title": "Motor caliente y fluidos inflamables",
            "mandatoryBefore": "oil_leak;coolant_leak;fuel_smell;engine_bay_work",
            "searchKeywords": "hot engine oil leak coolant fuel safety",
            "payloadJson": {
                "ppe": ["nitrile gloves", "eye protection", "cool inspection lamp"],
                "steps": [
                    "Shut engine off and let exhaust and oil temperature fall before touching fittings",
                    "Do not spray solvent over a hot manifold or catalyst",
                    "If the vehicle must be raised, use stands and wheel chocks"
                ],
                "fatal_risks": ["burns", "fire from solvents", "crush hazards"],
                "common_mistakes": ["hands near electric fan", "using only the floor jack as support"]
            },
        },
        {
            "protocolId": "hybrid_high_voltage",
            "system": "hev_ev",
            "title": "High voltage HEV / EV isolation",
            "mandatoryBefore": "battery_pack_inverter_ac_compressor_orange_cables",
            "searchKeywords": "hybrid ev high voltage loto orange cable safety",
            "payloadJson": {
                "ppe": ["class 0 gloves", "face shield", "lockout tagout"],
                "steps": [
                    "Follow OEM disable sequence",
                    "Remove service plug and secure the system",
                    "Verify absence of voltage with approved equipment"
                ],
                "fatal_risks": ["electric shock", "arc flash"],
            },
        },
        {
            "protocolId": "fuel_pressure_release",
            "system": "fuel_system",
            "title": "Fuel pressure release",
            "mandatoryBefore": "injector_rail_fuel_line_pump_filter_work",
            "searchKeywords": "fuel pressure release gasoline injector rail fire safety",
            "payloadJson": {
                "ppe": ["eye protection", "hydrocarbon-resistant gloves"],
                "steps": [
                    "Disable the pump per OEM procedure or remove fuse/relay if appropriate",
                    "Capture spills with absorbent material",
                    "Keep all ignition sources away from the work area"
                ],
                "fatal_risks": ["fire", "eye injury"],
            },
        },
    ],
    "symptom_guides": [
        {
            "symptomId": "oil_leak",
            "title": "How to diagnose an oil leak",
            "dangerLevel": "medium",
            "searchKeywords": "oil leak fuga aceite valve cover oil pan rear main",
            "relatedDtcs": "P0520,P0521,P0522,P0523,P0010,P0011,P0014",
            "payloadJson": {
                "first_checks": [
                    "Confirm the fluid is engine oil by color and feel",
                    "Check oil level before running the engine",
                    "Find the highest wet point, not just the drip point"
                ],
                "diagnostic_tree": [
                    "Clean the area first",
                    "Use UV dye if the leak is not obvious",
                    "Inspect top-down: valve cover, VVT, filter, cooler, oil pan, seals"
                ],
                "most_common_causes_ranked": [
                    "Valve cover gasket",
                    "Loose filter or double gasket",
                    "Oil pan drain plug or washer"
                ],
            },
        },
        {
            "symptomId": "coolant_leak",
            "title": "How to diagnose a coolant leak",
            "dangerLevel": "high",
            "searchKeywords": "coolant leak fuga agua radiator hose water pump overheating",
            "relatedDtcs": "P0117,P0118,P0125,P0128,P0217",
            "payloadJson": {
                "first_checks": [
                    "Never open a hot radiator cap",
                    "Confirm coolant color and smell",
                    "Check reservoir level and overheating history"
                ],
                "diagnostic_tree": [
                    "Pressure test cold",
                    "Inspect radiator, hoses, pump, thermostat housing, heater core",
                    "Use UV dye if it leaks only hot",
                    "Test for combustion gases if no external leak is found"
                ],
            },
        },
        {
            "symptomId": "alternator_not_charging",
            "title": "How to diagnose alternator not charging",
            "dangerLevel": "medium",
            "searchKeywords": "alternator not charging battery discharge low system voltage",
            "relatedDtcs": "P0560,P0562,P0563,P0620",
            "payloadJson": {
                "first_checks": [
                    "Check belt tension and pulley condition",
                    "Measure KOEO and running voltage",
                    "Confirm battery lamp or charging message"
                ],
                "diagnostic_tree": [
                    "Perform positive and ground voltage-drop tests under load",
                    "Measure AC ripple at the battery",
                    "Inspect smart charging control lines"
                ],
            },
        },
        {
            "symptomId": "spongy_brake_pedal",
            "title": "How to diagnose a spongy brake pedal",
            "dangerLevel": "critical",
            "searchKeywords": "spongy brake pedal soft pedal abs bleed master cylinder",
            "relatedDtcs": "C1201,C1234,C0020",
            "payloadJson": {
                "first_checks": [
                    "Do not road test if the pedal drops to the floor",
                    "Inspect fluid level and condition",
                    "Check each wheel, hose, and the master cylinder for leaks"
                ],
                "diagnostic_tree": [
                    "Bleed the system in OEM order",
                    "Isolate circuits if master cylinder bypass is suspected",
                    "Run ABS bleed routine with a scan tool if the HCU may contain air"
                ],
            },
        },
    ],
    "mechanical_procedures": [
        {
            "componentId": "alternator_replacement",
            "system": "charging_system",
            "title": "Alternator replacement",
            "difficulty": 3,
            "estimatedTimeHours": 1.8,
            "searchKeywords": "alternator replacement charging battery lamp",
            "payloadJson": {
                "required_tools": ["socket set", "torque wrench", "multimeter"],
                "removal_steps": [
                    "Disconnect negative battery terminal",
                    "Release belt tension",
                    "Disconnect B+ and control connector",
                    "Remove mounting fasteners"
                ],
                "installation_steps": [
                    "Compare pulley offset and clocking",
                    "Clean grounds and mating surfaces",
                    "Torque mounts and B+ terminal to OEM spec"
                ],
                "torque_specs": ["mounting fasteners typically 35-55 Nm", "B+ terminal typically 9-15 Nm"],
                "post_install_tests": ["13.5-14.8V typical charging", "AC ripple below 0.5V"],
            },
        },
        {
            "componentId": "starter_replacement",
            "system": "starting_system",
            "title": "Starter motor replacement",
            "difficulty": 3,
            "estimatedTimeHours": 2.0,
            "searchKeywords": "starter replacement slow crank click no start",
            "payloadJson": {
                "required_tools": ["deep sockets", "extensions", "torque wrench"],
                "removal_steps": [
                    "Verify battery and cable condition first",
                    "Disconnect B+ cable and solenoid trigger wire",
                    "Remove mounting bolts and support the unit"
                ],
                "electrical_specs": ["positive drop below 0.2V per segment", "ground drop below 0.1V per segment"],
                "post_install_tests": ["stable cranking", "no gear clash", "drop within spec"],
            },
        },
        {
            "componentId": "brake_service_full",
            "system": "braking_system",
            "title": "Complete brake service",
            "difficulty": 3,
            "estimatedTimeHours": 2.5,
            "searchKeywords": "brake service pads rotors caliper bleed",
            "payloadJson": {
                "required_tools": ["jack stands", "torque wrench", "piston tool", "bleed kit"],
                "installation_steps": [
                    "Clean hub and bracket contact points",
                    "Lubricate only approved hardware surfaces",
                    "Retract piston with the correct procedure"
                ],
                "post_install_tests": ["firm pedal before moving", "bedding procedure", "no leaks"],
            },
        },
        {
            "componentId": "valve_cover_gasket",
            "system": "engine_lubrication",
            "title": "Valve cover gasket replacement",
            "difficulty": 2,
            "estimatedTimeHours": 1.5,
            "searchKeywords": "valve cover gasket oil leak spark plug tube",
            "payloadJson": {
                "required_tools": ["10 mm sockets", "low-range torque wrench", "plastic scraper"],
                "installation_steps": [
                    "Clean the sealing surface without gouging aluminum",
                    "Apply RTV only at OEM-specified corners",
                    "Torque fasteners in the correct sequence"
                ],
                "torque_specs": ["valve cover fasteners often 7-10 Nm; verify OEM"],
            },
        },
        {
            "componentId": "oil_pan_gasket",
            "system": "engine_lubrication",
            "title": "Oil pan gasket or RTV reseal",
            "difficulty": 4,
            "estimatedTimeHours": 3.0,
            "searchKeywords": "oil pan gasket rtv drain plug leak",
            "payloadJson": {
                "required_tools": ["torque wrench", "plastic scraper", "drain pan"],
                "removal_steps": [
                    "Drain oil",
                    "Remove shields and any blocking hardware",
                    "Lower the pan without bending aluminum rails"
                ],
                "common_mistakes": ["too much RTV inside the engine", "oil contamination on the sealing surface"],
            },
        },
    ],
    "component_rebuild_guides": [
        {
            "componentId": "alternator",
            "rebuildPossible": True,
            "searchKeywords": "alternator rebuild rectifier rotor stator regulator brushes ripple",
            "payloadJson": {
                "internal_parts": ["rectifier bridge", "regulator", "rotor", "stator", "slip rings", "bearings", "brushes"],
                "bench_tests": ["AC ripple below 0.5V", "diode-mode rectifier test", "rotor resistance check"],
                "rebuild_steps": ["mark housing", "test each diode", "replace wear items", "bench test before install"],
            },
        },
        {
            "componentId": "starter_motor",
            "rebuildPossible": True,
            "searchKeywords": "starter rebuild solenoid bendix armature bushings brushes",
            "payloadJson": {
                "internal_parts": ["solenoid", "bendix", "armature", "brushes", "brush holder", "bushings"],
                "bench_tests": ["current draw test", "engagement test", "no-load speed"],
                "quality_control_tests": ["consistent engagement", "no overcurrent", "stable spin speed"],
            },
        },
    ],
    "trench_knowledge": [
        {
            "scenarioId": "seized_bolt",
            "title": "Seized or rusted fastener",
            "riskLevel": "high",
            "searchKeywords": "seized bolt rusted fastener induction heat weld nut extractor metabo",
            "payloadJson": {
                "escalation_ladder": [
                    "visual inspection",
                    "penetrant soak 20-60 min",
                    "controlled impact",
                    "localized heat",
                    "extractor",
                    "welded nut",
                    "destructive cutting",
                    "thread repair"
                ],
                "heat_allowed": True,
                "cutting_allowed": True,
                "thread_repair_options": ["helicoil", "timesert"],
            },
        },
        {
            "scenarioId": "broken_stud_extraction",
            "title": "Broken stud extraction",
            "riskLevel": "critical",
            "searchKeywords": "broken stud left hand drill centered drilling weld nut",
            "payloadJson": {
                "escalation_ladder": [
                    "center punch accurately",
                    "left-hand drill bit",
                    "extractor only if enough material remains",
                    "welded nut for thermal shock",
                    "progressive drilling and thread repair"
                ],
                "common_failures": ["snapped extractor makes the repair dramatically harder"],
            },
        },
        {
            "scenarioId": "aluminum_thread_repair",
            "title": "Aluminum thread repair",
            "riskLevel": "high",
            "searchKeywords": "aluminum thread repair helicoil timesert stripped threads",
            "payloadJson": {
                "escalation_ladder": [
                    "confirm pitch and depth",
                    "drill squarely",
                    "tap cleanly",
                    "install the correct insert",
                    "verify torque"
                ],
                "thread_repair_options": ["helicoil", "timesert"],
            },
        },
    ],
    "automotive_chemistry": [
        {
            "chemicalId": "penetrating_oil_pb",
            "category": "penetrant",
            "name": "Penetrating oil (PB Blaster / Kroil type)",
            "searchKeywords": "penetrating oil pb blaster kroil rusted bolt",
            "payloadJson": {
                "use_cases": ["rusted bolts", "seized sensors", "salt-corroded threads"],
                "application_time_minutes": [20, 60],
                "do_not_use_when": ["near open flame", "directly on very hot surfaces"],
            },
        },
        {
            "chemicalId": "atf_acetone_mix",
            "category": "penetrant",
            "name": "ATF + acetone mix",
            "searchKeywords": "atf acetone penetrant homemade extraction",
            "payloadJson": {
                "use_cases": ["aggressive rusted fastener extraction"],
                "application_time_minutes": [10, 30],
                "can_cause_damage": True,
                "do_not_use_when": ["near sparks or flame", "on delicate paint or plastic"],
            },
        },
        {
            "chemicalId": "uv_oil_dye",
            "category": "diagnostic_dye",
            "name": "UV dye for oil systems",
            "searchKeywords": "uv dye oil leak diagnostic lamp",
            "payloadJson": {
                "use_cases": ["small or intermittent oil leaks", "post-repair confirmation"],
                "application_time_minutes": [10, 30],
                "can_cause_damage": False,
            },
        },
        {
            "chemicalId": "dielectric_grease",
            "category": "electrical_protection",
            "name": "Dielectric grease",
            "searchKeywords": "dielectric grease coil boot connector moisture",
            "payloadJson": {
                "use_cases": ["coil boots", "environmental sealing of appropriate connectors"],
                "can_cause_damage": False,
                "do_not_use_when": ["trying to repair voltage drop by packing the actual contact surfaces"],
            },
        },
    ],
    "tool_usage_guides": [
        {
            "toolId": "angle_grinder_metabo",
            "name": "Angle grinder / Metabo",
            "searchKeywords": "metabo grinder destructive cutting stuck bolt",
            "payloadJson": {
                "allowed_use_cases": ["last-resort destructive extraction"],
                "forbidden_use_cases": ["near fuel lines", "near unshielded glass or wiring"],
                "fire_risk": True,
                "precision_risk": "high",
            },
        },
        {
            "toolId": "induction_heater",
            "name": "Induction heater",
            "searchKeywords": "induction heater flame free fastener extraction",
            "payloadJson": {
                "allowed_use_cases": ["ferrous fasteners near flame-sensitive areas"],
                "forbidden_use_cases": ["non-ferrous targets"],
                "fire_risk": False,
                "precision_risk": "medium",
            },
        },
        {
            "toolId": "smoke_machine",
            "name": "Smoke machine",
            "searchKeywords": "smoke machine evap vacuum leak intake",
            "payloadJson": {
                "allowed_use_cases": ["EVAP leaks", "intake and vacuum leaks"],
                "forbidden_use_cases": ["over-pressurizing delicate systems"],
                "fire_risk": False,
                "precision_risk": "low",
            },
        },
    ],
    "meet_knowledge_matrix": [
        {
            "dtcCode": "P0520",
            "componentName": "Oil pressure sensor",
            "systemCategory": "Powertrain - Lubrication",
            "urgencyLevel": "soon",
            "layerDiagnosticsJson": {
                "related_symptom_guides": ["oil_leak"],
                "diagnostic_steps": ["verify level", "inspect sensor for external leak", "measure mechanical oil pressure"],
                "confirmation_tests": ["mechanical pressure within spec", "no leak at connector or threads"],
            },
            "layerRebuildSpecsJson": {
                "electrical_specs": ["5V reference on many designs", "signal may be analog or switch-type"],
                "torque_specs": ["verify OEM low-torque spec for aluminum housings"],
            },
            "layerTrenchKnowledgeJson": {
                "related_trench_knowledge": ["seized_bolt"],
                "common_shop_notes": ["do not condemn a wet sensor if oil is running down from above"],
            },
            "layerAdvancedEngJson": {
                "scope_patterns": ["verify signal stability if the design uses an analog sender"],
                "ev_hvac_notes": [],
            },
        },
        {
            "dtcCode": "P0562",
            "componentName": "Alternator / charging system",
            "systemCategory": "Powertrain - Charging",
            "urgencyLevel": "soon",
            "layerDiagnosticsJson": {
                "related_symptom_guides": ["alternator_not_charging", "hard_start"],
                "diagnostic_steps": ["measure KOEO and running voltage", "perform voltage-drop tests", "measure AC ripple"],
                "confirmation_tests": ["stable 13.5-14.8V typical", "low ripple", "acceptable B+ drop"],
            },
            "layerRebuildSpecsJson": {
                "electrical_specs": ["charging typically 13.5-14.8V", "ripple under 0.5V AC", "B+ drop under 0.3V"],
                "rebuild_paths": ["alternator"],
            },
            "layerTrenchKnowledgeJson": {
                "related_trench_knowledge": ["broken_stud_extraction"],
                "common_shop_notes": ["check megafuse, grounds, and battery sensor strategy before condemning the alternator"],
            },
            "layerAdvancedEngJson": {
                "scope_patterns": ["six-pulse ripple pattern can help identify rectifier or phase faults"],
                "ev_hvac_notes": ["HEV systems may use DC-DC conversion instead of a conventional alternator"],
            },
        },
        {
            "dtcCode": "P0420",
            "componentName": "Catalyst efficiency",
            "systemCategory": "Powertrain - Emissions",
            "urgencyLevel": "monitor",
            "layerDiagnosticsJson": {
                "diagnostic_steps": ["rule out misfires and exhaust leaks", "compare upstream and downstream O2 patterns", "measure backpressure if power is down"],
                "confirmation_tests": ["backpressure under 1 PSI at 2500 RPM", "downstream O2 more stable than upstream"],
            },
            "layerRebuildSpecsJson": {
                "service_limits": ["elevated backpressure indicates physical restriction"],
            },
            "layerTrenchKnowledgeJson": {
                "common_shop_notes": ["do not replace the catalyst before resolving oil burning or misfire root causes"],
            },
            "layerAdvancedEngJson": {
                "scope_patterns": ["if downstream mirrors upstream, storage efficiency is weak"],
            },
        },
    ],
}


def scalar(value):
    if value is None:
        return None
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (dict, list)):
        return json.dumps(value, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
    return str(value)


def load_records(path):
    with path.open("r", encoding="utf-8") as fh:
        payload = json.load(fh)
    if isinstance(payload, list):
        metadata = {
            "schemaVersion": "legacy-array",
            "source": path.as_posix(),
            "totalRecords": len(payload),
        }
        return metadata, payload
    if isinstance(payload, dict) and isinstance(payload.get("records"), list):
        return payload, payload["records"]
    raise ValueError(f"{path} must be a legacy DTC array or a v4 object with a records array")


def create_schema(conn, include_graph):
    conn.executescript(
        """
        PRAGMA journal_mode = OFF;
        PRAGMA synchronous = OFF;

        CREATE TABLE dtc_definitions (
            code TEXT NOT NULL,
            manufacturer TEXT NOT NULL,
            isGeneric TEXT NOT NULL,
            descriptionEs TEXT NOT NULL,
            descriptionEn TEXT NOT NULL,
            obd2StandardNameEn TEXT,
            system TEXT NOT NULL,
            subSystem TEXT,
            severity TEXT NOT NULL,
            urgency TEXT NOT NULL,
            dtcCategory TEXT,
            faultType TEXT,
            monitorType TEXT,
            readinessMonitor TEXT,
            faultPersistence TEXT,
            possibleCauses TEXT,
            symptoms TEXT,
            affectedComponents TEXT,
            diagnosticSteps TEXT,
            relatedCodes TEXT,
            freezeFramePIDs TEXT,
            liveDataThresholds TEXT,
            repairComplexity TEXT,
            drivabilityImpact TEXT,
            repairCostUSD TEXT,
            laborHoursEstimate TEXT,
            diyFriendly TEXT,
            specialToolsRequired TEXT,
            repairVerification TEXT,
            preventiveMaintenance TEXT,
            milBehavior TEXT,
            emissionsImpact TEXT,
            warrantyNote TEXT,
            cascadeRisk TEXT,
            frequencyRank TEXT,
            safeToResetWithoutRepair TEXT,
            vehicleYearRange TEXT,
            obd2Protocol TEXT,
            countryRegulation TEXT,
            obd2DiagnosticMode TEXT,
            tsbBulletins TEXT,
            PRIMARY KEY(code, manufacturer)
        );

        CREATE INDEX idx_dtc_code ON dtc_definitions(code);
        CREATE INDEX idx_dtc_manufacturer ON dtc_definitions(manufacturer);
        CREATE INDEX idx_dtc_system ON dtc_definitions(system);
        CREATE INDEX idx_dtc_subsystem ON dtc_definitions(subSystem);
        CREATE INDEX idx_dtc_severity ON dtc_definitions(severity);
        CREATE INDEX idx_dtc_category ON dtc_definitions(dtcCategory);
        CREATE INDEX idx_dtc_readiness ON dtc_definitions(readinessMonitor);

        CREATE TABLE dtc_source_audit (
            sourceName TEXT PRIMARY KEY,
            sourceUrl TEXT NOT NULL,
            notes TEXT NOT NULL
        );

        CREATE TABLE dtc_quality_report (
            metric TEXT PRIMARY KEY,
            value TEXT NOT NULL
        );

        CREATE TABLE meet_knowledge_matrix (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            dtcCode TEXT,
            componentName TEXT,
            systemCategory TEXT,
            urgencyLevel TEXT,
            layerDiagnosticsJson TEXT NOT NULL,
            layerRebuildSpecsJson TEXT NOT NULL,
            layerTrenchKnowledgeJson TEXT NOT NULL,
            layerAdvancedEngJson TEXT NOT NULL,
            lastUpdated INTEGER NOT NULL
        );
        CREATE UNIQUE INDEX idx_matrix_dtc_component ON meet_knowledge_matrix(dtcCode, componentName);
        CREATE INDEX idx_matrix_dtc ON meet_knowledge_matrix(dtcCode);
        CREATE INDEX idx_matrix_component ON meet_knowledge_matrix(componentName);
        CREATE INDEX idx_matrix_system ON meet_knowledge_matrix(systemCategory);

        CREATE TABLE symptom_guides (
            symptomId TEXT PRIMARY KEY,
            title TEXT NOT NULL,
            dangerLevel TEXT NOT NULL,
            searchKeywords TEXT NOT NULL,
            relatedDtcs TEXT NOT NULL,
            payloadJson TEXT NOT NULL,
            updatedAt INTEGER NOT NULL
        );
        CREATE TABLE mechanical_procedures (
            componentId TEXT PRIMARY KEY,
            system TEXT NOT NULL,
            title TEXT NOT NULL,
            difficulty INTEGER NOT NULL,
            estimatedTimeHours REAL NOT NULL,
            searchKeywords TEXT NOT NULL,
            payloadJson TEXT NOT NULL,
            updatedAt INTEGER NOT NULL
        );
        CREATE TABLE component_rebuild_guides (
            componentId TEXT PRIMARY KEY,
            rebuildPossible INTEGER NOT NULL,
            searchKeywords TEXT NOT NULL,
            payloadJson TEXT NOT NULL,
            updatedAt INTEGER NOT NULL
        );
        CREATE TABLE trench_knowledge (
            scenarioId TEXT PRIMARY KEY,
            title TEXT NOT NULL,
            riskLevel TEXT NOT NULL,
            searchKeywords TEXT NOT NULL,
            payloadJson TEXT NOT NULL,
            updatedAt INTEGER NOT NULL
        );
        CREATE TABLE automotive_chemistry (
            chemicalId TEXT PRIMARY KEY,
            category TEXT NOT NULL,
            name TEXT NOT NULL,
            searchKeywords TEXT NOT NULL,
            payloadJson TEXT NOT NULL,
            updatedAt INTEGER NOT NULL
        );
        CREATE TABLE tool_usage_guides (
            toolId TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            searchKeywords TEXT NOT NULL,
            payloadJson TEXT NOT NULL,
            updatedAt INTEGER NOT NULL
        );
        CREATE TABLE safety_protocols (
            protocolId TEXT PRIMARY KEY,
            system TEXT NOT NULL,
            title TEXT NOT NULL,
            mandatoryBefore TEXT NOT NULL,
            searchKeywords TEXT NOT NULL,
            payloadJson TEXT NOT NULL,
            updatedAt INTEGER NOT NULL
        );
        """
    )

    if not include_graph:
        return

    conn.executescript(
        """
        CREATE TABLE dtc_symptoms (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            dtcCode TEXT NOT NULL,
            manufacturer TEXT NOT NULL DEFAULT 'GENERIC',
            symptomEs TEXT NOT NULL,
            symptomEn TEXT,
            probability TEXT NOT NULL,
            isDriverNoticeable INTEGER NOT NULL DEFAULT 1
        );
        CREATE INDEX idx_dtc_symptoms_code ON dtc_symptoms(dtcCode);

        CREATE TABLE dtc_causes (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            dtcCode TEXT NOT NULL,
            manufacturer TEXT NOT NULL DEFAULT 'GENERIC',
            causeEs TEXT NOT NULL,
            causeEn TEXT,
            probability TEXT NOT NULL,
            componentAffected TEXT,
            isElectronic INTEGER NOT NULL DEFAULT 0,
            isMechanical INTEGER NOT NULL DEFAULT 0
        );
        CREATE INDEX idx_dtc_causes_code ON dtc_causes(dtcCode);

        CREATE TABLE dtc_procedures (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            dtcCode TEXT NOT NULL,
            manufacturer TEXT NOT NULL DEFAULT 'GENERIC',
            stepNumber INTEGER NOT NULL,
            titleEs TEXT NOT NULL,
            descriptionEs TEXT NOT NULL,
            toolRequired TEXT,
            expectedValue TEXT,
            estimatedMinutes INTEGER NOT NULL DEFAULT 15,
            difficulty TEXT NOT NULL DEFAULT 'medio',
            icon TEXT NOT NULL DEFAULT 'tool'
        );
        CREATE INDEX idx_dtc_procedures_code ON dtc_procedures(dtcCode);

        CREATE TABLE dtc_related_pids (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            dtcCode TEXT NOT NULL,
            manufacturer TEXT NOT NULL DEFAULT 'GENERIC',
            pidCommand TEXT NOT NULL,
            pidNameEs TEXT NOT NULL,
            pidNameEn TEXT,
            normalRange TEXT,
            unit TEXT,
            priority INTEGER NOT NULL DEFAULT 0
        );
        CREATE INDEX idx_dtc_related_pids_code ON dtc_related_pids(dtcCode);

        CREATE TABLE dtc_co_occurrences (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            dtcCode TEXT NOT NULL,
            relatedDtcCode TEXT NOT NULL,
            correlationStrength REAL NOT NULL DEFAULT 0.5,
            combinedDiagnosisEs TEXT,
            combinedDiagnosisEn TEXT
        );
        CREATE INDEX idx_dtc_co_occurrences_code ON dtc_co_occurrences(dtcCode);
        CREATE INDEX idx_dtc_co_occurrences_related ON dtc_co_occurrences(relatedDtcCode);

        CREATE TABLE dtc_repair_costs (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            dtcCode TEXT NOT NULL,
            manufacturer TEXT NOT NULL DEFAULT 'GENERIC',
            region TEXT NOT NULL DEFAULT 'US',
            minCostUsd REAL NOT NULL,
            maxCostUsd REAL NOT NULL,
            laborHours REAL,
            partsDescription TEXT,
            currency TEXT NOT NULL DEFAULT 'USD',
            source TEXT,
            updatedAt INTEGER NOT NULL
        );
        CREATE INDEX idx_dtc_repair_costs_code ON dtc_repair_costs(dtcCode);
        """
    )


def record_to_definition(record):
    mapped = {
        "code": record.get("code", "").upper(),
        "manufacturer": record.get("manufacturer") or "GENERIC",
        "isGeneric": record.get("isGeneric", "true"),
        "descriptionEs": record.get("nameEs") or record.get("descriptionEs") or record.get("descriptionEn") or "Sin descripcion",
        "descriptionEn": record.get("nameEn") or record.get("descriptionEn") or record.get("descriptionEs") or "No description",
        "obd2StandardNameEn": record.get("obd2StandardNameEn"),
    }
    for column in DEFINITION_COLUMNS:
        mapped.setdefault(column, record.get(column))
    mapped["system"] = mapped.get("system") or "GENERAL"
    mapped["severity"] = mapped.get("severity") or "LOW"
    mapped["urgency"] = mapped.get("urgency") or "MONITOR"
    return [scalar(mapped.get(column)) for column in DEFINITION_COLUMNS]


def split_text_list(raw):
    if raw is None:
        return []
    if isinstance(raw, list):
        return [str(item).strip() for item in raw if str(item).strip()]
    if isinstance(raw, str):
        text = raw.strip()
        if not text:
            return []
        if "|" in text:
            return [part.strip() for part in text.split("|") if part.strip()]
        if "\n" in text:
            return [part.strip() for part in text.splitlines() if part.strip()]
        return [text]
    return [json.dumps(raw, ensure_ascii=False, separators=(",", ":"))]


def cause_flags(text):
    lowered = text.lower()
    is_electronic = any(term in lowered for term in ["circuit", "circuito", "sensor", "volt", "wire", "cable", "conector", "harness", "arnes", "module", "modulo"])
    is_mechanical = any(term in lowered for term in ["leak", "fuga", "valve", "valvula", "filter", "filtro", "pump", "bomba", "worn", "desgast", "compression", "compresion"])
    return int(is_electronic), int(is_mechanical)


def insert_graph_rows(conn, records):
    symptom_rows = []
    cause_rows = []
    procedure_rows = []
    pid_rows = []
    co_rows = []
    cost_rows = []

    for record in records:
        code = str(record.get("code", "")).upper()
        if not code:
            continue
        manufacturer = record.get("manufacturer") or "GENERIC"

        for symptom in split_text_list(record.get("symptoms")):
            symptom_rows.append((code, manufacturer, symptom, None, "media", 1))

        for index, cause in enumerate(split_text_list(record.get("possibleCauses"))):
            probability = "alta" if index == 0 else "media" if index == 1 else "baja"
            is_electronic, is_mechanical = cause_flags(cause)
            cause_rows.append((code, manufacturer, cause, None, probability, None, is_electronic, is_mechanical))

        for index, step in enumerate(split_text_list(record.get("diagnosticSteps")), start=1):
            clean = step.strip()
            title, description = (clean.split(":", 1) + [""])[:2] if ":" in clean else (f"Paso {index}", clean)
            procedure_rows.append((code, manufacturer, index, title.strip(), description.strip(), None, None, 10 + (index - 1) * 5, "facil" if index <= 2 else "medio" if index <= 4 else "dificil", "tool"))

        for index, pid in enumerate(split_text_list(record.get("freezeFramePIDs"))):
            if " -- " in pid:
                command, name = pid.split(" -- ", 1)
            elif " — " in pid:
                command, name = pid.split(" — ", 1)
            elif " - " in pid:
                command, name = pid.split(" - ", 1)
            else:
                command, name = pid, pid
            pid_rows.append((code, manufacturer, command.strip(), name.strip(), None, None, None, index))

        for related in split_text_list(record.get("relatedCodes")):
            related_code = related.strip().upper()
            if len(related_code) == 5 and related_code[0] in "PCBU" and all(ch in "0123456789ABCDEF" for ch in related_code[1:]):
                co_rows.append((code, related_code, 0.5, None, None))

        cost = record.get("repairCostUSD")
        if isinstance(cost, dict):
            min_cost = float(cost.get("minUSD") or 0)
            max_cost = float(cost.get("maxUSD") or min_cost)
            if min_cost > 0 or max_cost > 0:
                labor = record.get("laborHoursEstimate") if isinstance(record.get("laborHoursEstimate"), dict) else {}
                min_labor = labor.get("minH") if isinstance(labor, dict) else None
                cost_rows.append((code, manufacturer, "US", min_cost, max_cost, min_labor, cost.get("note"), "USD", "MEET_DTC_V4", 0))

    conn.executemany(
        "INSERT INTO dtc_symptoms (dtcCode, manufacturer, symptomEs, symptomEn, probability, isDriverNoticeable) VALUES (?, ?, ?, ?, ?, ?)",
        symptom_rows,
    )
    conn.executemany(
        "INSERT INTO dtc_causes (dtcCode, manufacturer, causeEs, causeEn, probability, componentAffected, isElectronic, isMechanical) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        cause_rows,
    )
    conn.executemany(
        "INSERT INTO dtc_procedures (dtcCode, manufacturer, stepNumber, titleEs, descriptionEs, toolRequired, expectedValue, estimatedMinutes, difficulty, icon) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        procedure_rows,
    )
    conn.executemany(
        "INSERT INTO dtc_related_pids (dtcCode, manufacturer, pidCommand, pidNameEs, pidNameEn, normalRange, unit, priority) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        pid_rows,
    )
    conn.executemany(
        "INSERT INTO dtc_co_occurrences (dtcCode, relatedDtcCode, correlationStrength, combinedDiagnosisEs, combinedDiagnosisEn) VALUES (?, ?, ?, ?, ?)",
        co_rows,
    )
    conn.executemany(
        "INSERT INTO dtc_repair_costs (dtcCode, manufacturer, region, minCostUsd, maxCostUsd, laborHours, partsDescription, currency, source, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        cost_rows,
    )

    return {
        "symptoms": len(symptom_rows),
        "causes": len(cause_rows),
        "procedures": len(procedure_rows),
        "related_pids": len(pid_rows),
        "co_occurrences": len(co_rows),
        "repair_costs": len(cost_rows),
    }


def insert_metadata(conn, metadata, records, graph_counts):
    manufacturers = sorted({record.get("manufacturer") or "GENERIC" for record in records})
    generic_count = sum(1 for record in records if bool(record.get("isGeneric")) is True)
    missing_related = sum(1 for record in records if not split_text_list(record.get("relatedCodes")))
    metrics = {
        "schemaVersion": metadata.get("schemaVersion", "unknown"),
        "source": metadata.get("source", "unknown"),
        "totalRecords": len(records),
        "totalFieldsPerRecord": len(DEFINITION_COLUMNS),
        "genericRecords": generic_count,
        "manufacturerSpecificRecords": len(records) - generic_count,
        "manufacturerCount": len(manufacturers),
        "manufacturers": ",".join(manufacturers),
        "recordsMissingRelatedCodes": missing_related,
    }
    for key, value in graph_counts.items():
        metrics[f"graph.{key}"] = value
    metrics["hybrid.matrixRows"] = len(MECHANICAL_SEED["meet_knowledge_matrix"])
    metrics["hybrid.symptomGuides"] = len(MECHANICAL_SEED["symptom_guides"])
    metrics["hybrid.mechanicalProcedures"] = len(MECHANICAL_SEED["mechanical_procedures"])
    metrics["hybrid.rebuildGuides"] = len(MECHANICAL_SEED["component_rebuild_guides"])
    metrics["hybrid.trenchKnowledge"] = len(MECHANICAL_SEED["trench_knowledge"])
    metrics["hybrid.chemicals"] = len(MECHANICAL_SEED["automotive_chemistry"])
    metrics["hybrid.tools"] = len(MECHANICAL_SEED["tool_usage_guides"])
    metrics["hybrid.safetyProtocols"] = len(MECHANICAL_SEED["safety_protocols"])

    conn.executemany(
        "INSERT INTO dtc_quality_report (metric, value) VALUES (?, ?)",
        [(key, scalar(value) or "") for key, value in metrics.items()],
    )
    conn.executemany(
        "INSERT INTO dtc_source_audit (sourceName, sourceUrl, notes) VALUES (?, ?, ?)",
        OFFICIAL_SOURCE_AUDIT,
    )


def insert_hybrid_knowledge(conn):
    now = 0

    conn.executemany(
        """
        INSERT OR REPLACE INTO safety_protocols
        (protocolId, system, title, mandatoryBefore, searchKeywords, payloadJson, updatedAt)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        [
            (
                row["protocolId"],
                row["system"],
                row["title"],
                row["mandatoryBefore"],
                row["searchKeywords"],
                scalar(row["payloadJson"]),
                now,
            )
            for row in MECHANICAL_SEED["safety_protocols"]
        ],
    )

    conn.executemany(
        """
        INSERT OR REPLACE INTO symptom_guides
        (symptomId, title, dangerLevel, searchKeywords, relatedDtcs, payloadJson, updatedAt)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        [
            (
                row["symptomId"],
                row["title"],
                row["dangerLevel"],
                row["searchKeywords"],
                row["relatedDtcs"],
                scalar(row["payloadJson"]),
                now,
            )
            for row in MECHANICAL_SEED["symptom_guides"]
        ],
    )

    conn.executemany(
        """
        INSERT OR REPLACE INTO mechanical_procedures
        (componentId, system, title, difficulty, estimatedTimeHours, searchKeywords, payloadJson, updatedAt)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        [
            (
                row["componentId"],
                row["system"],
                row["title"],
                row["difficulty"],
                row["estimatedTimeHours"],
                row["searchKeywords"],
                scalar(row["payloadJson"]),
                now,
            )
            for row in MECHANICAL_SEED["mechanical_procedures"]
        ],
    )

    conn.executemany(
        """
        INSERT OR REPLACE INTO component_rebuild_guides
        (componentId, rebuildPossible, searchKeywords, payloadJson, updatedAt)
        VALUES (?, ?, ?, ?, ?)
        """,
        [
            (
                row["componentId"],
                1 if row["rebuildPossible"] else 0,
                row["searchKeywords"],
                scalar(row["payloadJson"]),
                now,
            )
            for row in MECHANICAL_SEED["component_rebuild_guides"]
        ],
    )

    conn.executemany(
        """
        INSERT OR REPLACE INTO trench_knowledge
        (scenarioId, title, riskLevel, searchKeywords, payloadJson, updatedAt)
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        [
            (
                row["scenarioId"],
                row["title"],
                row["riskLevel"],
                row["searchKeywords"],
                scalar(row["payloadJson"]),
                now,
            )
            for row in MECHANICAL_SEED["trench_knowledge"]
        ],
    )

    conn.executemany(
        """
        INSERT OR REPLACE INTO automotive_chemistry
        (chemicalId, category, name, searchKeywords, payloadJson, updatedAt)
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        [
            (
                row["chemicalId"],
                row["category"],
                row["name"],
                row["searchKeywords"],
                scalar(row["payloadJson"]),
                now,
            )
            for row in MECHANICAL_SEED["automotive_chemistry"]
        ],
    )

    conn.executemany(
        """
        INSERT OR REPLACE INTO tool_usage_guides
        (toolId, name, searchKeywords, payloadJson, updatedAt)
        VALUES (?, ?, ?, ?, ?)
        """,
        [
            (
                row["toolId"],
                row["name"],
                row["searchKeywords"],
                scalar(row["payloadJson"]),
                now,
            )
            for row in MECHANICAL_SEED["tool_usage_guides"]
        ],
    )

    conn.executemany(
        """
        INSERT OR REPLACE INTO meet_knowledge_matrix
        (dtcCode, componentName, systemCategory, urgencyLevel, layerDiagnosticsJson, layerRebuildSpecsJson, layerTrenchKnowledgeJson, layerAdvancedEngJson, lastUpdated)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        [
            (
                row["dtcCode"],
                row["componentName"],
                row["systemCategory"],
                row["urgencyLevel"],
                scalar(row["layerDiagnosticsJson"]),
                scalar(row["layerRebuildSpecsJson"]),
                scalar(row["layerTrenchKnowledgeJson"]),
                scalar(row["layerAdvancedEngJson"]),
                now,
            )
            for row in MECHANICAL_SEED["meet_knowledge_matrix"]
        ],
    )


def build_database(source_path, output_path, include_graph):
    metadata, records = load_records(source_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    if output_path.exists():
        output_path.unlink()

    conn = sqlite3.connect(output_path)
    try:
        create_schema(conn, include_graph=include_graph)

        placeholders = ",".join("?" for _ in DEFINITION_COLUMNS)
        quoted_columns = ",".join(DEFINITION_COLUMNS)
        conn.executemany(
            f"INSERT OR REPLACE INTO dtc_definitions ({quoted_columns}) VALUES ({placeholders})",
            (record_to_definition(record) for record in records),
        )

        graph_counts = insert_graph_rows(conn, records) if include_graph else {}
        insert_hybrid_knowledge(conn)
        insert_metadata(conn, metadata, records, graph_counts)
        conn.commit()
        conn.execute("VACUUM")
    finally:
        conn.close()

    size_mb = output_path.stat().st_size / 1024 / 1024
    print(f"Built {output_path} from {len(records)} records ({size_mb:.1f} MB).")
    if include_graph:
        print("Knowledge graph rows:", ", ".join(f"{key}={value}" for key, value in graph_counts.items()))


def parse_args():
    parser = argparse.ArgumentParser(description="Build a Room-compatible enriched MEET DTC SQLite seed database.")
    parser.add_argument("--source", type=Path, default=SOURCE_JSON)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--include-graph", action="store_true", help="Also materialize DTC symptoms, causes, procedures, PIDs, co-occurrences, and costs.")
    return parser.parse_args()


def main():
    args = parse_args()
    build_database(args.source, args.output, include_graph=args.include_graph)


if __name__ == "__main__":
    main()
