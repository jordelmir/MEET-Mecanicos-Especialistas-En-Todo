import hashlib
import json
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
ATLAS_PATH = (
    REPO_ROOT
    / "android/app/src/main/assets/knowledge/g4ed/g4ed_engine_atlas.json"
)


class G4edEngineAtlasContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.raw = ATLAS_PATH.read_text(encoding="utf-8")
        cls.atlas = json.loads(cls.raw)
        cls.by_ordinal = {
            element["ordinal"]: element for element in cls.atlas["elements"]
        }

    def test_contains_exact_contiguous_420_elements(self):
        self.assertEqual(420, len(self.atlas["elements"]))
        self.assertEqual(list(range(1, 421)), sorted(self.by_ordinal))
        ids = [element["canonicalId"] for element in self.atlas["elements"]]
        self.assertEqual(len(ids), len(set(ids)))

    def test_content_hash_matches_canonical_payload(self):
        payload = dict(self.atlas)
        declared = payload.pop("contentSha256")
        canonical = json.dumps(
            payload,
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
        ).encode("utf-8")
        self.assertEqual(hashlib.sha256(canonical).hexdigest(), declared)

    def test_authority_never_claims_unavailable_oem_geometry(self):
        authorities = {
            element["visual"]["authority"] for element in self.atlas["elements"]
        }
        self.assertNotIn("OEM_VERIFIED", authorities)
        self.assertEqual(False, self.atlas["geometryPolicy"]["oemClaim"])
        self.assertEqual(
            "ILLUSTRATIVE_PROPORTIONS_ONLY",
            self.atlas["geometryPolicy"]["dimensionalState"],
        )

    def test_integrated_features_are_regions_and_not_directly_sellable(self):
        for ordinal in (2, 3, 4, 5, 6, 7, 8, 9, 10, 28, 29, 30):
            element = self.by_ordinal[ordinal]
            self.assertEqual("INTEGRATED_FEATURE", element["elementKind"])
            self.assertEqual("SEMANTIC_REGION", element["visual"]["renderStrategy"])
            self.assertEqual(False, element["commerce"]["directlySellable"])
            self.assertIsNotNone(element["parentCanonicalId"])

    def test_cvvt_is_conditional_and_requires_physical_confirmation(self):
        for ordinal in range(106, 110):
            element = self.by_ordinal[ordinal]
            self.assertEqual("CONDITIONAL_VARIANT", element["elementKind"])
            self.assertEqual(
                "PENDING_PHYSICAL_CONFIRMATION",
                element["applicability"]["installedState"],
            )
            self.assertIn("INSPECT_CYLINDER_HEAD", element["evidenceRequirements"])

    def test_automatic_scope_uses_flexplate_not_manual_clutch(self):
        flexplate = self.by_ordinal[360]
        self.assertIn("flexplate", flexplate["nameOriginal"].lower())
        self.assertEqual(True, flexplate["commerce"]["directlySellable"])
        names = " ".join(
            element["nameOriginal"].lower() for element in self.atlas["elements"]
        )
        self.assertNotIn("disco de clutch", names)
        self.assertNotIn("prensa de clutch", names)

    def test_all_elements_have_individual_3d_experience_contracts(self):
        for element in self.atlas["elements"]:
            visual = element["visual"]
            self.assertTrue(visual["packId"])
            self.assertTrue(visual["nodeKey"])
            self.assertTrue(visual["cameraPreset"])
            self.assertTrue(visual["interactionModes"])
            self.assertTrue(visual["animationMode"])

    def test_twenty_source_sections_preserve_knowledge(self):
        self.assertEqual(20, len(self.atlas["sections"]))
        for section in self.atlas["sections"]:
            self.assertTrue(section["title"])
            self.assertTrue(section["knowledge"])
            self.assertTrue(section["sourceReferences"])


if __name__ == "__main__":
    unittest.main()
