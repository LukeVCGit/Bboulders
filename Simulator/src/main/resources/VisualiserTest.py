#Test Suite for GPU-Accelerated Biofilm Visualization System

import unittest
import os
import shutil
import msgpack
import numpy as np
from pathlib import Path
import tempfile
import time
from unittest.mock import patch, MagicMock, Mock
import sys

from VisWithModernGL import (
calculate_visual_scale,
bacteria_data,
eps_data,
estimate_max_instances,
main
)

# UNIT TESTS - Test individual functions and methods

class TestUnitFunctions(unittest.TestCase):
    """Unit tests for individual functions using white-box and partition testing"""

    def setUp(self):
        self.test_dir = tempfile.mkdtemp(prefix="biofilm_test_")

    def tearDown(self):
        if os.path.exists(self.test_dir):
            shutil.rmtree(self.test_dir)

    # Partition Testing for calculate_visual_scale

    def test_visual_scale_empty_directory(self):
        result = calculate_visual_scale(self.test_dir)
        if isinstance(result, tuple):
            scale, center = result
            self.assertEqual(scale, 20.0)
            self.assertEqual(center, (0.0, 0.0))
        else:
            self.assertEqual(result, 20.0)

    def test_visual_scale_single_bacterium(self):
        data = {
            'bacteria': [
                {'position': {'x': 5.0, 'y': 5.0}, 'length': 2.0, 'angle': 0}
            ],
            'eps': []
        }
        with open(os.path.join(self.test_dir, "frame.boulder"), "wb") as f:
            msgpack.dump(data, f)

        result = calculate_visual_scale(self.test_dir)
        if isinstance(result, tuple):
            scale, center = result
            self.assertAlmostEqual(scale, 1.2, places=1)
            self.assertAlmostEqual(center[0], 5.0, places=1)
            self.assertAlmostEqual(center[1], 5.0, places=1)
        else:
            self.assertTrue(result > 0)

    def test_visual_scale_multiple_objects(self):
        data = {
            'bacteria': [
                {'position': {'x': 0, 'y': 0}, 'length': 1.0, 'angle': 0},
                {'position': {'x': 10, 'y': 10}, 'length': 1.0, 'angle': np.pi/2}
            ],
            'eps': [
                {'position': {'x': 5, 'y': 5}},
                {'position': {'x': -5, 'y': -5}}
            ]
        }
        with open(os.path.join(self.test_dir, "frame.boulder"), "wb") as f:
            msgpack.dump(data, f)

        result = calculate_visual_scale(self.test_dir)
        if isinstance(result, tuple):
            scale, center = result
            self.assertTrue(scale > 0)
            self.assertIsInstance(center, tuple)
            self.assertEqual(len(center), 2)
        else:
            self.assertTrue(result > 0)

    def test_visual_scale_extreme_positions(self):
        data = {
            'bacteria': [
                {'position': {'x': -1000, 'y': -1000}, 'length': 1.0, 'angle': 0},
                {'position': {'x': 1000, 'y': 1000}, 'length': 1.0, 'angle': 0}
            ],
            'eps': []
        }
        with open(os.path.join(self.test_dir, "frame.boulder"), "wb") as f:
            msgpack.dump(data, f)

        result = calculate_visual_scale(self.test_dir)
        if isinstance(result, tuple):
            scale, center = result
            self.assertAlmostEqual(center[0], 0.0, places=1)
            self.assertAlmostEqual(center[1], 0.0, places=1)
            self.assertTrue(scale > 1000)
        else:
            self.assertTrue(result > 1000)

    # White-box Testing for bacteria_data

    def test_bacteria_data_zero_angle(self):
        bacterium = {
            'position': {'x': 10, 'y': 20},
            'length': 4.0,
            'angle': 0.0
        }
        result = bacteria_data(bacterium, 1.0, 50.0, (0, 0))

        self.assertAlmostEqual(result[0], 0.2)
        self.assertAlmostEqual(result[1], 0.4)
        self.assertAlmostEqual(result[2], 0.08)
        self.assertAlmostEqual(result[4], 0.0)
        self.assertEqual(result[8], 0.0)

    def test_bacteria_data_with_rotation(self):
        bacterium = {
            'position': {'x': 0, 'y': 0},
            'length': 2.0,
            'angle': np.pi/2
        }
        result = bacteria_data(bacterium, 1.0, 10.0, (5, 5))

        self.assertAlmostEqual(result[0], -0.5)
        self.assertAlmostEqual(result[1], -0.5)
        self.assertAlmostEqual(result[4], np.pi/2)
        self.assertEqual(result[8], 0.0)

    # Testing for eps_data 

    def test_eps_data_transformation(self):
        eps = {'position': {'x': 15, 'y': 25}}
        result = eps_data(eps, 100.0, (10, 20))

        self.assertAlmostEqual(result[0], 0.05)
        self.assertAlmostEqual(result[1], 0.05)
        self.assertEqual(result[8], 1.0)

    # Testing estimate_max_instances

    def test_estimate_max_instances_empty(self):
        result = estimate_max_instances(self.test_dir)
        self.assertEqual(result, 1000)

    def test_estimate_max_instances_with_data(self):
        data = {
            'bacteria': [{'position': {'x': i, 'y': i}, 'length': 1.0, 'angle': 0}
                         for i in range(50)],
            'eps': [{'position': {'x': i, 'y': i}} for i in range(30)]
        }
        with open(os.path.join(self.test_dir, "frame.boulder"), "wb") as f:
            msgpack.dump(data, f)

        result = estimate_max_instances(self.test_dir)
        self.assertEqual(result, 120)


