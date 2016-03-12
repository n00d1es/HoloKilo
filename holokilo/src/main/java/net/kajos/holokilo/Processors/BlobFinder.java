package net.kajos.holokilo.Processors;

import android.util.Log;
import net.kajos.holokilo.Config;
import net.kajos.holokilo.GLRenderer;

import java.util.concurrent.Semaphore;

public class BlobFinder
{
    private static final byte[] DISTINCT_ARRAY = {(byte)64, (byte)128, (byte)196, (byte)255};

    private int[] labelBufferArray;

    private int[] labelArray;
    private int[] xMinArray;
    private int[] xMaxArray;
    private int[] yMinArray;
    private int[] yMaxArray;
    private int[] massArray;
    private int[] reflArray;
    private int[] reflRArray;
    private int[] reflGArray;
    private int[] reflBArray;
    private int[] massRArray;
    private int[] massGArray;
    private int[] massBArray;

    private int width, height;

    private int bound;
    public BlobFinder(int width, int height)
    {
        bound = width * height / Config.PIXELS_REQUIRED_DIV;

        this.width = width;
        this.height = height;

        labelBufferArray = new int[width * height];

        final int arraySize = width * height / 4;

        xMinArray = new int[arraySize];
        xMaxArray = new int[arraySize];
        yMinArray = new int[arraySize];
        yMaxArray = new int[arraySize];
        massArray = new int[arraySize];
        massRArray = new int[arraySize];
        massGArray = new int[arraySize];
        massBArray = new int[arraySize];
        reflArray = new int[arraySize];
        reflRArray = new int[arraySize];
        reflGArray = new int[arraySize];
        reflBArray = new int[arraySize];
        labelArray = new int[arraySize];
    }

    public int getLowPass(byte[] array) {
        int max =  (array[0] & 0xff);

        for (int i = 1; i < array.length; i++) {
            int current = (array[i] & 0xff);
            if (current > max)
                max = current;

        }
        return max / 2;
    }

    public int get(int x, int y) {
        return labelArray[labelBufferArray[y * width + x]];
    }

    public int getPointer(int x, int y) {
        return y * width + x;
    }

    public int getByPointer(int p) {
        return labelArray[labelBufferArray[p]];
    }

    private static void shiftArray(int start, int[] array) {
        if (array.length == 1)
            return;

        for (int i = array.length-1; i > start; i--) {
            array[i] = array[i-1];
        }
    }

    private static void shiftArray(int start, float[] array) {
        if (array.length == 1)
            return;

        for (int i = array.length-1; i > start; i--) {
            array[i] = array[i-1];
        }
    }

    private static double[] hitPoint = new double[2];

    public static double[] getHitPoint() {
        return hitPoint;
    }

    public enum ColorCode { RED(0), BLUE(1), GREEN(2), UNKNOWN(3);

        private final int value;
        private ColorCode(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        } };

    private static int extractCode(int value) {
        // Codes:
        // Red: 0
        // Blue: 1
        // Green: 2
        // Unknown: 3

        return value >> 6;
    }

    private static int extractRefl(int value) {
        return value & 63;
    }

    private static Semaphore blobSem = new Semaphore(1);
    private static BlobBundle blobs = new BlobBundle();
    private static int prevStableReflections = -1;
    private static int prevStableBlobs = -1;

    public static void reset() {
        blobSem.drainPermits();
        blobSem.release(1);
        blobs.clear();
        prevStableReflections = -1;
        prevStableBlobs = -1;
    }

