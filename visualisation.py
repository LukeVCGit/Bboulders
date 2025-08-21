import json
import os
import glob
import matplotlib.pyplot as plt
from matplotlib.patches import Rectangle
from matplotlib.patches import Circle
import numpy as np
import imageio.v2 as imageio
import shutil

DATA_DIRECTORY = 'simulation_data'
FRAME_DIRECTORY = 'frames'
BACTERIA_WIDTH = 1
FPS = 30

def make_frame(bacteria, eps, frame):
    fig, ax = plt.subplots(figsize=(5.12, 5.12)) #5.12 because 5.12 * 100 = 512 pixels, which is divisible by 16 for video encoding
    ax.get_xaxis().set_visible(False)
    ax.get_yaxis().set_visible(False)

    #plot eps
    for e in eps:
        circle = Circle(
            (e["position"]["x"], e["position"]["y"]),
            radius=0.25,  # radius = diameter/2 = 0.25
            color="red"
        )
        ax.add_patch(circle)

    #plot bacteria
    for b in bacteria:
        x = b["position"]["x"]
        y = b["position"]["y"]
        length = b["length"]
        angle = b["angle"] #radians

        #calculate horizontal and vertical offsets from center of rectangle to bottom left corner (rectangle object drawn from bottom-left)

        x_offset = length / 2 * np.cos(angle)
        y_offset = length / 2 * np.sin(angle)

        rect = Rectangle(
            (x - x_offset, y - y_offset),  #bottom-left corner
            length,
            BACTERIA_WIDTH,
            angle=np.degrees(angle),  #converts to degrees for matplotlib
            color='blue',
            alpha=0.7  #transparency
        )
        ax.add_patch(rect)  #add rect to plot

        #plt.show()
    
    #ax.set_title(f"frame_{frame:04d}")
    ax.set_aspect('equal')
    ax.set_xlim(-10, 10)
    ax.set_ylim(-10, 10)

    #save frame as PNG
    os.makedirs(FRAME_DIRECTORY, exist_ok=True)
    path = os.path.join(FRAME_DIRECTORY, f"frame_{frame:04d}.png")    #04d means 4 digits with zeros on left 
    plt.savefig(path, dpi=150)   #play with dpi

    plt.close(fig) 
    


def main():
    # clear frame directory
    if os.path.exists(FRAME_DIRECTORY):
        shutil.rmtree(FRAME_DIRECTORY)  # delete entire folder
    os.makedirs(FRAME_DIRECTORY)  # recreate it empty

    # get jsons 
    files = sorted(glob.glob(os.path.join(DATA_DIRECTORY, "*.json")))

    print(f"Found {len(files)} frames")

    # create png for each time
    for i, filename in enumerate(files, start=1):
        f = open(filename)
        data = json.load(f)
        make_frame(data["bacteria"], data["eps"], i)

    print(f"Frames saved to {FRAME_DIRECTORY}")

    #create video from frames
    writer = imageio.get_writer('video.mp4', fps=FPS)
    for frame_file in sorted(glob.glob(os.path.join(FRAME_DIRECTORY, "*.png"))):
        image = imageio.imread(frame_file)  #use one image at a time 
        writer.append_data(image)   #add image to video


if __name__ == "__main__":
    main()