# Integration tests - Test interaction between components

class TestIntegration(unittest.TestCase):
    """Integration tests for component interactions"""

    def setUp(self):
        self.test_dir = tempfile.mkdtemp(prefix="biofilm_int_test_")
        self.output_file = os.path.join(self.test_dir, "test_output.mp4")

    def tearDown(self):
        if os.path.exists(self.test_dir):
            shutil.rmtree(self.test_dir)

    def test_coordinate_transformation_pipeline(self):
        """Test the complete pipeline from raw data to normalized coordinates"""
        test_bacteria = [
            {'position': {'x': -10, 'y': -10}, 'length': 2.0, 'angle': 0},
            {'position': {'x': 10, 'y': 10}, 'length': 2.0, 'angle': np.pi/4}
        ]
        test_eps = [
            {'position': {'x': 0, 'y': 0}},
            {'position': {'x': 5, 'y': -5}}
        ]

        data = {'bacteria': test_bacteria, 'eps': test_eps}
        with open(os.path.join(self.test_dir, "frame.boulder"), "wb") as f:
            msgpack.dump(data, f)

        result = calculate_visual_scale(self.test_dir)
        if isinstance(result, tuple):
            scale, center = result
        else:
            scale = result
            center = (0, 0)

        b_data = bacteria_data(test_bacteria[0], 1.0, scale, center)
        e_data = eps_data(test_eps[0], scale, center)

        self.assertTrue(-1.5 <= b_data[0] <= 1.5)
        self.assertTrue(-1.5 <= b_data[1] <= 1.5)
        self.assertTrue(-1.5 <= e_data[0] <= 1.5)
        self.assertTrue(-1.5 <= e_data[1] <= 1.5)

    @patch('VisWithModernGL.moderngl')
    @patch('VisWithModernGL.imageio')
    def test_rendering_pipeline_mock(self, mock_imageio, mock_moderngl):
        """Test the rendering pipeline with mocked GPU context"""
        data = {
            'bacteria': [{'position': {'x': 0, 'y': 0}, 'length': 1.0, 'angle': 0}],
            'eps': [{'position': {'x': 1, 'y': 1}}]
        }
        with open(os.path.join(self.test_dir, "frame.boulder"), "wb") as f:
            msgpack.dump(data, f)

        mock_ctx = MagicMock()
        mock_moderngl.create_standalone_context.return_value = mock_ctx
        mock_ctx.info = {'GL_RENDERER': 'Mock Renderer'}
        mock_ctx.program.return_value = MagicMock()
        mock_ctx.buffer.return_value = MagicMock()
        mock_ctx.vertex_array.return_value = MagicMock()

        mock_fbo = MagicMock()
        mock_fbo.size = (1024, 1024)
        mock_fbo.read.return_value = b'\x00' * (1024 * 1024 * 3)
        mock_ctx.framebuffer.return_value = mock_fbo

        mock_ctx.texture.return_value = MagicMock()

        mock_moderngl.BLEND = 1
        mock_moderngl.SRC_ALPHA = 1
        mock_moderngl.ONE_MINUS_SRC_ALPHA = 1
        mock_moderngl.TRIANGLE_STRIP = 1

        mock_writer = MagicMock()
        mock_imageio.get_writer.return_value = mock_writer

        class MockArgs:
            data_dir = self.test_dir
            output = self.output_file

        try:
            main(MockArgs())
        except Exception as e:
            self.fail(f"Rendering pipeline failed: {e}")

        mock_moderngl.create_standalone_context.assert_called_once()
        mock_imageio.get_writer.assert_called_once()


