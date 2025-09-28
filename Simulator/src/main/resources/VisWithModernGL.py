import argparse
import json
import msgpack
import os
import glob
import numpy as np
import imageio.v2 as imageio
import moderngl
from PIL import Image

DATA_DIRECTORY = 'simulation_data'
OUTPUT_VIDEO = 'video_gpu.mp4'
FPS = 30
FRAME_SIZE = (1024, 1024)
BACTERIA_WIDTH = 1.0 
VISUAL_SCALE_BUFFER = 1.2 

# Vertex Shader positions each instance (bacterium or EPS) correctly on screen
VERTEX_SHADER = """
    #version 330

    in vec2 in_vert;     // Base vertex positions of shape
    in vec2 in_pos;      // Center position [-1, 1] 
    in vec2 in_size;     // length and width
    in float in_angle;   
    in vec3 in_color;    
    in float in_shape;   // shape type: 0 = capsule (bacteria), 1 = circle (EPS)

    // Outputs that get passed to the Fragment Shader.
    out vec3 v_color;
    out vec2 v_uv;       // UV coordinates for shape rendering
    out vec2 v_size;     
    out float v_shape;   

    void main() {
        // Pass data to fragment shader
        v_color = in_color;
        v_size = in_size;
        v_shape = in_shape;
        v_uv = in_vert;

        // 2D rotation matrix
        mat2 rotation = mat2(
            cos(in_angle), -sin(in_angle),
            sin(in_angle), cos(in_angle)
        );

        // Calculate the final position of the vertex 
        float expand = (in_shape < 0.5) ? 1.2 : 1.1;
        vec2 pos = (in_vert * in_size * expand) * rotation + in_pos;

        gl_Position = vec4(pos, 0.0, 1.0);
    }
"""

# Fragment Shader renders bacteria or EPS with smooth edges using Signed Distance Functions (SDF)
FRAGMENT_SHADER = """
    #version 330

    in vec3 v_color;
    in vec2 v_uv;
    in vec2 v_size;
    in float v_shape;

    out vec4 f_color;

    void main() {
        float alpha;

        if (v_shape < 0.5) {
            // Bacteria Capsule SDF

            float aspect = v_size.x / v_size.y;
            float radius = 0.5;
            float line_half_length = max(0.0, (aspect - 1.0) / 2.0);

            vec2 segment_start = vec2(-line_half_length, 0.0);
            vec2 segment_end   = vec2( line_half_length, 0.0);
            vec2 p = vec2(v_uv.x * aspect, v_uv.y);
            
            vec2 line_vec = segment_end - segment_start;
            vec2 point_vec = p - segment_start;

            float dist;
            // Handle the case of a perfect circle (aspect <= 1) to avoid division by zero.
            if (line_half_length == 0.0) {
                dist = length(p) - radius;
            } else {
                float h = clamp(dot(point_vec, line_vec) / dot(line_vec, line_vec), 0.0, 1.0);
                dist = length(point_vec - line_vec * h) - radius;
            }

            float aa = 0.02 / aspect;
            alpha = 1.0 - smoothstep(-aa, aa, dist);

            if (alpha < 0.01) {
                discard; // Optimization: Don't draw fully transparent pixels.
            }

        } else {
            // EPS circle SDF
            float radius = 0.5;
            float dist = length(v_uv) - radius;

            float aa = 0.02;
            alpha = 1.0 - smoothstep(-aa, aa, dist);

            if (alpha < 0.01) {
                discard;
            }
        }

        f_color = vec4(v_color, alpha);
    }
"""

