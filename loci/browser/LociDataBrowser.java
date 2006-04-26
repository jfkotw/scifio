//
// LociDataBrowser.java
//

package loci.browser;

import ij.*;
import ij.gui.ImageCanvas;
import ij.io.*;
import ij.plugin.PlugIn;
import java.awt.image.ColorModel;
import java.io.File;
import java.lang.reflect.Array;
import java.math.BigInteger;
import java.util.Arrays;
import loci.util.FilePattern;
import loci.util.MathUtil;
import javax.swing.*;


/**
 * LociDataBrowser is a plugin for ImageJ that allows for browsing of 4D
 * image data (stacks of image planes over time) with two-channel support.
 *
 * @author Francis Wong yutaiwong at wisc.edu
 * @author Curtis Rueden ctrueden at wisc.edu
 * @author Melissa Linkert linkert at cs.wisc.edu
 */
public class LociDataBrowser implements PlugIn {

  // -- Constants --

  /** Debugging flag. */
  protected static final boolean DEBUG = false;

  /** Prefix endings indicating numbering block represents T. */
  private static final String[] PRE_T = {
    "_TP", "-TP", ".TP", "_TL", "-TL", ".TL"
  };

  /** Prefix endings indicating numbering block represents Z. */
  private static final String[] PRE_Z = {
    "_Z", "-Z", ".Z", "_ZS", "-ZS", ".ZS"
  };

  /** Prefix endings indicating numbering block represents C. */
  private static final String[] PRE_C = {
    "_C", "-C", ".C", "_CH", "-CH", ".CH"
  };


  // -- Fields --

  /** filename for each index */
  protected String[] names;

  /** whether dataset has multiple Z, T and C positions */
  protected boolean hasZ, hasT, hasC;

  /** number of Z, T and C positions */
  protected int numZ, numT, numC;

  /** lengths of all dimensional axes; lengths[0] equals image depth */
  protected int[] lengths;

  /** indices into lengths array for Z, T and C */
  protected int zIndex, tIndex, cIndex;

  /** whether stack is accessed from disk as needed */
  protected boolean virtual;


  // -- LociDataBrowser methods --

  /** Displays the given ImageJ image in a 4D browser window. */
  public void show(ImagePlus imp) {
    int stackSize = imp == null ? 0 : imp.getStackSize();

    if (stackSize == 0) {
      msg("Cannot show invalid image.");
      return;
    }

    if (stackSize == 1) {
      // show single image normally
      imp.show();
      return;
    }

    new CustomWindow(this, imp, new ImageCanvas(imp));
  }

  /**
   * Shows the given image in a 4D browser window, using the given parameters.
   * @param imp the image to be shown
   * @param labels text to be shown for each slice
   * @param axes length of each dimensional axis
   * @param countZ number of Z positions (should be at least 1)
   * @param countT number of T positions (should be at least 1)
   * @param countC number of C positions (should be at least 1)
   * @param indexZ axis number of Z axis, or -1 for no Z axis
   * @param indexT axis number of T axis, or -1 for no T axis
   * @param indexC axis number of C axis, or -1 for no C axis
   */
  public void show(ImagePlus imp, String[] labels, int[] axes,
    int countZ, int countT, int countC, int indexZ, int indexT, int indexC)
  {
    names = labels;
    lengths = axes;
    numZ = countZ;
    numT = countT;
    numC = countC;
    hasZ = numZ > 1;
    hasT = numT > 1;
    hasC = numC > 1;
    zIndex = indexZ;
    tIndex = indexT;
    cIndex = indexC;
    show(imp);
  }

  /** gets the slice number for the given Z, T and C indices */
  public int getIndex(int z, int t, int c) {
    int[] pos = new int[lengths.length];
    if (zIndex >= 0) pos[zIndex] = z;
    if (tIndex >= 0) pos[tIndex] = t;
    if (cIndex >= 0) pos[cIndex] = c;
    return MathUtil.positionToRaster(lengths, pos) + 1;
  }


  // -- Plugin methods --

