"""
Fixed Test Suite for GPU-Accelerated Biofilm Visualization System
Corrected index issues and mock setup
"""

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

# Import the main module (adjust path as needed)
from VisWithModernGL import (
    calculate_visual_scale, 
    bacteria_data, 
    eps_data, 
    estimate_max_instances,
    main
)

# ============================================================================
# UNIT TESTS - Test individual functions and methods
# ============================================================================

class TestUnitFunctions(unittest.TestCase):
    """Unit tests for individual functions using white-box and partition testing"""
    
    def setUp(self):
        """Create test environment"""
        self.test_dir = tempfile.mkdtemp(prefix="biofilm_test_")
        
    def tearDown(self):
        """Clean up test environment"""
        if os.path.exists(self.test_dir):
            shutil.rmtree(self.test_dir)
    
    # --- Partition Testing for calculate_visual_scale ---
    
    def test_visual_scale_empty_directory(self):
        """Test boundary case: empty directory"""
        result = calculate_visual_scale(self.test_dir)
        # Check if it returns tuple or single value
        if isinstance(result, tuple):
            scale, center = result
            self.assertEqual(scale, 20.0)  # Default value
            self.assertEqual(center, (0.0, 0.0))
        else:
            # Old version might return only scale
            self.assertEqual(result, 20.0)
    
    def test_visual_scale_single_bacterium(self):
        """Test normal case: single bacterium"""
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
            # Bacterium extends from (4, 5) to (6, 5), so range is 2
            # With buffer 1.2: scale = (2 * 1.2) / 2 = 1.2
            self.assertAlmostEqual(scale, 1.2, places=1)
            self.assertAlmostEqual(center[0], 5.0, places=1)
            self.assertAlmostEqual(center[1], 5.0, places=1)
        else:
            self.assertTrue(result > 0)
    
    def test_visual_scale_multiple_objects(self):
        """Test normal case: multiple bacteria and EPS"""
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
        """Test boundary case: very large coordinate values"""
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
    
    # --- White-box Testing for bacteria_data ---
    
    def test_bacteria_data_zero_angle(self):
        """Test bacteria transformation with zero angle"""
        bacterium = {
            'position': {'x': 10, 'y': 20},
            'length': 4.0,
            'angle': 0.0
        }
        result = bacteria_data(bacterium, 1.0, 50.0, (0, 0))
        
        # The function returns: [pos_x, pos_y, size_x, size_y, angle, r, g, b, shape]
        # So shape is at index 8, not 7
        self.assertAlmostEqual(result[0], 0.2)  # pos_x = 10/50 = 0.2
        self.assertAlmostEqual(result[1], 0.4)  # pos_y = 20/50 = 0.4
        self.assertAlmostEqual(result[2], 0.08)  # length/scale
        self.assertAlmostEqual(result[4], 0.0)   # angle
        self.assertEqual(result[8], 0.0)  # shape type (index 8, not 7)
    
    def test_bacteria_data_with_rotation(self):
        """Test bacteria transformation with rotation"""
        bacterium = {
            'position': {'x': 0, 'y': 0},
            'length': 2.0,
            'angle': np.pi/2
        }
        result = bacteria_data(bacterium, 1.0, 10.0, (5, 5))
        
        # After translation: (-5, -5), after scaling: (-0.5, -0.5)
        self.assertAlmostEqual(result[0], -0.5)
        self.assertAlmostEqual(result[1], -0.5)
        self.assertAlmostEqual(result[4], np.pi/2)
        self.assertEqual(result[8], 0.0)  # shape type for bacteria
    
    # --- Testing for eps_data ---
    
    def test_eps_data_transformation(self):
        """Test EPS particle transformation"""
        eps = {'position': {'x': 15, 'y': 25}}
        result = eps_data(eps, 100.0, (10, 20))
        
        # After translation: (5, 5), after scaling: (0.05, 0.05)
        self.assertAlmostEqual(result[0], 0.05)
        self.assertAlmostEqual(result[1], 0.05)
        # The function returns: [pos_x, pos_y, size_x, size_y, angle, r, g, b, shape]
        self.assertEqual(result[8], 1.0)  # shape type for EPS (index 8, not 7)
    
    # --- Testing estimate_max_instances ---
    
    def test_estimate_max_instances_empty(self):
        """Test max instances estimation with no files"""
        result = estimate_max_instances(self.test_dir)
        self.assertEqual(result, 1000)  # Default fallback
    
    def test_estimate_max_instances_with_data(self):
        """Test max instances estimation with actual data"""
        data = {
            'bacteria': [{'position': {'x': i, 'y': i}, 'length': 1.0, 'angle': 0} 
                        for i in range(50)],
            'eps': [{'position': {'x': i, 'y': i}} for i in range(30)]
        }
        with open(os.path.join(self.test_dir, "frame.boulder"), "wb") as f:
            msgpack.dump(data, f)
        
        result = estimate_max_instances(self.test_dir)
        # 80 instances * 1.5 buffer = 120
        self.assertEqual(result, 120)


