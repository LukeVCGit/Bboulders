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
VISUAL_SCALE_BUFFER = 1.2  # Extra space around edges

# Vertex Shader positions each corner of base shape
# Now we pass shape type to fragment shader for proper rendering
VERTEX_SHADER = """
    #version 330

    // This is the input for a single corner of the base rectangle.
    in vec2 in_vert; 

    // These are the per-instance inputs, unique for each bacterium/EPS.
    in vec2 in_pos;      // Center position of the object in [-1, 1] space
    in vec2 in_size;     // length and width of the object in [-1, 1] space
    in float in_angle;   // angle in radians
    in vec3 in_color;    // color (R, G, B)
    in float in_shape;   // shape type: 0 = capsule (bacteria), 1 = circle (EPS)

    // Outputs that get passed to the Fragment Shader.
    out vec3 v_color;
    out vec2 v_uv;       // UV coordinates for shape rendering
    out vec2 v_size;     // Pass size to fragment shader
    out float v_shape;   // Pass shape type to fragment shader

    void main() {
        // Pass data to fragment shader
        v_color = in_color;
        v_size = in_size;
        v_shape = in_shape;
        
        // Calculate UV coordinates (-1 to 1 range for shape calculations)
        v_uv = in_vert;

        // Create rotation matrix
        mat2 rotation = mat2(
            cos(in_angle), -sin(in_angle),
            sin(in_angle), cos(in_angle)
        );

        // Calculate the final position of the vertex
        // We need to expand the bounding box slightly for rounded ends
        float expand = (in_shape < 0.5) ? 1.2 : 1.1; // Expand more for capsules
        vec2 pos = (in_vert * in_size * expand) * rotation + in_pos;

        gl_Position = vec4(pos, 0.0, 1.0);
    }
"""

# Fragment Shader with signed distance functions for capsule and circle shapes
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
            // Bacteria -> Capsule Shape using a Signed Distance Function (SDF)

            // 1. Get the aspect ratio from the bacterium's actual size.
            float aspect = v_size.x / v_size.y;
            
            // 2. Define the capsule's geometry in the local UV space.
            // The radius is half the height of the base quad.
            float radius = 0.5;
            
            // The central line segment's half-length depends on the aspect ratio.
            // max(0.0, ...) ensures this works even if the shape is wider than it is long (a circle).
            float line_half_length = max(0.0, (aspect - 1.0) / 2.0);
            vec2 segment_start = vec2(-line_half_length, 0.0);
            vec2 segment_end   = vec2( line_half_length, 0.0);
            
            // 3. Transform the square UV coordinates into a space with the correct aspect ratio.
            // This "stretches" our calculation space to match the bacterium's shape.
            vec2 p = vec2(v_uv.x * aspect, v_uv.y);
            
            // 4. Calculate the distance from the current pixel 'p' to the capsule's central line segment.
            vec2 line_vec = segment_end - segment_start;
            vec2 point_vec = p - segment_start;

            float dist;
            // Handle the case of a perfect circle (aspect <= 1) to avoid division by zero.
            if (line_half_length == 0.0) {
                dist = length(p) - radius;
            } else {
                // Project point_vec onto line_vec and clamp it to the segment.
                float h = clamp(dot(point_vec, line_vec) / dot(line_vec, line_vec), 0.0, 1.0);
                // The distance is from 'p' to the closest point on the segment, minus the radius.
                dist = length(point_vec - line_vec * h) - radius;
            }

            // 5. Use the distance to create a smooth, anti-aliased edge.
            float aa = 0.02 / aspect; // Adjust anti-aliasing for stretched shapes
            alpha = 1.0 - smoothstep(-aa, aa, dist);

            if (alpha < 0.01) {
                discard; // Optimization: Don't draw fully transparent pixels.
            }

        } else {
            // EPS -> circle
            float radius = 0.5;
            float dist = length(v_uv) - radius;

            // Use smoothstep for anti-aliasing
            float aa = 0.02;
            alpha = 1.0 - smoothstep(-aa, aa, dist);

            if (alpha < 0.01) {
                discard;
            }
        }

        f_color = vec4(v_color, alpha);
    }
