package main;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.imageio.ImageIO;

public class ColourRemover {

    /**
     * Regex used to match image filenames.
     */
    private static final String IMAGE_FILENAME_REGEX = 
            ".+\\.(?i)(bmp|jpg|gif|png)";

    /**
     * Colour to remove from the target image.
     */
    private int colourToRemove;
    
    /**
     * Minimum number of connected pixels before an area of the desired colour
     * should be removed.
     */
    private int threshold;

    /**
     * Constructs a ColourRemover capable of removing areas of the given colour.
     * 
     * @param colourToRemove
     * @param threshold
     */
    public ColourRemover(int colourToRemove, int threshold) {
        this.colourToRemove = colourToRemove;
        this.threshold = threshold;
    }

    /**
     * Removes areas of the desired colour from the given image, provided they
     * meet the required threshold for size.
     * 
     * @param image
     * @return Array of 2 images; the original image with the colour removed,
     * and an image containing just the removed pixels.
     */
    private BufferedImage[] process(BufferedImage image) {

        // The top-left colour is assumed to be the background colour
        int background = image.getRGB(0, 0);

        // Create a blank image to hold the removed pixels
        BufferedImage removedPixels = new BufferedImage(
                image.getWidth(),
                image.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics g = removedPixels.getGraphics();
        g.setColor(new Color(background));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        
        Set<ImagePixel> processedPixels = new HashSet<>();
        
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {

                ImagePixel thisPixel = new ImagePixel(x, y);
                
                if (image.getRGB(x, y) != colourToRemove || 
                        processedPixels.contains(thisPixel)) {
                    // Pixel is not the colour we are looking for, or has
                    // already been processed
                    continue;
                }
                
                // Find connected pixels of the desired colour
                Set<ImagePixel> connectedPixels = new HashSet<>();
                connectedPixels.add(thisPixel);
                findConnectedPixels(image, x, y, connectedPixels);

                // Remember pixels we have just processed
                processedPixels.addAll(connectedPixels);
                
                if (connectedPixels.size() < threshold) {
                    // This area is too small for us to remove
                    continue;
                }
                
                System.out.println(
                        "Removing " + connectedPixels.size() + " pixels");
                
                // Remove all connected pixels
                for (ImagePixel px : connectedPixels) {
                    image.setRGB(px.x, px.y, background);
                    removedPixels.setRGB(px.x, px.y, colourToRemove);
                }
            }
        }
        
        return new BufferedImage[] {
                image,
                removedPixels
        };
    }
    
    /**
     * Recursively find connected pixels in each direction.
     * 
     * <p>This is essentially a flood-fill algorithm, but instead of changing
     * the pixel colours immediately we are just remembering which pixels match
     * the desired colour.
     * 
     * @param image
     * @param x
     * @param y
     * @param connectedPixels
     */
    private void findConnectedPixels(
            BufferedImage image,
            int x,
            int y,
            Set<ImagePixel> connectedPixels) {
        
        // Left
        if (x > 0 &&
                image.getRGB(x - 1, y) == colourToRemove) {
            boolean added = connectedPixels.add(new ImagePixel(x - 1, y));
            if (added) {
                findConnectedPixels(image, x - 1, y, connectedPixels);                
            }
        }
        
        // Right
        if (x < image.getWidth() - 1 &&
                image.getRGB(x + 1, y) == colourToRemove) {
            boolean added = connectedPixels.add(new ImagePixel(x + 1, y));
            if (added) {
                findConnectedPixels(image, x + 1, y, connectedPixels);                
            }
        }
        
        // Top
        if (y > 0 &&
                image.getRGB(x, y - 1) == colourToRemove) {
            boolean added = connectedPixels.add(new ImagePixel(x, y - 1));
            if (added) {
                findConnectedPixels(image, x, y - 1, connectedPixels);                
            }
        }
        
        // Bottom
        if (y < image.getHeight() - 1 &&
                image.getRGB(x, y + 1) == colourToRemove) {
            boolean added = connectedPixels.add(new ImagePixel(x, y + 1));
            if (added) {
                findConnectedPixels(image, x, y + 1, connectedPixels);                
            }
        }
    }

    /**
     * Entry point for the application.
     * 
     * @param args
     */
    public static void main(String[] args) {

        if (args.length < 3) {
            System.err.println(
                    "Expected: SOURCE_FOLDER COLOUR_TO_REMOVE THRESHOLD");
            System.exit(-1);
        }

        ColourRemover colRemover = null;
        
        String imageDir = args[0];

        String[] colourParts = args[1].split(",");
        if (colourParts.length != 3) {
            System.err.println("Colour must of the form R,G,B");
        }
        
        try {
            
            int colourToRemove = new Color(
                    Integer.parseInt(colourParts[0]),
                    Integer.parseInt(colourParts[1]),
                    Integer.parseInt(colourParts[2])).getRGB();
            
            int threshold = Integer.parseInt(args[2]);
            
            colRemover = new ColourRemover(colourToRemove, threshold);

        } catch (NumberFormatException ex) {
            System.err.println("Argument is not a valid integer!");
            System.exit(1);
        }
        
        // Find all images in directory
        System.out.println("Finding files");
        File dir = new File(imageDir);
        File[] imageFiles = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.matches(IMAGE_FILENAME_REGEX);
            }
        });

        if (imageFiles.length == 0) {
            System.err.println("No image files found in directory: " + 
                    dir.getAbsolutePath());
            System.exit(1);
        }

        // Remove the desired colour from each image
        for (File file : imageFiles) {
            
            BufferedImage image = null;

            // Read image
            try {
                System.out.println("Reading image: " + file);
                image = ImageIO.read(file);
            } catch (IOException ex) {
                System.err.println("Unable to read image");
                ex.printStackTrace();
                continue;
            }
            
            // Remove the colour
            System.out.println("Processing...");
            BufferedImage[] processedImages = colRemover.process(image);
            
            // Ensure "out" directory exists
            new File("out").mkdir();
            
            // Save modified image
            String filename = file.getName();
                
            // Remove extension
            int pos = filename.lastIndexOf(".");
            if (pos > 0) {
                filename = filename.substring(0, pos);
            }
                
            try {
                ImageUtils.saveImage(processedImages[0], "out/" + filename + ".png");
                ImageUtils.saveImage(processedImages[1], "out/" + filename + "_s.png");
            } catch (IOException e) {
                System.err.println("Unable to save image");
                e.printStackTrace();
            }
        }
        
        System.out.println("Success!");
    }

}
