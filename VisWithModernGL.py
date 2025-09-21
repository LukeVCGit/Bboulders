import argparse
import json
import os
import glob
import numpy as np
import imageio.v2 as imageio
import moderngl
from PIL import Image

# --- CONFIGURATION ---
DATA_DIRECTORY = 'simulation_data'
FRAME_DIRECTORY = 'frames' # Not used by this script, but for consistency
OUTPUT_VIDEO = 'video_gpu.mp4'
FPS = 30
FRAME_SIZE = (1024, 1024)
BACTERIA_WIDTH = 1.0 # Use float for consistency

# --- SHADERS (RUN ON GPU) ---

# The Vertex Shader positions each corner of our base shape.
# It runs once for each corner of each instance (bacterium/EPS).
VERTEX_SHADER = """
    #version 330

    // This is the input for a single corner of the base 1x1 rectangle.
    in vec2 in_vert; 

    // These are the per-instance inputs, unique for each bacterium/EPS.
    in vec2 in_pos;      // Center position of the object in [-1, 1] space
    in vec2 in_size;     // length and width of the object in [-1, 1] space
    in float in_angle;   // angle in radians
    in vec3 in_color;    // color (R, G, B)

    // This is an output that gets passed to the Fragment Shader.
    out vec3 v_color;

    void main() {
        // Pass the color along to the next stage.
        v_color = in_color;

        // Create a 2x2 rotation matrix to apply the angle.
        mat2 rotation = mat2(
            cos(in_angle), -sin(in_angle),
            sin(in_angle), cos(in_angle)
        );

        // Calculate the final position of the vertex:
        // 1. Start with the base corner (in_vert)
        // 2. Scale it by the object's size (in_size)
        // 3. Rotate it using the rotation matrix
        // 4. Translate it to the object's final position (in_pos)
        vec2 pos = (in_vert * in_size) * rotation + in_pos;

        // gl_Position is a special output variable for the final vertex coordinates.
        gl_Position = vec4(pos, 0.0, 1.0);
    }
"""

# The Fragment Shader determines the color of each pixel.
# It runs for every single pixel inside the shapes drawn by the Vertex Shader.
FRAGMENT_SHADER = """
    #version 330

    // This is the color that was passed from the Vertex Shader.
    // The GPU automatically interpolates it across the shape's surface.
    in vec3 v_color;

    // This is the final output color for the current pixel.
    out vec4 f_color;

    void main() {
        // Set the pixel color, with full alpha (1.0 = fully opaque).
        f_color = vec4(v_color, 1.0);
    }
"""

def main():
    parser = argparse.ArgumentParser(description="Biofilm simulation video generator")
    parser.add_argument("--data_dir", default=DATA_DIRECTORY,
                        help="Directory containing simulation JSON files")
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

    # --- 1. GPU CONTEXT AND SHADER SETUP (DONE ONCE) ---
    # Create a "standalone" or "headless" ModernGL context because we don't need to display a window.
    ctx = moderngl.create_standalone_context(require=330)
    print(f"ModernGL Context Created using: {ctx.info['GL_RENDERER']}")
    
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
    vbo_per_instance = ctx.buffer(reserve=100000 * 8 * 4) # Reserve space for 100k objects, 8 floats each, 4 bytes per float.

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
            (vbo_per_instance, '2f 2f 1f 3f /i', 'in_pos', 'in_size', 'in_angle', 'in_color')
        ]
    )

    # --- 3. FRAMEBUFFER SETUP (DONE ONCE) ---
    # Create a Framebuffer Object (FBO). This is an off-screen canvas on the GPU that we can render to.
    fbo = ctx.framebuffer(
        color_attachments=[ctx.texture(FRAME_SIZE, 4)] # Attach a 4-component (RGBA) texture as the drawing surface.
    )
    
    # --- 4. MAIN RENDERING LOOP ---
    files = sorted(glob.glob(os.path.join(data_dir, "*.json")))
    if not files:
        print(f"No JSON files found in '{data_dir}'.")
        return
        
    print(f"Found {len(files)} frames. Starting GPU rendering...")
    
    writer = imageio.get_writer(output_file, fps=FPS, codec='libx264', bitrate='5000k') # Set a reasonable quality

    for i, filename in enumerate(files):
        print(f"\rProcessing frame {i+1}/{len(files)}", end="")
        with open(filename) as f:
            data = json.load(f)

        # Collate all object data for this frame into a single flat list.
        # This is much faster than sending data to the GPU one object at a time.
        instance_data = []
        
        # Add bacteria data
        for b in data.get('bacteria', []):
            # Normalize simulation coordinates from (-10, 10) to GPU screen space (-1, 1).
            pos_x = b['position']['x'] / 10.0
            pos_y = b['position']['y'] / 10.0
            size_x = b['length'] / 10.0
            size_y = BACTERIA_WIDTH / 10.0
            angle = b['angle']
            color = (0.2, 0.5, 1.0) # Blueish
            instance_data.extend([pos_x, pos_y, size_x, size_y, angle, *color])

        # Add EPS data (rendered as squares for simplicity using the same shader)
        for e in data.get('eps', []):
            pos_x = e['position']['x'] / 10.0
            pos_y = e['position']['y'] / 10.0
            size_x = 0.5 / 10.0 # radius 0.25 -> diameter 0.5
            size_y = 0.5 / 10.0
            angle = 0.0
            color = (1.0, 0.2, 0.2) # Red
            instance_data.extend([pos_x, pos_y, size_x, size_y, angle, *color])
        
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
            num_instances = len(np_data) // 8
            
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