"""


    # Calculate the visual scale by examining all data files to find the maximum extent.
    # Returns the scale needed to fit all data in the [-1, 1] coordinate space.
def calculate_visual_scale(data_dir, buffer_factor=1.2):
    
    files = sorted(glob.glob(os.path.join(data_dir, "*.boulder")))
    if not files:
        print(f"No MessagePack (.boulder) files found in '{data_dir}'.")
        return 20.0, (0.0, 0.0)  # Default
    
    print(f"Analyzing {len(files)} files to determine optimal visual scale...")
    
    all_x_coords = []
    all_y_coords = []
    
    # Sample files to determine extent - you could analyze all files or just sample
    # For large datasets, you might want to sample every Nth file for speed
    sample_files = files[::max(1, len(files)//20)]  # Sample up to 20 files
    
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
                
                # Calculate the extents of the bacteria considering its length and orientation
                half_length = length / 2
                dx = half_length * np.cos(angle)
                dy = half_length * np.sin(angle)
                
                # Add both ends of the bacteria
                all_x_coords.extend([x + dx, x - dx])
                all_y_coords.extend([y + dy, y - dy])
            
            # Collect EPS positions (assuming they have radius 0.25)
            for e in data.get('eps', []):
                x = e['position']['x']
                y = e['position']['y']
                radius = 0.25  # EPS radius
                
                # Add extents of the EPS circle
                all_x_coords.extend([x + radius, x - radius])
                all_y_coords.extend([y + radius, y - radius])
                
        except Exception as e:
            print(f"Warning: Could not read {filename}: {e}")
            continue
    
    if not all_x_coords or not all_y_coords:
        print("Warning: No valid data found, using default scale")
        return 20.0

    
    # Find the maximum extent in both dimensions
    min_x, max_x = min(all_x_coords), max(all_x_coords)
    min_y, max_y = min(all_y_coords), max(all_y_coords)

     # Calculate the center of the bounding box
    center_x = (min_x + max_x) / 2.0
    center_y = (min_y + max_y) / 2.0
    world_center = (center_x, center_y)
    
    # Calculate the range in each dimension
    x_range = max_x - min_x
    y_range = max_y - min_y
    
    # The visual scale should be half of the maximum range (since we map to [-1, 1])
    # We take the larger of the two ranges and add the buffer
    max_range = max(x_range, y_range)
    visual_scale = (max_range * buffer_factor) / 2.0
    
    print(f"Data extent: X=[{min_x:.2f}, {max_x:.2f}], Y=[{min_y:.2f}, {max_y:.2f}]")
    print(f"Max range: {max_range:.2f}, Visual scale: {visual_scale:.2f}")
    
    return visual_scale, world_center


def bacteria_data(b, bacteria_width,  visual_scale, world_center):
# Normalize simulation coordinates from sISUALISATION_SCALE to GPU screen space (-1, 1).

    #Translate by subtracting the world center
    translated_x = b['position']['x'] - world_center[0]
    translated_y = b['position']['y'] - world_center[1]

    # Scale the translated coordinates
    pos_x = translated_x / visual_scale
    pos_y = translated_y / visual_scale

    size_x = b['length'] /visual_scale
    size_y = bacteria_width /visual_scale
    angle = b['angle']
    color = (0.2, 0.5, 1.0) # Blueish
    shape = 0.0  # Capsule shape for bacteria
    return [pos_x, pos_y, size_x, size_y, angle, *color, shape]

def eps_data(e, visual_scale, world_center):

    #Translate
    translated_x = e['position']['x'] - world_center[0]
    translated_y = e['position']['y'] - world_center[1]
    # Scale
    pos_x = translated_x / visual_scale
    pos_y = translated_y / visual_scale

    size_x = 0.5 /visual_scale # diameter 0.5
    size_y = 0.5 /visual_scale
    angle = 0.0
    color = (1.0, 0.2, 0.2) # Red
    shape = 1.0  # Circle shape for EPS
    return [pos_x, pos_y, size_x, size_y, angle, *color, shape]


# Estimate the maximum number of instances across all files to reserve appropriate buffer size.
def estimate_max_instances(data_dir):
    
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


def main():
    parser = argparse.ArgumentParser(description="Biofilm simulation video generator")
    parser.add_argument("--data_dir", default=DATA_DIRECTORY,
                        help="Directory containing simulation MessagePack files")
    parser.add_argument("--output", default=OUTPUT_VIDEO,
                        help="Output video filename")
    args = parser.parse_args()

    # Resolve paths relative to the script's location
    script_dir = os.path.dirname(os.path.abspath(__file__))
    data_dir = os.path.join(script_dir, args.data_dir)
    output_file = os.path.join(script_dir, args.output)
    
    if not os.path.exists(data_dir):
        print(f"ERROR: Data directory '{data_dir}' not found!")
        return
    
    # Calculate the optimal visual scale
    visual_scale, world_center = calculate_visual_scale(data_dir, VISUAL_SCALE_BUFFER)
    print(f"Using visual scale: {visual_scale}")

    # Estimate maximum instances for buffer reservation
    max_instances = estimate_max_instances(data_dir)
    print(f"Estimated max instances: {max_instances}")

    # --- 1. GPU CONTEXT AND SHADER SETUP (DONE ONCE) ---
    # Create a "standalone" or "headless" ModernGL context because we don't need to display a window.
    ctx = moderngl.create_standalone_context(require=330)
    print(f"ModernGL Context Created using: {ctx.info['GL_RENDERER']}")
    
    # Enable blending for anti-aliasing
    ctx.enable(moderngl.BLEND)
    ctx.blend_func = moderngl.SRC_ALPHA, moderngl.ONE_MINUS_SRC_ALPHA

    # Create the GPU program by compiling and linking the two shaders.
    prog = ctx.program(vertex_shader=VERTEX_SHADER, fragment_shader=FRAGMENT_SHADER)

    # --- 2. GEOMETRY AND BUFFER SETUP (DONE ONCE) ---
    # Define the vertices for our base shape: a simple 1x1 rectangle.
    # The GPU will draw this shape thousands of times, transformed for each bacterium.
    vertices = np.array([
        -0.5, -0.5,
         0.5, -0.5,
        -0.5,  0.5,
         0.5,  0.5,
    ], dtype='f4')
    
    # Create a Vertex Buffer Object (VBO) for the base shape's vertices.
    vbo_vertices = ctx.buffer(vertices)

    # Create another VBO for the per-instance data. We 'reserve' a large amount of memory
    # on the GPU for this buffer. We will overwrite its contents each frame.
    # Reserving helps avoid re-allocating memory every frame, which is slow.
    vbo_per_instance = ctx.buffer(reserve=max_instances * 9 * 4) # Reserve space for per-instance data (9 floats per instance: pos, size, angle, color, shape)

    #################### make the above dynamic based on number of objects in data file

    # A Vertex Array Object (VAO) links our shader program with our data buffers.
    # It defines how the data in the buffers maps to the 'in' variables in the Vertex Shader.
    vao = ctx.vertex_array(
        prog,
        [
            # Mapping for the base vertices VBO
            (vbo_vertices, '2f', 'in_vert'),
            # Mapping for the per-instance VBO.
            # '2f 2f 1f 3f /i' means:
            # 2 floats for in_pos, 2 for in_size, 1 for in_angle, 3 for in_color.
            # '/i' specifies that this is per-instance data, advancing once per instance, not per vertex.
            (vbo_per_instance, '2f 2f 1f 3f 1f /i', 'in_pos', 'in_size', 'in_angle', 'in_color', 'in_shape')
        ]
    )

    # --- 3. FRAMEBUFFER SETUP (DONE ONCE) ---
    # Create a Framebuffer Object (FBO). This is an off-screen canvas on the GPU that we can render to.
    fbo = ctx.framebuffer(
        color_attachments=[ctx.texture(FRAME_SIZE, 4)] # Attach a 4-component (RGBA) texture as the drawing surface.
    )
    
    # --- 4. MAIN RENDERING LOOP ---
    files = sorted(glob.glob(os.path.join(data_dir, "*.boulder")))
    if not files:
        print(f"No MessagePack (.boulder) files found in '{data_dir}'.")
        return
        
    print(f"Found {len(files)} frames. Starting GPU rendering...")
    
    writer = imageio.get_writer(output_file, fps=FPS, codec='libx264', bitrate='5000k') # Set a reasonable quality

    for i, filename in enumerate(files):
        #prints only everry 100 frames or last frame 
        if (i + 1) % 100 == 0 or (i + 1) == len(files):
            print(f"\rProcessing frame {i+1}/{len(files)}", end="")

        with open(filename, "rb") as f:
            data = msgpack.load(f, raw=False)

        # Collate all object data for this frame into a single flat list.
        # This is much faster than sending data to the GPU one object at a time.
        instance_data = []
        
        # Add bacteria data
        for b in data.get('bacteria', []):
            instance_data.extend(bacteria_data(b, BACTERIA_WIDTH, visual_scale, world_center))

        # Add EPS data 
        for e in data.get('eps', []):
            instance_data.extend(eps_data(e, visual_scale, world_center))

        
        if not instance_data: # Skip empty frames
            # Create a blank frame to keep video timing consistent
            frame_array = np.zeros((FRAME_SIZE[1], FRAME_SIZE[0], 3), dtype=np.uint8)
        else:
            # --- RENDER A SINGLE FRAME ON THE GPU ---
            fbo.use() # Tell ModernGL to draw to our off-screen framebuffer.
            ctx.clear(0.0, 0.0, 0.0) # Clear it to black.
            
            # Convert our Python list to a NumPy array and write it to the GPU buffer.
            np_data = np.array(instance_data, dtype='f4')
            vbo_per_instance.write(np_data)
            
            # Calculate the number of instances to render. There are 8 floats per instance.
            num_instances = len(np_data) // 9
            
            # THE RENDER COMMAND!
            # Tell the GPU to render the VAO using the TRIANGLE_STRIP method for 'num_instances' times.
            vao.render(moderngl.TRIANGLE_STRIP, instances=num_instances)
            
            # --- READ THE FINISHED FRAME BACK TO THE CPU ---
            # Read the pixel data from the FBO into a buffer.
            image_buffer = fbo.read(components=3)
            # Create a Pillow Image from the raw buffer.
            image = Image.frombytes('RGB', fbo.size, image_buffer)
            # FBOs are often rendered upside-down, so we flip it.
            image = image.transpose(Image.FLIP_TOP_BOTTOM)
            

            #potentially dont need this below


            
            # Convert to a 3-channel (RGB) NumPy array for imageio.
            frame_array = np.array(image)
            #clips values to valid range and converts to uint8
            frame_array = np.clip(frame_array, 0, 255).astype(np.uint8)

            # Debug: Print min, max, mean of the frame array to ensure valid values
        #print(i, frame_array.min(), frame_array.max(), frame_array.mean())

        writer.append_data(frame_array)
    
    print(f"\nFinished processing all frames.")
    writer.close()
    print(f"GPU-rendered video saved as {output_file}")


if __name__ == "__main__":
    main()