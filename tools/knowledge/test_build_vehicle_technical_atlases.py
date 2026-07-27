import json
import unittest

from tools.knowledge.build_vehicle_technical_atlases import (
    SPECS,
    canonical_hash,
    parse_atlas,
)


class VehicleTechnicalAtlasesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.payloads = {
            key: parse_atlas(spec, spec.source_path.read_bytes())
            for key, spec in SPECS.items()
        }

    def test_expected_complete_counts(self):
        self.assertEqual(
            {
                "transmission_hydraulics": 838,
                "electrical": 1529,
                "body": 1665,
                "remaining_systems": 1953,
            },
            {
                key: len(payload["elements"])
                for key, payload in self.payloads.items()
            },
        )
        self.assertEqual(
            5985,
            sum(len(payload["elements"]) for payload in self.payloads.values()),
        )

    def test_ordinals_and_ids_are_unique(self):
        for key, payload in self.payloads.items():
            with self.subTest(atlas=key):
                count = len(payload["elements"])
                self.assertEqual(
                    list(range(1, count + 1)),
                    [element["ordinal"] for element in payload["elements"]],
                )
                ids = [element["canonicalId"] for element in payload["elements"]]
                self.assertEqual(len(ids), len(set(ids)))

    def test_hashes_match_canonical_payload(self):
        for key, payload in self.payloads.items():
            with self.subTest(atlas=key):
                self.assertEqual(payload["contentSha256"], canonical_hash(payload))

    def test_no_geometry_or_visual_compatibility_overclaim(self):
        for key, payload in self.payloads.items():
            with self.subTest(atlas=key):
                self.assertFalse(payload["geometryPolicy"]["oemClaim"])
                self.assertFalse(payload["geometryPolicy"]["vehicleSpecificClaim"])
                for element in payload["elements"]:
                    self.assertFalse(element["visual"]["oemClaim"])
                    self.assertFalse(element["visual"]["dimensional"])
                    self.assertFalse(
                        element["commerce"]["visualMatchIsExactCompatibility"]
                    )

    def test_semantic_regions_redirect_to_known_parent(self):
        for key, payload in self.payloads.items():
            ids = {element["canonicalId"] for element in payload["elements"]}
            semantic = [
                element
                for element in payload["elements"]
                if element["visual"]["renderStrategy"] == "SEMANTIC_REGION"
            ]
            self.assertTrue(semantic, key)
            for element in semantic:
                self.assertFalse(element["commerce"]["directlySellable"])
                self.assertTrue(element["commerce"]["redirectToParent"])
                self.assertIn(element["parentCanonicalId"], ids)

    def test_source_boundaries_exclude_followup_checklists(self):
        electrical_names = " ".join(
            element["nameOriginal"]
            for element in self.payloads["electrical"]["elements"]
        )
        body_names = " ".join(
            element["nameOriginal"] for element in self.payloads["body"]["elements"]
        )
        self.assertNotIn("Desconectar el borne negativo", electrical_names)
        self.assertNotIn("No comprar paneles solo", body_names)

    def test_remaining_systems_preserve_reset_source_ordinals_and_conditions(self):
        atlas = self.payloads["remaining_systems"]
        self.assertEqual(25, len(atlas["sections"]))
        self.assertEqual(1953, len(atlas["elements"]))
        rear_first = next(
            element
            for element in atlas["elements"]
            if element["sectionNumber"] == 2
        )
        self.assertEqual(1, rear_first["knowledgeBinding"]["sourceLocalOrdinal"])
        conditional_sections = [
            element
            for element in atlas["elements"]
            if element["sectionNumber"] in {21, 25}
        ]
        self.assertTrue(conditional_sections)
        self.assertTrue(
            all(
                element["elementKind"] == "CONDITIONAL_VARIANT"
                and element["applicability"]["installedState"]
                == "PENDING_PHYSICAL_CONFIRMATION"
                for element in conditional_sections
            )
        )

    def test_integration_gate_side_body_style_and_oem_state(self):
        for key, payload in self.payloads.items():
            identity_keys = []
            for element in payload["elements"]:
                self.assertTrue(element["systemId"])
                applicability = element["applicability"]
                name = element["nameOriginal"].lower()
                if "izquierd" in name:
                    self.assertIn(applicability["side"], {"LEFT", "LEFT_AND_RIGHT"})
                if "derech" in name:
                    self.assertIn(applicability["side"], {"RIGHT", "LEFT_AND_RIGHT"})
                if applicability["bodyStyleCondition"] == "HATCHBACK_ONLY":
                    self.assertNotIn("sedán 4 puertas", applicability["vehicleScope"])
                if element["elementKind"] == "CONDITIONAL_VARIANT":
                    self.assertEqual(
                        "PENDING_PHYSICAL_CONFIRMATION",
                        applicability["installedState"],
                    )
                normalization = element["normalization"]
                self.assertEqual("PENDING_VIN_EPC", normalization["oemResolutionState"])
                self.assertIsNone(normalization["oemNumber"])
                self.assertIsNone(normalization["quantity"])
                self.assertIsNone(normalization["supersededBy"])
                identity_keys.append(normalization["identityKey"])
            self.assertEqual(len(identity_keys), len(set(identity_keys)), key)


if __name__ == "__main__":
    unittest.main()