# Validation tests

class TestValidation(unittest.TestCase):
    """Validation tests to ensure requirements are met"""

    def setUp(self):
        self.test_dir = tempfile.mkdtemp(prefix="biofilm_val_test_")
        self.output_file = os.path.join(self.test_dir, "test_output.mp4")

    def tearDown(self):
        if os.path.exists(self.test_dir):
            shutil.rmtree(self.test_dir)

    def test_handles_variable_object_counts(self):
        """Requirement: System should handle varying numbers of objects per frame"""
        for i, count in enumerate([1, 10, 100, 0, 50]):
            data = {
                'bacteria': [{'position': {'x': j, 'y': j}, 'length': 1.0, 'angle': 0}
                             for j in range(count)],
                'eps': []
            }
            with open(os.path.join(self.test_dir, f"frame{i:04d}.boulder"), "wb") as f:
                msgpack.dump(data, f)

        result = calculate_visual_scale(self.test_dir)
        if isinstance(result, tuple):
            scale, center = result
            self.assertIsInstance(scale, float)
            self.assertIsInstance(center, tuple)
        else:
            self.assertIsInstance(result, float)

    def test_color_differentiation(self):
        """Requirement: Bacteria and EPS should have different colors"""
        bacterium = {'position': {'x': 0, 'y': 0}, 'length': 1.0, 'angle': 0}
        eps = {'position': {'x': 0, 'y': 0}}

        b_data = bacteria_data(bacterium, 1.0, 10.0, (0, 0))
        e_data = eps_data(eps, 10.0, (0, 0))

        b_color = b_data[5:8]
        e_color = e_data[5:8]

        self.assertNotEqual(b_color, e_color)
        self.assertTrue(b_color[2] > b_color[0])
        self.assertTrue(e_color[0] > e_color[2])

    def test_shape_differentiation(self):
        """Requirement: Bacteria should be capsule-shaped, EPS circular"""
        bacterium = {'position': {'x': 0, 'y': 0}, 'length': 2.0, 'angle': 0}
        eps = {'position': {'x': 0, 'y': 0}}

        b_data = bacteria_data(bacterium, 1.0, 10.0, (0, 0))
        e_data = eps_data(eps, 10.0, (0, 0))

        self.assertEqual(b_data[8], 0.0)
        self.assertEqual(e_data[8], 1.0)

    def test_maintains_aspect_ratio(self):
        """Requirement: Visualization should maintain proper aspect ratios"""
        data = {
            'bacteria': [
                {'position': {'x': 0, 'y': 0}, 'length': 1.0, 'angle': 0},
                {'position': {'x': 100, 'y': 10}, 'length': 1.0, 'angle': 0}
            ],
            'eps': []
        }
        with open(os.path.join(self.test_dir, "frame.boulder"), "wb") as f:
            msgpack.dump(data, f)

        result = calculate_visual_scale(self.test_dir)
        if isinstance(result, tuple):
            scale, center = result
            self.assertTrue(scale > 50)
        else:
            self.assertTrue(result > 50)


# system tests