  public void run(String arg) {
    LociOpener lociOpener = new LociOpener();

    String directory = lociOpener.getDirectory();
    String name = lociOpener.getFileName();
    virtual = lociOpener.getVirtual();
    if (name == null) return;

    // find all the files having similar names (using FilePattern class)
    String pattern = FilePattern.findPattern(name, directory);
    FilePattern fp = new FilePattern(pattern);
    String[] filenames = fp.getFiles(); // all the image files
    int numFiles = filenames.length;

    long size = (new File(filenames[0])).length();

    // warns if virtual stack box not checked
    // and projected memory usage > 512 MB
    if ((size*numFiles > 512000000) && !virtual) {
      Object[] options = {"Use Virtual Stack", "Don't Use Virtual Stack"};
      String warning =
        "Estimated memory usage exceeds total available memory.\n " +
        "Do you want to use virtual stack to load the images?\n";
      boolean done = false;
      while (!done) {
        int n = JOptionPane.showOptionDialog(null, warning, "Warning",
          JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
          null, options, options[0]);
        if (n == 0) {
          virtual = true;
          done = true;
        }
        else if (n == 1) {
          virtual = false;
          done = true;
        }
      }
    }

    // trim directory prefix from filename list
    int dirLen = directory.length();
    for (int i=0; i<filenames.length; i++) {
      int q = dirLen;
      while (filenames[i].charAt(q) == File.separatorChar) q++;
      filenames[i] = filenames[i].substring(q);
    }

    if (DEBUG) {
      log("directory", directory);
      log("name", name);
      log("pattern", pattern);
      log("filenames", filenames);
    }

    // read images
    int depth = 0, width = 0, height = 0, type = 0;
    ImageStack stack = null;
    VirtualStack vstack = null;
    FileInfo fi = null;
    try {
      for (int i=0; i<filenames.length; i++) {
        // open image
        ImagePlus imp = new Opener().openImage(directory, filenames[i]);
        if (imp == null) {
          // invalid image
          log(filenames[i] + ": unable to open");
          continue;
        }

        if (stack == null) {
          // first image in the stack
          depth = imp.getStackSize();
          width = imp.getWidth();
          height = imp.getHeight();
          type = imp.getType();
          ColorModel cm = imp.getProcessor().getColorModel();
          if (virtual) stack = new VirtualStack(width, height, cm, directory);
          else stack = new ImageStack(width, height, cm);

          // save original file info
          fi = imp.getOriginalFileInfo();
        }

        // verify image is sane
        int w = imp.getWidth(), h = imp.getHeight(), t = imp.getType();
        if (w != width || h != height) {
          // current image dimension different than those in the stack
          log(filenames[i] + ": wrong dimensions (" + w + " x " + h +
            " instead of " + width + " x " + height + ")");
          continue;
        }
        if (t != type) {
          // current image file type different than those in the stack
          log(filenames[i] + ": wrong type (" +
            t + " instead of " + type + ")");
          continue;
        }

        // process every slice in each stack
        for (int j=1; j<=depth; j++) {
          imp.setSlice(j);
          stack.addSlice(imp.getTitle(), imp.getProcessor());
        }
      }

      if (stack == null || stack.getSize() == 0) {
        // all image files were invalid
        msg("No valid files found.");
        return;
      }
    }
    catch (OutOfMemoryError e) {
      IJ.outOfMemory("LociDataBrowser");
      if (stack != null) stack.trim();
    }
    if (DEBUG) {
      log("depth", depth);
      log("width", width);
      log("height", height);
      log("type", type);
      log("description", fi == null ? null : fi.description);
    }

    // populate names list
    names = new String[numFiles * depth];
    for (int i=0; i<filenames.length; i++) {
      Arrays.fill(names, depth * i, depth * (i + 1), filenames[i]);
    }
    if (DEBUG) log("names", names);

    // gather more pattern information
    BigInteger[] first = fp.getFirst();
    BigInteger[] last = fp.getLast();
    BigInteger[] step = fp.getStep();
    int[] count = fp.getCount();
    String[] pre = fp.getPrefixes();
    if (DEBUG) {
      log("first", first);
      log("last", last);
      log("step", step);
      log("count", count);
      log("pre", pre);
    }

    // compile axis lengths
    lengths = new int[count.length + 1];
    lengths[0] = depth;
    System.arraycopy(count, 0, lengths, 1, count.length);

    // skip singleton axes
    boolean[] match = new boolean[lengths.length];
    int remain = lengths.length;
    for (int i=0; i<match.length; i++) {
      if (lengths[i] == 1) {
        match[i] = true;
        remain--;
      }
    }

    // determine which axes are which
    zIndex = tIndex = cIndex = -1;
    for (int i=1; i<=pre.length; i++) {
      if (match[i]) continue;

      // remove trailing digits and capitalize
      String p = pre[i - 1].replaceAll("\\d+$", "");
      if (i == 1) name = p;
      p = p.toUpperCase();

      // check for Z endings
      if (zIndex < 0) {
        for (int j=0; j<PRE_Z.length; j++) {
          if (p.endsWith(PRE_Z[j])) {
            zIndex = i;
            match[i] = true;
            remain--;
            break;
          }
        }
        if (match[i]) continue;
      }

      // check for T endings
      if (tIndex < 0) {
        for (int j=0; j<PRE_T.length; j++) {
          if (p.endsWith(PRE_T[j])) {
            tIndex = i;
            match[i] = true;
            remain--;
            break;
          }
        }
        if (match[i]) continue;
      }

      // check for C endings
      if (cIndex < 0) {
        for (int j=0; j<PRE_C.length; j++) {
          if (p.endsWith(PRE_C[j])) {
            cIndex = i;
            match[i] = true;
            remain--;
            break;
          }
        }
        if (match[i]) continue;

        // check for 2-3 numbering (C)
        if (first[i - 1].intValue() == 2 &&
          last[i - 1].intValue() == 3 && step[i - 1].intValue() == 1)
        {
          cIndex = i;
          match[i] = true;
          remain--;
          break;
        }
      }
    }

    // assign as many remaining axes as possible
    for (int i=0; i<match.length; i++) {
      if (match[i]) continue;
      if (remain == 1) { // TZC
        // e.g., tubhiswt_C<1-2>.tiff, mri-stack.tif
        if (tIndex < 0) tIndex = i;
        else if (zIndex < 0) zIndex = i;
        else if (cIndex < 0) cIndex = i;
      }
      else { // ZTC
        // e.g., sdub<1-12>.pic, TAABA<1-45>.pic
        if (zIndex < 0) zIndex = i;
        else if (tIndex < 0) tIndex = i;
        else if (cIndex < 0) cIndex = i;
      }
    }

    hasZ = zIndex >= 0;
    hasT = tIndex >= 0;
    hasC = cIndex >= 0;
    numZ = hasZ ? lengths[zIndex] : 1;
    numT = hasT ? lengths[tIndex] : 1;
    numC = hasC ? lengths[cIndex] : 1;

    if (DEBUG) {
      log("lengths", lengths);
      log("zIndex = " + zIndex);
      log("tIndex = " + tIndex);
      log("cIndex = " + cIndex);
      log("hasZ = " + hasZ);
      log("hasT = " + hasT);
      log("hasC = " + hasC);
      log("numZ = " + numZ);
      log("numT = " + numT);
      log("numC = " + numC);
    }

    // strip off endings, if any, from name
    int zLen = PRE_Z.length, tLen = PRE_T.length, cLen = PRE_C.length;
    String[] endings = new String[zLen + tLen + cLen];
    System.arraycopy(PRE_Z, 0, endings, 0, zLen);
    System.arraycopy(PRE_T, 0, endings, zLen, tLen);
    System.arraycopy(PRE_C, 0, endings, zLen + tLen, cLen);
    name = strip(name, endings);

    // display image onscreen
    ImagePlus imp = new ImagePlus(name, stack);
    if (fi != null) imp.setFileInfo(fi);
    show(imp);
  }


  // -- Internal LociDataBrowser methods --

  protected String strip(String name, String[] endings) {
    String upper = name.toUpperCase();
    for (int i=0; i<endings.length; i++) {
      if (upper.endsWith(endings[i])) {
        name = name.substring(0, name.length() - endings[i].length());
        name = name.replaceAll("\\d+$", ""); // strip trailing digits
        upper = name.toUpperCase();
        i = -1; // start over
      }
    }
    return name;
  }

  protected void msg(String msg) { IJ.showMessage("LociDataBrowser", msg); }

  protected void log(String msg) { IJ.log("LociDataBrowser: " + msg); }

  protected void log(String var, Object val) {
    int len = -1;
    try { len = Array.getLength(val); }
    catch (Exception exc) { }

    if (len < 0) log(var + " = " + val); // single object
    else {
      StringBuffer sb = new StringBuffer(var);
      sb.append(" =");
      for (int i=0; i<len; i++) {
        sb.append(" ");
        sb.append(Array.get(val, i).toString());
      }
      log(sb.toString());
    }
  }

  protected void log(String var, int val) { log(var + " = " + val); }

}