# ============================================================================
# INTEGRATION TESTS - Test interaction between components
# ============================================================================

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
        # Create test data with known positions
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
        
        # Calculate scale and center
        result = calculate_visual_scale(self.test_dir)
        if isinstance(result, tuple):
            scale, center = result
        else:
            scale = result
            center = (0, 0)  # Default center if not returned
        
        # Transform bacteria data
        b_data = bacteria_data(test_bacteria[0], 1.0, scale, center)
        e_data = eps_data(test_eps[0], scale, center)
        
        # All transformed coordinates should be in [-1, 1] range
        self.assertTrue(-1.5 <= b_data[0] <= 1.5)  # Allow some margin for expansion
        self.assertTrue(-1.5 <= b_data[1] <= 1.5)
        self.assertTrue(-1.5 <= e_data[0] <= 1.5)
        self.assertTrue(-1.5 <= e_data[1] <= 1.5)
    
    @patch('VisWithModernGL.moderngl')
    @patch('VisWithModernGL.imageio')
    def test_rendering_pipeline_mock(self, mock_imageio, mock_moderngl):
        """Test the rendering pipeline with mocked GPU context"""
        # Create simple test data
        data = {
            'bacteria': [{'position': {'x': 0, 'y': 0}, 'length': 1.0, 'angle': 0}],
            'eps': [{'position': {'x': 1, 'y': 1}}]
        }
        with open(os.path.join(self.test_dir, "frame.boulder"), "wb") as f:
            msgpack.dump(data, f)
        
        # Mock the GPU context with proper structure
        mock_ctx = MagicMock()
        mock_moderngl.create_standalone_context.return_value = mock_ctx
        mock_ctx.info = {'GL_RENDERER': 'Mock Renderer'}
        mock_ctx.program.return_value = MagicMock()
        mock_ctx.buffer.return_value = MagicMock()
        mock_ctx.vertex_array.return_value = MagicMock()
        
        # Mock framebuffer with proper size attribute
        mock_fbo = MagicMock()
        mock_fbo.size = (1024, 1024)  # This is crucial - size must be a tuple
        mock_fbo.read.return_value = b'\x00' * (1024 * 1024 * 3)  # RGB data
        mock_ctx.framebuffer.return_value = mock_fbo
        
        mock_ctx.texture.return_value = MagicMock()
        
        # Mock blend constants
        mock_moderngl.BLEND = 1
        mock_moderngl.SRC_ALPHA = 1
        mock_moderngl.ONE_MINUS_SRC_ALPHA = 1
        mock_moderngl.TRIANGLE_STRIP = 1
        
        # Mock imageio writer
        mock_writer = MagicMock()
        mock_imageio.get_writer.return_value = mock_writer
        
        # Create mock args
        class MockArgs:
            data_dir = self.test_dir
            output = self.output_file
        
        # This should run without errors
        try:
            main(MockArgs())
        except Exception as e:
            self.fail(f"Rendering pipeline failed: {e}")
        
        # Verify that rendering methods were called
        mock_moderngl.create_standalone_context.assert_called_once()
        mock_imageio.get_writer.assert_called_once()


# ============================================================================
# VALIDATION TESTS - Test against requirements
# ============================================================================

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
        # Create frames with different object counts
        for i, count in enumerate([1, 10, 100, 0, 50]):
            data = {
                'bacteria': [{'position': {'x': j, 'y': j}, 'length': 1.0, 'angle': 0} 
                           for j in range(count)],
                'eps': []
            }
            with open(os.path.join(self.test_dir, f"frame{i:04d}.boulder"), "wb") as f:
                msgpack.dump(data, f)
        
        # Calculate scale should handle all frames
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
        
        # Check colors are different (bacteria blue, EPS red)
        # Colors are at indices 5,6,7 in the returned array
        b_color = b_data[5:8]  # RGB values
        e_color = e_data[5:8]
        
        self.assertNotEqual(b_color, e_color)
        self.assertTrue(b_color[2] > b_color[0])  # More blue than red
        self.assertTrue(e_color[0] > e_color[2])  # More red than blue
    
    def test_shape_differentiation(self):
        """Requirement: Bacteria should be capsule-shaped, EPS circular"""
        bacterium = {'position': {'x': 0, 'y': 0}, 'length': 2.0, 'angle': 0}
        eps = {'position': {'x': 0, 'y': 0}}
        
        b_data = bacteria_data(bacterium, 1.0, 10.0, (0, 0))
        e_data = eps_data(eps, 10.0, (0, 0))
        
        # Check shape types (at index 8)
        self.assertEqual(b_data[8], 0.0)  # Capsule for bacteria
        self.assertEqual(e_data[8], 1.0)  # Circle for EPS
    
    def test_maintains_aspect_ratio(self):
        """Requirement: Visualization should maintain proper aspect ratios"""
        # Create asymmetric data
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
            # The scale should accommodate the larger dimension
            self.assertTrue(scale > 50)  # Should be based on larger dimension
        else:
            self.assertTrue(result > 50)


