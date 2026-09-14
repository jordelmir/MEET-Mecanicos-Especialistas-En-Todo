#!/usr/bin/env python3
"""Check that CI status never claims evidence absent from the current run."""

import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


GENERATOR = Path(__file__).with_name("generate-proof-status.py")


class ProofStatusTest(unittest.TestCase):
    def generate(self, gate="success", artifact_bytes=b"artifact bytes", include_artifact=True):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "proof-status.json"
            artifact = root / "outputs/apk/release/app-release.apk"
            if artifact_bytes is not None:
                artifact.parent.mkdir(parents=True)
                artifact.write_bytes(artifact_bytes)
            command = [
                sys.executable, str(GENERATOR),
                "--output", str(output),
                "--commit", "test-commit",
                "--run-url", "https://example.test/ci/run/1",
                "--version-name", "test-version",
                "--version-code", "1",
                "--gate", f"release-gates={gate}",
            ]
            if include_artifact:
                command.extend(["--artifact", f"release-apk={artifact}"])
            result = subprocess.run(command, capture_output=True, text=True, check=False)
            return result, json.loads(output.read_text(encoding="utf-8"))

    def test_success_hashes_bytes_without_claiming_device_or_hardware_verification(self):
        result, proof = self.generate()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(proof["verificationLevels"]["softwareState"], "SOFTWARE_VERIFIED")
        self.assertEqual(proof["verificationLevels"]["deviceRuntime"], "DEVICE_UNVERIFIED")
        self.assertEqual(proof["hardwareConformance"], "PENDING_PHYSICAL_CORPUS")
        self.assertNotEqual(proof["verificationState"], "PRODUCTION_VALIDATED")
        self.assertEqual(
            proof["artifacts"]["release-apk"]["sha256"],
            hashlib.sha256(b"artifact bytes").hexdigest(),
        )

    def test_missing_artifact_fails_even_when_the_gate_passed_and_keeps_status(self):
        result, proof = self.generate(artifact_bytes=None)
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(proof["artifacts"]["release-apk"]["present"])
        self.assertIsNone(proof["artifacts"]["release-apk"]["sha256"])
        self.assertEqual(proof["verificationLevels"]["softwareState"], "SOFTWARE_UNVERIFIED")

    def test_empty_artifact_is_not_evidence(self):
        result, proof = self.generate(artifact_bytes=b"")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(proof["artifacts"]["release-apk"]["sizeBytes"], 0)
        self.assertEqual(proof["verificationLevels"]["softwareState"], "SOFTWARE_UNVERIFIED")

    def test_no_artifact_arguments_cannot_create_a_verified_status(self):
        result, proof = self.generate(include_artifact=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(proof["verificationLevels"]["softwareState"], "SOFTWARE_UNVERIFIED")

    def test_failed_cancelled_skipped_or_unknown_gates_publish_unverified_status(self):
        for outcome in ("failure", "cancelled", "skipped", "unexpected"):
            with self.subTest(outcome=outcome):
                result, proof = self.generate(gate=outcome)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(proof["verificationLevels"]["softwareState"], "SOFTWARE_UNVERIFIED")
                self.assertEqual(proof["verificationLevels"]["deviceRuntime"], "DEVICE_UNVERIFIED")

    def test_failed_build_can_publish_missing_artifacts_without_masking_failure(self):
        result, proof = self.generate(gate="failure", artifact_bytes=None)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(proof["gates"]["release-gates"], "FAILED")
        self.assertFalse(proof["artifacts"]["release-apk"]["present"])
        self.assertEqual(proof["verificationLevels"]["softwareState"], "SOFTWARE_UNVERIFIED")


if __name__ == "__main__":
    unittest.main()