class TestSystem(unittest.TestCase):
    """System tests for robustness and performance"""

    def setUp(self):
        self.test_dir = tempfile.mkdtemp(prefix="biofilm_sys_test_")
        self.output_file = os.path.join(self.test_dir, "test_output.mp4")

    def tearDown(self):
        if os.path.exists(self.test_dir):
            shutil.rmtree(self.test_dir)

    def test_stress_large_dataset(self):
        """Stress test: Handle large number of objects"""
        data = {
            'bacteria': [{'position': {'x': i*0.1, 'y': j*0.1},
                          'length': 1.0, 'angle': 0}
                         for i in range(100) for j in range(100)],
            'eps': [{'position': {'x': i*0.2, 'y': j*0.2}}
                    for i in range(50) for j in range(50)]
        }
        with open(os.path.join(self.test_dir, "stress_test.boulder"), "wb") as f:
            msgpack.dump(data, f)

        result = calculate_visual_scale(self.test_dir)
        max_instances = estimate_max_instances(self.test_dir)

        if isinstance(result, tuple):
            scale, center = result
            self.assertIsInstance(scale, float)
        else:
            self.assertIsInstance(result, float)
        self.assertGreater(max_instances, 12500)

    def test_recovery_corrupted_file(self):
        """Recovery test: Handle corrupted data files gracefully"""
        data = {'bacteria': [], 'eps': []}
        with open(os.path.join(self.test_dir, "good.boulder"), "wb") as f:
            msgpack.dump(data, f)

        with open(os.path.join(self.test_dir, "bad.boulder"), "wb") as f:
            f.write(b"This is not msgpack data")

        result = calculate_visual_scale(self.test_dir)
        if isinstance(result, tuple):
            scale, center = result
            self.assertIsInstance(scale, float)
        else:
            self.assertEqual(result, 20.0)

    def test_performance_timing(self):
        """Performance test: Measure processing time"""
        for i in range(10):
            data = {
                'bacteria': [{'position': {'x': j, 'y': j},
                              'length': 1.0, 'angle': 0}
                             for j in range(100)],
                'eps': [{'position': {'x': j, 'y': -j}} for j in range(50)]
            }
            with open(os.path.join(self.test_dir, f"frame{i:04d}.boulder"), "wb") as f:
                msgpack.dump(data, f)

        start_time = time.time()
        result = calculate_visual_scale(self.test_dir)
        elapsed = time.time() - start_time

        self.assertLess(elapsed, 1.0)
        print(f"\nPerformance: Processed 10 frames in {elapsed:.3f} seconds")

    def test_boundary_empty_frames(self):
        """Boundary test: Handle empty frames in sequence"""
        for i in range(5):
            if i % 2 == 0:
                data = {'bacteria': [], 'eps': []}
            else:
                data = {
                    'bacteria': [{'position': {'x': 0, 'y': 0},
                                  'length': 1.0, 'angle': 0}],
                    'eps': []
                }
            with open(os.path.join(self.test_dir, f"frame{i:04d}.boulder"), "wb") as f:
                msgpack.dump(data, f)

        result = calculate_visual_scale(self.test_dir)
        if isinstance(result, tuple):
            scale, center = result
            self.assertIsInstance(scale, float)
            self.assertIsInstance(center, tuple)
        else:
            self.assertIsInstance(result, float)

    def test_invalid_data_types(self):
        """Test handling of invalid data types in input files"""
        data1 = {'bacteria': [{'position': {'x': 0}}], 'eps': []}

        data2 = {'bacteria': [{'position': {'x': 'not_a_number', 'y': 0},
                              'length': 1.0, 'angle': 0}], 'eps': []}

        with open(os.path.join(self.test_dir, "invalid1.boulder"), "wb") as f:
            msgpack.dump(data1, f)
        with open(os.path.join(self.test_dir, "invalid2.boulder"), "wb") as f:
            msgpack.dump(data2, f)

        try:
            result = calculate_visual_scale(self.test_dir)
            if isinstance(result, tuple):
                scale, center = result
                self.assertIsInstance(scale, float)
            else:
                self.assertIsInstance(result, float)
        except Exception as e:
            self.assertIsInstance(e, (KeyError, TypeError, ValueError))


# Run the test suite and generate report

def run_test_suite():
    """Run the complete test suite and generate report"""

    loader = unittest.TestLoader()
    suite = unittest.TestSuite()

    suite.addTests(loader.loadTestsFromTestCase(TestUnitFunctions))
    suite.addTests(loader.loadTestsFromTestCase(TestIntegration))
    suite.addTests(loader.loadTestsFromTestCase(TestValidation))
    suite.addTests(loader.loadTestsFromTestCase(TestSystem))

    runner = unittest.TextTestRunner(verbosity=2)
    result = runner.run(suite)

    print("\n" + "="*60)
    print("TEST SUMMARY REPORT")
    print("="*60)
    print(f"Tests Run: {result.testsRun}")
    print(f"Failures: {len(result.failures)}")
    print(f"Errors: {len(result.errors)}")
    print(f"Success Rate: {((result.testsRun - len(result.failures) - len(result.errors)) / result.testsRun * 100):.1f}%")

    if result.failures:
        print("\nFailed Tests:")
        for test, traceback in result.failures:
            print(f" - {test}")

    if result.errors:
        print("\nTests with Errors:")
        for test, traceback in result.errors:
            print(f" - {test}")

    return result


if __name__ == '__main__':
    result = run_test_suite()

    sys.exit(0 if result.wasSuccessful() else 1)