# ============================================================================
# SYSTEM TESTS - Test system behavior under various conditions
# ============================================================================

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
        # Create frame with many objects
        data = {
            'bacteria': [{'position': {'x': i*0.1, 'y': j*0.1}, 
                         'length': 1.0, 'angle': 0} 
                        for i in range(100) for j in range(100)],  # 10,000 bacteria
            'eps': [{'position': {'x': i*0.2, 'y': j*0.2}} 
                   for i in range(50) for j in range(50)]  # 2,500 EPS
        }
        with open(os.path.join(self.test_dir, "stress_test.boulder"), "wb") as f:
            msgpack.dump(data, f)
        
        # Should handle without crashing
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
        # Create valid file
        data = {'bacteria': [], 'eps': []}
        with open(os.path.join(self.test_dir, "good.boulder"), "wb") as f:
            msgpack.dump(data, f)
        
        # Create corrupted file
        with open(os.path.join(self.test_dir, "bad.boulder"), "wb") as f:
            f.write(b"This is not msgpack data")
        
        # Should skip corrupted file and continue
        result = calculate_visual_scale(self.test_dir)
        # The function should return default values when no valid data
        if isinstance(result, tuple):
            scale, center = result
            self.assertIsInstance(scale, float)
        else:
            # If it returns only scale (20.0 default), that's fine too
            self.assertEqual(result, 20.0)
    
    def test_performance_timing(self):
        """Performance test: Measure processing time"""
        # Create 10 frames with moderate complexity
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
        
        # Should complete in reasonable time (< 1 second for 10 frames)
        self.assertLess(elapsed, 1.0)
        print(f"\nPerformance: Processed 10 frames in {elapsed:.3f} seconds")
    
    def test_boundary_empty_frames(self):
        """Boundary test: Handle empty frames in sequence"""
        # Create mix of empty and populated frames
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
        
        # Should handle mixed empty/populated frames
        result = calculate_visual_scale(self.test_dir)
        if isinstance(result, tuple):
            scale, center = result
            self.assertIsInstance(scale, float)
            self.assertIsInstance(center, tuple)
        else:
            self.assertIsInstance(result, float)
    
    def test_invalid_data_types(self):
        """Test handling of invalid data types in input files"""
        # Missing required fields
        data1 = {'bacteria': [{'position': {'x': 0}}], 'eps': []}  # Missing y
        
        # Wrong data types
        data2 = {'bacteria': [{'position': {'x': 'not_a_number', 'y': 0}, 
                              'length': 1.0, 'angle': 0}], 'eps': []}
        
        # Create files
        with open(os.path.join(self.test_dir, "invalid1.boulder"), "wb") as f:
            msgpack.dump(data1, f)
        with open(os.path.join(self.test_dir, "invalid2.boulder"), "wb") as f:
            msgpack.dump(data2, f)
        
        # Should handle gracefully (not crash)
        try:
            result = calculate_visual_scale(self.test_dir)
            # May return default values but shouldn't crash
            if isinstance(result, tuple):
                scale, center = result
                self.assertIsInstance(scale, float)
            else:
                self.assertIsInstance(result, float)
        except Exception as e:
            # If it does raise an exception, it should be a specific one, not a crash
            self.assertIsInstance(e, (KeyError, TypeError, ValueError))


# ============================================================================
# TEST SUITE RUNNER
# ============================================================================

def run_test_suite():
    """Run the complete test suite and generate report"""
    
    # Create test loader
    loader = unittest.TestLoader()
    suite = unittest.TestSuite()
    
    # Add test classes
    suite.addTests(loader.loadTestsFromTestCase(TestUnitFunctions))
    suite.addTests(loader.loadTestsFromTestCase(TestIntegration))
    suite.addTests(loader.loadTestsFromTestCase(TestValidation))
    suite.addTests(loader.loadTestsFromTestCase(TestSystem))
    
    # Run tests with verbosity
    runner = unittest.TextTestRunner(verbosity=2)
    result = runner.run(suite)
    
    # Generate summary report
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
            print(f"  - {test}")
    
    if result.errors:
        print("\nTests with Errors:")
        for test, traceback in result.errors:
            print(f"  - {test}")
    
    return result


if __name__ == '__main__':
    # Run the test suite
    result = run_test_suite()
    
    # Exit with appropriate code
    sys.exit(0 if result.wasSuccessful() else 1)