    public float[][] getBlobs(byte[] srcData, boolean hasFlashOff, boolean compareSizes) {
        // This is the neighbouring pixel pattern. For position X, A, B, C & D are checked
        // A B C
        // D X

        int ptr = 0;
        int aPtr = -width - 1;
        int bPtr = -width;
        int cPtr = -width + 1;
        int dPtr = -1;

        int label = 1;

        boolean trackOneColor = Config.TRACK_CODE != null;
        // Iterate through pixels looking for connected regions. Assigning labels
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                labelBufferArray[ptr] = 0;

                int intVal = srcData[ptr] & 0xff;
                int refl = extractRefl(intVal);
                int code = extractCode(intVal);

                // Check if on foreground pixel
                if (refl != 0 && (trackOneColor && (code == ColorCode.UNKNOWN.getValue() || code == Config.TRACK_CODE.getValue()))) {
                    // Find label for neighbours (0 if out of range)
                    int aLabel = (x > 0 && y > 0) ? labelArray[labelBufferArray[aPtr]] : 0;
                    int bLabel = (y > 0) ? labelArray[labelBufferArray[bPtr]] : 0;
                    int cLabel = (x < width - 1 && y > 0) ? labelArray[labelBufferArray[cPtr]] : 0;
                    int dLabel = (x > 0) ? labelArray[labelBufferArray[dPtr]] : 0;

                    // Look for label with least value
                    int min = Integer.MAX_VALUE;
                    if (aLabel != 0 && aLabel < min) {
                        min = aLabel;
                    }
                    if (bLabel != 0 && bLabel < min) {
                        min = bLabel;
                    }
                    if (cLabel != 0 && cLabel < min) {
                        min = cLabel;
                    }
                    if (dLabel != 0 && dLabel < min) {
                        min = dLabel;
                    }

                    // If no neighbours in foreground
                    if (min == Integer.MAX_VALUE) {
                        labelBufferArray[ptr] = label;
                        labelArray[label] = label;

                        // Initialise min/max x,y for label
                        yMinArray[label] = y;
                        yMaxArray[label] = y;
                        xMinArray[label] = x;
                        xMaxArray[label] = x;
                        massArray[label] = 1;
                        reflArray[label] = refl;
                        if (code == ColorCode.RED.getValue()) {
                            reflRArray[label] = refl;
                            reflGArray[label] = 0;
                            reflBArray[label] = 0;
                            massRArray[label] = 1;
                            massGArray[label] = 0;
                            massBArray[label] = 0;
                        } else if (code == ColorCode.GREEN.getValue()) {
                            reflRArray[label] = 0;
                            reflGArray[label] = refl;
                            reflBArray[label] = 0;
                            massRArray[label] = 0;
                            massGArray[label] = 1;
                            massBArray[label] = 0;
                        } else if (code == ColorCode.BLUE.getValue()) {
                            reflRArray[label] = 0;
                            reflGArray[label] = 0;
                            reflBArray[label] = refl;
                            massRArray[label] = 0;
                            massGArray[label] = 0;
                            massBArray[label] = 1;
                        } else if (code == ColorCode.UNKNOWN.getValue()) {
                            reflRArray[label] = 0;
                            reflGArray[label] = 0;
                            reflBArray[label] = 0;
                            massRArray[label] = 0;
                            massGArray[label] = 0;
                            massBArray[label] = 0;
                        }

                        label++;
                    }
                    // Neighbour found
                    else {
                        // Label pixel with lowest label from neighbours
                        labelBufferArray[ptr] = min;

                        // Update min/max x,y for label
                        yMaxArray[min] = y;
                        massArray[min]++;
                        reflArray[min]+=refl;
                        if (code == ColorCode.RED.getValue()) {
                            reflRArray[min] += refl;
                            massRArray[min]++;
                        } else if (code == ColorCode.GREEN.getValue()) {
                            reflGArray[min] += refl;
                            massGArray[min]++;
                        } else if (code == ColorCode.BLUE.getValue()) {
                            reflBArray[min] += refl;
                            massBArray[min]++;
                        }

                        if (x < xMinArray[min]) xMinArray[min] = x;
                        if (x > xMaxArray[min]) xMaxArray[min] = x;

                        if (aLabel != 0) labelArray[aLabel] = min;
                        if (bLabel != 0) labelArray[bLabel] = min;
                        if (cLabel != 0) labelArray[cLabel] = min;
                        if (dLabel != 0) labelArray[dLabel] = min;
                    }
                }

                ptr++;
                aPtr++;
                bPtr++;
                cPtr++;
                dPtr++;
            }
        }

        BlobBundle newBlobs = new BlobBundle();

        int edgeStartW = 0;
        int edgeStartH = 0;
        int edgeEndW = width - 1;
        int edgeEndH = height - 1;

        for (int i = label - 1; i > 0; i--) {
            if (labelArray[i] != i) {
                if (xMaxArray[i] > xMaxArray[labelArray[i]]) xMaxArray[labelArray[i]] = xMaxArray[i];
                if (xMinArray[i] < xMinArray[labelArray[i]]) xMinArray[labelArray[i]] = xMinArray[i];
                if (yMaxArray[i] > yMaxArray[labelArray[i]]) yMaxArray[labelArray[i]] = yMaxArray[i];
                if (yMinArray[i] < yMinArray[labelArray[i]]) yMinArray[labelArray[i]] = yMinArray[i];
                massArray[labelArray[i]] += massArray[i];
                reflArray[labelArray[i]] += reflArray[i];

                reflRArray[labelArray[i]] += reflRArray[i];
                reflGArray[labelArray[i]] += reflGArray[i];
                reflBArray[labelArray[i]] += reflBArray[i];

                massRArray[labelArray[i]] += massRArray[i];
                massGArray[labelArray[i]] += massGArray[i];
                massBArray[labelArray[i]] += massBArray[i];

                int l = i;
                while (l != labelArray[l]) l = labelArray[l];
                labelArray[i] = l;
            } else {
                if (xMinArray[i] > edgeStartW && yMinArray[i] > edgeStartH && xMaxArray[i] < edgeEndW && yMaxArray[i] < edgeEndH) {
                    if (massGArray[i] > bound) { //massRArray[i] > bound || massGArray[i] > bound || massBArray[i] > bound) {

                        int x = (xMaxArray[i] + xMinArray[i]) / 2;
                        int y = (yMaxArray[i] + yMinArray[i]) / 2;
                        Blob blob = new Blob();

                        blob.label = i;
                        blob.x = x;
                        blob.y = y;
                        blob.xMin = xMinArray[i];
                        blob.yMin = yMinArray[i];
                        blob.xMax = xMaxArray[i];
                        blob.yMax = yMaxArray[i];

                        blob.refl = reflArray[i];
                        blob.rRefl = reflRArray[i];
                        blob.gRefl = reflGArray[i];
                        blob.bRefl = reflBArray[i];

                        blob.mass = massArray[i];
                        blob.rMass = massRArray[i];
                        blob.gMass = massGArray[i];
                        blob.bMass = massBArray[i];
                        newBlobs.add(blob);
                    }
                }
            }
        }

        // Use the new blobs with the blobs of the previous frame to see
        // which have changed or moved.
        // A new blob is found to be the same as another if it closest to it.
        // Blobs which are new are regarded as not being retro reflective, but a
        // flash is requested to see if they are.
        try {
            blobSem.acquire();
            if (hasFlashOff) {
                for (int i = 0; i < newBlobs.size(); i++) {
                    Blob blob = newBlobs.get(i);
                    blob.isLight = true;
                }
                Log.d(Config.TAG, "Flashless blobs: " + newBlobs.size());
            } else {
                int reflections = 0;
                if (blobs.size() < newBlobs.size()) {
                    blobs.findNeighborsTo(newBlobs);
                    // Grew blobs
                    for (int i = 0; i < newBlobs.size(); i++) {
                        Blob blob = newBlobs.get(i);
                        blob.isLight = false;
                    }
                    for (int i = 0; i < blobs.size(); i++) {
                        Blob blob = blobs.get(i);
                        Blob closest = blobs.getNeighbor(blob);
                        if (closest != null) {
                            closest.isLight = blob.isLight;
                            if (!closest.isLight)
                                reflections++;
                        }
                    }
                } else {
                    newBlobs.findNeighborsTo(blobs);
                    // Lost or equal blobs
                    for (int i = 0; i < newBlobs.size(); i++) {
                        Blob blob = newBlobs.get(i);
                        Blob closest = newBlobs.getNeighbor(blob);
                        if (closest != null) {
                            blob.isLight = closest.isLight;
                            if (!blob.isLight)
                                reflections++;
                        }
                    }
                }
                if (compareSizes) {
                    int size = newBlobs.size();
                    if (size != 0 && (size > prevStableBlobs || reflections < prevStableReflections)) {
                        GLRenderer.requestFlash();
                    }
                    prevStableReflections = reflections;
                    prevStableBlobs = size;
                }
                blobs = newBlobs;
            }
            blobSem.release();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        float maxR[] = new float[Config.MAX_BLOBS];
        int maxRLabel[] = new int[Config.MAX_BLOBS];
        float maxG[] = new float[Config.MAX_BLOBS];
        int maxGLabel[] = new int[Config.MAX_BLOBS];
        float maxB[] = new float[Config.MAX_BLOBS];
        int maxBLabel[] = new int[Config.MAX_BLOBS];

        for (int i = 0; i < Config.MAX_BLOBS; i++) {
            maxR[i] = -1;
            maxRLabel[i] = -1;
            maxG[i] = -1;
            maxGLabel[i] = -1;
            maxB[i] = -1;
            maxBLabel[i] = -1;
        }

        for (int i = 0; i < blobs.size(); i++) {
            Blob blob = blobs.get(i);

            if (blob.isLight)
                continue;

            if (blob.gRefl > blob.rRefl && blob.gRefl > blob.bRefl) {
                for (int h = 0; h < Config.MAX_BLOBS; h++) {
                    if (maxG[h] < blob.gRefl) {
                        shiftArray(h, maxG);
                        shiftArray(h, maxGLabel);
                        maxG[h] = blob.gRefl;
                        maxGLabel[h] = blob.label;
                        break;
                    }
                }
            } else if (blob.rRefl > blob.gRefl && blob.rRefl > blob.bRefl) {
                for (int h = 0; h < Config.MAX_BLOBS; h++) {
                    if (maxR[h] < blob.rRefl) {
                        shiftArray(h, maxR);
                        shiftArray(h, maxRLabel);
                        maxR[h] = blob.rRefl;
                        maxRLabel[h] = blob.label;
                        break;
                    }
                }
            } else if (blob.bRefl > blob.gRefl && blob.bRefl > blob.rRefl) {
                for (int h = 0; h < Config.MAX_BLOBS; h++) {
                    if (maxB[h] < blob.bRefl) {
                        shiftArray(h, maxB);
                        shiftArray(h, maxBLabel);
                        maxB[h] = blob.bRefl;
                        maxBLabel[h] = blob.label;
                        break;
                    }
                }
            }
        }

        for (int i = label - 1; i > 0; i--){
            if (labelArray[i] != i){
                int l = i;
                while (l != labelArray[l]) l = labelArray[l];
                labelArray[i] = l;
            }
        }

        float resData[][] = new float[Config.MAX_BLOBS][3];

        for (int i = 0; i < Config.MAX_BLOBS; i++) {
            resData[i][0] = Float.NaN;
            resData[i][1] = Float.NaN;
            resData[i][2] = Float.NaN;
        }

        if (!hasFlashOff) {
            for (int i = 0; i < blobs.size(); i++) {
                Blob blob = blobs.get(i);

                int type = -1;
                if (blob.label == maxRLabel[0]) {
                    type = 1;
                } else if (blob.label == maxGLabel[0]) {
                    type = 0;
                } else if (blob.label == maxBLabel[0]) {
                    type = 2;
                }
                if (type == -1)
                    continue;

                int lbl = labelArray[blob.label];
                float avg[] = {0, 0};
                int rawCount = 0;
                int count = 0;

                int midx = blob.x;
                int midy = blob.y;

                float dist = 0f;

                for (int y = blob.yMin; y <= blob.yMax; y++) {
                    int p = getPointer(blob.xMin, y);
                    for (int x = blob.xMin; x <= blob.xMax; x++) {
                        if (getByPointer(p) == lbl) {
                            int orig = srcData[p] & 0xff;
                            int refl = extractRefl(orig);
                            count += refl;
                            avg[0] += (float) x * (float) refl;
                            avg[1] += (float) y * (float) refl;
                            int dx = x - midx;
                            int dy = y - midy;
                            dist += (float) Math.sqrt(dx * dx + dy * dy);
                            rawCount++;
                        }
                        p++;
                    }
                }

                dist /= (float) rawCount;
                avg[0] /= (float) count;
                avg[1] /= (float) count;

                resData[type][0] = avg[0];
                resData[type][1] = avg[1];
                resData[type][2] = dist;

                if (Config.DRAW_POINT && type == 0) {
                    hitPoint[0] = resData[type][0];
                    hitPoint[1] = resData[type][1];
                }
            }
        }

        if (Config.SHOW_DEBUG) {
            int length = width * height;
            outerloop:
            for (int i = 0; i < length; i++) {
                int x = i % width;
                int y = i / width;
                //srcData[i] = 0;
                int value = srcData[i] & 0xff;
                int code = extractCode(value);
                int refl = extractRefl(value);
                if (refl != 0 && code == ColorCode.UNKNOWN.getValue()) {
                    srcData[i] = DISTINCT_ARRAY[code];
                } else {
                    srcData[i] = 0;
                }
//                // Red
//                if (xMinArray[maxRLabel[0]] == x) {
//                    srcData[i] = (byte)0xff;
//                    continue outerloop;
//                }
//                if (xMaxArray[maxRLabel[0]] == x) {
//                    srcData[i] = (byte)0xff;
//                    continue outerloop;
//                }
//                if (yMinArray[maxRLabel[0]] == y) {
//                    srcData[i] = (byte)0xff;
//                    continue outerloop;
//                }
//                if (yMaxArray[maxRLabel[0]] == y) {
//                    srcData[i] = (byte)0xff;
//                    continue outerloop;
//                }
//                // Green
//                if (xMinArray[maxGLabel[0]] == x) {
//                    srcData[i] = (byte)0xff;
//                    continue outerloop;
//                }
//                if (xMaxArray[maxGLabel[0]] == x) {
//                    srcData[i] = (byte)0xff;
//                    continue outerloop;
//                }
//                if (yMinArray[maxGLabel[0]] == y) {
//                    srcData[i] = (byte)0xff;
//                    continue outerloop;
//                }
//                if (yMaxArray[maxGLabel[0]] == y) {
//                    srcData[i] = (byte)0xff;
//                    continue outerloop;
//                }
//                // Blue
//                if (xMinArray[maxBLabel[0]] == x) {
//                    srcData[i] = (byte)0xff;
//                    continue outerloop;
//                }
//                if (xMaxArray[maxBLabel[0]] == x) {
//                    srcData[i] = (byte)0xff;
//                    continue outerloop;
//                }
//                if (yMinArray[maxBLabel[0]] == y) {
//                    srcData[i] = (byte)0xff;
//                    continue outerloop;
//                }
//                if (yMaxArray[maxBLabel[0]] == y) {
//                    srcData[i] = (byte)0xff;
//                    continue outerloop;
//                }

//                int lab = labelBufferArray[i];
//
//                lab %= DISTINCT_ARRAY.length;
//
//                srcData[i] = DISTINCT_ARRAY[lab];
            }
        }

        return resData;
    }
}