def calculate_visual_scale(data_dir, buffer_factor=1.2):
    """Calculate scale and center offset so all objects fit into [-1,1] space."""
    
    files = sorted(glob.glob(os.path.join(data_dir, "*.boulder")))
    if not files:
        print(f"No MessagePack (.boulder) files found in '{data_dir}'.")
        return 20.0, (0.0, 0.0)  # Default
    
    all_x_coords = []
    all_y_coords = []
    
    # Sample files to determine extent (every 20th file)
    sample_files = files[::max(1, len(files)//20)]  
    
    for filename in sample_files:
        try:
            with open(filename, "rb") as f:
                data = msgpack.load(f, raw=False)
            
            # Collect bacteria positions and account for their length
            for b in data.get('bacteria', []):
                x = b['position']['x']
                y = b['position']['y']
                length = b['length']
                angle = b['angle']
                
                # Calculate the extents of the bacteria
                half_length = length / 2
                dx = half_length * np.cos(angle)
                dy = half_length * np.sin(angle)
                
                # Add both ends of the bacteria
                all_x_coords.extend([x + dx, x - dx])
                all_y_coords.extend([y + dy, y - dy])
            
            for e in data.get('eps', []):
                x = e['position']['x']
                y = e['position']['y']
                radius = 0.25  # EPS radius
                
                all_x_coords.extend([x + radius, x - radius])
                all_y_coords.extend([y + radius, y - radius])
                
        except Exception as e:
            print(f"Warning: Could not read {filename}: {e}")
            continue
    
    if not all_x_coords or not all_y_coords:
        print("Warning: No valid data found, using default scale")
        return 20.0, (0.0, 0.0)

    
    # Find the maximum extent in both dimensions
    min_x, max_x = min(all_x_coords), max(all_x_coords)
    min_y, max_y = min(all_y_coords), max(all_y_coords)

    # Calculate the center of the bounding box
    center_x = (min_x + max_x) / 2.0
    center_y = (min_y + max_y) / 2.0
    world_center = (center_x, center_y)
    x_range = max_x - min_x
    y_range = max_y - min_y
    
    # Take the larger of the two ranges and add the buffer
    max_range = max(x_range, y_range)
    visual_scale = (max_range * buffer_factor) / 2.0
    
    return visual_scale, world_center


def bacteria_data(b, bacteria_width,  visual_scale, world_center):
    """Convert a bacterium entry into normalized GPU instance data."""

    #Translate by subtracting the world center
    translated_x = b['position']['x'] - world_center[0]
    translated_y = b['position']['y'] - world_center[1]

    pos_x = translated_x / visual_scale
    pos_y = translated_y / visual_scale

    size_x = b['length'] /visual_scale
    size_y = bacteria_width /visual_scale
    angle = b['angle']
    color = (0.2, 0.5, 1.0) # Blueish
    shape = 0.0  # Capsule shape for bacteria
    return [pos_x, pos_y, size_x, size_y, angle, *color, shape]

def eps_data(e, visual_scale, world_center):
    """Convert an EPS entry into normalized GPU instance data."""

    translated_x = e['position']['x'] - world_center[0]
    translated_y = e['position']['y'] - world_center[1]

    pos_x = translated_x / visual_scale
    pos_y = translated_y / visual_scale

    size_x = 0.5 /visual_scale 
    size_y = 0.5 /visual_scale
    angle = 0.0
    color = (1.0, 0.2, 0.2) # Red
    shape = 1.0  # Circle shape for EPS
    return [pos_x, pos_y, size_x, size_y, angle, *color, shape]


def estimate_max_instances(data_dir):
    """Estimate max objects across frames (with 50% buffer) for GPU buffer size."""
    
    files = sorted(glob.glob(os.path.join(data_dir, "*.boulder")))
    if not files:
        return 1000  # fallback
    
    max_instances = 0
    # Sample files to estimate maximum instance count
    sample_files = files[::max(1, len(files)//10)]  # Sample every 10th file
    
    for filename in sample_files:
        try:
            with open(filename, "rb") as f:
                data = msgpack.load(f, raw=False)
            
            instance_count = len(data.get('bacteria', [])) + len(data.get('eps', []))
            max_instances = max(max_instances, instance_count)
            
        except Exception as e:
            continue
    
    # Add 50% buffer to the estimated maximum
    return int(max_instances * 1.5)


def main(args):
    """Main loop"""
    script_dir = os.path.dirname(os.path.abspath(__file__))
    data_dir = os.path.join(script_dir, args.data_dir)
    output_file = os.path.join(script_dir, args.output)
    
    if not os.path.exists(data_dir):
        print(f"ERROR: Data directory '{data_dir}' not found!")
        return
    
    visual_scale, world_center = calculate_visual_scale(data_dir, VISUAL_SCALE_BUFFER)

    max_instances = estimate_max_instances(data_dir)

    # Initialize GPU context and program
    ctx = moderngl.create_standalone_context(require=330)
    print(f"ModernGL Context Created using: {ctx.info['GL_RENDERER']}")
    
    # Enable blending for anti-aliasing
    ctx.enable(moderngl.BLEND)
    ctx.blend_func = moderngl.SRC_ALPHA, moderngl.ONE_MINUS_SRC_ALPHA

    prog = ctx.program(vertex_shader=VERTEX_SHADER, fragment_shader=FRAGMENT_SHADER)

    # Define the vertices for our base shape: a simple 1x1 rectangle.
    vertices = np.array([
        -0.5, -0.5,
         0.5, -0.5,
        -0.5,  0.5,
         0.5,  0.5,
    ], dtype='f4')
    
    # Create a Vertex Buffer Object (VBO) for the base shape's vertices.
    vbo_vertices = ctx.buffer(vertices)
    vbo_per_instance = ctx.buffer(reserve=max_instances * 9 * 4) # Reserve space for per-instance data (9 floats per instance: pos, size, angle, color, shape)

    # A Vertex Array Object (VAO) links our shader program with our data buffers.
    vao = ctx.vertex_array(
        prog,
        [
            (vbo_vertices, '2f', 'in_vert'),
            (vbo_per_instance, '2f 2f 1f 3f 1f /i', 'in_pos', 'in_size', 'in_angle', 'in_color', 'in_shape')
        ]
    )

    # Create a Framebuffer Object (FBO) - an off screen canvas to draw on.
    fbo = ctx.framebuffer(
        color_attachments=[ctx.texture(FRAME_SIZE, 4)] # Attach a 4-component (RGBA) texture as the drawing surface.
    )
    
    # Main loop that processes each MessagePack file and render frames.
    files = sorted(glob.glob(os.path.join(data_dir, "*.boulder")))
    if not files:
        print(f"No MessagePack (.boulder) files found in '{data_dir}'.")
        return
        
    print(f"Found {len(files)} frames. Starting GPU rendering...")
    
    writer = imageio.get_writer(output_file, fps=FPS, codec='libx264', bitrate='5000k') # Set a reasonable quality

    for i, filename in enumerate(files):
        # Prints only everry 100 frames or last frame 
        if (i + 1) % 100 == 0 or (i + 1) == len(files):
            print(f"\rProcessing frame {i+1}/{len(files)}", end="")

        with open(filename, "rb") as f:
            data = msgpack.load(f, raw=False)

        # Collate all object data for this frame into a single flat list.
        instance_data = []
        
        # Add bacteria data
        for b in data.get('bacteria', []):
            instance_data.extend(bacteria_data(b, BACTERIA_WIDTH, visual_scale, world_center))

        # Add EPS data 
        for e in data.get('eps', []):
            instance_data.extend(eps_data(e, visual_scale, world_center))

        
        if not instance_data: 
            # Create a blank frame to keep video timing consistent
            frame_array = np.zeros((FRAME_SIZE[1], FRAME_SIZE[0], 3), dtype=np.uint8)
        else:
            # Render a single frame
            fbo.use() 
            ctx.clear(0.0, 0.0, 0.0) 
            
            np_data = np.array(instance_data, dtype='f4')
            vbo_per_instance.write(np_data)
            num_instances = len(np_data) // 9
            
            # Tell the GPU to render the VAO using the TRIANGLE_STRIP method for 'num_instances' times.
            vao.render(moderngl.TRIANGLE_STRIP, instances=num_instances)
            
            # Read frame back to CPU
            image_buffer = fbo.read(components=3)
            image = Image.frombytes('RGB', fbo.size, image_buffer)
            image = image.transpose(Image.FLIP_TOP_BOTTOM)
            
            # Convert to a 3-channel (RGB) NumPy array for imageio.
            frame_array = np.array(image)
            frame_array = np.clip(frame_array, 0, 255).astype(np.uint8)

        writer.append_data(frame_array)
    
    print(f"\nFinished processing all frames.")
    writer.close()
    print(f"GPU-rendered video saved as {output_file}")

    print(f"Simulation and visualization complete.")


def run():
    parser = argparse.ArgumentParser(description="Biofilm simulation video generator")
    parser.add_argument("--data_dir", default=DATA_DIRECTORY, help="Directory containing simulation MessagePack files")
    parser.add_argument("--output", default=OUTPUT_VIDEO, help="Output video filename")
    args = parser.parse_args()
    main(args)

if __name__ == "__main__":
    run()
